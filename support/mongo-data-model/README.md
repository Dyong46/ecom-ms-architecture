# Mongo data model

`BaseMongoDocument` supplies `Instant` creation/modification timestamps and String
creator/modifier metadata using Spring Data auditing annotations. Document classes
own their IDs. Lombok subclasses using builders should use `@SuperBuilder`.

Consumers explicitly enable `@EnableMongoAuditing`, own the MongoDB starter and
connection settings, and supply `AuditorAware<String>` when a trusted principal
is available. Without an auditor provider, user fields remain unset. This module
does not scan components or enable auditing automatically.

No dependency on `common-data-model`, JPA, Hibernate, other support modules or
applications is introduced. Mongo library versions follow the root Boot BOM.

Auditing applies through Spring Data entity lifecycle callbacks; raw/bulk updates
must manage their audit metadata explicitly. Existing documents are not backfilled.
`BaseSoftDeletableMongoDocument` extends the audit base with optional `deletedAt`
and `deletedBy`. `markDeleted(Instant, String)` records the first deletion only;
the consumer supplies the time and trusted actor (nullable if unknown). `restore()`
clears both fields and `isDeleted()` derives the state without persisting a flag.
These methods only change in-memory metadata. They do not save, filter queries,
intercept repository deletion, authorize restoration, or enforce retention.
Soft-delete/restore workflows, purgeAt, retention and purge belong to the consumer.

See [the accepted design](../../docs/mongo-data-model-design.md).
