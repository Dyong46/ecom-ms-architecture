package com.duong.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentRoutingTest {
    @Test
    void productionRoutesAndDiscoveryDoNotPublishPayment() throws Exception {
        var source = (EnumerablePropertySource<?>) new YamlPropertySourceLoader().load("gateway",
                new FileSystemResource("../config-server/src/main/resources/configurations/api-gateway.yml")).get(0);
        assertThat(Arrays.stream(source.getPropertyNames())
                .filter(name -> name.startsWith("spring.cloud.gateway.routes[") && name.endsWith(".uri"))
                .map(source::getProperty).toList()).doesNotContain("lb://PAYMENT-SERVICE");
        var expression = new SpelExpressionParser().parseExpression((String) source.getProperty(
                "spring.cloud.gateway.discovery.locator.include-expression"));
        assertThat(expression.getValue(new Service("payment-service"), Boolean.class)).isFalse();
        assertThat(expression.getValue(new Service("PAYMENT-SERVICE"), Boolean.class)).isFalse();
        assertThat(expression.getValue(new Service("notification-service"), Boolean.class)).isFalse();
        assertThat(expression.getValue(new Service("product-service"), Boolean.class)).isTrue();
    }

    public record Service(String serviceId) {
    }
}
