package com.duong.ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.duong.module.proxy.annotation.EnableProxyModule;
import com.duong.module.proxy.customer.CustomerClient;
import com.duong.module.proxy.product.ProductClient;
import com.duong.module.proxy.payment.PaymentClient;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableProxyModule(clients = {CustomerClient.class, ProductClient.class, PaymentClient.class})
@EnableJpaAuditing
public class OrderApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrderApplication.class, args);
	}

}
