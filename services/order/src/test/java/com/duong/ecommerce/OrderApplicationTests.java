package com.duong.ecommerce;

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

}
