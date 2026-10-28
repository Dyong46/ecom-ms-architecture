# Topic 4 — Transactional Outbox

**Status:** Planned  
**Depends on:** [Saga và compensation](03-saga-compensation.md)

## 1. Vấn đề cần giải quyết

Nếu service commit database rồi crash trước khi publish Kafka, state đã thay đổi nhưng
event bị mất. Nếu publish trước rồi database rollback, consumer thấy một event không có
state tương ứng. Outbox lưu business state và event trong cùng local transaction.

## 2. Kiến thức học được

- Dual-write problem.
- Atomic local transaction và eventual publication.
- At-least-once delivery.
- Polling publisher, ordering và outbox lag.

## 3. Tính năng cần xây dựng

1. Bảng `outbox_event` trong service phát event.
2. Ghi aggregate và outbox event trong cùng transaction.
3. Publisher đọc event chưa gửi theo batch và publish Kafka.
4. Mark event published sau khi broker acknowledge.
5. Retry event chưa gửi; cleanup event cũ theo retention.

## 4. Event envelope

```json
{
  "eventId": "01J...",
  "eventType": "OrderConfirmed",
  "eventVersion": 1,
  "aggregateType": "Order",
  "aggregateId": "42",
  "occurredAt": "2026-10-28T08:00:00Z",
  "traceId": "...",
  "payload": {}
}
```

## 5. Function dự kiến

```java
void saveOrderAndEvent(Order order, DomainEvent event);
List<OutboxEvent> claimPendingBatch(int batchSize);
void publish(OutboxEvent event);
void markPublished(String eventId, Instant publishedAt);
void markAttemptFailed(String eventId, String error);
int deletePublishedBefore(Instant cutoff);
```

## 6. Non-Functional Requirements

- **Atomicity:** Aggregate update và outbox insert cùng transaction.
- **Delivery:** At-least-once; consumer phải chịu duplicate.
- **Ordering:** Bảo toàn order theo aggregate khi business yêu cầu.
- **Scalability:** Claim batch an toàn giữa nhiều publisher instance.
- **Backpressure:** Batch size và poll interval cấu hình được.
- **Durability:** Không xóa event trước retention và publish confirmation.
- **Observability:** Outbox lag, pending count, oldest pending age, publish failures.
- **Schema evolution:** Event có version và compatibility policy.

## 7. Test strategy

- Rollback transaction không để lại outbox event.
- Commit transaction luôn có aggregate và event.
- Kafka unavailable giữ event ở pending.
- Publisher restart tiếp tục gửi event.
- Hai publisher không làm mất event; duplicate vẫn được consumer xử lý.

## 8. Acceptance criteria

- [ ] Không còn publish Kafka trực tiếp trong business transaction.
- [ ] Kafka outage không làm mất event.
- [ ] Có metric/alert cho outbox lag.
- [ ] Event envelope và version được document.
- [ ] Consumer duplicate scenario được test.

