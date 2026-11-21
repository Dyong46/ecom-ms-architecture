package com.duong.ecommerce.order;

import com.duong.ecommerce.order.entity.enumeration.PaymentMethodEnum;
import com.duong.ecommerce.order.integration.OrderPaymentGateway;
import com.duong.ecommerce.order.integration.kafka.producer.OrderProducer;
import com.duong.ecommerce.order.payload.request.OrderRequest;
import com.duong.ecommerce.order.payload.request.ShippingAddressRequest;
import com.duong.ecommerce.order.service.OrderCheckoutService;
import com.duong.module.common.exception.ConflictException;
import com.duong.module.proxy.customer.CustomerClient;
import com.duong.module.proxy.customer.response.CustomerResponse;
import com.duong.module.proxy.product.ProductClient;
import com.duong.module.proxy.product.request.PurchaseRequest;
import com.duong.module.proxy.product.response.PurchaseResponse;
import com.duong.module.proxy.response.ServiceResponse;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"spring.cloud.config.enabled=false", "order.payment-events.enabled=false", "eureka.client.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.kafka.admin.auto-create=false",
        "integration-config.ecom.oauth-base-url=http://127.0.0.1:1", "integration-config.ecom.oauth-realm=test",
        "integration-config.ecom.oauth-username=test", "integration-config.ecom.oauth-password=test-only",
        "application.config.customer-url=http://127.0.0.1:1", "application.config.product-url=http://127.0.0.1:1",
        "application.config.payment-url=http://127.0.0.1:1"})
@Testcontainers
class OrderIdempotencyPostgresTest {
    private static final String SKU = "00000000-0000-4000-8000-000000000001";
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired
    OrderCheckoutService checkout;
    @Autowired
    JdbcTemplate jdbc;
    @MockBean
    CustomerClient customers;
    @MockBean
    ProductClient products;
    @MockBean
    OrderPaymentGateway payments;
    @MockBean
    OrderProducer events;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @BeforeEach
    void downstream() {
        when(customers.findCustomerById("c1")).thenReturn(Optional.of(new ServiceResponse<>(
                new CustomerResponse("c1", "Ada", "Lovelace", "ada@example.test"), null)));
        when(products.purchaseResponses(anyString(), any())).thenReturn(new ServiceResponse<>(List.of(
                new PurchaseResponse("00000000-0000-4000-8000-000000000002", SKU, "SKU", "Original Product", "",
                        java.util.Map.of("Color", "Blue"), new BigDecimal("25.00"), 2)), null));
    }

    private OrderRequest request(String reference, String recipient) {
        return new OrderRequest(null, reference, PaymentMethodEnum.CREDIT_CARD, "c1", List.of(new PurchaseRequest(SKU, 2)),
                recipient, "+84901234567", new ShippingAddressRequest("12 Main", null, "Ward", "City", null, "VN"), null);
    }

    @Test
    void paymentTimeoutResumesSameDraftAndSameOutboundKeyWithoutRepurchasing() {
        var key = UUID.randomUUID().toString();
        var reference = UUID.randomUUID().toString();
        when(payments.requestOrderPayment(anyString(), any())).thenThrow(new IllegalStateException("response lost"))
                .thenReturn(77);
        assertThatThrownBy(() -> checkout.create(key, request(reference, "Recipient"))).isInstanceOf(IllegalStateException.class);
        int orderId = jdbc.queryForObject("select id from customer_order where reference=?", Integer.class, reference);
        assertThat(jdbc.queryForObject("select creation_status from customer_order where id=?", String.class, orderId)).isEqualTo("PROCESSING");
        assertThatThrownBy(() -> checkout.create(key, request(reference, "Different"))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> checkout.create(UUID.randomUUID().toString(), request(reference, "Recipient")))
                .isInstanceOf(ConflictException.class);
        assertThat(checkout.create(key, request(reference, "Recipient"))).isEqualTo(orderId);
        assertThat(checkout.create(key, request(reference, "Recipient"))).isEqualTo(orderId);
        assertThat(jdbc.queryForObject("select creation_status from customer_order where id=?", String.class, orderId)).isEqualTo("COMPLETED");
        verify(customers, times(1)).findCustomerById("c1");
        verify(products, times(1)).purchaseResponses(startsWith("purchase:"), any());
        var keys = org.mockito.ArgumentCaptor.forClass(String.class);
        var payloads = org.mockito.ArgumentCaptor.forClass(com.duong.module.proxy.payment.request.PaymentRequest.class);
        verify(payments, times(2)).requestOrderPayment(keys.capture(), payloads.capture());
        assertThat(keys.getAllValues().get(0)).isEqualTo(keys.getAllValues().get(1));
        assertThat(payloads.getAllValues().get(0)).isEqualTo(payloads.getAllValues().get(1));
        assertThat(payloads.getValue().orderId()).isEqualTo(orderId);
        verify(events, times(1)).sendOrderConfirmation(any());
    }

    @Test
    void concurrentCheckoutReturnsProcessingWithoutCallingPaymentTwice() throws Exception {
        var key = UUID.randomUUID().toString();
        var request = request(UUID.randomUUID().toString(), "Recipient");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(payments.requestOrderPayment(anyString(), any())).thenAnswer(call -> {
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
            return 77;
        });
        var executor = Executors.newSingleThreadExecutor();
        try {
            var first = executor.submit(() -> checkout.create(key, request));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> checkout.create(key, request)).isInstanceOf(ConflictException.class);
            release.countDown();
            int id = first.get(10, TimeUnit.SECONDS);
            assertThat(checkout.create(key, request)).isEqualTo(id);
            verify(payments, times(1)).requestOrderPayment(anyString(), any());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void legacyReferenceWithoutLedgerIsRejectedBeforePurchasing() {
        var reference = UUID.randomUUID().toString();
        jdbc.update("insert into customer_order(reference,total_amount,payment_method,customer_id,create_at) "
                + "values (?, 25, 'PAYPAL', 'c1', current_timestamp)", reference);
        assertThatThrownBy(() -> checkout.create(UUID.randomUUID().toString(), request(reference, "Recipient")))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(customers, products, payments, events);
    }
}
