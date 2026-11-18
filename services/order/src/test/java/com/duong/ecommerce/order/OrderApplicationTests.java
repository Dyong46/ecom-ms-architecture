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
    }

    @Test
    void rejectsFractionalSkuQuantityBeforeCallingProduct() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/orders")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""
                    {"reference":"FRACTIONAL","paymentMethod":"PAYPAL","customerId":"customer-1",
                     "products":[{"skuId":"00000000-0000-4000-8000-000000000001","quantity":1.5}]}
                    """))
                .andExpect(status().isBadRequest());
    }
}
