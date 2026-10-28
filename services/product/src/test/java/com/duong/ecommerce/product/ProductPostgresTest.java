package com.duong.ecommerce.product;

import com.duong.ecommerce.config.AuditingConfig;
import com.duong.ecommerce.exception.ProductPurchaseException;
import com.duong.module.data.model.specification.SpecificationBuilder;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {"spring.cloud.config.enabled=false", "eureka.client.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AuditingConfig.class, ProductService.class, ProductMapper.class})
@Testcontainers
class ProductPostgresTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }
    @Autowired ProductRepository repository;
    @Autowired ProductService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entityManager;

    static class Filter {
        private final String nameLk;
        Filter(String name) { this.nameLk = name; }
    }

    static class ExclusionFilter {
        private final java.util.List<Integer> idNotin;
        ExclusionFilter(Integer id) { this.idNotin = java.util.List.of(id); }
    }

    static class InvalidFilter {
        private final String unknownEq = "value";
    }

    @Test
    void filtersExcludeIdsEscapeWildcardsAndRejectUnknownFields() {
        var product = repository.saveAndFlush(Product.builder().name("Literal%_Name")
                .price(BigDecimal.ONE).build());
        assertThat(repository.findAll(SpecificationBuilder.<Product>build(new Filter("%_"))))
                .extracting(Product::getId).containsExactly(product.getId());
        assertThat(repository.findAll(SpecificationBuilder.<Product>build(new ExclusionFilter(product.getId()))))
                .extracting(Product::getId).doesNotContain(product.getId());
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> repository.findAll(SpecificationBuilder.<Product>build(new InvalidFilter())));
        assertThat(product.getCreatedBy()).isEqualTo("system");
    }

    @Test
    void auditingFilteringAndSoftDeleteUseRealPostgres() {
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test")
                .header("alg", "RS256").subject("user-uuid").build();
        SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt,
                        java.util.List.of()));
        try {
            var product = repository.saveAndFlush(Product.builder().name("Unique Keyboard")
                    .price(BigDecimal.TEN).availableQuantity(5).build());
            assertThat(product.getCreatedBy()).isEqualTo("user-uuid");
            assertThat(product.getCreatedAt()).isNotNull();
            product.setDescription("updated");
            repository.flush();
            assertThat(product.getUpdatedBy()).isEqualTo("user-uuid");
            var page = repository.findAll(SpecificationBuilder.<Product>build(new Filter("KEYBOARD")),
                    org.springframework.data.domain.PageRequest.of(0, 10));
            assertThat(page.getContent()).extracting(Product::getId).contains(product.getId());
            new ProductService(repository, new ProductMapper()).softDelete(product.getId());
            repository.flush();
            entityManager.clear();
            assertThat(repository.findById(product.getId())).isEmpty();
            assertThat(jdbc.queryForObject("select deleted_at is not null from product where id = ?",
                    Boolean.class, product.getId())).isTrue();
            assertThat(repository.findAll(SpecificationBuilder.<Product>build(new Filter("KEYBOARD"))))
                    .extracting(Product::getId).doesNotContain(product.getId());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void pessimisticLockPreventsConcurrentPurchasesFromOverselling() throws Exception {
        var product = repository.saveAndFlush(Product.builder()
                .name("Concurrency test product")
                .price(BigDecimal.TEN)
                .availableQuantity(5)
                .build());
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        try {
            var purchase = (java.util.concurrent.Callable<Boolean>) () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent purchases did not start in time");
                }
                try {
                    service.purchaseProducts(List.of(new ProductPurchaseRequest(product.getId(), 4)));
                    return true;
                } catch (ProductPurchaseException exception) {
                    assertThat(exception).hasMessageContaining("Insufficient stock quantity");
                    return false;
                }
            };

            var first = executor.submit(purchase);
            var second = executor.submit(purchase);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(jdbc.queryForObject(
                    "select available_quantity from product where id = ?",
                    Double.class,
                    product.getId()))
                    .isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void databaseRejectsNegativeAvailableQuantity() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into product (id, name, available_quantity, price) values (?, ?, ?, ?)",
                900001,
                "Invalid stock product",
                -1,
                BigDecimal.TEN))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
