package com.duong.gateway.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** Real Gateway HTTP forwarding against an isolated Payment endpoint fixture. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.location=file:../config-server/src/main/resources/configurations/api-gateway.yml",
        "spring.application.name=api-gateway",
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false"
})
class StripeWebhookRoutingTest {
    private static HttpServer payment;
    private static final List<Delivery> deliveries = new CopyOnWriteArrayList<>();
    private static volatile int paymentStatus = 200;

    @DynamicPropertySource
    static void paymentEndpoint(DynamicPropertyRegistry registry) throws Exception {
        payment = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        payment.createContext("/webhooks/stripe", exchange -> {
            deliveries.add(new Delivery(exchange.getRequestURI().getPath(), exchange.getRequestMethod(),
                    exchange.getRequestHeaders().getFirst("Stripe-Signature"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            exchange.sendResponseHeaders(paymentStatus, -1);
            exchange.close();
        });
        payment.start();
        // Supply a local discovery instance; keep the production lb:// route unchanged.
        registry.add("spring.cloud.discovery.client.simple.instances.PAYMENT-SERVICE[0].uri", () -> "http://127.0.0.1:" + payment.getAddress().getPort());
    }

    @MockBean ReactiveJwtDecoder jwt;
    @Autowired org.springframework.core.env.Environment environment;
    private WebTestClient client;

    @BeforeEach
    void setup() {
        deliveries.clear();
        paymentStatus = 200;
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + environment.getProperty("local.server.port")).build();
    }

    @AfterAll
    static void stop() {
        payment.stop(0);
    }

    @Test
    void forwardsRawBodyAndSignatureWithoutClientJwt() {
        String raw = "{\n  \"id\": \"evt_fixture\", \"data\": {\"value\": \"Tiếng Việt\"}\n}";
        String signature = "t=123,v1=fixture";
        client.post().uri("/webhooks/stripe").header("Content-Type", "application/json")
                .header("Stripe-Signature", signature).bodyValue(raw).exchange().expectStatus().isOk();
        assertThat(deliveries).containsExactly(new Delivery("/webhooks/stripe", "POST", signature, raw));
    }

    @Test
    void propagatesPaymentSignatureRejection() {
        paymentStatus = 400;
        client.post().uri("/webhooks/stripe").header("Content-Type", "application/json")
                .header("Stripe-Signature", "invalid").bodyValue("{}").exchange().expectStatus().isBadRequest();
        assertThat(deliveries).hasSize(1);
    }

    @Test
    void keepsInternalEndpointsAndOtherWebhookPathsProtected() {
        for (String path : List.of("/internal/payments", "/PAYMENT-SERVICE/internal/payments",
                "/NOTIFICATION-SERVICE/internal/jobs")) {
            client.post().uri(path).exchange().expectStatus().isNotFound();
        }
        client.get().uri("/webhooks/stripe").exchange().expectStatus().isUnauthorized();
        client.post().uri("/webhooks/stripe/extra").exchange().expectStatus().isUnauthorized();
        assertThat(deliveries).isEmpty();
    }

    private record Delivery(String path, String method, String signature, String raw) { }
}
