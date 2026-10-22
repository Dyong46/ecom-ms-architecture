# Authentication and Authorization Design Note

**Status:** Proposed — deferred for later development  
**Recorded:** 2026-10-22  
**Scope:** API Gateway, business microservices, and reusable support modules

## 1. Context

The system currently uses Spring Cloud Gateway as the external entry point and
OAuth2 Resource Server with JWT validation. Public and protected paths are
configured at the gateway.

As the number of services and endpoints grows, maintaining every public path in
gateway configuration creates duplication and makes authorization policy drift
more likely. A future implementation should let each service own its detailed
authorization rules while retaining coarse protection at the edge.

Authentication and authorization are separate concerns:

- **Authentication** validates the access token and establishes the caller's
  identity.
- **Authorization** decides whether that identity may perform an action on a
  specific resource.
- `@PublicEndpoint` is an authorization rule that permits anonymous access; it
  does not replace token validation for protected endpoints.

## 2. Proposed decision

Adopt a hybrid authorization model:

1. A central identity provider such as Keycloak authenticates users and issues
   OAuth2 access tokens.
2. The gateway validates tokens and applies coarse controls such as public/private
   route namespaces, rate limiting, CORS, callback IP filtering, and routing.
3. Every business microservice is also an OAuth2 Resource Server and validates
   the access token it receives.
4. Each microservice owns fine-grained authorization because it has access to
   domain data such as ownership, tenant, entity state, and business rules.
5. Endpoints are private by default. Only an explicit `@PublicEndpoint` may allow
   anonymous access.

Use a URL convention to avoid declaring every public endpoint separately at the
gateway:

```text
/api/public/**  -> anonymous access is allowed at the gateway
/api/**         -> a valid access token is required at the gateway
```

The downstream service should use the same URL where practical. `RewritePath`
is only needed when the external API contract intentionally differs from the
downstream controller path.

## 3. Target request flow

### Public endpoint

```text
Client
  -> Gateway matches /api/public/**
  -> Gateway applies edge controls but does not require a token
  -> Service finds @PublicEndpoint
  -> Service permits anonymous access
```

### Protected endpoint

```text
Client with Bearer token
  -> Gateway validates token and applies coarse route policy
  -> Gateway forwards the original Authorization header
  -> Service validates signature, issuer, audience, and token lifetime
  -> Service evaluates scope/role and domain-specific authorization
  -> Controller or application service executes
```

### Service-to-service call

```text
Calling service
  -> propagates the user token when acting on behalf of a user, or
  -> obtains a machine token through client credentials
  -> target service validates the token and authorizes the operation
```

## 4. Proposed support modules

Do not combine Servlet and reactive security implementations in one module.

```text
support/security-core
  - @PublicEndpoint
  - shared claim and authority names
  - security-related error contracts

support/security-servlet
  - Spring MVC SecurityFilterChain auto-configuration
  - OAuth2 Resource Server configuration
  - method-security integration
  - default-private authorization manager
  - standard 401/403 Problem Details responses
  - JWT-to-authority conversion

support/security-reactive
  - WebFlux equivalents needed by the gateway
  - reactive JWT-to-authority conversion
  - standard reactive 401/403 responses
```

Business services should depend on `security-servlet`. The gateway should depend
on `security-reactive` only when shared reactive behavior is required.

## 5. Annotation contract

Expected usage:

```java
@RestController
@RequestMapping("/api/v1/products")
class ProductController {

    @PublicEndpoint
    @GetMapping
    PageResponse<ProductResponse> findAll(...) {
        // Anonymous access is allowed.
    }

    @PreAuthorize("hasAuthority('SCOPE_product:write')")
    @PostMapping
    ProductResponse create(...) {
        // A valid token and the required scope are needed.
    }
}
```

Required behavior:

- Missing annotation means authenticated access is required.
- `@PublicEndpoint` may be used on a controller method or, after explicit review,
  on a controller class.
- A public annotation must not override callback IP restrictions, rate limits,
  tenant checks, or other independent controls.
- Role or scope checks should use Spring Method Security.
- Ownership and entity-state checks belong in the application/domain layer, not
  solely in the gateway or annotation.

The implementation must fail closed. If annotation discovery, policy loading,
or token validation fails, protected requests must be denied.

## 6. Options and trade-offs

| Option | Benefits | Costs and risks |
| --- | --- | --- |
| Gateway-only authorization | One policy location; rejects invalid traffic early; simple operations | Direct/internal service access can bypass policy; gateway lacks domain context; policy becomes a central bottleneck |
| Service-only authorization | Rules stay close to data; protects direct and internal calls; strong service ownership | Invalid requests reach services; repeated JWT configuration; consistency and auditing are harder |
| Hybrid gateway + service | Defense in depth; early rejection plus domain-aware decisions; protects external and internal paths | JWT may be validated twice; public policy can be duplicated; more tests and operational configuration |
| Central policy engine or service mesh | Central policy lifecycle and auditing; consistent cross-service rules | Extra infrastructure, latency, runtime dependency, and operational complexity |

The hybrid model is preferred for this repository. A central policy engine such
as OPA or service-mesh authorization should be reconsidered only when policy
volume, organization size, or cross-service requirements justify the added
complexity.

## 7. Security invariants

The future implementation must preserve these rules:

1. Deny access by default.
2. Validate authorization on every request.
3. Validate JWT signature, `iss`, `aud`, `exp`, and `nbf` where applicable.
4. Use asymmetric signing and JWKS-based key rotation rather than sharing a JWT
   signing secret across services.
5. Do not trust user ID, tenant ID, role, or ownership data supplied through
   arbitrary client headers.
6. Do not expose business services directly to external networks.
7. Preserve the `Authorization` header across gateway routing when downstream
   validation is enabled.
8. Use separate user and machine identities for service-to-service calls.
9. Return consistent `401` and `403` Problem Details responses without leaking
   token or exception details.
10. Audit changes to public endpoints as security-sensitive code changes.

## 8. Suggested rollout

### Phase 1 — Foundation

- Create `security-core` and `security-servlet` modules.
- Define `@PublicEndpoint` and default-private behavior.
- Standardize JWT authority conversion and 401/403 responses.
- Add tests proving an unannotated endpoint is protected.

### Phase 2 — One-service pilot

- Integrate the product service first.
- Make product listing explicitly public.
- Keep product creation and purchase protected.
- Verify direct service calls and calls through the gateway.

### Phase 3 — Gateway alignment

- Adopt the `/api/public/**` convention, or document why an exception is needed.
- Remove per-endpoint gateway allowlists that are covered by the convention.
- Keep callback IP policies separate from public endpoint policy.

### Phase 4 — Remaining services

- Integrate customer, order, and payment services.
- Add scope/role checks and ownership tests.
- Add service-to-service machine authentication.

### Phase 5 — Production hardening

- Add audience validation per service.
- Add authorization metrics and audit logs.
- Add contract/security tests to CI.
- Evaluate mTLS and a central policy engine only if requirements demand them.

## 9. Acceptance criteria

Before considering this design implemented:

- [ ] A new controller endpoint is private without any security annotation.
- [ ] Only `@PublicEndpoint` permits anonymous access.
- [ ] Public GET requests work without a token through the gateway and directly
      against the service where direct access is allowed for testing.
- [ ] Protected requests without a token return `401`.
- [ ] Valid tokens without sufficient authority return `403`.
- [ ] Invalid issuer, audience, signature, or expired tokens are rejected.
- [ ] Object ownership and cross-tenant access have negative tests.
- [ ] Gateway and service return compatible Problem Details responses.
- [ ] Public endpoint discovery cannot silently fail open.
- [ ] Servlet and WebFlux security modules are tested independently.

## 10. Open questions

- Should the external public URL always contain `/public`, or should selected
  existing URLs remain public without that prefix?
- Which scopes and audiences should Keycloak issue for each service?
- Should internal calls propagate the user token, use client credentials, or use
  token exchange for each workflow?
- Should class-level `@PublicEndpoint` be prohibited to reduce accidental exposure?
- At what scale would centralized policy evaluation become worth its operational
  cost?

## 11. References

- [Spring Security OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/7.0/reactive/oauth2/resource-server/jwt.html)
- [Spring Security Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html)
- [RFC 6750: OAuth 2.0 Bearer Token Usage](https://www.rfc-editor.org/info/rfc6750/)
- [RFC 9068: JWT Profile for OAuth 2.0 Access Tokens](https://www.rfc-editor.org/info/rfc9068/)
- [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html)
- [OWASP Authorization Patterns](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Patterns_Cheat_Sheet.html)
- [Istio Security Best Practices](https://istio.io/latest/docs/ops/best-practices/security/)
