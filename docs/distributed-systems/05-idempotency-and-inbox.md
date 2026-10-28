# Topic 5 — Idempotency Key and Consumer Inbox

**Status:** Planned  
**Depends on:** [Transactional Outbox](04-transactional-outbox.md)

## 1. Vấn đề cần giải quyết

HTTP retry có thể tạo hai order/payment. Kafka at-least-once có thể giao cùng event
nhiều lần. Idempotency đảm bảo cùng một operation identity không tạo side effect lần hai.

## 2. Kiến thức học được

- Idempotency key và request fingerprint.
- Exactly-once effect trên nền at-least-once delivery.
- Inbox/deduplication transaction.
- Retry an toàn và conflict detection.

## 3. Tính năng cần xây dựng

### HTTP idempotency

- `POST /orders` và `POST /payments` nhận `Idempotency-Key`.
- Lưu key, request hash, status và response.
- Cùng key/cùng payload trả lại response cũ.
- Cùng key/payload khác trả `409 Conflict`.

### Kafka Inbox

- Consumer lưu `eventId` đã xử lý.
- Inbox insert và business side effect cùng transaction.
- Duplicate event được acknowledge nhưng không chạy side effect lại.

## 4. Function dự kiến

```java
IdempotencyDecision begin(String key, String requestHash);
void complete(String key, int statusCode, String responseBody);
void fail(String key, String failureCode);

boolean hasProcessed(String consumerName, String eventId);
void processOnce(EventEnvelope event, Runnable businessAction);
```

## 5. Non-Functional Requirements

- **Correctness:** Cùng identity chỉ tạo một business effect.
- **Concurrency:** Hai request cùng key chạy đồng thời chỉ có một owner.
- **Security:** Key được scope theo caller/operation, tránh đọc response người khác.
- **Retention:** Key/inbox có TTL dài hơn retry window và Kafka retention cần thiết.
- **Performance:** Có unique index; lookup không scan toàn bảng.
- **Privacy:** Không lưu raw sensitive request nếu hash/selected fields là đủ.
- **Observability:** Metric hit, conflict, in-progress và duplicate event.

## 6. Test strategy

- Hai request payment cùng key đồng thời chỉ tạo một payment.
- Cùng key, payload khác trả conflict.
- Retry sau timeout trả cùng kết quả.
- Kafka duplicate chỉ gửi một email hoặc tạo một record.
- Crash giữa inbox insert và side effect không tạo trạng thái nửa vời.

## 7. Acceptance criteria

- [ ] Create order/payment retry an toàn.
- [ ] Consumer xử lý duplicate event không lặp side effect.
- [ ] Request fingerprint và retention policy được document.
- [ ] Unique constraint bảo vệ race condition ở database.
- [ ] Metric duplicate/conflict có sẵn.

