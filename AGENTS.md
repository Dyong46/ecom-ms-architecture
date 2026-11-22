# Working in this repository

## Before editing

Read the [architecture rules](docs/architecture-rules.md) before changing code. Locate the service that owns the behavior and read its accepted design through the [docs index](docs/README.md). The distributed systems learning path includes planned features. Check the current implementation and tests before treating a topic as existing behavior.

## Decisions and boundaries

- Applications in `services/` and `cloud/` own workflows. Support libraries implement technical capabilities. In checkout, the consuming service chooses credentials and composes token acquisition, cache and fallback. Proxy handles HTTP and caching handles storage.
- Judge dependencies by responsibility, direction and cycles. A justified support-to-support dependency is possible. Preserve existing foundation relationships and record purpose, direction and consumer impact before adding a new one.
- Proceed autonomously with routine implementation, bug fixes and changes the owner explicitly requested. Stay within scope and follow accepted designs. If a significant architectural choice remains open, including a change to an established boundary the owner has not approved, explain the alternatives and trade-offs, recommend an approach and obtain approval before implementation. Record the decision.
- Prefer the simplest design that protects the owning service's invariants. A new pattern or abstraction needs a concrete problem and actual consumers.

## Documentation

- Documentation is optional for routine coding tasks. Skip automatic READMEs, design documents, tutorials and implementation notes.
- When a change affects a documented contract, decision, failure scenario or operating procedure, update its existing source of truth. Keep module-specific guidance with the owning module and cross-service guidance in `docs/`.
- Write concise instructions for the reader's task. Skip routine framework usage and details already clear from code or official library documentation.
- Create a document only on explicit request or for a significant architectural or operational need. If that need calls for a new standalone design document, explain why an existing document is insufficient and ask the owner first.

## Before handing back a change

- Match validation to the change and report results and any skipped checks.
  - Documentation only: check links, references and formatting. Skip Maven tests.
  - Application code: when feasible, run relevant tests for affected modules and required dependencies. For Order, for example, run `./services/order/mvnw -f pom.xml -pl services/order -am --batch-mode verify` from the repository root.
  - Shared support: check direct and transitive consumers, their wiring and affected tests.
  - Configuration: validate the affected settings and, when feasible, startup behavior.
  - Use the full Maven reactor only when the impact spans modules or broader verification is justified.
- After a support change, inspect POMs and consumer wiring, including optional and test dependencies, annotations and runtime bean lookups.
- Update the handwritten OpenAPI contract when changing a client route or payload. For workflow changes, verify failure and retry behavior in the consuming service.
- Do not introduce standalone test scripts, sample applications, or test infrastructure unless explicitly requested. Prefer existing project testing conventions when tests are required.
