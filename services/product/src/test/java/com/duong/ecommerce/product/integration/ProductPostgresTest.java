package com.duong.ecommerce.product.integration;

import com.duong.ecommerce.product.entity.*;
import com.duong.ecommerce.product.mapper.ProductMapperImpl;
import com.duong.ecommerce.product.payload.request.*;
import com.duong.ecommerce.product.repository.*;
import com.duong.ecommerce.product.service.*;
import com.duong.ecommerce.product.exception.ProductPurchaseException;
import com.duong.module.common.exception.BadRequestException;
import com.duong.module.common.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = {"spring.cloud.config.enabled=false", "eureka.client.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ProductService.class, ProductOptionService.class, ProductMapperImpl.class})
@Testcontainers
class ProductPostgresTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }
    @Autowired ProductRepository repository;
    @Autowired ProductVariantRepository variants;
    @Autowired ProductService service;
    @Autowired ProductOptionService options;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entityManager;

    private String product(List<VariantRequest> skus) {
        return service.createProduct(new ProductRequest("Phone", UUID.randomUUID().toString(), "Description", skus));
    }
    private VariantRequest sku(String code, int stock, String... values) {
        return new VariantRequest(code, new BigDecimal("99.90"), stock, List.of(values));
    }
    private String option(String name) { return options.create(new OptionRequest(name)); }
    private String value(String option, String value) { return options.addValue(option, new OptionValueRequest(value, null)); }

    @Test void createsFiveTableCatalogWithUuidIdsAndNestedVariantOptions() {
        var color=option("Color");
        var synonyms=new com.fasterxml.jackson.databind.ObjectMapper().createArrayNode().add("azure");
        var blue=options.addValue(color,new OptionValueRequest("Blue",synonyms));
        var id=product(List.of(sku("PHONE-BLUE",5,blue)));
        entityManager.flush(); entityManager.clear();
        var response=service.findById(id);
        assertThat(UUID.fromString(id).version()).isEqualTo(4);
        var variant=response.variants().get(0);
        assertThat(UUID.fromString(variant.id()).version()).isEqualTo(4);
        assertThat(UUID.fromString(color).version()).isEqualTo(4);
        assertThat(UUID.fromString(blue).version()).isEqualTo(4);
        assertThat(variant.options().get(0).optionName()).isEqualTo("Color");
        assertThat(jdbc.queryForObject("select count(*) from product_variant_option_value where product_variant_id=?",
                Integer.class,variant.id())).isEqualTo(1);
        assertThat(options.findAll(org.springframework.data.domain.PageRequest.of(0,20)).getContent().get(0)
                .values().get(0).synonyms()).isEqualTo(synonyms);
        assertThat(response.createdAt()).isNotNull();
    }

    @Test void rejectsMultipleValuesOfSameOptionDuplicateSetsAndMissingValues() {
        var color=option("Color"); var blue=value(color,"Blue"); var red=value(color,"Red");
        var id=product(List.of(sku("PHONE-BLUE",5,blue)));
        assertThatThrownBy(()->service.addVariant(id,sku("PHONE-TWO-COLORS",5,blue,red)))
                .isInstanceOf(BadRequestException.class);
        // New transactions are needed after a transactional service exception marks this test transaction rollback-only.
    }

    @Test void rejectsDuplicateCombinationEvenWhenOptionIdsAreReordered() {
        var color=option("Color"); var blue=value(color,"Blue");
        var storage=option("Storage"); var capacity=value(storage,"256GB");
        var id=product(List.of(sku("PHONE-BLUE-256",5,blue,capacity)));
        assertThatThrownBy(()->service.addVariant(id,sku("DUPLICATE",5,capacity,blue)))
                .isInstanceOf(ConflictException.class);
    }

    @Test void rejectsMissingOptionValue() {
        var id=product(List.of());
        assertThatThrownBy(()->service.addVariant(id,sku("MISSING",1,UUID.randomUUID().toString())))
                .isInstanceOf(BadRequestException.class);
    }

    @Test void purchaseReturnsSnapshotAndDecrementsOnlySelectedSku() {
        var color=option("Color"); var blue=value(color,"Blue"); var red=value(color,"Red");
        var id=product(List.of(sku("PHONE-BLUE",5,blue),sku("PHONE-RED",8,red)));
        var detail=service.findById(id); var selected=detail.variants().get(0);
        var response=service.purchaseProducts(List.of(new ProductPurchaseRequest(selected.id(),2))).get(0);
        assertThat(response.productId()).isEqualTo(id);
        assertThat(response.skuId()).isEqualTo(selected.id());
        assertThat(response.options()).containsEntry("Color","Blue");
        assertThat(response.price()).isEqualByComparingTo("99.90");
        entityManager.flush();entityManager.clear();
        assertThat(variants.findById(selected.id()).orElseThrow().getStockQuantity()).isEqualTo(3);
        assertThat(variants.findById(detail.variants().get(1).id()).orElseThrow().getStockQuantity()).isEqualTo(8);
    }

    @Test void deletingProductCascadesVariantsAndSelectionsButPreservesSharedOptions() {
        var color=option("Color");var blue=value(color,"Blue");
        var id=product(List.of(sku("PHONE-BLUE",5,blue)));
        var skuId=service.findById(id).variants().get(0).id();
        service.deleteProduct(id);entityManager.flush();entityManager.clear();
        assertThat(repository.findById(id)).isEmpty();
        assertThat(variants.findById(skuId)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from product_variant_option_value",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from option_value where id=?",Integer.class,blue)).isEqualTo(1);
    }

    @Test void deletingOptionCascadesValuesAndSelectionsButRetainsSkuAsSelectedByOwner() {
        var color=option("Color");var blue=value(color,"Blue");
        var id=product(List.of(sku("PHONE-BLUE",5,blue)));
        options.delete(color);
        assertThat(service.findById(id).variants().get(0).options()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from option_value where option_id=?",Integer.class,color)).isZero();
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void concurrentPurchasesCannotOversellSku() throws Exception {
        var id=product(List.of(sku("CONCURRENT-"+UUID.randomUUID(),5)));
        var skuId=service.findById(id).variants().get(0).id();
        var outcomes=concurrently(()->{
            try { service.purchaseProducts(List.of(new ProductPurchaseRequest(skuId,4)));return true; }
            catch(ProductPurchaseException exception){ assertThat(exception).hasMessageContaining("Insufficient stock");return false; }
        });
        assertThat(outcomes).containsExactlyInAnyOrder(true,false);
        assertThat(jdbc.queryForObject("select stock_quantity from product_variant where id=?",Integer.class,skuId)).isEqualTo(1);
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void concurrentVariantCreationSerializesCombinationValidation() throws Exception {
        var id=product(List.of());
        assertThat(concurrently(()->{
            try { service.addVariant(id,sku("CONCURRENT-"+UUID.randomUUID(),1));return true; }
            catch(ConflictException exception){return false;}
        })).containsExactlyInAnyOrder(true,false);
        assertThat(service.findById(id).variants()).hasSize(1);
    }

    private List<Boolean> concurrently(Callable<Boolean> task) throws Exception {
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        var executor=Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> synchronizedTask=()->{ready.countDown();if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException();return task.call();};
            var first=executor.submit(synchronizedTask);var second=executor.submit(synchronizedTask);
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
            return List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS));
        } finally {start.countDown();executor.shutdownNow();executor.awaitTermination(5,TimeUnit.SECONDS);}
    }
}
