# Order snapshots

Status: Accepted. Date: 2026-11-20. Decision owner: project owner.

## Decision and ownership

Order persists purchase-time snapshots rather than rebuilding historical orders
from current Customer/Product data. IDs remain references for tracing, without
cross-service foreign keys. The owner selected explicit recipient/address input
in the create-order request instead of always using the Customer profile address.
The buyer and recipient can therefore be different people.

Customer full name (first name followed by last name) and email come from the
existing internal Customer response. Recipient full name, phone, shipping address
and optional delivery note come from the validated Order request. They do not
update the Customer profile. Address line 1, ward, city and uppercase two-letter
country code are required; line 2 and postal code are optional. Phone is a string
with basic length/character validation, not country-specific phone verification.

SKU IDs, code, product name, options, unit price and quantity continue to come
from Product's purchase response. OrderLine additionally persists a variant
display name, built from option names sorted lexically, e.g. `Color: Blue / Size: M`.
A SKU without selections uses its SKU code as the display name. No new Product
field or shared module relationship is introduced.

## Money and scope

The owner selected VND and a shipping fee placeholder of zero. Order records
`itemsSubtotal`, `shippingFee` and `currencyCode`; the existing response `amount`
is the total payable (`itemsSubtotal + shippingFee`) sent to Payment and the
existing confirmation event. Client input cannot set those amounts or currency.
There is no shipping quote/calculation integration yet. Discount and tax are deferred.

Payment and the current event contracts do not carry a currency field. This scope
retains the single-currency convention rather than adding conversion or multiple
currencies. Revisit those contracts before introducing a second currency.

## Persistence and compatibility

Flyway V2 adds snapshot columns without modifying the executed V1 baseline.
Existing orders/lines retain null in new fields: current master data cannot
reconstruct historical buyer, delivery or variant display values reliably.
Historical total amounts and existing item snapshots are preserved.

New create requests require recipient/address data. This is an intentional client
contract change; list/detail responses add snapshot fields, and the existing
order-lines endpoint adds `variantName`. Reads use Order's database only.
The existing create-input `id` is ignored, matching its documented server-assigned
behavior and preventing a create request from replacing a historical snapshot.

There is no snapshot edit endpoint. Changes in Customer/Product do not propagate
into saved orders. Authentication, distributed checkout failure handling, payment
state and notification transport are unchanged. Order still owns composition of
Customer/Product/Payment capabilities.

## Verification

Tests cover invalid recipient/address rejection before checkout, server-derived
buyer and monetary data, different buyer/recipient identities, database persistence,
snapshot reads without Customer/Product calls, variant labels and V1-to-V2 upgrades
without historical backfill. The persistence tests use isolated H2 in PostgreSQL
compatibility mode; they do not mutate live service databases.

## Idempotency follow-up

[Checkout idempotency](checkout-idempotency-design.md) adds PROCESSING/COMPLETED
creation progress and commits these snapshots before Payment so retry resumes the
same order. It does not introduce an edit endpoint.
