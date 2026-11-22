# Topic 3 — Saga Orchestration and Compensation

**Status:** Planned  
**Depends on:** [Order state machine](01-order-state-machine.md), [Inventory reservation](02-inventory-reservation.md)

## 1. Vấn đề cần giải quyết

Transaction của Order database không thể rollback reservation đã commit trong Product
database. Khi payment thất bại, hệ thống cần một business action ngược lại: release
inventory. Đây là Saga compensation, không phải distributed database rollback.

## 2. Kiến thức học được

- Saga orchestration và local transaction boundary.
- Partial failure và compensation.
- Phân biệt business failure, technical failure và unknown outcome.
- Thiết kế workflow có thể retry/recover.

## 3. Happy path

```text
Order: create PENDING
  -> Product: reserve
Order: INVENTORY_RESERVED
Order: PAYMENT_PENDING
  -> Payment: process
  -> Product: confirm reservation
Order: CONFIRMED
```

## 4. Payment failure path

```text
Payment returns FAILED
  -> Order: PAYMENT_FAILED
  -> Product: release reservation
     -> success: Order CANCELLED
     -> failure: Order COMPENSATION_PENDING
```

Nếu payment timeout, kết quả là `UNKNOWN`, không được mặc định coi là failed rồi retry
payment một cách mù quáng. Topic idempotency sẽ cung cấp payment key để query/retry an toàn.

## 5. Tính năng cần xây dựng

1. Order service làm Saga orchestrator.
2. Mỗi state update dùng local transaction ngắn.
3. Network call nằm ngoài database transaction.
4. Payment failure kích hoạt inventory release.
5. Compensation failure được persist thành `COMPENSATION_PENDING`.
6. Recovery worker retry compensation với backoff và giới hạn.
7. Operator có thể xem reason và trigger retry có kiểm soát.

## 6. Function dự kiến

```java
CreateOrderResult createOrder(OrderRequest request);
void handleInventoryFailure(Integer orderId, Failure failure);
void handlePaymentFailure(Integer orderId, Failure failure);
void compensateInventory(Integer orderId, String reservationId);
int retryPendingCompensations(int batchSize);
```

Orchestrator không nên là một method `@Transactional` bao quanh toàn flow. Một cách tổ
chức dễ hiểu:

```java
Order createPending(...);            // local transaction
ReservationResponse reserve(...);    // HTTP
Order markInventoryReserved(...);    // local transaction
Order markPaymentPending(...);       // local transaction
PaymentResult pay(...);              // HTTP
confirmReservation(...);             // HTTP
Order confirm(...);                  // local transaction
```

## 7. Failure classification

| Failure | Ví dụ | Hành động |
| --- | --- | --- |
| Business | Hết hàng, card declined | Không retry operation gốc; cancel/compensate |
| Transient technical | Connection reset, 503 | Retry có giới hạn nếu operation idempotent |
| Permanent technical | Invalid contract, 400 | Không retry; báo lỗi và điều tra |
| Unknown outcome | Payment timeout sau khi request đã gửi | Query bằng idempotency key trước khi quyết định |

## 8. Non-Functional Requirements

- **Consistency:** Mọi terminal order phải có inventory/payment outcome tương ứng.
- **Durability:** Compensation pending phải tồn tại sau restart.
- **Idempotency:** Compensation retry không cộng stock nhiều lần.
- **Bounded retries:** Retry có max attempts/backoff, không loop vô hạn.
- **Latency:** Client không phải chờ compensation retry dài; trả trạng thái có thể query.
- **Availability:** Product/payment outage không làm mất orchestration progress.
- **Observability:** Metric `compensation.pending`, `compensation.failed`, saga duration.
- **Operability:** Có query/runbook cho order kẹt ở intermediate state.

## 9. Test strategy

- Happy path: order và reservation đều confirmed.
- Inventory failure: payment không được gọi.
- Payment declined: inventory được release và order cancelled.
- Release failure: order thành `COMPENSATION_PENDING`.
- Recovery retry: release thành công đúng một lần.
- Crash simulation sau từng bước rồi chạy recovery.
- Timeout payment: không tạo payment thứ hai khi outcome chưa rõ.

## 10. Acceptance criteria

- [ ] Payment failure không làm mất stock vĩnh viễn.
- [ ] Compensation failure không bị che bởi exception/log.
- [ ] Không có transaction giữ mở xuyên HTTP call.
- [ ] Workflow có thể tiếp tục sau process restart.
- [ ] Business, transient, permanent và unknown failure được xử lý khác nhau.
- [ ] Có automated test cho crash/failure tại từng boundary quan trọng.

## 11. Trade-off

Orchestration đặt coupling workflow ở Order service nhưng dễ đọc, test và quan sát cho
learning project. Choreography bằng Kafka sẽ được cân nhắc khi event contract và Outbox
đã ổn định; dùng quá sớm dễ biến flow thành chuỗi event khó lần theo.

