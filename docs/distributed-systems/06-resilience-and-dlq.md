# Topic 6 — Timeout, Retry, Circuit Breaker and DLQ

**Status:** Planned  
**Depends on:** [Idempotency và Inbox](05-idempotency-and-inbox.md)

## 1. Vấn đề cần giải quyết

Không có timeout làm thread chờ vô hạn; retry sai làm nhân tải và side effect; dependency
lỗi kéo sập caller; poison event làm consumer loop mãi. Resilience phải được cấu hình
theo loại operation và failure, không áp một policy chung cho mọi call.

## 2. Kiến thức học được

- Timeout budget và failure propagation.
- Exponential backoff với jitter.
- Circuit breaker state.
- Retryable/non-retryable error.
- DLQ, replay và poison message handling.

## 3. Tính năng cần xây dựng

- Connect/read timeout cho Product và Payment client.
- Retry có giới hạn cho operation idempotent.
- Circuit breaker riêng cho từng dependency.
- Kafka retry topic hoặc backoff policy.
- DLQ chứa event envelope và failure metadata.
- Replay procedure có kiểm soát.

## 4. Policy ban đầu

| Failure | Retry? | Ghi chú |
| --- | --- | --- |
| HTTP 400/409 business error | Không | Caller phải sửa request hoặc xử lý business result |
| HTTP 429/503 | Có giới hạn | Tôn trọng `Retry-After` nếu có |
| Connect timeout | Có thể | Chỉ khi operation idempotent |
| Read timeout payment | Không retry mù | Outcome có thể đã thành công; query bằng key |
| Invalid event schema | Không | Đưa DLQ ngay |
| Temporary consumer dependency error | Có | Backoff + max attempts |

## 5. Công nghệ dự kiến

- Resilience4j qua Spring Cloud CircuitBreaker.
- HTTP client timeout ở RestTemplate/OpenFeign configuration.
- Spring Kafka retry/DLT support.
- Micrometer metrics cho retry, breaker và DLQ.

## 6. Non-Functional Requirements

- **Bounded latency:** Mỗi call có deadline nhỏ hơn request budget tổng.
- **Bounded retries:** Có max attempts và exponential backoff với jitter.
- **No retry storm:** Retry không nhân tải vô hạn khi dependency down.
- **Isolation:** Circuit breaker của Payment không ảnh hưởng Product.
- **Recoverability:** DLQ event không bị mất và có replay procedure.
- **Safety:** Chỉ retry write khi operation idempotent.
- **Observability:** Alert theo breaker open rate, retry exhaustion và DLQ depth.

## 7. Test strategy

- Dependency chậm hơn timeout.
- 503 hai lần rồi thành công.
- 400 không retry.
- Breaker open và half-open recovery.
- Poison event vào DLQ sau đúng số lần thử.
- Replay DLQ không tạo duplicate side effect.

## 8. Acceptance criteria

- [ ] Mọi outbound call có timeout rõ ràng.
- [ ] Retry policy được document theo operation.
- [ ] Write retry có idempotency protection.
- [ ] DLQ có dashboard, alert và replay runbook.
- [ ] Failure injection test chứng minh hệ thống degrade có kiểm soát.

