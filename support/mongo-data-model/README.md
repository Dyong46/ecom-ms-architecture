# Mongo data model

This module keeps Spring Data MongoDB auditing separate from the JPA foundation.
A separate Maven artifact lets Mongo consumers use the shared metadata without
inheriting Hibernate or JPA. Each service owns its document IDs, connection and migrations.

## Audit metadata

Consumers explicitly enable Mongo auditing. Spring Data callbacks populate
`createdAt` and `updatedAt` on entity saves. Raw and bulk writes must maintain
audit fields explicitly. Only a trusted service-side identity may populate
`createdBy` and `updatedBy` through `AuditorAware<String>`. Customer currently
leaves actors unset and ignores audit and deletion fields in request mapping.

## Compatibility and deletion

Existing documents remain readable with missing audit fields and receive no
historical backfill. In `BaseSoftDeletableMongoDocument`, a missing `deletedAt`
means the document is active. Repeated `markDeleted` calls preserve the first
timestamp and actor. `restore()` clears both fields in memory. The consumer owns
persistence, query filtering, restore authorization and retention.

Customer owns its Mongock baseline and unique email index, including the behavior
for records carrying deletion metadata. [Customer persistence and migrations](../../services/customer/README.md)
