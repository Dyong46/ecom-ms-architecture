# PostgreSQL checkout idempotency

Status: Accepted. Date: 2026-11-20. Decision owner: project owner.

## Decision and options

The owner selected persistent idempotency across Order -> Product -> Payment.
Reservation, stock return, payment-provider state and authorization remain deferred.
A successful response lost in transit must not cause another stock decrement or
Payment insert. Redis is not the source of idempotency truth.

For stable recovery, the owner chose to commit Order and its snapshots before
calling Payment. The alternative was to keep Order invisible until completion,
preallocate an ID and store all progress separately. That preserves the previous
visibility but requires a more complex ID/persistence design. The selected option
exposes incomplete orders with creationStatus PROCESSING; completion sets COMPLETED.
These are creation-progress states, not settlement/delivery states.

## HTTP contract

Idempotency-Key is required on create Order, purchase Product and create Payment,
including their internal counterparts. Keys contain 1-128 ASCII letters, digits,
dot, underscore, colon or hyphen. Generate a UUID per operation and retain it across
retries/restarts. Missing/malformed keys return 400 before mutation.

Within each service, public/internal entry points share one operation ledger.
Keys are globally scoped to that operation in that service. Identity/tenant-scoped
keys require the deferred authorization design. Idempotency does not authorize
access. Use unguessable keys; do not log keys or snapshot contents.

| Situation                   | Result                                                                               |
|-----------------------------|--------------------------------------------------------------------------------------|
| Same key/input, completed   | Saved business result in the existing 200 envelope; trace/request headers may differ |
| Same key, different input   | 409, without downstream calls                                                        |
| Same key currently locked   | 409 processing; retry the same key later                                             |
| Failed/incomplete operation | Binding survives; retry the same input/key                                           |

Errors are not cached as success. A later retry can succeed if a business
precondition changes, such as available stock. Order also rejects a different key
for the same reference before purchasing Product. A different key for an existing
Payment order returns a conflict rather than inserting another record.

Fingerprints use SHA-256 over canonical JSON: sorted object keys and normalized
decimals. SKU batches are sorted by SKU ID, preserving quantity. Order's ignored
create-input id is excluded. Validated delivery details remain in the fingerprint.
Unknown JSON fields are ignored by the existing request DTO contract.

## Transactions and recovery

Each service owns idempotency_request and its service-local implementation. A short
independent transaction inserts the unique key/fingerprint and a random stable
operation ID with ON CONFLICT DO NOTHING. There is no automatic expiry/cleanup;
deleting bindings can allow old retries to execute again.

Execution takes the committed ledger row with FOR UPDATE NOWAIT. PostgreSQL 55P03
is explicitly translated to a processing conflict. Successful result JSON commits
atomically with the local business changes. Process death rolls back the active
transaction and releases its lock; the binding remains. No lease expiration or
manual unlock is needed.

Product commits the stock decrement and full purchase response together. Replay
uses that response even after prices/options change or SKU deletion, without a
fresh purchase or current-catalog read.

Order holds its ledger lock while a separate transaction commits the PROCESSING
Order, lines and order_checkout_draft. Product receives purchase:<operation-id>.
If its response is lost before draft commit, retry uses that key to recover the
original snapshot. The draft stores the complete Payment request and confirmation
event, including buyer data, totals and event ID. Once saved, retry does not reload
Customer or repurchase Product.

Order calls Payment with payment:<operation-id>. Payment commits its record and
saved result ID together. Order then atomically marks creation complete and stores
the order ID result. The same IDs/outgoing snapshots survive application restart.

## Trade-offs and boundaries

This synchronous design keeps a transaction/connection and row lock open during
outbound calls; draft preparation temporarily needs another Order connection.
Bound outbound timeouts and size the pool for checkout concurrency. Ledger and
draft JSON contain customer snapshots and consume storage; retention requires a
separate decision. Bindings currently remain indefinitely.

Notifications are sent after local commit and are not re-emitted on successful
replay. There is no transactional outbox: a crash between commit and publish can
still lose an event. This guarantees idempotent database mutations, not exactly-once
Kafka/email delivery. Failed checkout can retain deducted stock and a PROCESSING
Order indefinitely. No reservation, refund, cancellation or stock return is added.

All orchestration/failure decisions/token acquisition remain service-owned. Proxy
only transports the explicit header and existing payloads. No new production
module relationship or shared workflow is introduced. Local ledger duplication
avoids placing checkout policy into support. Order/Payment gain only test-scoped
Testcontainers dependencies.

## Migration and verification

Product V2, Payment V2 and Order V3 add ledgers without changing executed baselines.
Existing orders receive creation status COMPLETED; no ledger history is invented.
Only operations executed through the new keyed endpoints have replay history.

PostgreSQL tests cover replay, payload mismatch, concurrent NOWAIT locking, rollback,
retained bindings, decimal normalization and stable drafts. Proxy HTTP tests verify
header propagation without implicit retry/interceptors.

After packaging, run the isolated HTTP scenario:

```sh
./services/order/mvnw -o -pl services/order,services/product,services/payment -am package -DskipTests
python3 scripts/test_checkout_idempotency.py
```

The script runs three real services on ephemeral loopback ports against disposable
PostgreSQL. Customer/OAuth use local fixtures. HTTP proxies return 504 only after
real Product/Payment commits. It changes the catalog price, restarts the Order JVM,
retries the same key and verifies one order, one payment, one stock decrement and
preserved price. Existing service databases, Kafka, Redis and Elasticsearch are
not used. Kafka delivery itself is not tested.
