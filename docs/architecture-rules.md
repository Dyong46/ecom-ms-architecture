# Architecture rules: module boundaries and design decisions

Status: Accepted. Date: 2026-11-15. Decision owner: project owner.

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

## Mandatory rules

1. Independent capability modules (`proxy`, `caching`, `kafka`, `logging`, `mail`) must not depend
   on each other or on application modules. Optional and test dependencies do not bypass this rule.
2. No cross-capability imports, enabling annotations, reflection/bean lookups or configuration
   that implicitly wires another support capability. An interface inside proxy does not make
   a Redis implementation inside proxy independent of caching.
3. Proxy owns HTTP contracts, serialization and transport settings. Its OAuth client only
   performs the requested HTTP exchange. PaymentClient receives Authorization explicitly.
   It must not fetch/cache tokens, read Redis or decide which identity an application uses.
4. Caching owns storage, serialization, namespaces, TTL and reusable expiry validation.
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

## Existing foundation dependencies

The repository already has `web-module → common-module` and
`common-data-model → web-module/common-module` (including a transitive common dependency).
These are recorded legacy foundation relationships, not a claim that every support module is
already fully independent. This change fixes proxy/caching and prevents new relationships;
refactoring the existing foundation stack is outside this change.

Any new relationship requires updating this decision with its reason and consumer impact,
and reviewing the affected module dependencies. Do not silently add an exception.
Reference folders ending in `-mau` are not production reactor modules.

Notification now composes `mail-module` for SMTP transport and keeps business
templates, rendering, Kafka handling and failure policy in the service. Mail has
no dependency on other support capabilities or the foundation stack. See
[Mail module decision](mail-module-design.md) and
[Notification delivery decision](notification-delivery.md).

## OOP, SOLID and proportionate design

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
- Removing duplication must not move business policy out of its service or reconnect
  independent support capabilities. Some small duplication is an acceptable trade-off.

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
code but coupled independent capabilities and hid policy in a transport library. Composition
now belongs to Order. Other services may need a small equivalent composition layer. That cost
keeps infrastructure modules reusable and makes each service's policy visible and testable.
