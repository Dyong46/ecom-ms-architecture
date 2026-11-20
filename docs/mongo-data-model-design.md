# Mongo data model

Status: Accepted. Date: 2026-11-19. Decision owner: project owner.

## Decision and rationale

Create `support/mongo-data-model` separately from the JPA/Hibernate-oriented
`common-data-model`. The owner chose module separation after considering direct
Customer fields and extending the existing module. Package separation alone would
not isolate transitive dependencies. Existing foundation relationships stay intact.

New dependency direction: `customer -> mongo-data-model -> spring-data-mongodb`.
The model library has optional Lombok for compilation and no application, JPA,
Hibernate, web-module or other support dependencies. Customer continues to own
its MongoDB starter and connection configuration. Parent dependency management
sets versions; declaring a managed dependency does not activate it in consumers.

`BaseMongoDocument` contains createdAt/updatedAt as Instant and createdBy/updatedBy
as String. Customer owns its ID and explicitly enables Mongo auditing. Address
remains an embedded value without its own auditing lifecycle. Request mapping
ignores audit fields, and existing response DTOs do not expose them.

Customer currently has no trusted service-side authentication principal. Dates
are populated but actor fields remain null until a service-owned AuditorAware
can resolve an authenticated identity. Client headers are not accepted as auditors.

## Scope and consumer impact

The JPA module and its consumers remain unchanged. The new model does not activate
configuration globally or provide a generic service/repository abstraction.
Existing Customer documents remain readable without audit fields; there is no
historical backfill. Standard saves update timestamps via Mongo callbacks; raw
updates do not automatically provide equivalent auditing.

Previously agreed Customer rules (unique email including soft-deleted documents,
optional address at creation, soft delete/restore and configurable eventual purge)
are separate behavioral work. This change only introduces the model and auditing.
Deletion metadata and retention decisions belong to Customer. Scheduler versus TTL
and retention-change semantics remain pending; no purge mechanism is introduced.

## Customer migration follow-up (accepted 2026-11-19)

The owner selected Mongock in Customer and a single initial migration baseline,
treating the project as new. Customer directly depends on the Boot 3 runner and
Spring Data 4 driver; `mongo-data-model` does not depend on Mongock. The initial
change creates the full unique email index, including any soft-deleted documents.
Existing data is not reset or merged automatically. Duplicate data prevents
startup. There is no historical audit backfill or purge scheduler. Details and
verification commands are in [Customer persistence](../services/customer/README.md).

## Verification

Follow-up accepted on 2026-11-19: add the optional
`BaseSoftDeletableMongoDocument` subclass with deletedAt/deletedBy and in-memory
markDeleted/restore helpers. Customer now inherits this subclass. Repeated marking
preserves the original deletion metadata; time and actor are supplied by the
consumer. Existing documents with no deletedAt are considered not deleted.
This does not implement soft-delete API behavior, query filtering, purgeAt or
restore eligibility. No additional dependencies or automatic configuration are
introduced. Request mapping ignores the deletion fields.

Exercise Mongo auditing callbacks and BSON mapping on Customer without a live DB,
including create/update timestamps and preservation of creation metadata. Run
Customer regression tests and inspect module dependency trees for JPA/Hibernate
leakage. Test fixtures supply auditors; production does not fabricate one.
