# Architecture rules: module boundaries and design decisions

Status: Accepted. Date: 2026-11-15. Updated: 2026-11-18. Decision owner: project owner.

## Ownership and dependency direction

A service owns its business use cases and the composition of infrastructure capabilities.
`support/` supplies reusable technical capabilities, without deciding application workflows.

```text
Order service
  ├─ OrderPaymentGateway → PaymentClient [proxy-module] → Payment HTTP
  └─ EcomTokenService
       ├─ RedisEcomTokenCache → AccessTokenCache [caching-module] → Redis
       └─ EcomAuthClient [proxy-module] → Keycloak HTTP
```

The runtime calls all execute inside the consuming service. Support modules are libraries,
not intermediate services.

Dependencies are evaluated by module responsibility and architectural direction. There is
no blanket prohibition on one support module depending on another.

| Module role | Responsibility | Dependency guidance |
| --- | --- | --- |
| Business service | Product, Order, Customer and their application workflows | May compose support modules and shared contracts |
| Infrastructure module | HTTP, Kafka, Redis, Elasticsearch and other technical capabilities | May use libraries, abstractions and other support modules when justified by its technical responsibility; must not depend on application modules |
| Shared contract/API | DTOs, interfaces and event contracts | Keep dependencies minimal; must not depend on application implementations |
| Common utilities | General exceptions and helpers | Must not depend on business or infrastructure modules |

For example, `product-service → elasticsearch-module` and `product-service → kafka-module`
fit these roles. `elasticsearch-module → kafka-module` is not automatically wrong: it needs
a concrete reusable technical purpose and an explicit assessment of direction, coupling and
consumer impact. Product-specific event handling and indexing orchestration belong in
Product. `kafka-module → product-service` reverses the allowed direction.

## Mandatory rules

1. Module dependencies must follow the responsibilities and direction described above, avoid
   cycles and have a clear purpose. Support-to-support dependencies are allowed when justified;
   infrastructure and shared modules must not depend on application modules.
2. Review direct, transitive, optional and test dependencies, imports, enabling annotations,
   reflection/bean lookups and configuration against the same rules. An interface or runtime
   lookup does not remove the actual dependency. Wiring other modules must be explicit.
3. In the current proxy/caching design, proxy owns HTTP contracts, serialization and transport settings. Its OAuth client only
   performs the requested HTTP exchange. PaymentClient receives Authorization explicitly.
   It must not fetch/cache tokens, read Redis or decide which identity an application uses.
4. In that design, caching owns storage, serialization, namespaces, TTL and reusable expiry validation.
   It must not call OAuth/HTTP, attach headers or decide application fallback behavior.
5. A consuming service owns cache-aside flow, token identity, cache-miss handling, refresh,
   fallback, outbound credential choice, business retry/idempotency and transaction boundaries.
   Technical adapters composing modules belong in that service's integration/configuration packages.
6. Keep business rules in their owning service: prices, stock changes, payment eligibility,
   order state and compensation do not belong in shared helpers or generic DTO modules.
7. Modules are enabled explicitly. No broad component scans or hidden side effects on every
   consumer. Secrets remain runtime configuration; never log tokens, secrets or cache values.
8. A new shared workflow needs an explicit architecture decision and a separate abstraction
   justified by actual consumers. Do not move it into an unrelated capability to reduce duplication.

Rules 3–5 record the current proxy/caching and service ownership decisions. They do not
establish a general ban on support-to-support dependencies. Changes to those decisions
follow the decision process below.

## Existing foundation dependencies

The repository already has `web-module → common-module` and
`common-data-model → web-module/common-module` (including a transitive common dependency).
These are recorded legacy foundation relationships to preserve within this scope. They do
not imply a ban on new support relationships; each new dependency is evaluated by role,
direction and purpose. Refactoring the existing foundation stack is outside this change.

Any new relationship requires a relevant design decision recording its reason, direction,
coupling and consumer impact, and a review of the affected module dependencies. Follow the
owner-choice process below rather than silently adding a relationship.
Reference folders ending in `-mau` are not production reactor modules.

Notification now composes `mail-module` for SMTP transport and keeps business
templates, rendering, Kafka handling and failure policy in the service. Mail has
no dependency on other support capabilities or the foundation stack. See
[Mail module decision](mail-module-design.md) and
[Notification delivery decision](notification-delivery.md).

## OOP, SOLID and proportionate design

Top-level enums use the `Enum` suffix and live under `entity.enumeration` within
their owning service or support capability. Keep enum ownership and module
boundaries intact. See [Service layout and mapping](service-layout-and-mapping.md)
for the project naming convention and examples.

The accepted exception-handling direction and optional future extensions are
documented in [Exception handling design](exception-handling-design.md). It selects
base exceptions with centralized HTTP handling; its implementation and response
format must be distinguished from that architectural choice.

Use these principles to solve concrete problems, not as a requirement to apply every
pattern to every feature:

- OOP: keep state and behavior with their owner, protect invariants through encapsulation,
  and give classes cohesive responsibilities. Prefer composition when inheritance does not
  represent a valid substitutable relationship.
- SOLID: separate reasons to change (SRP); add extension points for demonstrated variation
  (OCP); preserve behavioral contracts when substituting implementations (LSP); expose
  focused consumer contracts (ISP); keep policy independent of infrastructure details where
  the boundary warrants it (DIP). DIP does not require an interface for every class.
- Favor high cohesion, low coupling, KISS and YAGNI. A concrete class or direct call can be
  the appropriate design. Add abstractions when they clarify ownership, isolate a real
  boundary or support an actual variation, not speculative future requirements.
- Introduce a design pattern only with an explanation of the problem it solves, why the
  simpler approach is insufficient and what maintenance cost it adds. Pattern names alone
  do not justify a design. Do not copy a reference project's architecture without checking
  this project's requirements and constraints.
- Removing duplication must not move business policy out of its service or introduce
  dependencies without a justified technical responsibility and valid architectural direction.
  Some small duplication is an acceptable trade-off.

## Clarification and owner choice before implementation

A material decision changes module/service ownership or dependencies, public contracts,
authentication, storage or cache policy, failure/retry behavior, consistency/transactions,
deployment topology, or introduces a substantial pattern/abstraction. For these decisions:

1. State the problem, known constraints and existing agreed decisions. Label unresolved
   assumptions explicitly; do not treat missing information as agreement.
2. Ask focused questions about requirements that can change the design: expected use cases,
   consumers, business invariants, failure behavior and relevant operational constraints.
   Read available project evidence first rather than asking the owner for facts in the repo.
3. Present the viable options, usually two or three, with how each works and its effects on
   ownership, coupling, complexity, testing, operation and future change. Include the simple
   approach or keeping the current design when viable. Do not invent unsuitable options to
   fill a quota; explain when constraints leave only one viable approach.
4. Give a recommendation tied to the actual requirements, explicitly separate from the
   owner's selection. Ask the owner to choose before implementing the dependent design.
   While waiting, continue only work that does not commit to that choice. Silence or elapsed
   time is not a choice.
5. After selection, record the chosen option, rationale, accepted trade-offs, scope and any
   explicitly accepted assumptions in the relevant design document or ADR. Implement and
   verify the selected behavior, including material failure paths.

Do not repeatedly ask for a decision already explicitly made by the owner. Routine naming,
formatting and local implementation details within an agreed design can proceed. If new
evidence requires a material departure from that design, explain it and ask a focused
follow-up before making the departure. Do not silently change architecture as part of a fix.

## Verification

Review POM dependencies, Java references, configuration and reflective/dynamic bean lookups
against these rules during code review. No dedicated boundary-check script is used.

Test support transport/storage separately. Test the composed workflow in the service,
including cache hit/miss, expiry, infrastructure failures and whether downstream operations
were sent. Keep data-mutating tests isolated from live services.

## Decision and trade-offs

Previously proxy composed Redis caching and OAuth token acquisition. That reduced application
code but hid application policy in a transport library. The accepted design places this
composition in Order. Other services may need a small equivalent composition layer. That
cost keeps the current infrastructure modules reusable and makes each service's policy
visible and testable.

The 2026-11-18 revision replaces the blanket ban on dependencies between support capabilities
with responsibility- and direction-based review, as requested by the project owner. This
allows justified reusable infrastructure dependencies while retaining explicit ownership,
acyclic dependencies and review of coupling and consumer impact. It does not by itself
change the current proxy/caching implementation or authorize a new concrete dependency.
