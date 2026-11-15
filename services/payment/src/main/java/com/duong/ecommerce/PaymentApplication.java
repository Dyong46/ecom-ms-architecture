package com.duong.ecommerce;

import org.springframework.boot.SpringApplication;
import com.duong.module.kafka.annotation.EnableKafkaModule;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableKafkaModule
@EnableJpaAuditing
public class PaymentApplication {

	public static void main(String[] args) {
		SpringApplication.run(PaymentApplication.class, args);
	}

}
