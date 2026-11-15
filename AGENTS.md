# Repository rules

Read and follow [architecture rules](docs/architecture-rules.md) before changing code.

- Applications in `services/` and `cloud/` own business workflows and compose support capabilities.
- Independent support capabilities must not depend on one another, even through optional dependencies, cross-module annotations or runtime bean lookups.
- Specifically, `proxy-module` must not import or depend on `caching-module`, and vice versa.
- Keep token acquisition/cache orchestration, fallback policy and decisions about outbound authentication in the consuming service.
- Do not place application-specific orchestration in support to avoid a small amount of service code duplication.
- Preserve the documented legacy foundation dependencies; do not add new support relationships silently.
- Apply OOP, SOLID, cohesion, low coupling, KISS and YAGNI in context. Use a design pattern only when it solves an identified problem; do not introduce patterns, interfaces, inheritance or abstraction layers merely to follow a checklist.
- Before implementing a material design decision, clarify ambiguous requirements and present viable options, trade-offs and a reasoned recommendation. Let the project owner choose; do not silently infer a preference or implement the recommended option while the choice is pending. Follow the decision process in the architecture rules.
- Reuse decisions already agreed in this conversation/repository. Routine implementation details within that scope can proceed without another design-choice question. If new evidence changes the scope or trade-offs, raise the choice again explicitly.
- Run `python3 scripts/check-support-boundaries.py` after changes to support modules, plus tests appropriate to changed behavior.
