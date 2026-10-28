package com.duong.ecommerce.product;

import com.duong.ecommerce.config.AuditingConfig;
import com.duong.module.data.model.specification.SpecificationBuilder;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {"spring.cloud.config.enabled=false", "eureka.client.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AuditingConfig.class)
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
}
