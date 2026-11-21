# Shared internal clients and Ecom OAuth

`com.duong:proxy-module:1.0.0` is a normal Java library. It contains HTTP clients,
integration DTOs and an opt-in Ecom OAuth HTTP client, not a standalone proxy server.
It has no main class, database, repositories or application business logic.

## Current request model

```text
Bruno / frontend -- user token --> Gateway --> Order
                                             |-- CustomerClient --> /internal/customers/{id}
                                             |-- ProductClient  --> /internal/products/purchase
                                             `-- PaymentClient -- Bearer token --> /internal/payments

Order EcomTokenService → AccessTokenCache [caching-module] → Redis
                        └─ cache miss → EcomAuthClient [proxy-module] → Keycloak
OrderPaymentGateway → PaymentClient(Bearer token, request) → Payment
```

Order owns token acquisition/cache orchestration and passes the Authorization header
explicitly. Customer and Product calls do not receive credentials. Payment validates
JWT signature, expiry, issuer and signed `azp` matching the configured client.
Proxy has no dependency on caching-module or Redis. See the mandatory
[architecture rules](../../docs/architecture-rules.md).

## Internal endpoints and boundaries

| Client | Direct service endpoint | Authentication |
| --- | --- | --- |
| CustomerClient | GET `/internal/customers/{customer-id}` | Private network |
| ProductClient | POST `/internal/products/purchase` | Private network |
| PaymentClient | Create, lookup and retry under `/internal/payments` | JWT from `ecom-services` + private network |

Internal controllers delegate to existing CustomerService, ProductService and
PaymentService. They do not duplicate business logic. Product keeps the existing
JSON-array purchase payload and quantity validation. Existing `/api/v1/**`
controllers and their security behavior remain available for external flows.

Gateway rejects an `internal` path segment with 404, including discovery routes
such as `/PRODUCT-SERVICE/internal/products/purchase` and encoded variants.
Product permits the exact internal purchase POST and excludes that POST from CSRF
checks; its external write APIs remain protected. Deployment must expose only the
Gateway externally and keep service ports/Config Server on the private network.
Gateway's filter does not firewall direct connections to service ports.

## Register the HTTP clients you need

Add to a consuming application's POM (version managed by the root parent):

```xml
<dependency>
    <groupId>com.duong</groupId>
    <artifactId>proxy-module</artifactId>
</dependency>
```

Order registers only its three internal clients:

```java
@SpringBootApplication
@EnableProxyModule(clients = {
    CustomerClient.class, ProductClient.class, PaymentClient.class
})
public class OrderApplication {
    // OrderOAuthConfiguration separately composes OAuth HTTP + Redis caching.
}
```

A Customer-only consumer can register just `CustomerClient.class`. No blanket
component scan is needed. Keep Feign configuration classes in their named child
contexts; do not import the Basic Auth interceptor into the application context.

The library uses OpenFeign, Micrometer integration, Jackson annotations and
validation annotations. It does not depend on application modules, web-module,
common-module, JPA or Spring OAuth2 Client. Providers retain their own API DTOs
and domain models; the proxy PaymentMethodEnum is a transport enum mapped by name
from Order's domain enum. Kafka events remain owned by applications.

## Central Config Server settings

Common settings live in
`cloud/config-server/src/main/resources/configurations/application.yml`:

```yaml
application:
  config:
    customer-url: ${CUSTOMER_SERVICE_URL:http://localhost:8090/internal/customers}
    product-url: ${PRODUCT_SERVICE_URL:http://localhost:8050/internal/products}
    payment-url: ${PAYMENT_SERVICE_URL:http://localhost:8060/internal/payments}

proxy:
  http:
    connect-timeout: ${PROXY_CONNECT_TIMEOUT:2s}
    read-timeout: ${PROXY_READ_TIMEOUT:5s}

integration-config:
  ecom:
    oauth-base-url: ${ECOM_OAUTH_BASE_URL:http://localhost:9098}
    oauth-realm: ${ECOM_OAUTH_REALM:micro-services}
    oauth-username: ${ECOM_OAUTH_CLIENT_ID:ecom-services}
    oauth-password: ${ECOM_OAUTH_CLIENT_SECRET:}
```

Order no longer requires `PRODUCT_OAUTH2_CLIENT_ID`, `PRODUCT_OAUTH2_CLIENT_SECRET`
or `PRODUCT_OAUTH2_TOKEN_URI`. Remove old URL overrides pointing to `/api/v1`
and update them to `/internal` when overriding the defaults. Calls still use
configured direct URLs; Eureka registration does not override those URLs.

Timeouts must be positive and fit integer milliseconds. Standard per-client
OpenFeign overrides remain available:

```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          product-service:
            connectTimeout: 2000
            readTimeout: 10000
```

Automatic retries and redirects are disabled. Feign logging defaults to NONE;
Micrometer and the existing access/application logs still work. Remote HTTP
errors preserve FeignException status/body; connection and timeout failures are
not converted to fake results. A RetryableException is an error type, not proof
that a retry occurred.

## Shared Ecom credentials and token client

For team onboarding, follow the [Keycloak client and service secret setup guide](../../docs/ecom-oauth-setup.md)
for client creation, IntelliJ environment variables, deployment and token verification.

Only applications needing machine tokens opt in:

```java
@SpringBootApplication
@EnableProxyModule(clients = CustomerClient.class)
@EnableEcomAuth
public class IntegrationApplication {
    // Registers only the EcomAuthClient HTTP transport and its properties.
}
```

Order's `OrderOAuthConfiguration` enables the independent proxy auth client and
caching module, then wires the token service and Redis adapter owned by Order.
Other consumers decide their own cache and authentication policies.
Customer-only, Product-only and Payment-only consumers need no OAuth client setup.

Provide the shared Keycloak identity through environment/deployment configuration:

```dotenv
ECOM_OAUTH_BASE_URL=http://localhost:9098
ECOM_OAUTH_REALM=micro-services
ECOM_OAUTH_CLIENT_ID=ecom-services
ECOM_OAUTH_CLIENT_SECRET=<secret for that client>
```

Use the actual client ID/secret from Keycloak, with Client authentication and
Service accounts roles enabled. Never commit the secret. The YAML uses environment
placeholders; credentials are not hardcoded or generated by this module. Supply
the same identity to consumers that should share it. Local Java processes do not
automatically read a Docker `.env` file; set their Run Configuration environment.
Missing credentials fail startup only when Ecom auth is explicitly enabled.

`EcomAuthClient` sends:

```http
POST /realms/{realm}/protocol/openid-connect/token
Authorization: Basic <client ID and secret>
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials
```

`oauth-username` means client ID and `oauth-password` means client secret, not a
human login/password. The interceptor is scoped to this client and never attached
to Customer or Product calls. Payment receives its Bearer header explicitly from the caller. Token/credentials DTO toString methods
redact secrets; do not log response bodies or authorization headers yourself.

## Order owns token caching and outbound policy

`EcomTokenService`, `RedisEcomTokenCache`, `EcomTokenCache` and
`OrderOAuthConfiguration` live in `services/order`, not in either support module.
Order explicitly depends on both independent libraries. `@EnableEcomAuth` registers
only HTTP transport; it does not register a token/cache service. The old combined
`@EnableEcomRedisAuth` annotation and Payment OAuth interceptor have been removed.

Order supplies the machine token to PaymentClient. Its Redis fallback, expiry and rotation policy and the local verification commands are in the [Order OAuth guide](../../docs/ecom-oauth-setup.md#order--payment-qua-oauth2).

## Build, restart and verify

From the repository root:

```bash
./services/product/mvnw -f pom.xml -DskipTests package
```

Reload Maven and rebuild in the IDE. Restart Config Server first to serve the
updated common configuration; then restart Customer, Product, Payment, Order and
Gateway. Keep ordinary Gateway user-token authentication configured as before.
Order requires `ECOM_OAUTH_CLIENT_SECRET` in its runtime environment. Payment
requires only the issuer/JWKS URL and allowed client ID, never the client secret.
See the [setup guide](../../docs/ecom-oauth-setup.md#order--payment-qua-oauth2)
for restart steps and receiver configuration.

The internal contract is visible in the three Internal*Controller classes and
HTTP fixture tests. Do not call a live purchase/payment endpoint just to check
availability: these operations mutate data.

Tests use isolated local HTTP stand-ins, MockMvc/WebTestClient and H2. They cover
Customer/Product calls without credentials, authenticated Payment calls, wire payloads, token Basic/form encoding,
explicit Authorization transport, Basic Auth interceptor isolation, failure/timeout/no-retry behavior,
Product security/validation, Gateway path blocking and existing Order behavior.
Real Keycloak credentials and production network isolation are not established
by those tests and must match the deployment environment.

When changing DTOs or method signatures, maintain compatibility or publish a new
module version.

## Checkout idempotency headers

ProductClient.purchaseResponses takes an explicit Idempotency-Key before the batch.
PaymentClient.requestOrderPayment takes Authorization, Idempotency-Key and the request.
Order derives stable downstream keys from its persisted operation ID. Proxy does not
generate keys, cache results or retry purchases/payments implicitly. All consumers
must supply a stable key for retries. See [checkout idempotency](../../docs/checkout-idempotency-design.md).
