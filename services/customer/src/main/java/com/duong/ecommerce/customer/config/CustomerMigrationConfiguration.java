package com.duong.ecommerce.customer.config;

import io.mongock.runner.springboot.EnableMongock;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableMongock
public class CustomerMigrationConfiguration {
}
