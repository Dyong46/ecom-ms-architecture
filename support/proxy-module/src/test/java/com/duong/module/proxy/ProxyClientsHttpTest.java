package com.duong.module.proxy;

import com.duong.module.proxy.annotation.EnableProxyModule;
import com.duong.module.proxy.annotation.EnableEcomAuth;
import com.duong.module.proxy.ecom.EcomAuthClient;

import com.duong.module.proxy.customer.CustomerClient;
import com.duong.module.proxy.customer.response.CustomerResponse;
import com.duong.module.proxy.payment.PaymentClient;
import com.duong.module.proxy.payment.enumtype.PaymentMethod;
import com.duong.module.proxy.payment.request.PaymentRequest;
import com.duong.module.proxy.product.ProductClient;
import com.duong.module.proxy.product.request.PurchaseRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import feign.FeignException;
import feign.RequestInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises actual HTTP serialization, scoped credentials, failures and timeouts. */
class ProxyClientsHttpTest {
    private HttpServer server;
    private ExecutorService executor;
    private String base;
    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private volatile int productStatus;
    private volatile int customerStatus;
    private volatile int tokenStatus;
    private volatile int paymentStatus;
    private volatile long customerDelay;
    private final ObjectMapper mapper = new ObjectMapper();

    record Call(String path, String method, String authorization, String body) {}

    @BeforeEach
    void startDependencies() throws Exception {
        productStatus = 200;
        customerStatus = 200;
        tokenStatus = 200;
        paymentStatus = 200;
        customerDelay = 0;
        calls.clear();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            calls.add(new Call(path, exchange.getRequestMethod(), exchange.getRequestHeaders().getFirst("Authorization"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            String body;
            int status;
            switch (path) {
                case "/realms/micro-services/protocol/openid-connect/token" -> {
                    status = tokenStatus;
                    body = status == 200 ? "{\"access_token\":\"service-token\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                            : "{\"error\":\"invalid_client\"}";
                }
                case "/internal/customers/c1" -> {
                    if (customerDelay > 0) {
                        try { Thread.sleep(customerDelay); }
                        catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                    }
                    status = customerStatus;
                    body = "{\"id\":\"c1\",\"firstname\":\"Ada\",\"lastname\":\"Lovelace\",\"email\":\"ada@example.test\"}";
                }
                case "/internal/products/purchase" -> {
                    status = productStatus;
                    body = status == 200 ? "[{\"productId\":1,\"name\":\"Keyboard\",\"description\":\"\",\"price\":10.00,\"quantity\":2}]"
                            : "{\"detail\":\"upstream failure\"}";
                    if (status == 503) { exchange.getResponseHeaders().set("Retry-After", "0"); }
                    if (status == 302) { exchange.getResponseHeaders().set("Location", "/unexpected-redirect"); }
                }
                case "/internal/payments" -> { status = paymentStatus; body = status == 200 ? "42" : "{}"; }
                default -> { status = 501; body = "Unexpected dependency request"; }
            }
            if (path.startsWith("/internal/") && status == 200) {
                body = "{\"data\":" + body + ",\"request_id\":null}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            try {
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally { exchange.close(); }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopDependencies() {
        server.stop(0);
        executor.shutdownNow();
    }

    private ApplicationContextRunner context() {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                HttpMessageConvertersAutoConfiguration.class, FeignAutoConfiguration.class));
    }

    private ApplicationContextRunner allClients() {
        return context().withUserConfiguration(AllClients.class, SharedAuth.class)
                .withPropertyValues("integration-config.ecom.oauth-base-url=" + base,
                        "integration-config.ecom.oauth-realm=micro-services",
                        "integration-config.ecom.oauth-username=ecom-test",
                        "integration-config.ecom.oauth-password=dummy-secret")
                .withPropertyValues("spring.application.name=order-service",
                        "application.config.customer-url=" + base + "/internal/customers",
                        "application.config.product-url=" + base + "/internal/products",
                        "application.config.payment-url=" + base + "/internal/payments");
    }

    private ApplicationContextRunner withSharedAuth() {
        return allClients().withUserConfiguration(SharedAuth.class).withPropertyValues(
                "integration-config.ecom.oauth-base-url=" + base,
                "integration-config.ecom.oauth-realm=micro-services",
                "integration-config.ecom.oauth-username=ecom-test",
                "integration-config.ecom.oauth-password=dummy-secret");
    }

    @Test
    void customerOnlyConsumerNeedsNeitherOtherUrlsNorOAuthClasses() {
        context().withUserConfiguration(CustomerOnly.class)
                .withClassLoader(new FilteredClassLoader("org.springframework.security.oauth2.client", "com.duong.module.caching", "org.springframework.data.redis"))
                .withPropertyValues("application.config.customer-url=" + base + "/internal/customers")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed().hasSingleBean(CustomerClient.class)
                            .doesNotHaveBean(ProductClient.class).doesNotHaveBean(PaymentClient.class);
                    assertThat(ctx.getBean(CustomerClient.class).findCustomerById("c1")).get()
                            .extracting(response -> response.data().id()).isEqualTo("c1");
                    assertThat(calls).hasSize(1);
                    assertThat(calls.get(0).authorization()).isNull();
                });
    }

    @Test
    void paymentOnlyConsumerSuppliesItsOwnAuthorizationWithoutOAuthOrRedis() {
        context().withUserConfiguration(PaymentOnly.class)
                .withClassLoader(new FilteredClassLoader("com.duong.module.caching", "org.springframework.data.redis"))
                .withPropertyValues("application.config.payment-url=" + base + "/internal/payments")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed().doesNotHaveBean(EcomAuthClient.class);
                    ctx.getBean(PaymentClient.class).requestOrderPayment("Bearer caller-token",
                            new PaymentRequest(BigDecimal.TEN, PaymentMethod.PAYPAL, 7, "ORDER-7", null));
                    assertThat(calls).hasSize(1);
                    assertThat(calls.get(0).authorization()).isEqualTo("Bearer caller-token");
                });
    }

    @Test
    void preservesWireContractsWithOAuthOnlyForPayment() {
        allClients().run(ctx -> {
            assertThat(ctx).hasNotFailed().doesNotHaveBean(RequestInterceptor.class);
            var customer = ctx.getBean(CustomerClient.class).findCustomerById("c1").orElseThrow().data();
            var products = ctx.getBean(ProductClient.class).purchaseResponses(List.of(new PurchaseRequest(1, 2))).data();
            assertThat(products).hasSize(1);
            assertThat(products.get(0).price()).isEqualByComparingTo("10.00");
            assertThat(ctx.getBean(PaymentClient.class).requestOrderPayment("Bearer service-token", new PaymentRequest(
                    new BigDecimal("20.00"), PaymentMethod.PAYPAL, 7, "ORDER-7", customer)).data()).isEqualTo(42);
            for (var call : calls) {
                switch (call.path()) {
                    case "/internal/products/purchase" -> {
                        assertThat(call.method()).isEqualTo("POST");
                        assertThat(call.authorization()).isNull();
                        assertThat(mapper.readTree(call.body())).isEqualTo(mapper.readTree("[{\"productId\":1,\"quantity\":2.0}]"));
                    }
                    case "/internal/customers/c1" -> assertThat(call.authorization()).isNull();
                    case "/internal/payments" -> assertThat(call.authorization()).isEqualTo("Bearer service-token");
                    default -> { }
                }
            }
            var payment = calls.stream().filter(c -> c.path().equals("/internal/payments")).findFirst().orElseThrow();
            assertThat(mapper.readTree(payment.body())).isEqualTo(mapper.readTree("""
                    {"amount":20.00,"paymentMethod":"PAYPAL","orderId":7,"orderReference":"ORDER-7",
                    "customer":{"id":"c1","firstname":"Ada","lastname":"Lovelace","email":"ada@example.test"}}
                    """));
        });
    }

    @Test
    void exposesUpstreamStatusAndDoesNotRetryPurchaseEvenWithRetryAfter() {
        productStatus = 503;
        allClients().run(ctx -> {
            assertThatThrownBy(() -> ctx.getBean(ProductClient.class).purchaseResponses(List.of(new PurchaseRequest(1, 2))))
                    .isInstanceOf(FeignException.class)
                    .satisfies(ex -> assertThat(((FeignException) ex).status()).isEqualTo(503));
            assertThat(calls.stream().filter(c -> c.path().equals("/internal/products/purchase"))).hasSize(1);
        });
    }

    @Test
    void oauthClientSendsBasicCredentialsAndFormOnEveryExplicitRequest() {
        withSharedAuth().run(ctx -> {
            var auth = ctx.getBean(EcomAuthClient.class);
            auth.oauthToken("micro-services", java.util.Map.of("grant_type", "client_credentials"));
            auth.oauthToken("micro-services", java.util.Map.of("grant_type", "client_credentials"));
            assertThat(calls).hasSize(2);
            String basic = "Basic " + Base64.getEncoder().encodeToString("ecom-test:dummy-secret".getBytes(StandardCharsets.UTF_8));
            assertThat(calls).allSatisfy(call -> {
                assertThat(call.authorization()).isEqualTo(basic);
                assertThat(call.body()).isEqualTo("grant_type=client_credentials");
            });
        });
    }

    @Test
    void sharedOAuthFailureIsNotRetriedAndDoesNotPreventInternalCalls() {
        tokenStatus = 401;
        withSharedAuth().run(ctx -> {
            assertThatThrownBy(() -> ctx.getBean(EcomAuthClient.class).oauthToken("micro-services", java.util.Map.of("grant_type", "client_credentials")))
                    .isInstanceOf(FeignException.Unauthorized.class);
            assertThat(calls).hasSize(1);
            assertThat(ctx.getBean(CustomerClient.class).findCustomerById("c1")).isPresent();
        });
    }

    @Test
    void doesNotFollowInternalPurchaseRedirects() {
        productStatus = 302;
        allClients().run(ctx -> {
            assertThatThrownBy(() -> ctx.getBean(ProductClient.class).purchaseResponses(List.of(new PurchaseRequest(1, 2))))
                    .isInstanceOf(FeignException.class)
                    .satisfies(ex -> assertThat(((FeignException) ex).status()).isEqualTo(302));
            assertThat(calls.stream().map(Call::path)).containsExactly("/internal/products/purchase");
        });
    }

    @Test
    void optingIntoEcomAuthRequiresSecret() {
        withSharedAuth().withPropertyValues("integration-config.ecom.oauth-password=")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void credentialsAndTokenAreNotExposedByDtoToString() {
        var props = new com.duong.module.proxy.ecom.config.EcomOAuthProperties(base, "micro-services", "ecom-test", "dummy-secret");
        var response = new com.duong.module.proxy.ecom.response.EcomTokenResponse("service-token", 300, "Bearer");
        assertThat(props.toString()).doesNotContain("dummy-secret");
        assertThat(response.toString()).doesNotContain("service-token");
    }

    @Test
    void preservesNotFoundInsteadOfReturningEmptyData() {
        customerStatus = 404;
        context().withUserConfiguration(CustomerOnly.class)
                .withPropertyValues("application.config.customer-url=" + base + "/internal/customers")
                .run(ctx -> assertThatThrownBy(() -> ctx.getBean(CustomerClient.class).findCustomerById("c1"))
                        .isInstanceOf(FeignException.NotFound.class));
    }

    @Test
    void appliesReadTimeoutAndDoesNotRetry() {
        customerDelay = 500;
        context().withUserConfiguration(CustomerOnly.class)
                .withPropertyValues("application.config.customer-url=" + base + "/internal/customers", "proxy.http.read-timeout=50ms")
                .run(ctx -> {
                    assertThatThrownBy(() -> ctx.getBean(CustomerClient.class).findCustomerById("c1"))
                            .isInstanceOf(feign.RetryableException.class)
                            .hasRootCauseInstanceOf(java.net.SocketTimeoutException.class);
                    assertThat(calls).hasSize(1);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableProxyModule(clients = PaymentClient.class)
    static class PaymentOnly {}

    @Configuration(proxyBeanMethods = false)
    @EnableEcomAuth
    static class SharedAuth {}

    @Configuration(proxyBeanMethods = false)
    @EnableProxyModule(clients = CustomerClient.class)
    static class CustomerOnly {}

    @Configuration(proxyBeanMethods = false)
    @EnableProxyModule(clients = {CustomerClient.class, ProductClient.class, PaymentClient.class})
    static class AllClients {}
}
