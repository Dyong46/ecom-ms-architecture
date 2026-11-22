# Customer persistence and migrations

Customer owns its MongoDB migrations under
`com.duong.ecommerce.customer.migration`. `mongo-data-model` only supplies auditing
metadata; it has no Mongock dependency or migration scanning.

## Initial baseline

Mongock 5.5.1 uses the Spring Boot 3 runner and Spring Data MongoDB 4 driver.
`CustomerMigrationConfiguration` explicitly enables it. `application.yml` selects
the service's migration package and the initializing-bean runner, so a failed
migration aborts application startup. Native transactions are disabled for the
standalone MongoDB used locally; index creation runs in `@BeforeExecution`.

`customer-001-initialize` is the single fresh-project baseline. It creates
`uk_customer_email`, a full unique ascending index on `customer.email`.
There is no partial/sparse filter: documents marked deleted still reserve email.
Uniqueness uses MongoDB's default exact, case-sensitive comparison; this change
does not normalize case or whitespace. Email is still required by the existing
request contract. Case-insensitive email identity would be a separate decision.

MongoDB creates the collection as part of index creation on a fresh database.
New documents receive audit timestamps through Spring Data callbacks. The baseline
does not seed customers, backfill invented creation dates, merge records, or reset
an existing database. Existing duplicate emails cause index creation and startup
to fail; they must be resolved explicitly outside this fresh-project baseline.

Mongock stores migration history and lock metadata in its default
`mongockChangeLog` and `mongockLock` collections. Completed changes are not rerun on
restart. The index operation is retry-safe. Rollback methods deliberately retain
the index rather than dropping a potentially pre-existing data constraint; this
baseline is forward-only. After deployment, add a new change unit for changes
instead of editing an already executed baseline.

Create/update duplicate-key failures return HTTP 409 through the shared error
envelope without exposing MongoDB's key values. Deletion endpoints still retain
their existing behavior: soft-delete/restore and timed purge are separate work.
No TTL index, purge job or scheduler is installed by this migration.

## Verification

With Docker available:

```sh
./services/customer/mvnw -pl services/customer -am test \
  -Dtest=CustomerMigrationIntegrationTest,CustomerMongoAuditingTest,CustomerMapperTest,CustomerPaginationTest,InternalCustomerControllerTest,SharedExceptionHandlerTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Integration tests start an isolated MongoDB 8.0.9 container with randomly named
databases. They exercise real Customer startup and restart, index enforcement on
create/update, email reservation for a document carrying deletedAt, reuse after
physical deletion, and safe failure on existing duplicates. No live service
database is used. Tests require Docker and do not silently skip it.
