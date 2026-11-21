package com.duong.gateway.security;

import com.duong.gateway.handler.ProblemDetailsHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.net.URI;

class InternalApiBoundaryWebFilterTest {
    private final WebTestClient client = WebTestClient.bindToWebHandler(exchange -> {
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        return Mono.empty();
    }).webFilter(new InternalApiBoundaryWebFilter(new ProblemDetailsHandler(new ObjectMapper()))).build();

    @ParameterizedTest
    @ValueSource(strings = {"/internal/products/purchase", "/PRODUCT-SERVICE/internal/products/purchase",
            "/product-service/%69nternal/products/purchase", "/product-service/internal;v=1/products/purchase",
            "/product-service/INTERNAL/products/purchase", "/product-service/internal%2Fproducts/purchase",
            "/internal/payments", "/PAYMENT-SERVICE/internal/payments", "/payment-service/%69nternal/payments"})
    void deniesInternalPathsIncludingDiscoveryAndEncodedVariants(String path) {
        client.post().uri(URI.create(path)).header("Authorization", "Bearer dummy")
                .exchange().expectStatus().isNotFound();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/orders", "/api/v1/products", "/api/public/v1/products",
            "/actuator/prometheus", "/api/v1/internalization"})
    void keepsExistingNonInternalPaths(String path) {
        client.get().uri(path).exchange().expectStatus().isOk();
    }
}
