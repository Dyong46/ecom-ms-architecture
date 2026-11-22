package com.duong.ecommerce.payment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.cloud.config.enabled=false",
        "payment.workers.enabled=false",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9098/realms/micro-services",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:9098/realms/micro-services/protocol/openid-connect/certs",
		"eureka.client.enabled=false",
		"spring.datasource.url=jdbc:h2:mem:payment;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.flyway.enabled=true",
		"spring.kafka.admin.auto-create=false"
})
class PaymentApplicationTests {

	@Test
	void contextLoads() {
	}

}
