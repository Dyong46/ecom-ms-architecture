package com.duong.ecommerce;

import org.springframework.boot.SpringApplication;
import com.duong.module.kafka.annotation.EnableKafkaModule;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableKafkaModule
@EnableAsync
public class NotificationApplication {

	public static void main(String[] args) {
		SpringApplication.run(NotificationApplication.class, args);
	}

}
