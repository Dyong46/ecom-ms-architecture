# Local microservice monitoring

This setup assumes the Java applications run on the host through an IDE or Maven.
Prometheus and Grafana run in a separate Docker Compose project.

```text
Spring Boot Actuator / Micrometer -> Prometheus <- Grafana
Spring Boot tracing              -> Zipkin (existing stack)
```

## 1. Expose application metrics

Customer, Product, Order, Payment, Notification and API Gateway expose HTTP metrics through
Actuator and `micrometer-registry-prometheus`. Shared Config Server configuration exposes
`health,info,prometheus`, adds the `application` label, and enables HTTP latency
histograms. Gateway also exposes Prometheus in its local configuration.

Restart Config Server first so it reloads its updated classpath resources, then
rebuild/restart the Java applications. Start infrastructure dependencies and Eureka
as usual. For example, verify Product directly:

```bash
curl -f http://localhost:8050/actuator/prometheus
```

The response should contain Prometheus metrics such as `jvm_memory_used_bytes`.
Product and Gateway allow anonymous GET requests to this exact metrics path for
local scraping. This is a local development setup: deployment must restrict metrics
to a private network or add authentication. Do not expose application Actuator
endpoints publicly.

## 2. Start Prometheus and Grafana

Run these commands from the repository root. The initial setup creates an ignored
`.env.monitoring` with a generated Grafana admin password. On a fresh checkout,
create it if it does not already exist:

```bash
python3 - <<'PY'
import os
import secrets

fd = os.open('.env.monitoring', os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
with os.fdopen(fd, 'w') as env:
    env.write('GRAFANA_ADMIN_PASSWORD=' + secrets.token_hex(24) + '\n')
PY

docker compose --env-file .env.monitoring -f docker-compose.monitoring.yml up -d
```

Open:

- Prometheus targets: <http://localhost:9090/targets>
- Grafana dashboard: <http://localhost:3000/d/ecommerce-overview>

Log in to Grafana with username `admin` and the password from `.env.monitoring`.
The password initializes a new Grafana database; changing the environment file
later does not reset an existing account password.

The Compose project automatically provisions the Prometheus datasource and the
`Ecommerce / Ecommerce Overview` dashboard. Its service selector covers:

| Application | Host port |
| --- | --- |
| customer-service | 8090 |
| product-service | 8050 |
| order-service | 8070 |
| payment-service | 8060 |
| notification-service | 8040 |
| api-gateway | 8222 |

Targets have an `application` label so even a down target appears under its service
name. Config Server and Eureka are not scrape targets in this initial setup.
Notification processes business events through Kafka. Its embedded HTTP server on
port 8040 exposes only the `health` and `prometheus` Actuator endpoints. Gateway's
discovery locator excludes Notification so it does not create a public route to
these endpoints. Restart Config Server, Gateway and Notification after updating
this configuration. For deployment, restrict port 8040 to the monitoring/private
network using firewall or network policy; excluding a Gateway route alone does
not prevent direct access to a host port.

```bash
curl -f http://localhost:8040/actuator/health
curl -f http://localhost:8040/actuator/prometheus
```

## 3. Verify data

Validate configuration and container health:

```bash
docker compose --env-file .env.monitoring -f docker-compose.monitoring.yml config --quiet
docker compose --env-file .env.monitoring -f docker-compose.monitoring.yml exec -T prometheus promtool check config /etc/prometheus/prometheus.yml
curl -f http://localhost:9090/-/ready
curl -f http://localhost:3000/api/health
```

Run `up{job="ecommerce"}` in Prometheus: `1` means a successful scrape, `0` means
the target cannot be scraped. Allow at least two scrape intervals (30 seconds)
after starting an application for rate graphs to have data. Call a business API
to populate HTTP metrics; latency and HTTP traffic panels exclude Actuator requests.
Notification's HTTP traffic panels can remain empty because it receives business
events through Kafka. JVM/CPU metrics and scrape status are available; email
outcomes and processing durations require additional business instrumentation.

Common failures:

- **Connection refused:** start the target service or correct its port. Containers
  use `host.docker.internal`, not `localhost`, to reach host Java processes. Ensure
  the application listens on an interface reachable from Docker.
- **404:** rebuild with the Prometheus registry and check endpoint exposure. Restart
  Config Server and the application after changing shared classpath configuration.
- **401/403:** check the service security rules for GET `/actuator/prometheus`.
- **Empty p95:** ensure histograms are enabled and send API requests, then wait for
  multiple scrapes.

If applications move into Docker, put them and Prometheus on a shared network and
replace host targets with their Compose service names and internal ports.

## Storage and stopping

Prometheus retains metrics for up to seven days or roughly 2 GB of persisted
blocks, whichever limit applies first. Allow additional disk space for its WAL and
head data. Grafana configuration and accounts persist in a separate named volume.
Both web interfaces bind to host loopback only.

Stop monitoring while keeping data:

```bash
docker compose --env-file .env.monitoring -f docker-compose.monitoring.yml down
```

Provisioned dashboards are maintained in
`docker/grafana/dashboards/ecommerce-overview.json`; edit the file rather than
saving changes in the Grafana UI. The original infrastructure Compose is independent
and can continue running while monitoring is stopped.

Version references: [Prometheus releases](https://prometheus.io/download/) and
[Grafana 13.3.1](https://github.com/grafana/grafana/releases/tag/v13.3.1).
