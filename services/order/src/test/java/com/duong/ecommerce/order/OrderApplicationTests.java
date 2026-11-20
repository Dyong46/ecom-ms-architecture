package com.duong.ecommerce.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest(properties = {
		"spring.cloud.config.enabled=false",
        "integration-config.ecom.oauth-base-url=http://localhost:9098",
        "integration-config.ecom.oauth-realm=micro-services",
        "integration-config.ecom.oauth-username=ecom-services",
        "integration-config.ecom.oauth-password=test-only-secret",
		"eureka.client.enabled=false",
		"spring.datasource.url=jdbc:h2:mem:orders;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.flyway.enabled=true",
		"spring.kafka.admin.auto-create=false",
		"application.config.customer-url=http://localhost/internal/customers",
		"application.config.payment-url=http://localhost/internal/payments",
		"application.config.product-url=http://localhost/internal/products"
})
class OrderApplicationTests {
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.duong.module.proxy.customer.CustomerClient customers;
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.duong.module.proxy.product.ProductClient products;
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.duong.ecommerce.order.integration.OrderPaymentGateway payments;
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.duong.ecommerce.order.integration.kafka.producer.OrderProducer events;
	@Autowired
	private MockMvc mvc;

	@Test
	void contextLoads() {
	}

	@Test
	void orderApiRemainsAccessibleWithoutInboundAuthentication() throws Exception {
		mvc.perform(get("/api/v1/orders"))
				.andExpect(status().isOk());
	}


    @Autowired private com.duong.ecommerce.order.repository.OrderRepository orders;
    @Autowired private com.duong.ecommerce.order.orderline.service.OrderLineService lines;
    @Autowired private jakarta.persistence.EntityManager entityManager;

    @Test
    @org.springframework.transaction.annotation.Transactional
    void persistsSkuSnapshotWithUuidReferencesAndJsonOptions() {
        var order = orders.saveAndFlush(com.duong.ecommerce.order.entity.Order.builder()
                .reference("SNAPSHOT-" + java.util.UUID.randomUUID()).totalAmount(java.math.BigDecimal.TEN)
                .paymentMethod(com.duong.ecommerce.order.entity.enumeration.PaymentMethodEnum.PAYPAL)
                .customerId("customer-1").build());
        var productId = java.util.UUID.randomUUID().toString();
        var skuId = java.util.UUID.randomUUID().toString();
        lines.saveOrderLine(new com.duong.ecommerce.order.orderline.payload.request.OrderLineRequest(
                null, order.getId(), productId, skuId, "PHONE-BLUE", "Phone",
                java.util.Map.of("Color", "Blue"), java.math.BigDecimal.TEN, 1));
        entityManager.flush(); entityManager.clear();
        var saved = lines.findAllByOrderId(order.getId()).get(0);
        org.assertj.core.api.Assertions.assertThat(saved.productId()).isEqualTo(productId);
        org.assertj.core.api.Assertions.assertThat(saved.skuId()).isEqualTo(skuId);
        org.assertj.core.api.Assertions.assertThat(saved.options()).containsEntry("Color", "Blue");
        org.assertj.core.api.Assertions.assertThat(saved.unitPrice()).isEqualByComparingTo("10.00");
        org.assertj.core.api.Assertions.assertThat(saved.variantName()).isEqualTo("Color: Blue");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void createsAndReadsSnapshotsWithoutReloadingCustomerOrProduct() throws Exception {
        var customer = new com.duong.module.proxy.customer.response.CustomerResponse(
                "customer-1", "Ada", "Lovelace", "ada@example.test");
        var skuId = "00000000-0000-4000-8000-000000000001";
        org.mockito.Mockito.when(customers.findCustomerById("customer-1")).thenReturn(java.util.Optional.of(
                new com.duong.module.proxy.response.ServiceResponse<>(customer, null)));
        org.mockito.Mockito.when(products.purchaseResponses(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(
                new com.duong.module.proxy.response.ServiceResponse<>(java.util.List.of(
                        new com.duong.module.proxy.product.response.PurchaseResponse(
                                "00000000-0000-4000-8000-000000000002", skuId, "PHONE-BLUE", "Original Phone", "",
                                java.util.Map.of("Size", "M", "Color", "Blue"), new java.math.BigDecimal("25.00"), 2)), null));
        org.mockito.Mockito.when(payments.requestOrderPayment(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(42);
        var result = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/orders").header("Idempotency-Key", "test-key")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("""
                                {"id":99999,"reference":"FULL-SNAPSHOT","paymentMethod":"PAYPAL","customerId":"customer-1",
                                 "recipientFullName":"Different Person","recipientPhone":"+84901234567",
                                 "shippingAddress":{"addressLine1":"12 Main","ward":"Ward","city":"City","countryCode":"VN"},
                                 "deliveryNote":"Call on arrival","customerFullName":"Forged Buyer","shippingFee":999,
                                 "itemsSubtotal":1,"currencyCode":"USD",
                                 "products":[{"skuId":"00000000-0000-4000-8000-000000000001","quantity":2}]}
                                """))
                .andExpect(status().isOk()).andReturn();
        int orderId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.getResponse().getContentAsString()).get("data").asInt();
        org.assertj.core.api.Assertions.assertThat(orderId).isNotEqualTo(99999);
        entityManager.flush();
        entityManager.clear();
        org.mockito.Mockito.reset(customers, products);
        mvc.perform(get("/api/v1/orders/" + orderId)).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.customerFullName").value("Ada Lovelace"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.customerEmail").value("ada@example.test"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.recipientFullName").value("Different Person"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.recipientPhone").value("+84901234567"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.shippingAddress.addressLine1").value("12 Main"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.deliveryNote").value("Call on arrival"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.itemsSubtotal").value(50))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.shippingFee").value(0))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.amount").value(50))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.currencyCode").value("VND"));
        mvc.perform(get("/api/v1/order-lines/order/" + orderId)).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data[0].name").value("Original Phone"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data[0].variantName").value("Color: Blue / Size: M"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data[0].quantity").value(2));
        org.mockito.Mockito.verifyNoInteractions(customers, products);
        var payment = org.mockito.ArgumentCaptor.forClass(com.duong.module.proxy.payment.request.PaymentRequest.class);
        org.mockito.Mockito.verify(payments).requestOrderPayment(org.mockito.ArgumentMatchers.startsWith("payment:"), payment.capture());
        org.assertj.core.api.Assertions.assertThat(payment.getValue().amount()).isEqualByComparingTo("50.00");
    }

    @Test
    void rejectsFractionalSkuQuantityBeforeCallingProduct() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/orders").header("Idempotency-Key", "test-key")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""
                    {"reference":"FRACTIONAL","paymentMethod":"PAYPAL","customerId":"customer-1",
                         "recipientFullName":"Recipient","recipientPhone":"+84901234567",
                         "shippingAddress":{"addressLine1":"12 Main","ward":"Ward","city":"City","countryCode":"VN"},
                     "products":[{"skuId":"00000000-0000-4000-8000-000000000001","quantity":1.5}]}
                    """))
                .andExpect(status().isBadRequest());
    }
}
