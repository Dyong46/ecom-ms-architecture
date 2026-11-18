# Repository rules

Read and follow [architecture rules](docs/architecture-rules.md) before changing code.

- Applications in `services/` and `cloud/` own business workflows and compose support capabilities.
- Module dependencies are governed by responsibility and architectural direction, not a blanket ban on support-to-support dependencies. A support module may use another support module when the dependency serves its technical responsibility, has a clear justification and does not introduce cycles or application-specific orchestration.
- Infrastructure and shared modules must not depend on application modules. Shared contracts keep dependencies minimal; common utilities must not depend on business or infrastructure modules. Review direct, transitive, optional and test dependencies, cross-module annotations and runtime bean lookups against the same responsibility and direction rules.
- The current proxy/caching design keeps HTTP transport and cache storage separate, with orchestration in the consuming service. This is a scoped design decision, not a universal prohibition on dependencies between support modules.
- Keep token acquisition/cache orchestration, fallback policy and decisions about outbound authentication in the consuming service.
- Do not place application-specific orchestration in support to avoid a small amount of service code duplication.
- Preserve the documented foundation dependencies within this scope. Document the reason, architectural direction and consumer impact of new module relationships through the architecture decision process; do not add them silently.
- Apply OOP, SOLID, cohesion, low coupling, KISS and YAGNI in context. Use a design pattern only when it solves an identified problem; do not introduce patterns, interfaces, inheritance or abstraction layers merely to follow a checklist.
- Before implementing a material design decision, clarify ambiguous requirements and present viable options, trade-offs and a reasoned recommendation. Let the project owner choose; do not silently infer a preference or implement the recommended option while the choice is pending. Follow the decision process in the architecture rules.
- Reuse decisions already agreed in this conversation/repository. Routine implementation details within that scope can proceed without another design-choice question. If new evidence changes the scope or trade-offs, raise the choice again explicitly.
- Review module dependencies against the architecture rules after changes to support modules, and run tests appropriate to changed behavior.
