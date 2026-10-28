# Feature Roadmap

**Status:** Living document  
**Purpose:** Describe capabilities the system should gain next without treating a
technology name as the goal itself.

## 1. Inventory ownership

Establish `inventory-service` as the owner of stock, availability, reservations,
reservation expiration, confirmation, and release. Product service remains the owner
of catalog data and pricing.

## 2. Reliable order workflow

- Add an explicit Order state machine.
- Add inventory reservation before payment.
- Compensate inventory when payment fails.
- Persist incomplete compensation so it can be retried after restart.

## 3. Reliable event delivery

- Store domain events with business state using Transactional Outbox.
- Publish pending events to Kafka.
- Deduplicate consumed events through an Inbox.
- Version event contracts and test compatibility.

## 4. Safe retries

- Add idempotency keys for create-order and create-payment operations.
- Classify business, transient, permanent, and unknown failures.
- Ensure retry never creates a duplicate payment or stock update.

## 5. Service resilience

- Set an explicit timeout budget for outbound calls.
- Add bounded retry with backoff and jitter where it is safe.
- Add circuit breakers for external dependencies.
- Add DLQ, replay procedures, and operator-visible recovery state.

## 6. Security hardening

- Complete service-level JWT validation and authorization.
- Define machine identities for internal service calls.
- Move local credentials to environment-specific secret management.
- Add TLS, scope/role checks, and security integration tests.

## 7. Observability and operations

- Propagate traces through HTTP and Kafka.
- Add structured logs with business correlation fields.
- Add Prometheus metrics and Grafana dashboards.
- Define SLI/SLO and actionable alerts.
- Write runbooks for common service, database, and Kafka failures.

## 8. Data protection

- Automate PostgreSQL and MongoDB backups.
- Verify restore procedures instead of only creating backups.
- Measure recovery point and recovery time objectives.
- Add replication and failover only with documented consistency trade-offs.

## 9. Search and read performance

- Create repeatable read and search benchmarks.
- Add Redis only for a defined cache, rate-limit, or coordination use case.
- Document TTL, invalidation, persistence, replication, and failover behavior.
- Add Elasticsearch only when product search requirements exceed database search.

## 10. Deployment and delivery

- Package services consistently as containers.
- Deploy and operate services with Kubernetes.
- Add environment-specific configuration and secret delivery.
- Add readiness/liveness probes, resource limits, and autoscaling policies.
- Add staging deployment, release verification, and rollback workflows.

## 11. Evidence-based scaling

- Establish throughput and latency baselines.
- Find bottlenecks using application, database, and Kafka metrics.
- Compare results before and after each optimization.
- Consider partitioning or sharding only after simpler improvements are measured.

Detailed implementation specifications are maintained in the
[Distributed Systems Learning Path](../distributed-systems/README.md).

