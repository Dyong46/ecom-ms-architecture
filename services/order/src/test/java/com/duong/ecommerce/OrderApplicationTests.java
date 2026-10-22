package com.duong.ecommerce;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.cloud.config.enabled=false",
		"eureka.client.enabled=false",
		"spring.datasource.url=jdbc:h2:mem:orders;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.flyway.enabled=true",
		"spring.kafka.admin.auto-create=false",
		"application.config.customer-url=http://localhost/customers",
		"application.config.payment-url=http://localhost/payments",
		"application.config.product-url=http://localhost/products"
})
class OrderApplicationTests {

	@Test
	void contextLoads() {
	}

}
