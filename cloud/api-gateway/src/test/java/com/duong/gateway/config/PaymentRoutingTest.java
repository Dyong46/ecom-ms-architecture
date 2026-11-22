package com.duong.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.io.FileSystemResource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentRoutingTest {
    @Test
    void routesStripeWebhookToPaymentAndDoesNotExcludeServicesFromDiscovery() throws Exception {
        var source = (EnumerablePropertySource<?>) new YamlPropertySourceLoader().load("gateway",
                new FileSystemResource("../config-server/src/main/resources/configurations/api-gateway.yml")).get(0);
        assertThat(Arrays.stream(source.getPropertyNames())
                .filter(name -> name.startsWith("spring.cloud.gateway.routes[") && name.endsWith(".uri"))
                .map(source::getProperty).toList()).contains("lb://PAYMENT-SERVICE");
        assertThat(source.getProperty("spring.cloud.gateway.discovery.locator.enabled")).isEqualTo(true);
        assertThat(source.getProperty("spring.cloud.gateway.discovery.locator.include-expression")).isNull();
        assertThat(source.getProperty("spring.cloud.gateway.routes[0].id")).isEqualTo("stripe-webhook");
        assertThat(source.getProperty("spring.cloud.gateway.routes[0].uri")).isEqualTo("lb://PAYMENT-SERVICE");
        assertThat(source.getProperty("spring.cloud.gateway.routes[0].predicates[0]")).isEqualTo("Path=/webhooks/stripe");
        assertThat(source.getProperty("spring.cloud.gateway.routes[0].predicates[1]")).isEqualTo("Method=POST");
    }
}
