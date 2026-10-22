package com.duong.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

@SpringBootTest(properties = {
		"spring.config.import=optional:file:./config-not-required-for-tests.yml",
		"spring.cloud.config.enabled=false",
		"eureka.client.enabled=false"
})
class GatewayApplicationTests {

	@MockBean
	private ReactiveJwtDecoder reactiveJwtDecoder;

	@Test
	void contextLoads() {
	}

}
