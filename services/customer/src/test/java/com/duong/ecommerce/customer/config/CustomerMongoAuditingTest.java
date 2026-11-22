package com.duong.ecommerce.customer.config;

import com.duong.ecommerce.customer.entity.Customer;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.auditing.IsNewAwareAuditingHandler;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.mapping.event.AuditingEntityCallback;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(classes = {CustomerMongoAuditingConfiguration.class, CustomerMongoAuditingTest.TestConfig.class})
class CustomerMongoAuditingTest {
    @Autowired
    private AuditingEntityCallback callback;
    @Autowired
    private IsNewAwareAuditingHandler handler;
    @Autowired
    private MongoMappingContext mappingContext;

    @Test
    void deletionMetadataSurvivesBsonRoundTripAndRestoreClearsIt() {
        var converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, mappingContext);
        converter.afterPropertiesSet();
        var customer = converter.read(Customer.class, new Document("_id", "legacy"));
        assertThat(customer.isDeleted()).isFalse();
        Instant deletedAt = Instant.parse("2026-11-19T10:00:00Z");
        customer.markDeleted(deletedAt, "admin-1");
        customer.markDeleted(deletedAt.plusSeconds(60), "admin-2");
        var bson = new Document();
        converter.write(customer, bson);
        assertThat(bson.get("deletedAt")).isEqualTo(Date.from(deletedAt));
        assertThat(bson.get("deletedBy")).isEqualTo("admin-1");
        assertThat(bson).doesNotContainKeys("deleted", "purgeAt");

        var loaded = converter.read(Customer.class, bson);
        assertThat(loaded.isDeleted()).isTrue();
        assertThat(loaded.getDeletedBy()).isEqualTo("admin-1");
        loaded.restore();
        var restored = new Document();
        converter.write(loaded, restored);
        assertThat(loaded.isDeleted()).isFalse();
        assertThat(loaded.getDeletedBy()).isNull();
        assertThat(restored).doesNotContainKeys("deletedAt", "deletedBy");
        loaded.markDeleted(deletedAt.plusSeconds(120), null);
        assertThat(loaded.getDeletedAt()).isEqualTo(deletedAt.plusSeconds(120));
    }

    @Test
    void auditsInheritedFieldsAndRoundTripsThemAsBsonDates() {
        Instant created = Instant.parse("2026-11-19T10:00:00Z");
        Instant updated = created.plusSeconds(60);
        var customer = Customer.builder().firstname("Ada").email("ada@example.test").build();
        handler.setDateTimeProvider(() -> Optional.of(created));
        callback.onBeforeConvert(customer, "customer");
        assertThat(customer.getCreatedAt()).isEqualTo(created);
        assertThat(customer.getUpdatedAt()).isEqualTo(created);
        assertThat(customer.getCreatedBy()).isEqualTo("test-admin");

        customer.setId("customer-1");
        handler.setDateTimeProvider(() -> Optional.of(updated));
        callback.onBeforeConvert(customer, "customer");
        assertThat(customer.getCreatedAt()).isEqualTo(created);
        assertThat(customer.getUpdatedAt()).isEqualTo(updated);
        assertThat(customer.getUpdatedBy()).isEqualTo("test-admin");

        var converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, mappingContext);
        converter.afterPropertiesSet();
        var bson = new Document();
        converter.write(customer, bson);
        assertThat(bson.get("createdAt")).isEqualTo(Date.from(created));
        assertThat(bson.get("updatedAt")).isEqualTo(Date.from(updated));
        var restored = converter.read(Customer.class, bson);
        assertThat(restored.getCreatedAt()).isEqualTo(created);
        assertThat(restored.getUpdatedAt()).isEqualTo(updated);
        assertThat(restored.getCreatedBy()).isEqualTo("test-admin");
        var legacy = converter.read(Customer.class, new Document("_id", "legacy").append("firstname", "Ada"));
        assertThat(legacy.getCreatedAt()).isNull();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {
        @Bean
        MongoMappingContext mongoMappingContext() {
            var context = new MongoMappingContext();
            context.setSimpleTypeHolder(org.springframework.data.mongodb.core.convert.MongoCustomConversions
                    .create(adapter -> {
                    }).getSimpleTypeHolder());
            context.setInitialEntitySet(Set.of(Customer.class));
            return context;
        }

        @Bean
        AuditorAware<String> auditorAware() {
            return () -> Optional.of("test-admin");
        }
    }
}
