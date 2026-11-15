# Local microservice monitoring

This setup assumes the Java applications run on the host through an IDE or Maven.
Prometheus, Grafana, Loki, Alloy and Kafka Exporter run in a separate Docker Compose project.

```text
Spring Boot Actuator / Micrometer -> Prometheus <- Grafana
Spring Boot tracing              -> Zipkin (existing stack)
Spring Boot JSON log files       -> Alloy -> Loki <- Grafana
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

Start Kafka infrastructure first. Kafka Exporter uses the local `ms_kafka`
container and the infrastructure Docker network.

```bash
docker compose up -d zookeeper kafka
```

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

## Traces in Grafana (Zipkin)

Applications export traces to Zipkin, which runs in the original infrastructure
Compose on port 9411. Grafana provisions a Zipkin datasource at
`http://host.docker.internal:9411`; the host mapping is configured on Grafana's
container, because the two Compose projects have separate networks.

Start Zipkin if needed and apply the Grafana datasource configuration:

```bash
docker compose up -d zipkin
docker compose --env-file .env.monitoring -f docker-compose.monitoring.yml up -d grafana
docker compose --env-file .env.monitoring -f docker-compose.monitoring.yml restart grafana
```

Open **Explore**, select **Zipkin**, choose a service and time range, and run a
search. Alternatively, query an exact trace ID or use Zipkin UI directly at
<http://localhost:9411>. Loki's derived `TraceID` field adds a **View trace** link
for JSON logs containing `traceId`. The trace must have been exported and still
exist in Zipkin for that link to return data.

**Drilldown > Traces** is a different interface: it requires a Tempo datasource
with TraceQL metrics and does not work with the Zipkin datasource. An empty
datasource selector on that page does not prove that Zipkin has no traces.
See [Traces Drilldown prerequisites](https://grafana.com/docs/grafana/latest/visualizations/simplified-exploration/traces/access/).

Traces appearing under every service prove that spans are being exported; this
alone does not prove end-to-end context propagation. Verify a real business flow
and check that its service spans share the same trace ID. HTTP clients must be
instrumented, and Kafka producers/listeners need observation enabled for their
message spans. The current Zipkin container uses its default memory storage, so
restarting it loses collected traces even though Loki logs persist separately.

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

## Centralized JSON logs

The five business services and Gateway depend on `support/logging-module`. The
module supplies one classpath `logback-spring.xml` and a Jackson 2 compatible JSON
encoder. IntelliJ's console remains readable text; file output is JSON Lines.
Config Server and Eureka are not included in this initial file logging setup.

### Configure Java applications

In each of the six IntelliJ Run Configurations, set the same **absolute** directory
in the environment variable `LOG_ROOT`, pointing to `<repository>/logs`. Set
`APP_ENV=local` optionally (this is the default). Reload the Maven project and
restart the applications after adding the logging module.

For applications launched from a terminal at the repository root:

```bash
export LOG_ROOT="$(pwd)/logs"
export APP_ENV=local
```

These shell exports do not update an already-open IntelliJ process. Docker's
`.env.monitoring` is not automatically loaded by Java applications either.
Without `LOG_ROOT`, logging falls back to `<process-working-directory>/logs`;
set it explicitly to avoid files being written under individual service modules.

Files appear under these directories:

```text
logs/
  api-gateway/application.log
  customer-service/application.log
  product-service/application.log
  order-service/application.log
  payment-service/application.log
  notification-service/application.log
```

The entire repository `logs/` directory is ignored by Git. Alloy mounts it read-only.
If you choose a different host directory, update Alloy's Compose bind mount too.

Each JSON event includes `timestamp` (UTC), `level`, `service_name`, `environment`,
`logger`, `thread`, and `message`. Exceptions are kept in `stack_trace` with escaped
newlines, so a full exception is one JSON line. MDC fields are allowlisted to
`traceId`, `spanId`, `orderId`, and `paymentId`. Trace fields appear only when a
trace context exists; logging does not fix missing HTTP/Kafka trace propagation.
Do not put credentials, tokens, or personal data into messages or exceptions;
the JSON encoder does not automatically redact arbitrary message contents.

### API access logs

The shared logging module automatically registers HTTP server observation handlers
for both Servlet services and the WebFlux Gateway. A completed request emits one
`http.access` log event with these JSON fields:

- `event`: `http_access`
- `method`, `path` (without query parameters), `status`, `duration_ms`
- `traceId` and `spanId` when tracing is enabled, plus the standard service fields

Successful responses use INFO, 4xx use WARN, and 5xx use ERROR. The final server
exchange is observed, including asynchronous responses and responses rejected
before the controller. Unhandled server exceptions are recorded as 500; a reactive
connection abort uses status 0 because no reliable response status exists. Access
logs summarize the exchange; application exception logs retain diagnostic details.

Headers, cookies, Authorization tokens and request/response bodies are not read
or logged. URL paths can still contain sensitive values if your API puts them
there. Actuator requests are excluded using `management.endpoints.web.base-path`
(default `/actuator`) to avoid polling noise. Notification has no business HTTP
API, so it normally produces no access logs beyond non-Actuator requests.

Access logging relies on Spring's HTTP server observations. Keep these enabled;
turning off `http.server.requests` observations also stops access logging. You can
disable only access logs with `observability.access-log.enabled=false`.

In Grafana Explore, select Loki. View API requests:

```logql
{job="ecommerce"} | json | event="http_access"
```

View Gateway errors, including 404 and 501:

```logql
{service_name="api-gateway"} | json | event="http_access" | status >= 400
```

View only 404 responses across applications:

```logql
{job="ecommerce"} | json | event="http_access" | status = 404
```

Method, path and status remain JSON fields rather than indexed Loki labels. A
request forwarded through Gateway and a business service produces one event in
each application; use the trace ID to correlate them, rather than counting them
as separate client requests. Restart Java applications after rebuilding the shared
module. Alloy, Loki and Grafana do not need a configuration change for access logs.

### Rotation and retention

| Setting | Value |
| --- | --- |
| Default root level | INFO |
| Active file | `application.log` |
| Rotation | Daily in UTC or around 20 MB |
| Archive pattern | `application.YYYY-MM-DD.N.log.gz` |
| Archive history | 3 daily periods |
| Archive total size cap | 200 MB per application |
| Loki retention | 168 hours (7 days) |

Rotation archives old data and opens a new active file; it does not overwrite the
archived log. Logback cleanup runs at startup and rollover. The archive cap does
not include the active file and is not a strict instantaneous filesystem quota.
The bundled Logback checks file size periodically (by default once per minute in
version 1.5.7), so a burst can temporarily exceed the 20 MB rotation threshold.
Loki retains already-ingested logs independently of local archive deletion.
Loki retention uses the Compactor with persistent working data and a two-hour
deletion delay; expiry is asynchronous, not an exact deletion deadline. Monitor
disk space: seven-day retention is not a disk size limit.

Alloy persists read offsets in its `alloy-data` volume and tails only active
`application.log` files. It follows rotation while running. Compressed archives
are not automatically replayed, to avoid ingesting each event twice. If Alloy is
offline while files rotate, some uncollected logs can remain only in archives;
this local setup does not guarantee lossless delivery. Keep Alloy running and use
the retained archives for manual recovery if needed.

### Start and query logs

```bash
docker compose --env-file .env.monitoring -f docker-compose.monitoring.yml up -d
docker compose --env-file .env.monitoring -f docker-compose.monitoring.yml restart grafana
```

The Grafana restart loads new datasource provisioning when adding Loki to an
already-running stack. Open <http://localhost:3000/explore>, select **Loki**, and run:

```logql
{job="ecommerce", service_name="order-service"}
```

Only errors:

```logql
{job="ecommerce", service_name="order-service", level="ERROR"}
```

Find a trace ID (substitute an actual trace ID from your logs or Zipkin):

```logql
{job="ecommerce"} | json | traceId="your-trace-id"
```

Only `job`, `service_name`, `environment`, and `level` are stream labels. IDs stay
inside JSON and are parsed when querying. This keeps stream cardinality bounded.

Verification commands:

```bash
docker compose --env-file .env.monitoring -f docker-compose.monitoring.yml run --rm --no-deps alloy validate /etc/alloy/config.alloy
docker compose --env-file .env.monitoring -f docker-compose.monitoring.yml run --rm --no-deps loki -config.file=/etc/loki/config.yml -verify-config=true
curl -f http://localhost:3100/ready
docker compose --env-file .env.monitoring -f docker-compose.monitoring.yml logs --tail=30 alloy loki
```

If no logs appear, first check that the application's file exists under the shared
`logs/` directory and contains JSON. Then check Alloy errors and the Grafana time
range. Loki and its filesystem storage persist in `loki-data`; Alloy offsets persist
in `alloy-data`. Loki has no authentication in this local setup and its published
port binds only to `127.0.0.1`; production requires separate access controls.

Version references: [Prometheus releases](https://prometheus.io/download/) and
[Grafana 13.3.1](https://github.com/grafana/grafana/releases/tag/v13.3.1).


## Kafka dashboard and warnings

Open [Kafka Overview](http://localhost:3000/d/kafka-overview) under the Ecommerce
folder. The dashboard is provisioned from `docker/grafana/dashboards/kafka-overview.json`.
Grafana-managed alerts are provisioned from
`docker/grafana/provisioning/alerting/kafka-alerts.json` and appear under
**Alerting → Alert rules → Ecommerce / Kafka monitoring**.

Kafka Exporter v1.9.0 supplies broker discovery, topic/partition metadata, committed
offsets, consumer group members and approximate lag. Application metrics continue
to come from Actuator. Publisher errors before enqueue and asynchronous delivery
failures are monitored through Loki logs from KafkaPublisher/SafeProducerListener.

| Rule | Severity | Initial condition |
| --- | --- | --- |
| Exporter scrape unavailable | warning | Scrape fails or target series is missing for 1m |
| Broker discovery unavailable | critical | Exporter is reachable, broker metric is missing or broker count is below 1 for 1m |
| Notification endpoint unavailable | critical | Notification Actuator scrape fails for 1m |
| Expected consumer group inactive | warning | Group absent or members below 1 for 5m while exporter and broker metrics are available |
| Lag high | warning | More than 100 pending messages per expected group/topic for 5m |
| Consumer stalled | warning | Positive lag with no offset changes in the preceding 1m, sustained for 5m |
| Publisher error logs | warning | Matching publisher error logs exist in the last 5m |
| Expected topic metadata missing | warning | Either expected topic is absent from exporter metadata for 5m while broker is discoverable |

Rules are evaluated every 30s. Thresholds are starting values for local use and
should match workload and notification latency targets. The expected topics are
`order-topic` and `payment-topic`; the expected group is the single literal string
`paymentGroup,orderGroup`. Update rule queries when overriding application topic
or group names. Dashboard variable selections affect charts, not alert selectors.

Exporter scrape success measures HTTP availability. Broker discovery has a separate
rule. An unavailable broker, missing metrics and an empty backlog have different
interpretations. Fresh groups can have no lag series until offsets have been
committed; the lag panel omits unknown negative values and shows missing data.
The dashboard includes the number of partitions with known lag values. Native
producer metrics appear after a producer client is used. Consumer fetch throughput
measures fetched records, not successful email delivery.

Prometheus query errors move rules to Alerting. Lag and publisher-log queries use
OK on empty results, with monitoring/group/topic availability checked by separate
rules. Loki query failures are evaluation errors, while an empty log result can
mean no matching error logs or no ingested stream. These alerts depend on their
data source and the configured logging pipeline.

### Docker networking for the existing local broker

The current broker advertises `localhost:9092`. Kafka Exporter joins its network
namespace with `network_mode: container:ms_kafka`, so broker metadata addresses
are reachable. Exporter listens on port 9308 within that namespace. Prometheus
joins the infrastructure network and scrapes `ms_kafka:9308`; port 9308 is not
published to the host.

The default infrastructure network name is
`ecom-ms-architecture_microservices-net`. If the infrastructure Compose project
name differs, set `KAFKA_DOCKER_NETWORK` in the ignored `.env.monitoring` file.
This local arrangement expects the broker container name `ms_kafka`. Recreate the
exporter after recreating or restarting the broker so it joins the current network
namespace.

```bash
docker compose --env-file .env.monitoring -f docker-compose.monitoring.yml up -d --force-recreate kafka-exporter
```

For a deployment with proper internal advertised listeners, connect the exporter
to that listener over a shared network and update the Prometheus target. The local
namespace-sharing configuration is specific to this repository's Docker setup.

### Apply and verify

```bash
docker compose --env-file .env.monitoring -f docker-compose.monitoring.yml up -d kafka-exporter prometheus grafana
docker compose --env-file .env.monitoring -f docker-compose.monitoring.yml restart prometheus grafana
docker compose --env-file .env.monitoring -f docker-compose.monitoring.yml exec -T prometheus promtool check config /etc/prometheus/prometheus.yml
```

Verify `up{job="kafka-exporter"}` and `kafka_brokers{job="kafka-exporter"}` in
Prometheus. Check rule evaluation health in Grafana. File-provisioned rule changes
are applied by editing the source file and restarting Grafana or using its admin
provisioning reload API. Dashboard changes are picked up by its file provider.

The initial implementation was checked against the running exporter and seven
synthetic Prometheus scenarios covering healthy data, exporter failure, missing
broker data, persistent lag/stall, inactive group, Notification scrape failure and
missing topic metadata. The checks used synthetic time series rather than stopping
services or publishing business events.

### Receiving notifications

Rules are active in Grafana. Contact points and external notification destinations
have not been configured by this setup. To receive messages, configure a contact
point in **Alerting → Contact points**, then route the `component=kafka` alerts
through a notification policy. Email requires Grafana SMTP configuration. Supply
SMTP credentials, webhook URLs and integration tokens through runtime secret
configuration. Confirm the recipient and test delivery as part of that channel's
setup.

References: [Kafka Exporter](https://github.com/danielqsj/kafka_exporter),
[Grafana file provisioning](https://grafana.com/docs/grafana/latest/alerting/set-up/provision-alerting-resources/file-provisioning/),
[Grafana contact points](https://grafana.com/docs/grafana/latest/alerting/configure-notifications/manage-contact-points/).
