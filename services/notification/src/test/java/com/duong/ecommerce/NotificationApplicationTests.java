package com.duong.ecommerce;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.cloud.config.enabled=false",
		"eureka.client.enabled=false",
		"spring.kafka.listener.auto-startup=false",
		"spring.mail.host=localhost",
		"spring.mail.port=1025"
})
class NotificationApplicationTests {

	@Test
	void contextLoads() {
	}

}
