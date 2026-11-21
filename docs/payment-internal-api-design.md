# Payment internal API

Status: Accepted. Date: 2026-11-21. Decision owner: project owner.

## Decision

The owner selected moving both create and paginated list operations to
`/internal/payments`, instead of keeping the read operation public. The previous
`/api/v1/payments` controller is removed. Order already calls the internal POST;
its explicit Authorization and Idempotency-Key headers remain unchanged.

Both POST and GET use Payment's existing machine-token policy: a valid Keycloak
JWT whose azp matches payment.security.allowed-client-id (default ecom-services).
The remaining security chain permits only explicit health/info/Prometheus/error
paths and denies other paths, so the removed URLs cannot bypass the internal rule.
Pagination, validation, response envelopes and idempotency behavior are preserved.
The internal-only route change itself needed no storage migration or new module
dependency; the later Stripe extension below adds lifecycle tables and its SDK.

## Gateway and documentation

The owner revised the webhook topology: Gateway now routes exact POST
`/webhooks/stripe` to Payment without requiring a Keycloak client JWT. Payment
verifies Stripe's signature and durably stores the inbox before ACK. Payment and
Notification are no longer excluded from discovery routing. The internal-path
boundary still rejects direct/discovery-prefixed internal URLs at the edge.
Keep business-service ports private.

Payment business operations remain absent from client Swagger. The internal
contract targets Payment directly; its webhook operation specifies Gateway as its
server. No public alias for internal create/list/checkout/retry is introduced.

## Consumer impact and verification

Clients must use Order to create checkout/payment records. A trusted internal
consumer can list Payment directly with its machine token. Public Payment clients
must stop using the old URLs. Restart Config Server, Gateway and Payment after
building to apply route/resource changes; Order's existing internal client stays valid.

Tests cover POST/GET machine authentication, wrong-client denial, rejection of old
public paths, internal pagination/validation, Gateway webhook forwarding and
internal-path rejection, and removal of client Swagger Payment. The isolated checkout HTTP script uses authenticated internal
GET for readiness and continues exercising real Order -> Product -> Payment calls.

## Development verification workflow

Payment business APIs remain internal. The signed Stripe webhook is now routed
through Gateway, following the owner's revised ingress decision. Developer verification is not a reason to publish it, add a public
alias or bypass its authentication.

After developing or changing Payment, developers must verify the affected behavior
using one or more of these paths, according to the scope of the change:

1. Run isolated unit/integration tests for business logic, validation, persistence,
   transaction boundaries and idempotency. Use test databases and dependencies.
2. Call `/internal/payments` directly with Bruno/Postman in a dev/test environment,
   using the allowed machine token and, for POST, an Idempotency-Key. Use the
   service's internal address; where needed, use authorized VPN/port-forward access.
   Keep authentication enabled. The internal OpenAPI file defines the HTTP contract.
3. Exercise the API through its consuming service, currently Order. Create an Order
   and verify the Payment call, responses and resulting state. Include retries and
   failure cases when the change affects cross-service behavior.

These paths provide different evidence: isolated tests check local behavior, direct
HTTP calls check the internal contract/authentication, and consumer-driven checks
verify integration. Choose the paths needed to cover the change; all three are not
mandatory for every small change. Keep dev/test credentials and data separate from
production, without adding a public route for manual testing.

## Stripe sandbox extension (2026-11-21)

See [Stripe sandbox checkout](stripe-sandbox-design.md) for the current payment
lifecycle. Creation success does not prove settlement: positive VND payments use
Hosted Checkout and settle only after verified webhook/reconciliation. Zero VND
settles locally. Success events are now written transactionally to an outbox.
Private checkout lookup/retry APIs and the Gateway-routed signed webhook are
described in the updated Payment and Order OpenAPI contracts.
