# Topic 1 — Order State Machine

**Status:** Planned  
**Depends on:** [Business foundation](00-business-foundation.md)

## 1. Vấn đề cần giải quyết

Một method `createOrder()` gọi nhiều service nhưng không lưu tiến độ khiến hệ thống
không biết order đã chạy tới bước nào khi timeout hoặc process bị dừng. State machine
biến tiến độ đó thành dữ liệu bền vững và kiểm soát transition hợp lệ.

## 2. Kiến thức học được

- Mô hình hóa lifecycle bằng finite state machine.
- Phân biệt business state với technical error.
- Guard transition và bảo vệ concurrent update.
- Thiết kế workflow có thể resume thay vì chạy lại từ đầu.

## 3. State model ban đầu

```text
PENDING
  |
  v
INVENTORY_RESERVED
  |
  v
PAYMENT_PENDING
  |             \
  v              v
CONFIRMED     PAYMENT_FAILED
                 |
                 v
              CANCELLED
```

Khi compensation chưa hoàn tất, dùng `COMPENSATION_PENDING`. Không thêm state mới nếu
không có behavior vận hành hoặc recovery tương ứng.

## 4. Tính năng cần xây dựng

1. Thêm `OrderStatus` vào order entity và database.
2. Lưu `failureStage`, `failureCode` và `failureMessage` để điều tra.
3. Định nghĩa transition hợp lệ tại một nơi duy nhất.
4. Từ chối transition không hợp lệ.
5. Trả state trong `OrderResponse` để client và operator theo dõi.
6. Thêm optimistic version để tránh hai worker cập nhật cùng order âm thầm ghi đè.

## 5. Data model

```sql
alter table customer_order add column status varchar(32) not null;
alter table customer_order add column version bigint not null default 0;
alter table customer_order add column failure_stage varchar(32);
alter table customer_order add column failure_code varchar(64);
alter table customer_order add column failure_message varchar(500);
```

Existing order cần được backfill thành trạng thái phù hợp trước khi thêm `NOT NULL`.

## 6. Function dự kiến

```java
Order createPendingOrder(OrderRequest request);
Order markInventoryReserved(Integer orderId, String reservationId, BigDecimal amount);
Order markPaymentPending(Integer orderId);
Order confirm(Integer orderId);
Order markPaymentFailed(Integer orderId, String code, String message);
Order markCompensationPending(Integer orderId, String code, String message);
Order cancel(Integer orderId);
```

Mỗi function phải:

- Load current state.
- Kiểm tra transition.
- Thay đổi state và metadata liên quan.
- Commit trong một local transaction ngắn.
- Phát metric cho transition thành công/thất bại.

## 7. API thay đổi

`GET /api/v1/orders/{orderId}` bổ sung:

```json
{
  "id": 42,
  "reference": "ORDER-001",
  "status": "PAYMENT_PENDING",
  "amount": 25.00,
  "failure": null
}
```

Không tạo endpoint cho client tùy ý set status. Transition chỉ được gọi qua application
service hoặc internal recovery operation được bảo vệ.

## 8. Non-Functional Requirements

- **Consistency:** Transition và dữ liệu đi kèm phải commit trong cùng local transaction.
- **Concurrency:** Concurrent update phải phát hiện bằng optimistic locking, không lost update.
- **Durability:** State đã commit phải tồn tại sau restart.
- **Auditability:** Log transition có `orderId`, `fromStatus`, `toStatus`, `traceId`.
- **Recoverability:** Có thể query các order ở `PAYMENT_PENDING` hoặc `COMPENSATION_PENDING`.
- **Performance:** Transition đơn lẻ không thực hiện network I/O trong transaction.
- **Compatibility:** Existing response chỉ được mở rộng theo cách không phá caller hiện tại.

## 9. Test strategy

- Unit test toàn bộ transition hợp lệ.
- Parameterized test toàn bộ transition bị cấm.
- JPA integration test cho optimistic locking.
- Restart/reload test chứng minh state được persist.
- Controller test kiểm tra response có status và failure code.

## 10. Acceptance criteria

- [ ] Không thể chuyển order từ `CANCELLED` sang `CONFIRMED`.
- [ ] Hai update đồng thời không silently overwrite nhau.
- [ ] Network call không nằm trong transaction cập nhật state.
- [ ] Có query/index cho các state cần recovery.
- [ ] Failure được biểu diễn bằng state/code, không chỉ bằng exception log.
- [ ] Transition matrix được test tự động.

## 11. Trade-off

State machine tự viết dễ hiểu và đủ cho workflow nhỏ. Chưa dùng Temporal, Camunda hoặc
Spring Statemachine vì chúng tăng abstraction và vận hành trước khi project có nhu cầu
workflow phức tạp, long-running hoặc human approval.

