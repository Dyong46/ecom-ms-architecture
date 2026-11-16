package com.duong.ecommerce.order.config;

import com.duong.module.caching.entity.enumeration.CacheScopeEnum;

import com.duong.module.caching.core.*;
import com.duong.ecommerce.order.config.OrderOAuthConfiguration;
import com.duong.ecommerce.order.integration.OrderPaymentGateway;
import com.duong.module.proxy.annotation.EnableProxyModule;
import com.duong.module.proxy.payment.PaymentClient;
import com.duong.module.proxy.payment.request.PaymentRequest;
import com.duong.module.proxy.payment.entity.enumeration.PaymentMethodEnum;
import java.math.BigDecimal;
import org.springframework.context.annotation.Import;
import com.duong.ecommerce.order.integration.oauth.EcomTokenService;
import com.sun.net.httpserver.HttpServer;
import feign.FeignException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "CACHE_TEST_REDIS_PORT", matches = "[0-9]+")
class EcomRedisAuthIntegrationTest {
    private HttpServer auth;
    private String base;
    private String namespace;
    private final AtomicInteger requests = new AtomicInteger();
    private volatile int authStatus;
    private volatile int paymentStatus;
    private volatile String paymentAuthorization;
    private final AtomicInteger paymentRequests = new AtomicInteger();

    @BeforeEach
    void startAuthServer() throws Exception {
        requests.set(0);
        authStatus = 200;
        paymentStatus = 200;
        paymentRequests.set(0);
        paymentAuthorization = null;
        namespace = "auth-test-" + UUID.randomUUID();
        auth = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        auth.createContext("/realms/micro-services/protocol/openid-connect/token", exchange -> {
            int count = requests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            String response = authStatus == 200
                    ? "{\"access_token\":\"fixture-token-" + count + "\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                    : "{\"error\":\"invalid_client\"}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(authStatus, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        auth.createContext("/internal/payments", exchange -> {
            paymentRequests.incrementAndGet();
            paymentAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = (paymentStatus == 200 ? "{\"data\":42,\"request_id\":null}" : "{}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(paymentStatus, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        auth.start();
        base = "http://127.0.0.1:" + auth.getAddress().getPort();
    }

    @AfterEach
    void stopAuthServer() {
        auth.stop(0);
    }

    private ApplicationContextRunner context() {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                RedisAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class, FeignAutoConfiguration.class))
                .withUserConfiguration(Enabled.class).withPropertyValues(
                        "spring.application.name=order-service",
                        "spring.data.redis.host=127.0.0.1", "spring.data.redis.port=" + System.getenv("CACHE_TEST_REDIS_PORT"),
                        "spring.data.redis.connect-timeout=1s", "spring.data.redis.timeout=1s",
                        "caching-config.namespace=" + namespace,
                        "integration-config.ecom.oauth-base-url=" + base,
                        "application.config.payment-url=" + base + "/internal/payments",
                        "integration-config.ecom.oauth-realm=micro-services",
                        "integration-config.ecom.oauth-username=ecom-services",
                        "integration-config.ecom.oauth-password=fixture-secret");
    }

    private OAuthTokenKey identity() {
        return new OAuthTokenKey(base + "/realms/micro-services", "ecom-services", "");
    }

    @Test
    void freshTokenIsStoredWithExpiryAndReusedAfterConsumerContextRestart() {
        context().run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(EcomTokenService.class).getAccessToken()).isEqualTo("fixture-token-1");
            var redis = new StringRedisTemplate(ctx.getBean(RedisConnectionFactory.class));
            String key = ctx.getBean(CacheKeys.class).jsonKey(CacheScopeEnum.GLOBAL, "oauth-tokens", identity().cacheKey());
            assertThat(redis.getExpire(key, TimeUnit.SECONDS)).isBetween(250L, 270L);
            assertThat(ctx.getBean(AccessTokenCache.class).getValidToken(identity())).contains("fixture-token-1");
        });
        context().withPropertyValues("spring.application.name=another-consumer").run(ctx ->
                assertThat(ctx.getBean(EcomTokenService.class).getAccessToken()).isEqualTo("fixture-token-1"));
        assertThat(requests).hasValue(1);
    }

    @Test
    void nearExpiryCachedTokenIsReplacedAndDifferentClientHasItsOwnEntry() {
        context().run(ctx -> {
            ctx.getBean(RedisJsonCache.class).put(CacheScopeEnum.GLOBAL, "oauth-tokens", identity().cacheKey(),
                    new CachedAccessToken("old-fixture-token", Instant.now().plusSeconds(10)), Duration.ofMinutes(5));
            assertThat(ctx.getBean(EcomTokenService.class).getAccessToken()).isEqualTo("fixture-token-1");
        });
        context().withPropertyValues("integration-config.ecom.oauth-username=another-client").run(ctx ->
                assertThat(ctx.getBean(EcomTokenService.class).getAccessToken()).isEqualTo("fixture-token-2"));
        assertThat(requests).hasValue(2);
    }

    @Test
    void authFailureOnCacheMissDoesNotCreateTokenEntry() {
        authStatus = 401;
        context().run(ctx -> {
            assertThatThrownBy(() -> ctx.getBean(EcomTokenService.class).getAccessToken())
                    .isInstanceOf(FeignException.Unauthorized.class);
            assertThat(ctx.getBean(AccessTokenCache.class).getValidToken(identity())).isEmpty();
        });
        assertThat(requests).hasValue(1);
    }

    @Test
    void orderAttachesCachedTokenAndDoesNotRetryPayment401() {
        context().run(ctx -> {
            var gateway = ctx.getBean(OrderPaymentGateway.class);
            var request = new PaymentRequest(BigDecimal.TEN, PaymentMethodEnum.PAYPAL, 7, "fixture-order", null);
            assertThat(gateway.requestOrderPayment(request)).isEqualTo(42);
            assertThat(gateway.requestOrderPayment(request)).isEqualTo(42);
            assertThat(requests).hasValue(1);
            assertThat(paymentAuthorization).isEqualTo("Bearer fixture-token-1");
            paymentStatus = 401;
            assertThatThrownBy(() -> gateway.requestOrderPayment(request)).isInstanceOf(FeignException.Unauthorized.class);
            assertThat(paymentRequests).hasValue(3);
            assertThat(requests).hasValue(1);
        });
    }

    @Test
    void authFailurePreventsOrderFromSendingPayment() {
        authStatus = 401;
        context().run(ctx -> {
            var request = new PaymentRequest(BigDecimal.TEN, PaymentMethodEnum.PAYPAL, 7, "fixture-order", null);
            assertThatThrownBy(() -> ctx.getBean(OrderPaymentGateway.class).requestOrderPayment(request))
                    .isInstanceOf(FeignException.Unauthorized.class);
            assertThat(requests).hasValue(1);
            assertThat(paymentRequests).hasValue(0);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableProxyModule(clients = PaymentClient.class)
    @Import({OrderOAuthConfiguration.class, OrderPaymentGateway.class})
    static class Enabled {}
}
