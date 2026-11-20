package com.duong.ecommerce.customer.migration;

import com.duong.ecommerce.customer.CustomerApplication;
import com.duong.ecommerce.customer.entity.Address;
import com.duong.ecommerce.customer.entity.Customer;
import com.duong.ecommerce.customer.payload.request.CustomerRequest;
import com.duong.ecommerce.customer.service.CustomerService;
import com.duong.module.common.exception.ConflictException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static com.mongodb.client.model.Filters.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class CustomerMigrationIntegrationTest {
    @Container
    static final GenericContainer<?> mongo = new GenericContainer<>("mongo:8.0.9").withExposedPorts(27017);

    private String uri(String database) {
        return "mongodb://" + mongo.getHost() + ":" + mongo.getMappedPort(27017) + "/" + database;
    }

    private String database() {
        return "customer_migration_" + UUID.randomUUID().toString().replace("-", "");
    }

    private ConfigurableApplicationContext start(String database) {
        return new SpringApplicationBuilder(CustomerApplication.class)
                .web(WebApplicationType.NONE)
                .run("--spring.cloud.config.enabled=false", "--eureka.client.enabled=false",
                        "--spring.cloud.discovery.enabled=false", "--spring.main.banner-mode=off",
                        "--management.tracing.enabled=false", "--spring.data.mongodb.uri=" + uri(database));
    }

    private CustomerRequest request(String id, String email) {
        return new CustomerRequest(id, "Ada", "Lovelace", email,
                new Address("Main", "12", "70000"));
    }

    @Test
    void initializesOnceAndEnforcesUniquenessAcrossCreateUpdateAndDeletedDocuments() {
        String database = database();
        long historyCount;
        try (var context = start(database)) {
            var template = context.getBean(MongoTemplate.class);
            var service = context.getBean(CustomerService.class);
            var index = template.getCollection("customer").listIndexes()
                    .into(new java.util.ArrayList<>()).stream()
                    .filter(it -> V001InitializeCustomer.EMAIL_INDEX.equals(it.getString("name")))
                    .findFirst().orElseThrow();
            assertThat(index.getBoolean("unique")).isTrue();
            assertThat(index).doesNotContainKeys("partialFilterExpression", "sparse", "expireAfterSeconds");

            String first = service.createCustomer(request(null, "ada@example.test"));
            assertThat(template.findById(first, Customer.class).getCreatedAt()).isNotNull();
            assertThat(template.findById(first, Customer.class).getCreatedBy()).isNull();
            assertThatThrownBy(() -> service.createCustomer(request(null, "ada@example.test")))
                    .isInstanceOf(ConflictException.class);
            String second = service.createCustomer(request(null, "grace@example.test"));
            service.updateCustomer(request(first, "ada@example.test"));
            assertThatThrownBy(() -> service.updateCustomer(request(second, "ada@example.test")))
                    .isInstanceOf(ConflictException.class);
            assertThat(template.findById(second, Customer.class).getEmail()).isEqualTo("grace@example.test");

            // Exercise the index's future soft-delete contract without adding deletion behavior.
            template.getCollection("customer").insertOne(new Document("_id", "deleted")
                    .append("email", "deleted@example.test").append("deletedAt", Date.from(Instant.now())));
            assertThatThrownBy(() -> service.createCustomer(request(null, "deleted@example.test")))
                    .isInstanceOf(ConflictException.class);
            template.getCollection("customer").deleteOne(eq("_id", "deleted"));
            service.createCustomer(request(null, "deleted@example.test"));
            historyCount = template.getCollection("mongockChangeLog").countDocuments();
            assertThat(historyCount).isPositive();
        }
        try (var restarted = start(database)) {
            var template = restarted.getBean(MongoTemplate.class);
            assertThat(template.getCollection("mongockChangeLog").countDocuments()).isEqualTo(historyCount);
            assertThat(template.getCollection("customer").countDocuments()).isEqualTo(3);
        }
    }

    @Test
    void duplicateExistingDataFailsStartupWithoutDeletingOrMergingRecords() {
        String database = database();
        try (MongoClient client = MongoClients.create(uri(database))) {
            var collection = client.getDatabase(database).getCollection("customer");
            collection.insertMany(java.util.List.of(
                    new Document("_id", "a").append("email", "duplicate@example.test"),
                    new Document("_id", "b").append("email", "duplicate@example.test")));
            assertThatThrownBy(() -> {
                try (var ignored = start(database)) {
                    // Startup must abort if the unique index cannot be created.
                }
            }).hasRootCauseInstanceOf(com.mongodb.DuplicateKeyException.class);
            assertThat(collection.countDocuments()).isEqualTo(2);
        }
    }
}
