# E-commerce Microservices

[English](README.md) | [Tiếng Việt](README.vi.md)

This Java 17, Spring Boot and Spring Cloud backend follows an e-commerce purchase across service boundaries. The checkout illustrates how services own data, how an interrupted HTTP request can be retried, and how the system determines that a customer has paid.

[Run locally](#run-locally) · [Test checkout in the sandbox](docs/stripe-sandbox-design.md#local-sandbox-verification) · [Find service documentation](docs/README.md#implemented)

## Where to make a change

| Location | Responsibility |
| --- | --- |
| `services/customer`, `services/product` | Customer stores profiles in MongoDB. Product owns the SPU/SKU catalog, prices and stock in PostgreSQL and indexes SKUs in Elasticsearch. |
| `services/order`, `services/payment` | Order coordinates checkout and records purchase snapshots. Payment manages payment attempts, verifies Stripe webhooks and publishes settlement events to Kafka. |
| `services/notification` | Consumes events, stores email jobs in MongoDB and sends them over SMTP. |
| `cloud/` | Config Server supplies configuration, Eureka tracks service instances, and API Gateway receives client requests. |
| `support/` | Java libraries embedded in the applications, including HTTP clients, caching, Kafka, mail and shared data models. |

## Follow an order

1. The client sends `POST /api/v1/orders` with an `Idempotency-Key`. Order reads the customer, purchases SKUs from Product at server-side prices and saves an order snapshot.
2. Order calls the internal Payment API with a machine token. The client receives an order ID and polls `GET /api/v1/orders/{orderId}/payment` for a Stripe checkout link. Order creation can finish while payment is still pending.
3. For a paid checkout, Stripe sends a webhook through Gateway. Payment verifies the signature and commits settlement with a success event in its outbox. Order consumes the Kafka event to update payment status. Notification handles email separately.

Product deducts stock at purchase time. Expiring reservations and Saga compensation are planned learning topics. [Checkout idempotency](docs/checkout-idempotency-design.md) · [Stripe design](docs/stripe-sandbox-design.md)

## Run locally

Install JDK 17 and Docker Compose. Run commands from the repository root. To try the catalog first, start infrastructure with Elasticsearch enabled:

For a fresh setup, copy `.env.example` to `.env` and fill in the three local admin passwords. For existing databases, use their current credentials. Set the same `MONGO_INITDB_ROOT_PASSWORD` in the Customer and Notification IDE run configurations. If you already have a Keycloak container, [preserve its realm before the first Compose update](docs/ecom-oauth-setup.md#keycloak-volume).

If your current Kafka broker uses ZooKeeper, preserve its topics and consumer offsets before starting the new KRaft broker. [Local Kafka setup](support/kafka-module/README.md)

```sh
docker compose --profile search up -d
```

1. Import the root `pom.xml` in your IDE. Start Config Server, Discovery, Product and API Gateway in that order. Activate the Gateway's `local` Spring profile for Swagger.
2. Once Product appears in [Eureka](http://localhost:8761), request the catalog through Gateway:

   ```sh
   curl -i 'http://localhost:8222/api/public/v1/products?page=1&size=5'
   ```

   Expect HTTP 200 with a list in `data.content`. An empty list is valid on a fresh database. For SKUs to use in checkout, follow the [sample catalog import guide](scripts/README.md). For a customer to place the order, see [local Customer seed data](scripts/customer-data.md).
3. For checkout, start Redis with `docker compose --profile caching up -d redis`, configure `ECOM_OAUTH_CLIENT_SECRET` for Order, then start Customer, Payment, Order and Notification. Use the repo root as the working directory when loading Order and Payment's local configuration files. [Keycloak setup](docs/ecom-oauth-setup.md) · [Stripe sandbox and webhook forwarding](docs/stripe-sandbox-design.md#local-sandbox-verification)

Swagger through Gateway: [Product](http://localhost:8222/docs/product) · [Customer](http://localhost:8222/docs/customer) · [Order](http://localhost:8222/docs/order)

## Build and test

Keep Docker running so Testcontainers can start PostgreSQL for the integration tests.

```sh
# Verify Order and its dependencies
./services/order/mvnw -f pom.xml -pl services/order -am --batch-mode verify

# Verify the full reactor
./services/order/mvnw -f pom.xml --batch-mode verify
```

[Documentation by task](docs/README.md) · [Implementation status](docs/roadmap/implementation-status.md) · [Distributed systems learning path](docs/distributed-systems/README.md)
