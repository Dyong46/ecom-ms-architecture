# Feature Roadmap

**Status:** Living document  
**Purpose:** Define the order in which business and production capabilities are
added. Technology is introduced only when there is a concrete problem for it to
solve.

The phases are ordered by dependency, not by calendar date. A later phase may be
explored earlier, but it should not hide correctness problems in an earlier phase.

## Phase 1 — Business correctness and distributed consistency

**Goal:** Make the checkout flow correct before optimizing or deploying it at scale.

### Capabilities

- Establish `inventory-service` as the owner of stock and reservations.
- Implement the Order state machine and validate every state transition.
- Reserve inventory before payment, with expiration, confirmation, and release.
- Compensate by releasing inventory and cancelling the order when payment fails.
- Store unfinished compensation so recovery can continue after a restart.
- Add Transactional Outbox for reliable event publication.
- Add Consumer Inbox and idempotency for HTTP commands and Kafka events.

### Main technologies

- PostgreSQL and Flyway for transactional state and constraints.
- Kafka for asynchronous workflow events.
- Testcontainers for database and integration tests.

### Completion evidence

- Concurrent orders cannot oversell stock.
- Duplicate commands and events do not create duplicate payments or stock changes.
- Failed checkout scenarios end in a known state and can be recovered.
- State transitions, expiration, and compensation paths have integration tests.

## Phase 2 — Event contracts and safe evolution

**Goal:** Allow producers and consumers to evolve without silently breaking Kafka
event compatibility.

### Capabilities

- Define schemas for order, inventory, payment, and notification events.
- Register and version event schemas.
- Select and enforce a compatibility policy.
- Validate event compatibility in CI.
- Document semantic changes that schema validation cannot detect.

### Main technology

- Schema Registry with an agreed serialization format such as Avro, Protobuf, or
  JSON Schema.

### Completion evidence

- An incompatible schema change fails before deployment.
- Producers and consumers have integration tests against registered contracts.
- Every event includes stable identifiers and a schema version or schema ID.

## Phase 3 — Service resilience and failure recovery

**Goal:** Prevent a slow or failing dependency from causing cascading failures.

### Capabilities

- Define an explicit timeout budget for every outbound call.
- Retry only transient and idempotent operations, using bounded backoff and jitter.
- Add circuit breakers per dependency.
- Add bulkhead or concurrency limits where resource isolation is needed.
- Add DLQ handling, replay procedures, and operator-visible recovery state.

### Main technology

- Resilience4j for timeout, retry, circuit breaker, bulkhead, and rate-limit policies.

### Completion evidence

- Dependency failure tests demonstrate timeout and circuit-breaker behavior.
- Retry cannot duplicate a payment, reservation, or event effect.
- DLQ messages can be diagnosed and replayed using a documented procedure.

## Phase 4 — Observability and production operations

**Goal:** Make a distributed business flow traceable, measurable, and actionable.

### Capabilities

- Propagate trace context through HTTP and Kafka.
- Add structured logs containing `traceId`, `orderId`, and `eventId` where relevant.
- Measure request latency, error rate, Kafka lag, outbox lag, payment failures, and
  expired reservations.
- Build dashboards and actionable alerts.
- Define initial SLI/SLO and operational runbooks.

### Main technologies

- OpenTelemetry for standardized traces, metrics, and context propagation.
- Prometheus for metric collection and time-series queries.
- Grafana for dashboards and alert visualization.
- A trace backend such as Zipkin, Jaeger, or Grafana Tempo.

### Completion evidence

- One checkout request can be traced across services and Kafka consumers.
- Dashboards expose the health of the checkout flow, not only host resources.
- Alerts link to a runbook and avoid high-cardinality metric labels.

## Phase 5 — Security and data protection

**Goal:** Protect service access, credentials, and persisted business data.

### Capabilities

- Complete service-level JWT validation and authorization.
- Define machine identities for internal service calls.
- Move credentials to environment-specific secret management.
- Add TLS, scope/role checks, and security integration tests.
- Automate PostgreSQL and MongoDB backups and verify restore procedures.
- Measure recovery point and recovery time objectives.

### Completion evidence

- Unauthorized service and user flows are rejected by integration tests.
- Secrets are not stored in application configuration or repository history.
- A restore drill proves that backup data is usable.

## Phase 6 — Performance and evidence-based scaling

**Goal:** Measure system capacity and find bottlenecks before adding scaling
technologies.

### Capabilities

- Model realistic browse, checkout, payment-failure, and traffic-spike scenarios.
- Establish throughput, error-rate, and p95/p99 latency baselines.
- Test concurrency invariants while the system is under load.
- Compare measurements before and after every optimization.
- Add caching, search indexing, partitioning, or sharding only for a measured need.

### Main technology

- k6 as the initial load-testing tool, or Gatling when JVM-based test code and its
  DSL are preferred. Only one is required initially.

### Conditional technologies

- Redis for a defined cache, rate-limit, or coordination use case, with documented
  TTL, invalidation, persistence, replication, and failover behavior.
- Elasticsearch when product-search requirements exceed database search.

### Completion evidence

- Load-test scenarios are repeatable locally and in CI or a dedicated environment.
- Capacity results include the test environment and workload assumptions.
- Optimization decisions include before-and-after measurements.

## Phase 7 — Deployment and delivery

**Goal:** Deploy, configure, upgrade, and roll back the services consistently across
environments.

### Capabilities

- Package every service as a versioned container image.
- Add readiness/liveness probes and graceful shutdown.
- Define resource requests, limits, and autoscaling policies from measured data.
- Separate environment-specific configuration and secret delivery.
- Add staging deployment, release verification, and rollback workflows.

### Main technologies

- Kubernetes for workload orchestration, networking, health management, and scaling.
- Helm for reusable, versioned Kubernetes deployment packages and environment values.

### Completion evidence

- Services can be installed into a clean cluster using documented commands.
- A failed release can be rolled back without manually editing cluster resources.
- Deployment manifests define probes and resource boundaries for every service.

Detailed topic specifications are maintained in the
[Distributed Systems Learning Path](../distributed-systems/README.md). Current
repository evidence is tracked separately in
[Implementation Status](implementation-status.md).
