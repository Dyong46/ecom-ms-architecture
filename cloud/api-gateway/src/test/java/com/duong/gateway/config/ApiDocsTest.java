package com.duong.gateway.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.config.location=classpath:/application-local.yml",
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:1/jwks"
})
@AutoConfigureWebTestClient
class ApiDocsTest {
    @Nested
    @ActiveProfiles("local")
    class Local {
        @Autowired
        WebTestClient client;

        @ParameterizedTest
        @CsvSource({
                "product, /api/public/v1/products/search:",
                "customer, /api/v1/customers:",
                "order, /api/v1/order-lines/order/{orderId}:",
                "payment, /api/v1/payments:"
        })
        void servesSwaggerAndItsClientContractWithoutAuthentication(String service, String endpoint) {
            String path = "/docs/" + service;
            client.get().uri(path).exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                    .expectBody(String.class).value(body -> assertThat(body)
                            .contains("/docs/swagger-ui.css", "/docs/swagger-ui-bundle.js",
                                    "/docs/swagger-initializer.js"));
            client.get().uri(path + "/openapi.yaml").exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentType("application/yaml")
                    .expectBody(String.class).value(body -> assertThat(body)
                            .contains(endpoint, "bearerAuth:")
                            .doesNotContain("/internal/", "/api/callbacks/", "/search/reindex"));
        }

        @Test
        void servesSharedSwaggerAssetsAndRejectsUndocumentedServices() {
            client.get().uri("/docs/swagger-ui.css").exchange().expectStatus().isOk();
            client.get().uri("/docs/swagger-ui-bundle.js").exchange().expectStatus().isOk();
            client.get().uri("/docs/swagger-initializer.js").exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class).value(body -> assertThat(body)
                            .contains("SwaggerUIBundle", "window.location.pathname", "/openapi.yaml"));
            client.get().uri("/docs/notification/openapi.yaml").exchange().expectStatus().isNotFound();
        }

        @ParameterizedTest
        @ValueSource(strings = {"/api/v1/products", "/api/v1/customers", "/api/v1/orders", "/api/v1/payments"})
        void keepsClientWritesProtected(String endpoint) {
            client.post().uri(endpoint).exchange().expectStatus().isUnauthorized();
        }
    }

    @Nested
    @ActiveProfiles("default")
    class Default {
        @Autowired
        ApplicationContext context;
        @Autowired
        WebTestClient client;

        @Test
        void leavesDocsUnavailableWithoutLocalProfile() {
            assertThat(context.containsBean("apiDocs")).isFalse();
            client.get().uri("/docs/customer/openapi.yaml").exchange().expectStatus().isUnauthorized();
        }
    }
}
