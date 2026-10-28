# Topic 2 — Inventory Reservation

**Status:** Planned  
**Depends on:** [Order state machine](01-order-state-machine.md)

## 1. Vấn đề cần giải quyết

Trừ stock vĩnh viễn ngay khi tạo order khiến hàng bị mất nếu payment thất bại hoặc
order service dừng giữa flow. Reservation tách “hàng đang được giữ” khỏi “hàng đã bán”.

## 2. Kiến thức học được

- Reservation pattern và resource lease có TTL.
- Pessimistic locking và deadlock avoidance.
- Idempotent confirm/release.
- Background expiration và race condition giữa confirm với expire.

## 3. Lifecycle

```text
ACTIVE
  | confirm
  v
CONFIRMED

ACTIVE
  | release hoặc hết hạn
  v
RELEASED
```

`releaseReason` phân biệt `PAYMENT_FAILED`, `ORDER_CANCELLED` và `EXPIRED`, tránh thêm
quá nhiều state chỉ để ghi nguyên nhân.

## 4. Tính năng cần xây dựng

1. Reserve nhiều product trong một local transaction.
2. Lock product theo ID tăng dần để giảm nguy cơ deadlock.
3. Lưu price snapshot tại thời điểm reserve.
4. Confirm reservation sau khi payment thành công.
5. Release reservation và cộng stock trở lại.
6. Expire reservation `ACTIVE` quá TTL bằng scheduled worker.
7. Confirm/release lặp lại không tạo side effect lần hai.

## 5. API contract

### Reserve

```http
POST /api/v1/inventory-reservations
Content-Type: application/json

{
  "reservationId": "01J...",
  "orderReference": "ORDER-001",
  "items": [
    {"productId": 1, "quantity": 2}
  ]
}
```

Response chứa dữ liệu trusted để Order tính amount:

```json
{
  "reservationId": "01J...",
  "status": "ACTIVE",
  "expiresAt": "2026-10-28T08:15:00Z",
  "items": [
    {"productId": 1, "quantity": 2, "unitPrice": 10.00}
  ]
}
```

### Confirm và release

```http
POST /api/v1/inventory-reservations/{reservationId}/confirm
POST /api/v1/inventory-reservations/{reservationId}/release

{"reason": "PAYMENT_FAILED"}
```

## 6. Function dự kiến

```java
ReservationResponse reserve(ReservationRequest request);
ReservationResponse confirm(String reservationId);
ReservationResponse release(String reservationId, ReleaseReason reason);
int releaseExpiredReservations(Instant now, int batchSize);
```

Repository cần operation lock:

```java
List<Product> findAllByIdInOrderByIdForUpdate(List<Integer> productIds);
Optional<InventoryReservation> findByIdForUpdate(String reservationId);
```

## 7. Data model

```text
inventory_reservation
- id
- order_reference
- status
- expires_at
- release_reason
- created_at
- updated_at

inventory_reservation_line
- id
- reservation_id
- product_id
- quantity
- unit_price
```

Database constraint:

- Reservation ID duy nhất.
- Quantity lớn hơn `0`.
- Stock không âm.
- Index `(status, expires_at)` phục vụ expiration worker.

## 8. Expiration worker

Worker chạy theo batch nhỏ:

```text
find ACTIVE where expires_at <= now
  -> lock reservation
  -> kiểm tra lại status
  -> lock products theo ID
  -> cộng stock
  -> mark RELEASED, reason=EXPIRED
```

Không giữ một transaction cho toàn bộ danh sách lớn. Mỗi batch hoặc reservation dùng
transaction giới hạn để tránh giữ lock quá lâu.

## 9. Non-Functional Requirements

- **Correctness:** Reserve tất cả item hoặc không item nào; không partial reservation.
- **Concurrency:** Không oversell khi nhiều request reserve cùng product.
- **Idempotency:** Cùng `reservationId` và cùng payload trả cùng kết quả; payload khác bị từ chối.
- **Time:** Dùng UTC `Instant`; TTL do server cấu hình, không tin client.
- **Recoverability:** Reservation hết hạn được release sau restart.
- **Performance:** Lock product theo thứ tự; expiration có batch size và index.
- **Observability:** Metric active/expired/released reservation và thời gian giữ lock.
- **Security:** Chỉ Order service được reserve/confirm/release.

## 10. Test strategy

- PostgreSQL Testcontainers test hai transaction tranh cùng stock.
- Test reserve nhiều sản phẩm rollback toàn bộ khi một sản phẩm thiếu hàng.
- Test confirm hai lần không trừ stock lần nữa.
- Test release hai lần chỉ cộng stock một lần.
- Test race confirm và expiration chỉ có một terminal result.
- Test worker tiếp tục xử lý reservation sau restart.

## 11. Acceptance criteria

- [ ] Stock không âm dưới concurrent load.
- [ ] Payment chưa thành công thì reservation vẫn có thể release.
- [ ] Confirmed reservation không thể release tự động.
- [ ] Expired reservation trả đủ stock đúng một lần.
- [ ] API trả price snapshot phía server.
- [ ] Có index và batch limit cho expiration scan.

