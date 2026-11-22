package com.duong.ecommerce.customer.migration;

import io.mongock.api.annotations.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

/**
 * Initial baseline for a fresh project; never resets or deduplicates existing data.
 */
@ChangeUnit(id = "customer-001-initialize", order = "001", author = "customer-service")
public class V001InitializeCustomer {
    public static final String EMAIL_INDEX = "uk_customer_email";

    @BeforeExecution
    public void createIndexes(MongoTemplate mongoTemplate) {
        // Full index: soft-deleted documents must continue reserving their email.
        mongoTemplate.indexOps("customer").ensureIndex(
                new Index().on("email", Sort.Direction.ASC).unique().named(EMAIL_INDEX));
    }

    @RollbackBeforeExecution
    public void rollbackIndexes() {
        // Forward-only: never drop a possibly pre-existing uniqueness constraint.
        // ensureIndex is retry-safe; conflicting existing data must be resolved explicitly.
    }

    @Execution
    public void initialize() {
        // No seed data or historical audit backfill in the fresh-project baseline.
    }

    @RollbackExecution
    public void rollback() {
        // No document mutations to undo; keep the uniqueness constraint in place.
    }
}
