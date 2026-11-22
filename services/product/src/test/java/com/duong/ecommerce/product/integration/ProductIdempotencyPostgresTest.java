package com.duong.ecommerce.product.integration;

import com.duong.ecommerce.product.integration.idempotency.PostgresIdempotencyStore;
import com.duong.ecommerce.product.mapper.ProductMapperImpl;
import com.duong.ecommerce.product.payload.request.ProductPurchaseRequest;
import com.duong.ecommerce.product.payload.request.ProductRequest;
import com.duong.ecommerce.product.payload.request.VariantRequest;
import com.duong.ecommerce.product.service.ProductPurchaseService;
import com.duong.ecommerce.product.service.ProductService;
import com.duong.module.common.exception.ConflictException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {"spring.cloud.config.enabled=false", "eureka.client.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ProductService.class, ProductMapperImpl.class, ProductPurchaseService.class, PostgresIdempotencyStore.class,
        ProductIdempotencyPostgresTest.Json.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class ProductIdempotencyPostgresTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired
    ProductService products;
    @Autowired
    ProductPurchaseService purchases;
    @Autowired
    PostgresIdempotencyStore ledger;
    @Autowired
    JdbcTemplate jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    private String sku() {
        var id = products.createProduct(new ProductRequest("Original Phone", UUID.randomUUID().toString(), "original",
                List.of(new VariantRequest(UUID.randomUUID().toString(), new BigDecimal("25.00"), 10, List.of()))));
        return products.findById(id).variants().get(0).id();
    }

    @Test
    void replayKeepsOriginalSnapshotEvenAfterCatalogChangesAndDeletion() {
        var sku = sku();
        var key = UUID.randomUUID().toString();
        var request = List.of(new ProductPurchaseRequest(sku, 2));
        var first = purchases.purchase(key, request);
        assertThat(jdbc.queryForObject("select stock_quantity from product_variant where id=?", Integer.class, sku)).isEqualTo(8);
        jdbc.update("update product_variant set price=99 where id=?", sku);
        assertThat(purchases.purchase(key, request)).isEqualTo(first);
        jdbc.update("delete from product_variant where id=?", sku);
        assertThat(purchases.purchase(key, request)).isEqualTo(first);
        assertThatThrownBy(() -> purchases.purchase(key, List.of(new ProductPurchaseRequest(sku, 3))))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void skuOrderDoesNotChangeFingerprint() {
        var a = sku();
        var b = sku();
        var key = UUID.randomUUID().toString();
        var one = new ProductPurchaseRequest(a, 1);
        var two = new ProductPurchaseRequest(b, 2);
        var first = purchases.purchase(key, List.of(one, two));
        assertThat(purchases.purchase(key, List.of(two, one))).isEqualTo(first);
        assertThat(jdbc.queryForObject("select stock_quantity from product_variant where id=?", Integer.class, a)).isEqualTo(9);
    }

    @Test
    void rolledBackMutationCanRetryWithTheSameBinding() {
        var sku = sku();
        var key = UUID.randomUUID().toString();
        assertThatThrownBy(() -> ledger.execute(key, "same-input", new TypeReference<Integer>() {
        }, operation -> {
            jdbc.update("update product_variant set stock_quantity=0 where id=?", sku);
            throw new IllegalStateException("crash before commit");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select stock_quantity from product_variant where id=?", Integer.class, sku)).isEqualTo(10);
        assertThat(ledger.execute(key, "same-input", new TypeReference<Integer>() {
        }, operation -> 42)).isEqualTo(42);
        assertThatThrownBy(() -> ledger.execute(key, "different", new TypeReference<Integer>() {
        }, operation -> 99))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void simultaneousSameKeyDoesNotExecuteTwice() throws Exception {
        var key = UUID.randomUUID().toString();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var first = executor.submit(() -> ledger.execute(key, "input", new TypeReference<Integer>() {
            }, operation -> {
                entered.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return 42;
            }));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> ledger.execute(key, "input", new TypeReference<Integer>() {
            }, operation -> 99))
                    .isInstanceOf(ConflictException.class).hasMessageContaining("still processing");
            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(42);
            assertThat(ledger.execute(key, "input", new TypeReference<Integer>() {
            }, operation -> 99)).isEqualTo(42);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @TestConfiguration
    static class Json {
        @Bean
        ObjectMapper json() {
            return new ObjectMapper();
        }
    }
}
