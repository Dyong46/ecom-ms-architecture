# Notification: durable email jobs and SMTP retry

Status: Accepted. Date: 2026-11-16. Decision owner: project owner.

## Decision and scope

The owner selected MongoDB job state plus a scheduled worker over Kafka retry
topics/DLT. Notification owns job persistence, failure classification, retry,
leases and idempotency; `mail-module` remains synchronous SMTP transport only.
There are no new relationships between independent support capabilities.

```text
Order / Payment → Kafka event with eventId
  → Notification listener → render email → durable Mongo job → acknowledge offset
  → Worker claims job → SMTP → persist SENT / RETRY / FAILED
```

`SENT` means SMTP accepted the submission (`SUBMITTED`), not inbox delivery or read.
No DELIVERED/BOUNCED callbacks or exactly-once SMTP guarantee are implemented.

## Event identity and Kafka acknowledgement

OrderConfirmation and PaymentNotificationRequest now carry an additive `eventId`.
Existing Java constructors generate a UUID once when the event object is created.
Resend the same event with the same ID; constructing another event generates a new
identity. Domain-level duplicate requests and producer transaction/outbox reliability
remain outside this change.

Notification uses `eventId` as both the stored eventId and Mongo `_id`. The native
unique `_id` index prevents duplicate jobs even with concurrent insert/redelivery;
duplicates never overwrite the original email snapshot or restart a completed job.
For records published before this field existed, use
`legacy:<topic>:<partition>:<offset>`. This fallback deduplicates redelivery of the
same Kafka record, not republishing the payload to a new offset. Topic recreation
must not reuse a legacy namespace without an explicit migration plan.

The listener only prepares and inserts a job; it never performs SMTP. The service
uses majority, journaled Mongo writes and majority reads. After an acknowledged
insert (or a confirmed existing duplicate), it acknowledges Kafka synchronously
with MANUAL_IMMEDIATE; enable-auto-commit is false. Mongo errors leave the offset
unacknowledged. Do not override these durability settings with weaker runtime
configuration. Replica sets are recommended for production majority durability;
standalone test containers verify query/concurrency behavior, not replica failover.

The Config Server's shared `application.yml` defaults to `ack-mode: record`.
Notification overrides it with `manual_immediate` in the Config Server's
`notification-service.yml`, in addition to its local fallback configuration.
Setting only the local value is insufficient because imported remote configuration
takes precedence. If startup uses RECORD, listeners requiring `Acknowledgment`
fail with `No Acknowledgment available ... must have a MANUAL AckMode` before
enqueueing a job. Deploy/restart Config Server with the updated configuration,
then restart Notification to reload it. Do not reset offsets to resolve this error.

The Notification error handler retries failed ingestion with exponential delays:
**1 / 2 / 4 / 8 / 16 / 32 / 60 seconds**, then stays at **60 seconds** while the
record keeps failing. Changing exception types does not reset that record's retry
sequence. A new failing record starts its own sequence at one second; restarting
or reassigning a consumer can also reset the in-memory retry state. The handler
does not recover/commit a failed record. There is no retry topic or DLT.

Each failure writes one concise WARN with topic, partition, offset, attempt and
exception class, without payload, exception message or stack trace. Framework
retry stack traces are DEBUG, available when explicitly enabled for diagnosis.
The Kafka client's seek INFO messages can still appear at the reduced retry rate.

This is a blocking consumer backoff, not continuous busy polling. New events on
the blocked partition wait behind the failing record. Other partitions assigned
to the same consumer can also be delayed; other consumer threads/instances can
continue processing their own partitions. Creating another consumer does not let
it bypass an earlier failed record on the same partition: assignment/rebalance
still gives each partition to one consumer in the group. The 60-second cap trades
lower load during an outage for up to the remaining backoff delay before retry
after recovery. It does not change the worker's five-second Mongo polling interval
or the separate SMTP retry schedule.

Poison records and prolonged database errors can therefore block consumption
until an operator resolves them. Invalid render/input data that reaches the listener
is recorded as FAILED/MESSAGE_PREPARATION_FAILED and then acknowledged; it is not
an SMTP retry. Deserialization failures cannot be converted into a valid job and
remain blocked for operator investigation rather than silently skipped.

## Stored state and retry policy

Jobs stay in the existing `notification` Mongo collection. The prepared sender,
recipient, subject and rendered HTML are stored so retries do not change when
configuration or templates change. Existing event payload fields are retained.

| Status | Meaning |
| --- | --- |
| PENDING | Durably saved, eligible for its first attempt |
| PROCESSING | Claimed by a worker with an active lease/token |
| RETRY | A known temporary failure; wait until nextRetryAt |
| SENT | SMTP accepted submission; submittedAt is recorded |
| FAILED | Permanent/unclassified failure, preparation failure or attempt limit reached |

Fields include eventId, attemptCount, lastAttemptAt, nextRetryAt, leaseUntil,
leaseToken, lastError and submittedAt. `lastError` stores a safe diagnostic code,
not exception text, credentials or SMTP payload. Successful completion clears the
previous error, retry deadline and lease. Attempt history is not a separate log.

There are at most **five claims/attempts in total**, including the first one.
Delays after failed attempts 1–4 are **1 min / 5 min / 15 min / 1 hour**, with
uniform ±20% jitter. The deadline is persisted; no thread sleeps during backoff.
A crash before the SMTP call can consume an attempt conservatively.

- SMTP 4xx responses and connection/I/O failures are retryable.
- SMTP 5xx, authentication, address/message preparation and TLS handshake failures
  are permanent. Unknown failures are terminal rather than retried blindly.
- Nested causes, JavaMail nextException and per-message MailSendException errors
  are inspected. A permanent reason wins if multiple reasons are present.
- The fifth failed attempt becomes FAILED with no nextRetryAt.

## Claims, leases and recovery

Each poll uses atomic findAndModify with a new UUID lease token and increments the
attempt counter before sending. Only PENDING/RETRY jobs whose deadlines have passed,
or PROCESSING jobs whose lease expired, are eligible. The defaults are a five-second
poll, a two-minute lease and at most 20 jobs processed sequentially per poll cycle.
Multiple service instances coordinate through Mongo; no distributed support module
or additional lock service is needed.

Completion/failure updates require `_id + PROCESSING + leaseToken`. A stale worker
cannot overwrite a newer worker's outcome. An expired fifth attempt is changed to
FAILED/LEASE_EXPIRED_AT_ATTEMPT_LIMIT, rather than left PROCESSING forever. Other
expired jobs are reclaimed on a later poll with a new token and attempt count.

The worker does not heartbeat/extend a running claim. SMTP connect/read/write
socket timeouts default to 10 seconds each, with a two-minute lease providing a
margin for ordinary calls. Network/DNS pauses, process suspension or unusually slow
operations can still outlive a lease: a second worker can attempt the job while the
old SMTP result is uncertain. Tune the lease above measured send latency; adding
heartbeats is a future extension if long-running sends become a real requirement.

SMTP and Mongo updates are not one transaction. If SMTP accepts a message but the
process crashes or Mongo cannot record SENT, lease recovery may send it again.
Timeouts can also occur after provider acceptance. Token fencing prevents stale
Mongo writes, not duplicate external SMTP effects. A terminal FAILED after lease
exhaustion likewise does not prove that no mail was accepted during earlier attempts.

## Operations and compatibility

- `notification.delivery.enabled` / `NOTIFICATION_DELIVERY_ENABLED`: defaults true.
  False pauses the worker; it does not stop listeners from saving jobs.
- `notification.delivery.lease` / `NOTIFICATION_DELIVERY_LEASE`: defaults PT2M.
- `notification.delivery.poll-delay-ms` / `NOTIFICATION_DELIVERY_POLL_DELAY_MS`:
  defaults 5000.
- Standard spring.mail.properties timeouts remain configurable per environment.
- Two non-unique queue indexes (status/nextRetryAt and status/leaseUntil) are created
  lazily before the first insert/claim. The application needs index creation rights.
- Legacy notification documents without status are never automatically resent.
- No unauthenticated status endpoint or manual-resend API is added. Inspect Mongo
  by eventId; a future API needs an explicit authorization/access design.

Example Mongo queries against the Notification database:

```javascript
db.notification.findOne({eventId: "<event UUID>"}, {
  eventId: 1, status: 1, attemptCount: 1, lastError: 1,
  lastAttemptAt: 1, nextRetryAt: 1, submittedAt: 1, leaseUntil: 1
});
db.notification.countDocuments({status: "FAILED"});
db.notification.countDocuments({status: {$in: ["PENDING", "RETRY"]}});
```

Deploy producer changes and Notification together where possible. The consumer
accepts old records via the legacy fallback. Do not bulk resend legacy records or
reset terminal jobs without considering possible prior SMTP acceptance. Retention
and operator retry policies are not implemented in this change.

## Verification

Run `./services/product/mvnw -f pom.xml -pl services/notification,services/order,services/payment -am --batch-mode verify`.
Mongo integration tests use an isolated disposable container, never the service DB.
They cover duplicate insertion, concurrent claims, due times, restart recovery,
lease fencing, terminal attempt exhaustion and legacy data. Unit tests cover SMTP
classification, jitter bounds, worker completion failures and save-before-ack.
Boot configuration tests assert manual synchronous acknowledgements and Mongo
majority/journal settings. SMTP is mocked; no real email is sent.
