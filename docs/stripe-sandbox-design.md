# Stripe sandbox checkout

Status: Accepted, 2026-11-21. Owner: project owner.

## Selected scope

The owner selected Stripe Hosted Checkout, VND only, Payment/PaymentAttempt,
a durable webhook inbox and transactional outbox. USD, PayPal, VietQR, stock
reservation/return, discounts and tax are deferred. Shipping remains a zero
placeholder. Zero VND settles as a free payment without calling Stripe.
The owner superseded the separate Payment ingress decision: Gateway now exposes
exactly POST `/webhooks/stripe` and forwards it via `lb://PAYMENT-SERVICE`. Payment
and Notification are no longer excluded from discovery routing. Machine-token
APIs under `/internal/payments` stay private and blocked at Gateway.

One Payment represents one order's amount and currency. Each PaymentAttempt
represents one provider session creation operation. A provider timeout must not
create a new Payment or a new attempt. Explicit retry may create another attempt
only after the preceding attempt is definitively FAILED or EXPIRED. An unresolved
UNKNOWN/REVIEW_REQUIRED attempt blocks that operation. Existing historical records
are UNKNOWN; migration does not invent successful charges or emit old events.

## Flow and ownership

1. Order validates card method, purchases Product with its existing idempotency
   key, commits its PROCESSING snapshot, then calls private Payment with the
   stable order operation key. Reservation/compensation remains deferred.
2. Payment commits the idempotency result, PENDING aggregate and CREATING attempt
   together. It returns its existing integer ID. Order also retains its existing
   integer create response and completes its creation workflow independently of
   whether the customer has paid.
3. A Payment worker claims a PostgreSQL row using SKIP LOCKED and a short lease,
   commits, then calls Stripe outside a DB transaction. The immutable request
   contains the amount, VND, metadata linking attempt/payment/order, return URLs
   and expiry. The provider idempotency key belongs to that attempt.
4. Client calls `GET /api/v1/orders/{orderId}/payment` through Gateway to poll for
   checkoutUrl, then opens Stripe. Order acquires its machine token and calls
   `GET /internal/payments/order/{orderId}`. A PENDING response may have no URL
   while creation is in progress. No frontend page is added in this scope.
5. Stripe signs its webhook and calls Gateway. Gateway permits POST on the exact
   webhook path without Keycloak JWT, preserves the body/signature and forwards to
   Payment. Payment verifies the raw body and timestamp with
   stripe-java before inserting a unique inbox event and returning 200. Processing
   the inbox validates sandbox mode, session ID, payment/order/attempt metadata,
   currency and exact amount. The success redirect is never proof of payment.
6. On paid, Payment locks the aggregate and atomically saves SUCCEEDED, paid_at
   and one PAYMENT_SUCCEEDED outbox row. A zero payment uses the same settlement
   transaction immediately without a Stripe session.
7. Outbox publishing runs outside the DB transaction. Only a Kafka ACK marks the
   row published. Retry keeps the eventId. The existing paymentConfirmation type
   alias remains compatible with Notification; the event now includes paymentId,
   orderId and currencyCode. Order uses a separate consumer group and its own
   transactional event inbox to project paymentStatus=SUCCEEDED after validating
   order reference, amount and currency. creationStatus remains independent.

Payment owns provider settings, lifecycle, retry policy and persistence. Stripe
transport and orchestration remain inside Payment. The external stripe-java
34.2.0 dependency supplies signature verification; JDK HttpClient handles the
small pinned REST contract. No provider abstraction is introduced for future
providers. Proxy owns only HTTP contracts and explicit Authorization/header
transport. Order owns token acquisition, user-facing payment queries and retries.
Existing support relationships remain unchanged; no support module imports an
application implementation. Notification keeps rendering and delivery policy.

## Recovery and limits

- Workers poll at 3 seconds by default. Session reconciliation polls at roughly
  60 seconds after a successful provider response and can settle a missed webhook.
- Creation uncertainty retries the same stored form and provider key. After 23
  hours without a known session, the attempt becomes REVIEW_REQUIRED to avoid a
  fresh create outside Stripe's idempotency retention window. Do not delete the
  ledger or generate another key to bypass this state; inspect Stripe first.
- Sessions currently expire one hour after attempt creation. A long outage before
  first create can therefore fail provider expiry validation; an explicitly new
  attempt is allowed after a definitive failure. It is not a stock reservation.
- Signed events are deduplicated by Stripe event ID; paid never regresses.
  An old attempt's unpaid/expired event cannot override a newer attempt's state.
  Invalid correlations are REJECTED. Unknown attempt references retry up to 50
  processing attempts before rejection. Inbox raw payloads contain personal data;
  operational access and retention follow the application's database policy.
- Delivery is at least once. An ACK lost before the published marker can produce
  duplicates; Order and Notification must deduplicate eventId. There is no claim
  of a distributed transaction or exactly-once delivery.
- New VND amounts must be integral and fit int64. `50000.00` is valid; `50000.50`
  is rejected. VND is sent without multiplying by 100. Stripe validates its own
  minimum and maximum amounts. Product's generic price model remains unchanged.
- Existing public Order authorization/ownership policy is unchanged and remains
  a separate agreed follow-up. Internal Payment still requires the configured
  machine JWT client. Gateway exposes the webhook; it does not expose internal
  payment creation/list/checkout/retry endpoints.

## Local sandbox verification

Payment imports the optional `services/payment/.env.properties` file relative to
the process working directory, alongside its existing Config Server import. Use the repository root
as PaymentApplication's working directory in IntelliJ. The service-local
`services/payment/.env.properties` is ignored by Git;
`services/payment/.env.properties.example` is the safe, committable template.

A local file is provided for you to fill in. On a fresh checkout, create it with:

```sh
cp services/payment/.env.properties.example services/payment/.env.properties
chmod 600 services/payment/.env.properties
```

Fill in STRIPE_SECRET_KEY and STRIPE_WEBHOOK_SECRET, then set STRIPE_ENABLED=true.
Values use properties syntax: no `export` prefix or surrounding quotes. Restart
Payment after changes. The template keeps Stripe disabled until secrets are ready.
Existing STRIPE_* environment variables in the run configuration take precedence
over file values, so remove conflicting values if switching to file configuration.
Never commit real secrets or share them in chat.

Only test keys are accepted; live keys fail startup. Stripe API version defaults
to `2026-11-18.endive` and is configurable via `payment.stripe.api-version`.
For local development, authenticate Stripe CLI in your own terminal and run:

```sh
stripe listen --forward-to localhost:8222/webhooks/stripe
```

Use the webhook secret printed by this listener for the local Payment process.
A Dashboard endpoint has its own different secret. Start/restart Payment after
setting its environment. Start Config Server and Gateway with the updated route.
Gateway must reach Eureka, and Payment must be registered there for the load-balanced route. Keep PostgreSQL, Kafka, Keycloak, Product, Customer and
Order available using the existing local configuration.

With Bruno or Swagger:

1. POST `/api/v1/orders` through Gateway with a new Idempotency-Key, a real
   customer/SKU, recipient/address and paymentMethod CREDIT_CARD. Use an integral
   VND total, for example 50000. Retrying this request keeps the same key/body.
2. GET `/api/v1/orders/{id}/payment` until checkoutUrl is present, then open that
   URL in a browser and use a Stripe sandbox payment method. The return URLs are
   configured destinations; this repository does not implement their UI.
3. Verify GET payment returns SUCCEEDED, Payment has paid_at, inbox is PROCESSED,
   and outbox has published_at. GET Order eventually shows paymentStatus SUCCEEDED
   after Kafka delivery. Creation being COMPLETED alone does not mean paid.
4. Re-deliver the same real event using Stripe's resend mechanism: no new Payment,
   attempt or business success event should be created. Do not use an unrelated
   synthetic `stripe trigger` session to assert payment of this order: its
   metadata/amount will not match the application attempt.
5. After a genuinely expired/failed attempt, POST
   `/api/v1/orders/{id}/payment-attempts` with a new Idempotency-Key. Retry that
   operation using the same key. An active or uncertain attempt must return 409.
6. Create a zero-priced order: no Stripe call/URL; Payment settles and emits its
   success event through the outbox. Negative/fractional VND is rejected.

For deployed sandbox, register the public HTTPS Gateway URL ending in
`/webhooks/stripe` on Stripe. The optional TLS proxy template at
[`deploy/payment-webhooks/nginx.conf.template`](../deploy/payment-webhooks/nginx.conf.template)
now targets Gateway using GATEWAY_UPSTREAM, never Payment directly. Existing
load-balancer/ingress TLS termination can replace this template. Configure Stripe
to send checkout.session.completed, checkout.session.expired,
checkout.session.async_payment_succeeded and checkout.session.async_payment_failed.
This implementation expects snapshot events containing data.object. Keep the
Dashboard endpoint secret in Payment; it differs from the local CLI secret.
Gateway permits only exact POST /webhooks/stripe without a client JWT; other paths
retain existing authentication and internal-path restrictions. Payment remains
responsible for raw-body signature verification, inbox persistence and settlement.
The generic `/api/callbacks/**` CIDR policy is unchanged and is not the Stripe route.
No Payment client Swagger group is added.

## Verification evidence

`StripeLifecyclePostgresTest` uses PostgreSQL Testcontainers, embedded Kafka and a
local Stripe HTTP fixture. It verifies exact VND units, signature/timestamp checks,
inbox duplicates, mismatches, lost create responses and stable provider keys,
webhook-before-create-response recovery, zero bypass, expired retry and outbox
ACK/retry behavior. `OrderPaymentProjectionPostgresTest` verifies transactional
projection, deduplication and rejection of mismatched events against PostgreSQL.
`StripeWebhookRoutingTest` additionally runs a real Gateway HTTP server and an
HTTP Payment fixture discovered through the load balancer. It verifies raw body
and signature preservation, unauthenticated exact webhook POST, downstream error
propagation and internal-route denial.
These are isolated integration tests, not proof of acceptance by live Stripe
sandbox. Real sandbox testing additionally requires local runtime credentials and
the Stripe listener/registered ingress above.

Provider contracts: [Hosted Checkout](https://docs.stripe.com/payments/checkout/how-checkout-works),
[webhooks](https://docs.stripe.com/webhooks),
[idempotency](https://docs.stripe.com/api/idempotent_requests),
[currency units](https://docs.stripe.com/currencies).
