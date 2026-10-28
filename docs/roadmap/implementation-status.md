# Implementation Status

**Status:** Living checklist  
**Purpose:** Track repository capabilities separately from the introductory README.

This checklist describes evidence currently expected in the repository. A checked
item should have implementation or configuration that can be reviewed; production
operation still requires environment-specific validation.

## Foundation

- [x] Root Maven parent and multi-module reactor.
- [x] Centralized Spring Cloud configuration.
- [x] Eureka service discovery and API Gateway routing.
- [x] PostgreSQL and MongoDB persistence.
- [x] Flyway migrations for transactional relational services.
- [x] Database pagination for list APIs.
- [x] Problem Details error responses.
- [x] GitHub Actions verification for repository modules.

## Correctness and testing

- [x] Positive quantity validation.
- [x] Server-side order amount calculation.
- [x] PostgreSQL stock constraint.
- [x] Pessimistic inventory locking.
- [x] PostgreSQL Testcontainers concurrency coverage.
- [ ] Kafka integration and event-contract tests.
- [ ] End-to-end checkout flow tests.

## Distributed workflow

- [ ] Independent `inventory-service`.
- [ ] Order state machine.
- [ ] Inventory reservation and expiration.
- [ ] Saga compensation.
- [ ] Transactional Outbox.
- [ ] Consumer Inbox and deduplication.
- [ ] HTTP idempotency keys.

## Reliability and operations

- [ ] Explicit outbound timeout policies.
- [ ] Resilience4j bounded retry, circuit breakers, and dependency isolation.
- [ ] DLQ and replay procedure.
- [ ] OpenTelemetry context propagation across HTTP and Kafka.
- [ ] Prometheus/Grafana dashboards and alerts.
- [ ] Structured, correlated logs across services.
- [ ] Verified backup and restore drills.
- [ ] Measured SLI/SLO.

## Security

- [x] Gateway OAuth2 Resource Server and JWT foundation.
- [x] Keycloak local environment.
- [ ] Service-level authorization policy completed for all business services.
- [ ] Production secret management.
- [ ] TLS and security integration tests.

## Delivery and scale

- [ ] Versioned Kafka contracts managed through Schema Registry.
- [ ] Repeatable k6 or Gatling scenarios and a capacity baseline.
- [ ] Kubernetes deployment packaged with Helm.
- [ ] Multi-environment CI/CD and rollback workflow.
- [ ] Data/cache/search changes supported by before-and-after benchmarks.

