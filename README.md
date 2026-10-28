# E-commerce Microservices

[English](README.md) | [Tiếng Việt](README.vi.md)

An e-commerce platform built as a microservices-based distributed system and
designed to evolve toward a production-oriented architecture. The system covers
customer management, product catalog, inventory, orders, payments, and notifications.
Its architecture emphasizes clear service boundaries, independent data ownership,
reliable communication, failure recovery, security, observability, and scalability.

## Project Goals

- Build a clear microservice architecture around a small e-commerce domain.
- Keep data ownership and service responsibilities explicit.
- Explore synchronous and asynchronous communication between services.
- Implement distributed-system patterns through observable business scenarios.
- Add testing, security, resilience, operations, and deployment capabilities
  incrementally.
- Document design decisions, trade-offs, and lessons learned as the system evolves.

## Functional Requirements

At a high level, the system supports:

- Managing customer profiles and addresses.
- Managing the product catalog, categories, and server-side pricing.
- Managing inventory, stock availability, and inventory reservations.
- Creating orders and tracking their lifecycle.
- Processing and recording payments.
- Publishing order and payment events.
- Sending email notifications from asynchronous events.
- Exposing services through a centralized API gateway.

Detailed behavior, API contracts, failure handling, and Non-Functional Requirements
belong to their corresponding implementation topics under
[`docs/distributed-systems`](docs/distributed-systems/README.md).

## System Architecture

The system is divided by business capability. Each service owns its domain logic
and data, and no service writes directly to another service's database.

```text
                         +-------------------+
                         |   Config Server   |
                         +---------+---------+
                                   |
Client --> API Gateway ------------+--> Customer Service
              |                    +--> Product Service
              |                    +--> Inventory Service
              |                    +--> Order Service
              |                    +--> Payment Service
              |                    +--> Notification Service
              |
              +--> Service Discovery

Order / Payment Services --> Kafka --> Notification Service
```

### Business services

| Service | Responsibility |
| --- | --- |
| `customer-service` | Customer profiles, contact information, and addresses |
| `product-service` | Product catalog, categories, descriptions, and pricing |
| `inventory-service` | Stock availability, reservations, confirmation, release, and expiration |
| `order-service` | Order lifecycle and orchestration of the checkout workflow |
| `payment-service` | Payment processing and payment records |
| `notification-service` | Email delivery and notification history from asynchronous events |

### Platform services

| Service | Responsibility |
| --- | --- |
| `api-gateway` | External entry point, routing, and edge security |
| `config-server` | Centralized application configuration |
| `discovery` | Service registration and discovery with Eureka |

Product and Inventory are separate domain responsibilities:

```text
Product Service   -> what a product is and how much it costs
Inventory Service -> how much stock exists and whether it is reserved
```

## Core Business Flow

The main workflow is intentionally straightforward:

```text
Create order
  -> validate customer
  -> reserve inventory
  -> process payment
  -> confirm inventory and order
  -> publish notification events
```

The main compensation path is:

```text
Payment failed
  -> release inventory
  -> cancel order
```

This flow provides the foundation for implementing order state machines, inventory
reservations, Saga compensation, Transactional Outbox, idempotency, retries, DLQ,
and distributed tracing.

## Technology Stack

### Backend

- Java 17
- Spring Boot 3
- Spring Cloud Config, Gateway, OpenFeign, and Netflix Eureka
- Spring Data JPA and Spring Data MongoDB
- Maven multi-module build

### Databases and migrations

- PostgreSQL for transactional domain data
- MongoDB for customer and notification data
- Flyway for relational database migrations

### Communication and messaging

- REST for synchronous service communication
- Apache Kafka for asynchronous events
- Zookeeper for the current local Kafka setup

### Security

- OAuth 2.0 Resource Server
- JWT
- Keycloak for local identity and access management

### Testing

- JUnit 5
- Mockito
- Spring Boot Test
- Testcontainers with PostgreSQL

### Observability

- Spring Boot Actuator
- Micrometer Tracing
- Zipkin

### Build and local infrastructure

- GitHub Actions
- Docker Compose
- MailDev for local email testing

### Planned technology additions

- Redis for caching, rate limiting, and coordination use cases supported by benchmarks
- Elasticsearch for product search and indexing
- Resilience4j for retry, circuit breaker, rate limiter, and bulkhead policies
- OpenTelemetry for standardized trace and telemetry instrumentation
- Prometheus and Grafana for metrics, dashboards, and alerting
- Schema Registry for versioned Kafka event contracts
- Kubernetes and Helm for deployment, configuration, and release management
- k6 or Gatling for repeatable load and capacity testing

## Repository Structure

```text
.
├── cloud/                  # API Gateway and cloud-facing components
├── services/               # Business and platform services
├── support/                # Reusable Java modules
├── docs/                   # Design, roadmap, and implementation documentation
├── diagrams/               # Architecture and domain diagrams
├── docker/                 # Local infrastructure initialization
├── docker-compose.yml      # Local PostgreSQL, MongoDB, Kafka, and supporting tools
└── pom.xml                 # Root Maven parent and reactor
```

## Architecture Diagrams

### System architecture

![System Architecture](diagrams/diagram.png)

### Domain diagram

![Domain Diagram](diagrams/domain_diagram.png)

The diagrams may evolve as service boundaries and implementation topics are
completed. The written service ownership rules are the source of truth when an
older diagram differs from the current design.

## Getting Started

### Prerequisites

- Java 17
- Docker with Docker Compose
- Git

### Start local infrastructure

From the repository root:

```bash
docker compose up -d
```

This starts PostgreSQL, MongoDB, Kafka, Zookeeper, Zipkin, Keycloak, and MailDev.
Credentials in `docker-compose.yml` are for local development only.

### Build and test the repository

```bash
./services/order/mvnw -f pom.xml --batch-mode verify
```

To build and test one module with its dependencies:

```bash
./services/order/mvnw -f pom.xml \
  -pl services/product \
  -am \
  --batch-mode verify
```

### Run services locally

Start the applications in this order:

1. `config-server`
2. `discovery`
3. Business services
4. `api-gateway`

Each implemented service contains a Maven Wrapper and can be started from its directory:

```bash
./mvnw spring-boot:run
```

Environment-specific secrets, such as OAuth2 client secrets, must be supplied by
the local environment and must not be committed to the repository.

## Testing and Continuous Integration

The repository contains unit, web-layer, application-context, and PostgreSQL
integration tests. The inventory concurrency tests use a real PostgreSQL container
to verify database locking and stock constraints.

GitHub Actions runs Maven verification for business, platform, and support modules
on pushes and pull requests. Repository branch rules should require these checks
before changes can be merged into the default branch.

See the current implementation checklist in
[`docs/roadmap/implementation-status.md`](docs/roadmap/implementation-status.md).

## Development Roadmap

Development is organized into dependency-ordered phases:

1. **Business correctness:** Order state machine, inventory reservation, Saga
   compensation, Outbox, Inbox, and idempotency.
2. **Event contracts:** Schema Registry, versioned Kafka schemas, compatibility
   policies, and contract checks in CI.
3. **Resilience:** Resilience4j timeout, bounded retry, circuit breaker, bulkhead,
   DLQ, and replay procedures.
4. **Observability:** OpenTelemetry across HTTP and Kafka, Prometheus metrics,
   Grafana dashboards and alerts, SLI/SLO, and runbooks.
5. **Security and data protection:** service authorization, secret management,
   TLS, backup, and verified restore.
6. **Performance and scaling:** repeatable k6 or Gatling scenarios, capacity
   baselines, and measurement-driven Redis, search, partitioning, or sharding.
7. **Deployment and delivery:** Kubernetes, Helm, environment configuration,
   health probes, autoscaling, release verification, and rollback.

Each phase defines capabilities and completion evidence; adding a technology alone
does not complete a phase. See
[`docs/roadmap/feature-roadmap.md`](docs/roadmap/feature-roadmap.md) for details.

## Documentation

- [Distributed Systems Learning Path](docs/distributed-systems/README.md)
- [Topic Documentation Template](docs/distributed-systems/TOPIC_TEMPLATE.md)
- [API Conventions](docs/api-conventions.md)
- [Feature Roadmap](docs/roadmap/feature-roadmap.md)
- [Implementation Status](docs/roadmap/implementation-status.md)
- [Authentication and Authorization Design](docs/authentication-authorization-design.md)
