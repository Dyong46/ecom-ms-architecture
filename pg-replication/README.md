# PostgreSQL High-Availability Replication Cluster

This directory contains everything needed to run a production-grade PostgreSQL replication cluster locally with **automatic failover**, **read/write splitting**, and **connection pooling**.

---

## Architecture Overview

```
                         ┌─────────────────────────────────────────┐
                         │          Application / Spring Boot        │
                         └──────┬──────────────────────┬────────────┘
                                │ Writes               │ Reads
                                ▼                       ▼
                    ┌───────────────────┐   ┌──────────────────────┐
                    │   PgBouncer       │   │      HAProxy          │
                    │  :6432 (pooling)  │   │  :5001 (read LB)     │
                    └─────────┬─────────┘   └──────┬───────────────┘
                              │                     │ Round-robin
                              ▼                     │ to replicas
                    ┌─────────────────┐             │
                    │    HAProxy      │             │
                    │  :5000 (write)  │             │
                    └────────┬────────┘             │
                             │ HTTP health check     │
                             │ GET /primary          │ GET /replica
                             ▼                       ▼
              ┌──────────────────────────────────────────────┐
              │              Patroni Cluster                  │
              │  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
              │  │   pg1    │  │   pg2    │  │   pg3    │   │
              │  │ PRIMARY  │  │ REPLICA  │  │ REPLICA  │   │
              │  │ :5432    │  │ :5432    │  │ :5432    │   │
              │  │ REST:8008│  │ REST:8008│  │ REST:8008│   │
              │  └────┬─────┘  └────▲─────┘  └────▲─────┘   │
              │       │  WAL stream │              │          │
              │       └────────────┴──────────────┘          │
              └────────────────────┬─────────────────────────┘
                                   │ Leader election / cluster state
                                   ▼
                         ┌─────────────────┐
                         │     etcd1       │
                         │   :2379/:2380   │
                         └─────────────────┘
```

### Component Responsibilities

| Component  | Role |
|------------|------|
| **etcd**   | Distributed Configuration Store (DCS). Stores Patroni cluster state and coordinates leader election. |
| **Patroni** | Cluster manager running alongside each PostgreSQL node. Manages replication, leader election, and automatic failover. |
| **pg1/pg2/pg3** | PostgreSQL 16 instances. One acts as the write primary; the others are streaming replicas. |
| **HAProxy** | Routes writes to the primary (port 5000) and load-balances reads across replicas (port 5001). Uses Patroni REST API health checks. |
| **PgBouncer** | Optional connection pooler in front of HAProxy (port 6432). Reduces connection overhead for the application. |

### Port Mapping

| Port | Service | Purpose |
|------|---------|---------|
| 5000 | HAProxy | **Write endpoint** – always routes to the current primary |
| 5001 | HAProxy | **Read endpoint** – round-robin across healthy replicas |
| 6432 | PgBouncer | **Pooled write endpoint** – recommended for apps |
| 7000 | HAProxy | Stats dashboard (`http://localhost:7000/stats`) |
| 5432 | pg1 | Direct access to node 1 |
| 5433 | pg2 | Direct access to node 2 |
| 5434 | pg3 | Direct access to node 3 |
| 8008 | pg1 | Patroni REST API (node 1) |
| 8009 | pg2 | Patroni REST API (node 2) |
| 8010 | pg3 | Patroni REST API (node 3) |
| 2379 | etcd1 | etcd client endpoint |

---

## Prerequisites

- Docker ≥ 24 and Docker Compose v2
- Ports 5000, 5001, 5432–5434, 6432, 7000, 8008–8010, 2379 must be free on your host

> ⚠️ **Security Warning**: The default credentials in `.env.example` are for **local development only**.
> Copy `.env.example` to `.env` and replace all passwords with strong, randomly-generated values before
> connecting to any non-local network. For production, use Docker secrets, HashiCorp Vault, or your
> cloud provider's secrets manager.

---

## Step-by-Step Setup Guide

### 1 – Configure credentials

```bash
cd pg-replication
cp .env.example .env
# Edit .env and set strong passwords for POSTGRES_PASSWORD, REPLICATION_PASSWORD, ADMIN_PASSWORD
```

### 2 – Build and Start the Cluster

```bash
# Build the custom Patroni + PostgreSQL image and start all services
docker compose up --build -d
```

### 3 – Wait for the Cluster to Initialise

Patroni needs ~30–60 s to elect a leader and stream the WAL to replicas.

```bash
# Watch Patroni logs for all three nodes
docker compose logs -f pg1 pg2 pg3

# Expected output on the primary (pg1):
#   pg1: started as primary (DCS initialized)
# Expected on replicas:
#   pg2: started as replica
#   pg3: started as replica
```

### 4 – Verify Cluster Status

```bash
# Check cluster topology via the Patroni REST API
curl -s http://localhost:8008/cluster | python3 -m json.tool

# Quick role check for each node
curl -s http://localhost:8008/primary   # 200 on primary, 503 on replica
curl -s http://localhost:8009/replica   # 200 on replica, 503 on primary
curl -s http://localhost:8010/replica   # 200 on replica, 503 on primary

# Alternative: use patronictl inside the container
docker exec -it pg_patroni1 patronictl -c /etc/patroni/patroni.yml list
```

Expected output:
```
+ Cluster: postgres-cluster ----+----+-----------+
| Member | Host       | Role    | State   | TL | Lag in MB |
+--------+------------+---------+---------+----+-----------+
| pg1    | pg1:5432   | Leader  | running |  1 |           |
| pg2    | pg2:5432   | Replica | running |  1 |         0 |
| pg3    | pg3:5432   | Replica | running |  1 |         0 |
+--------+------------+---------+---------+----+-----------+
```

### 5 – Verify Application Databases

The `init_db.sh` post-init hook creates `orderdb`, `paymentdb`, and `productdb` on first bootstrap.

```bash
psql -h localhost -p 5000 -U postgres -c "\l"
```

### 6 – Connect from Spring Boot Services

Update your service `application.yml` (or config-server configurations):

```yaml
# Write operations → primary via PgBouncer
spring:
  datasource:
    url: jdbc:postgresql://localhost:6432/<database>
    username: postgres
    password: ${POSTGRES_PASSWORD}   # Load from environment or secrets manager

# Critical reads that must be consistent → also use port 6432 (primary)

# Non-critical reads → replicas via HAProxy
# spring.datasource.read-url: jdbc:postgresql://localhost:5001/<database>
```

---

## Failover Testing Guide

### Simulate Primary Failure

```bash
# 1. Note the current primary
docker exec -it pg_patroni1 patronictl -c /etc/patroni/patroni.yml list

# 2. Pause / kill the current primary container
docker pause pg_patroni1   # or: docker stop pg_patroni1

# 3. Watch the remaining nodes elect a new leader (within ~30 s)
docker compose logs -f pg2 pg3

# 4. Confirm new primary
curl -s http://localhost:8009/primary   # Should now return 200
curl -s http://localhost:8010/primary   # Only one will return 200

# 5. Verify writes still work through HAProxy port 5000
psql -h localhost -p 5000 -U postgres -d orderdb -c "SELECT 1;"

# 6. Resume the old primary – Patroni will re-join as a replica
docker unpause pg_patroni1

# 7. Verify the cluster is healthy again
docker exec -it pg_patroni2 patronictl -c /etc/patroni/patroni.yml list
```

### Manual Switchover (Zero-Downtime)

```bash
# Trigger a graceful switchover from within the current leader
docker exec -it pg_patroni1 patronictl \
    -c /etc/patroni/patroni.yml \
    switchover postgres-cluster \
    --master pg1 --candidate pg2 --scheduled now --force
```

### Verify Read/Write Routing

```bash
# Create a test table on the primary (write port 5000)
psql -h localhost -p 5000 -U postgres -d orderdb -c "
  CREATE TABLE IF NOT EXISTS routing_test (id serial, node text, ts timestamptz DEFAULT now());
  INSERT INTO routing_test (node) VALUES ('primary-insert');
"

# Read from the load-balanced read port (5001) – hits a replica
psql -h localhost -p 5001 -U postgres -d orderdb -c "
  SELECT * FROM routing_test;
  SELECT inet_server_addr() AS serving_node;
"

# Confirm write attempt on read port is rejected (replicas are read-only)
psql -h localhost -p 5001 -U postgres -d orderdb -c "
  INSERT INTO routing_test (node) VALUES ('should-fail');
"
# Expected: ERROR: cannot execute INSERT in a read-only transaction
```

---

## Read/Write Routing Verification

HAProxy uses Patroni's health-check endpoints to determine routing:

| HAProxy backend | Patroni endpoint polled | Returns 200 when… |
|-----------------|-------------------------|--------------------|
| `pg_write_backend` (port 5000) | `GET /primary` | Node is the current leader |
| `pg_read_backend` (port 5001)  | `GET /replica`  | Node is a healthy standby |

Because the primary does **not** return 200 for `/replica`, and replicas do **not** return 200 for `/primary`, the routing is enforced entirely at the load-balancer level without any application-side logic.

After a failover, HAProxy automatically re-routes writes to whichever node Patroni has promoted as the new leader. **No application connection-string change is required.**

---

## Replication Lag Considerations

- Replicas use **streaming replication** (WAL shipping over TCP). Under normal conditions, lag is sub-second.
- You can monitor lag via:
  ```sql
  -- Run on the primary (port 5000)
  SELECT client_addr, state, sent_lsn, write_lsn,
         flush_lsn, replay_lsn,
         (sent_lsn - replay_lsn) AS lag_bytes
  FROM pg_stat_replication;
  ```
- For reads that **must** see the latest committed data (e.g. post-write reads), route them to the write port (5000 / PgBouncer 6432) instead of the replica port (5001).
- Patroni's `maximum_lag_on_failover` setting (default 1 MB) prevents a lagging replica from being promoted during failover.

---

## Backup Strategy Note

> Replication is **not** a backup solution. A dropped table is immediately replicated to all nodes.

Recommended backup approach alongside this cluster:

1. **Continuous WAL archiving** – enable `archive_mode = on` and ship WAL files to S3/GCS/MinIO.
2. **Base backups with `pg_basebackup`** – schedule nightly base backups from a replica (pg2 or pg3) to avoid load on the primary.
3. **Point-in-Time Recovery (PITR)** – combine base backups + archived WAL to restore to any point in time.
4. Tools: [pgBackRest](https://pgbackrest.org/), [Barman](https://www.pgbarman.org/), or [WAL-G](https://github.com/wal-g/wal-g).

---

## Production Considerations

| Concern | Recommendation |
|---------|---------------|
| etcd HA | Run a 3- or 5-node etcd cluster instead of the single-node dev setup |
| TLS | Enable TLS on Patroni REST API, PostgreSQL, and etcd connections |
| Secrets management | Replace plain-text passwords with Docker secrets or Vault |
| Monitoring | Integrate with Prometheus + Grafana using `postgres_exporter` and `patroni_exporter` |
| Network | Place the cluster on a private network; expose only HAProxy/PgBouncer to the application tier |
| Synchronous replication | For zero data-loss, set `synchronous_mode: true` in Patroni DCS config (adds write latency) |

---

## Stopping the Cluster

```bash
cd pg-replication
docker compose down          # Stop and remove containers (volumes preserved)
docker compose down -v       # Stop, remove containers AND volumes (data loss!)
```
