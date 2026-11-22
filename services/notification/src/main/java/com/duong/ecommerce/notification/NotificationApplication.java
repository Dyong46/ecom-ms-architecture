package com.duong.ecommerce.notification;

import com.duong.module.mail.annotation.EnableMailModule;
import org.springframework.boot.SpringApplication;
import com.duong.module.kafka.annotation.EnableKafkaModule;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableKafkaModule
@EnableMailModule
public class NotificationApplication {

	public static void main(String[] args) {
		SpringApplication.run(NotificationApplication.class, args);
	}

}
