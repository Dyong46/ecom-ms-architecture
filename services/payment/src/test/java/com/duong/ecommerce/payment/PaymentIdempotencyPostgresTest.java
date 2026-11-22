package com.duong.ecommerce.payment;

import com.duong.ecommerce.payment.entity.enumeration.PaymentMethodEnum;
import com.duong.ecommerce.payment.integration.idempotency.PostgresIdempotencyStore;
import com.duong.ecommerce.payment.mapper.PaymentMapperImpl;
import com.duong.ecommerce.payment.payload.request.Customer;
import com.duong.ecommerce.payment.payload.request.PaymentRequest;
import com.duong.ecommerce.payment.service.PaymentCreationService;
import com.duong.ecommerce.payment.service.PaymentService;
import com.duong.ecommerce.payment.service.PaymentAttemptService;
import com.duong.ecommerce.payment.service.PaymentSettlementService;
import com.duong.ecommerce.payment.integration.stripe.StripeCheckoutClient;
import com.duong.module.common.exception.ConflictException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {"spring.cloud.config.enabled=false", "eureka.client.enabled=false", "spring.jpa.hibernate.ddl-auto=validate", "payment.stripe.enabled=true", "payment.stripe.secret-key=sk_test_fixture", "payment.stripe.webhook-secret=whsec_fixture"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PaymentAttemptService.class,PaymentSettlementService.class,StripeCheckoutClient.class,PaymentService.class, PaymentMapperImpl.class, PaymentCreationService.class, PostgresIdempotencyStore.class,
        PaymentIdempotencyPostgresTest.Json.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class PaymentIdempotencyPostgresTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired
    PaymentCreationService creation;
    @Autowired
    PaymentService payments;
    @Autowired
    PostgresIdempotencyStore ledger;
    @Autowired
    JdbcTemplate jdbc;
    @MockBean
    com.duong.module.kafka.service.KafkaPublisher publisher;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    private PaymentRequest request(int orderId, String amount) {
        return new PaymentRequest(new BigDecimal(amount), PaymentMethodEnum.CREDIT_CARD, orderId, "ORDER-" + orderId,
                new Customer("c1", "Ada", "Lovelace", "ada@example.test"));
    }

    @Test
    void replaysPaymentAndNormalizesDecimalScaleWithoutAnotherNotification() {
        String key = UUID.randomUUID().toString();
        int id = creation.create(key, request(101, "25.00"));
        assertThat(creation.create(key, request(101, "25"))).isEqualTo(id);
        assertThat(jdbc.queryForObject("select count(*) from payment where order_id=101", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from payment_outbox where payment_id=?",Integer.class,id)).isZero();
        assertThatThrownBy(() -> creation.create(key, request(101, "26.00"))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> creation.create(UUID.randomUUID().toString(), request(101, "25.00")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rollbackDoesNotLeavePaymentOrPublishNotification() {
        String key = UUID.randomUUID().toString();
        assertThatThrownBy(() -> ledger.execute(key, request(102, "0.00"), new TypeReference<Integer>() {
        }, operation -> {
            payments.createPayment(request(102, "0.00"));
            throw new IllegalStateException("crash before commit");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select count(*) from payment where order_id=102", Integer.class)).isZero();
        verifyNoInteractions(publisher);
        assertThat(jdbc.queryForObject("select count(*) from payment_outbox",Integer.class)).isZero();
        assertThat(creation.create(key, request(102, "0.00"))).isPositive();
        assertThat(jdbc.queryForObject("select count(*) from payment_outbox",Integer.class)).isEqualTo(1);
    }

    @TestConfiguration
    @org.springframework.boot.context.properties.EnableConfigurationProperties(com.duong.ecommerce.payment.config.StripeSettings.class)
    static class Json {
        @Bean
        ObjectMapper json() {
            return new ObjectMapper();
        }
    }
}
