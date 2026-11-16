# Product mapping decision

Status: Accepted. Date: 2026-11-15. Decision owner: project owner.

The owner selected Product as the first service to migrate from manual mapping to
MapStruct. This limits the initial scope while exercising record DTOs, Lombok
builders, category references and responses with multiple source parameters.

The project-wide follow-up on 2026-11-16 supersedes the initial scope and build
layout below: all five business services now use MapStruct and inherit compiler
settings from `services/pom.xml`. ModelMapper has been removed by owner choice.
See [Current package and mapping conventions](service-layout-and-mapping.md).

## Configuration and ownership

- The root POM manages MapStruct 1.6.3 and the Lombok binding version. Product
  declares the MapStruct dependency and enables its annotation processor alongside
  Lombok. Customer adopted the same setup in the follow-up recorded below.
- `ProductMapper` belongs to Product and is generated as a Spring bean. Unmapped
  target properties fail compilation; audit and soft-delete fields are explicitly
  ignored when constructing a product and remain owned by persistence.
- No shared mapper module, base mapper or new dependency between support modules
  is introduced. Business rules remain in `ProductService`.

The Product feature uses subpackages by responsibility: `controller`, `service`,
`repository`, `mapper`, `payload/request`, `payload/response` and `entity`. Tests follow the corresponding
subpackage, with database integration tests in `integration`. MapStruct generates
`ProductMapperImpl` in the `mapper` package alongside its interface.

## Compatibility and trade-offs

MapStruct replaces repetitive field copying with compile-time generated Java, at
the cost of annotation processor configuration and explicit mapping declarations.
Generated implementations are build artifacts and are not committed.

The existing method signatures and data behavior are preserved: request category
IDs create an ID-only category reference (including a null ID), response category
fields are flattened, and purchase quantity comes from the method argument rather
than available stock. Audit fields are left unset. Null source objects and a
missing category in a product response continue to throw `NullPointerException`;
purchase responses do not require a category. Small hooks and explicit expressions
preserve these behaviors instead of adopting MapStruct's default null handling.

Other service mappers and the existing ModelMapper utilities are outside this
migration's initial Product scope. Subsequent migrations can reuse the version settings after their
individual mapping behavior has been checked.

## Customer follow-up

Accepted by the project owner on 2026-11-16: migrate `CustomerMapper` using the same
module-local dependency and Lombok processor setup. The mapper remains in
`customer.mapper`, is generated as a Spring bean and fails compilation on unmapped
target fields. No shared mapper module or additional support relationship is added.

Both method signatures and existing behavior are preserved: `toCustomer(null)`
returns null, `fromCustomer(null)` throws `NullPointerException`, null field values
remain null, and the `Address` instance is assigned directly in both directions.
Customer update/merge logic remains in `CustomerService`; it is not part of this
mapper migration. `CustomerMapperTest` covers generated bean injection and these
compatibility cases. Verify with
`./services/product/mvnw -f pom.xml -pl services/customer -am --batch-mode clean verify`.

## Verification

Run `./services/product/mvnw -f pom.xml -pl services/product -am --batch-mode test`
with Java 17 and Docker available for the existing PostgreSQL Testcontainers tests.
`ProductMapperTest` checks generated Spring bean registration, mapping behavior,
audit exclusions and null compatibility without a database.
