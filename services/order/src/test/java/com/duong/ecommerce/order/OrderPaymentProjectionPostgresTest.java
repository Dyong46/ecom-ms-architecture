package com.duong.ecommerce.order;

import com.duong.ecommerce.order.integration.kafka.consumer.OrderPaymentEventConsumer;
import com.duong.ecommerce.order.payload.event.PaymentSucceeded;
import com.duong.ecommerce.order.integration.OrderPaymentGateway;
import com.duong.ecommerce.order.integration.kafka.producer.OrderProducer;
import com.duong.module.proxy.customer.CustomerClient;
import com.duong.module.proxy.product.ProductClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties={"spring.cloud.config.enabled=false","eureka.client.enabled=false",
        "order.payment-events.enabled=true","order.payment-events.listener-enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate","spring.kafka.admin.auto-create=false",
        "integration-config.ecom.oauth-base-url=http://127.0.0.1:1","integration-config.ecom.oauth-realm=test",
        "integration-config.ecom.oauth-username=test","integration-config.ecom.oauth-password=test-only",
        "application.config.customer-url=http://127.0.0.1:1","application.config.product-url=http://127.0.0.1:1",
        "application.config.payment-url=http://127.0.0.1:1"})
@Testcontainers
class OrderPaymentProjectionPostgresTest {
    @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);
        r.add("spring.datasource.password",postgres::getPassword);r.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
    }
    @MockBean CustomerClient customers;
    @MockBean ProductClient products;
    @MockBean OrderPaymentGateway payments;
    @MockBean OrderProducer events;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderPaymentEventConsumer consumer;

    @Test void paidProjectionIsIdempotentValidatedAndIndependentOfCreationProgress() {
        String reference=UUID.randomUUID().toString();
        jdbc.update("insert into customer_order(reference,total_amount,payment_method,customer_id,currency_code,create_at,creation_status,payment_status) "
                + "values (?,50000,'CREDIT_CARD','c1','VND',current_timestamp,'PROCESSING','PENDING')",reference);
        int id=jdbc.queryForObject("select id from customer_order where reference=?",Integer.class,reference);
        var good=new PaymentSucceeded(UUID.randomUUID().toString(),42,id,reference,new BigDecimal("50000"),"VND");
        consumer.receive(good);consumer.receive(good);
        assertThat(jdbc.queryForObject("select payment_status from customer_order where id=?",String.class,id)).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("select creation_status from customer_order where id=?",String.class,id)).isEqualTo("PROCESSING");
        assertThat(jdbc.queryForObject("select count(*) from order_payment_event_inbox where event_id=?",Integer.class,good.eventId())).isEqualTo(1);
        var wrong=new PaymentSucceeded(UUID.randomUUID().toString(),43,id,reference,new BigDecimal("1"),"USD");
        consumer.receive(wrong);
        assertThat(jdbc.queryForObject("select status from order_payment_event_inbox where event_id=?",String.class,wrong.eventId())).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("select payment_status from customer_order where id=?",String.class,id)).isEqualTo("SUCCEEDED");
    }
}
