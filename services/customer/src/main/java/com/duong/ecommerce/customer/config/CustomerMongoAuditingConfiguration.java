package com.duong.ecommerce.customer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * Date auditing is local to Customer; trusted auditor resolution is added with service authentication.
 */
@Configuration(proxyBeanMethods = false)
@EnableMongoAuditing
public class CustomerMongoAuditingConfiguration {
}
