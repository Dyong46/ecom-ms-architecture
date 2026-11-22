# Architecture rules

Accepted by the project owner on 2026-11-15. Dependency direction revised on 2026-11-18.

## Ownership and dependency direction

| Owner | Responsibility |
| --- | --- |
| Applications in `services/` and `cloud/` | Own workflows and compose support modules. Keep pricing, stock, payment eligibility, order state, compensation and transaction boundaries in the owning service. |
| Infrastructure libraries in `support/` | Provide reusable technical capabilities. A support-to-support dependency requires a technical purpose, an approved decision and an acyclic dependency graph. Keep infrastructure independent of application modules. |
| Shared contracts | Keep DTOs, interfaces and events independent of application modules, with minimal dependencies. |
| Common utilities | Keep general helpers independent of business and infrastructure modules. |

Preserve the foundation dependencies `web-module → common-module` and `common-data-model → web-module/common-module`, including the transitive common dependency. Directories ending in `-mau` contain reference code outside the production reactor.

Enable modules explicitly. Scope component scans to the intended beans. Supply secrets at runtime and keep tokens, secrets and cache values out of logs.

## Current checkout boundary

| Owner | Responsibility |
| --- | --- |
| Proxy module | HTTP contracts, serialization and transport settings. The OAuth client performs the requested exchange. The caller supplies the Authorization header to PaymentClient. |
| Caching module | Storage, serialization, namespaces, TTL and expiry validation. |
| Consuming service | Choose outbound identity and credentials. Compose token acquisition, cache hits and misses, refresh and fallback in integration or configuration code. Own business retries and idempotency. |

Order accepts a small amount of adapter code to make its authentication and failure policies explicit and testable. Other support relationships follow the dependency rules above. [HTTP client design](../support/proxy-module/README.md) · [Cache design](../support/caching-module/README.md)

## Changing the design

A decision is material when it changes ownership, dependencies, public contracts, authentication, storage, cache or retry policy, failure behavior, consistency, transactions or deployment topology, or introduces a substantial abstraction.

1. Read the relevant decision. State the problem, constraints and open assumptions. Ask about consumers, invariants or failure paths that could change the choice. [Decisions by topic](README.md)
2. Explain viable options, including the current design when appropriate. Describe trade-offs in coupling, complexity, testing, operation and future changes. Recommend an option and wait for the owner's selection before implementing it.
3. Record the choice, rationale, scope, assumptions and accepted trade-offs in the relevant design document. For a new module relationship, document its purpose, direction, coupling and consumer impact.
4. Implement and test the selected behavior and failure paths. Reuse agreed decisions for routine work. Reopen a choice when new evidence changes its scope or trade-offs.

## Implementation and review

- Apply OOP and SOLID to keep responsibilities cohesive, invariants explicit and coupling low. Follow KISS and YAGNI. Justify a pattern with the problem it solves, the limits of the simpler solution and its maintenance cost. A shared workflow needs actual consumers and its own approved abstraction.
- Keep top-level enums in the owner's `entity.enumeration` package with the `Enum` suffix. Update the handwritten OpenAPI contract when a client route or payload changes. [Service layout](service-layout-and-mapping.md) · [API contracts](README.md)
- Review direct, transitive, optional and test dependencies in the POMs, plus Java imports, cross-module annotations, configuration, reflection and runtime bean lookups.
- Test support transport and storage separately from the service workflow. For token caching, cover hits, misses, expiry, infrastructure failures and whether a downstream call occurred. Isolate data-mutating tests from live services.
