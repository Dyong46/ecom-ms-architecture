# Topic 8 — Load Testing and Evidence-Based Scaling

**Status:** Planned  
**Depends on:** [Observability](07-observability.md)

## 1. Vấn đề cần giải quyết

Thêm Redis, Elasticsearch, partitioning hoặc sharding trước khi đo tải thường làm hệ
thống phức tạp hơn mà chưa giải quyết đúng bottleneck. Topic này tạo baseline, tìm giới
hạn và chỉ thay đổi kiến trúc khi có bằng chứng.

## 2. Kiến thức học được

- Workload model, throughput, latency percentile và saturation.
- Capacity planning và bottleneck analysis.
- Coordinated omission và test data isolation.
- Đánh giá trước/sau một optimization.

## 3. Scenario cần xây dựng

1. Browse product: read-heavy.
2. Create order với stock đủ.
3. Hot product: nhiều request tranh cùng một product.
4. Payment latency tăng.
5. Kafka/Outbox backlog.
6. Reservation expiration burst.

Mỗi scenario ghi rõ virtual users, request rate, duration, data set và success criteria.

## 4. Công nghệ dự kiến

- k6 hoặc Gatling cho load test version-controlled.
- Prometheus/Grafana để quan sát service và dependency.
- PostgreSQL `pg_stat_statements` và connection-pool metrics.
- Kafka consumer lag/outbox lag metrics.

## 5. Baseline report

Mỗi lần benchmark cần ghi:

```text
Commit/version
Hardware/container limits
Dataset size
Workload
Throughput
p50/p95/p99 latency
Error rate
CPU/memory/GC
DB connections/locks/query time
Kafka/outbox lag
Observed bottleneck
```

## 6. Non-Functional Requirements

- **Repeatability:** Test script, data và environment được version control.
- **Isolation:** Không chạy destructive load test vào production.
- **Validity:** Có warm-up và tránh coordinated omission.
- **Correctness under load:** Không chỉ đo tốc độ; phải kiểm tra stock/order invariant.
- **Comparability:** Giữ workload cố định khi so sánh trước/sau.
- **Resource limits:** Ghi rõ CPU/memory/connection limit.

## 7. Decision rules

- Thêm cache chỉ khi read/query là bottleneck và có invalidation strategy.
- Partition table khi query/maintenance chứng minh table size là vấn đề.
- Shard chỉ khi một database node không đáp ứng sau khi đã tối ưu hợp lý.
- Scale consumer khi lag do processing capacity, không phải poison event.
- Tối ưu lock khi hot-product scenario cho thấy contention thực tế.

## 8. Test strategy

- Chạy baseline tối thiểu ba lần và báo median/variance.
- Kiểm tra database invariant sau test.
- Failure injection trong một scenario riêng.
- So sánh result trước/sau optimization bằng cùng workload.

## 9. Acceptance criteria

- [ ] Có load-test script tái chạy được trong repository.
- [ ] Có baseline report và bottleneck dựa trên metric.
- [ ] Không mất order, oversell hoặc duplicate payment dưới tải.
- [ ] Mọi optimization có số liệu trước/sau.
- [ ] Redis/search/sharding chỉ được đề xuất cùng access pattern và trade-off rõ ràng.

