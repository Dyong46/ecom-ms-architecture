# Topic 0 — Business Foundation

**Status:** Planned  
**Prerequisite:** Validation, server-side pricing và inventory locking cơ bản

## 1. Mục tiêu

Xây một business flow nhỏ, ổn định và dễ hiểu để các topic distributed systems tập
trung vào vấn đề kỹ thuật thay vì bị che khuất bởi nghiệp vụ e-commerce phức tạp.

Sau topic này, người học hiểu:

- Vì sao service boundary phải đi theo quyền sở hữu dữ liệu.
- Business invariant khác validation ở API như thế nào.
- Vì sao phải xác định success/failure rõ ràng trước khi thêm Kafka hoặc retry.

## 2. Business scenario

Khách hàng tạo một order gồm một hoặc nhiều sản phẩm. Hệ thống phải giữ đủ hàng,
xử lý một payment và chỉ xác nhận order khi cả hai bước thành công.

Ba kết quả business chính:

```text
SUCCESS                -> order confirmed
INSUFFICIENT_INVENTORY -> order cancelled, không gọi payment
PAYMENT_FAILED         -> order cancelled, inventory được release
```

## 3. Service boundary

| Service | Sở hữu | Không chịu trách nhiệm |
| --- | --- | --- |
| Order | Order lifecycle, orchestration progress, order lines | Tự sửa stock hoặc payment record |
| Product | Product, available stock, reservation | Quyết định order đã thanh toán |
| Payment | Payment attempt và kết quả | Tự confirm/cancel order |
| Notification | Gửi thông báo từ event | Nằm trên critical path tạo order |

## 4. Business invariant

- Quantity phải là số hữu hạn và lớn hơn `0`.
- Stock không bao giờ nhỏ hơn `0`.
- Price và total amount phải được tính từ dữ liệu phía server.
- Một order chỉ `CONFIRMED` sau khi payment thành công.
- Một reservation chỉ được confirm hoặc release một lần về mặt side effect.
- Notification lỗi không được làm order đã thanh toán chuyển thành thất bại.

## 5. Tính năng và function cần có

### Order service

```java
Integer createOrder(OrderRequest request);
OrderResponse findById(Integer orderId);
```

### Product service

Các function purchase hiện tại sẽ dần được thay bằng reservation trong Topic 2.

```java
ReservationResponse reserve(ReservationRequest request);
void confirmReservation(String reservationId);
void releaseReservation(String reservationId, ReleaseReason reason);
```

### Payment service

```java
PaymentResult processPayment(PaymentRequest request);
```

Payment giả lập chỉ cần hai kết quả `SUCCEEDED` và `FAILED`. Không cần tích hợp payment
provider thật trong learning path ban đầu.

## 6. API contract tối thiểu

```http
POST /api/v1/orders
GET  /api/v1/orders/{orderId}

POST /api/v1/inventory-reservations
POST /api/v1/inventory-reservations/{reservationId}/confirm
POST /api/v1/inventory-reservations/{reservationId}/release

POST /api/v1/payments
```

Error response dùng `application/problem+json`. Business error cần có machine-readable
code, ví dụ `INSUFFICIENT_INVENTORY`, thay vì buộc caller parse message.

## 7. Non-Functional Requirements

- **Correctness:** Không oversell và không tin price/amount từ client.
- **Consistency:** Mỗi service giữ invariant của dữ liệu mình sở hữu.
- **Latency:** Target ban đầu cho create order là p95 dưới 1 giây khi dependency khỏe.
- **Availability:** Notification không ảnh hưởng availability của create order.
- **Security:** Internal write API cần machine identity; không public trực tiếp.
- **Auditability:** Có thể tra order theo `orderId` và `reference` duy nhất.
- **Maintainability:** Business rule nằm ở service/domain layer, không nằm rải rác trong controller.

## 8. Test strategy

- Unit test cho quantity, pricing và state transition.
- PostgreSQL integration test cho constraint và concurrent inventory update.
- Component test cho ba kết quả business chính.
- Negative test chứng minh notification failure không rollback order.

## 9. Acceptance criteria

- [ ] Service boundary và invariant được phản ánh trong code.
- [ ] API không nhận total amount do client quyết định.
- [ ] `reference` của order là duy nhất.
- [ ] Error contract có code ổn định cho business failure.
- [ ] Ba kết quả business chính có automated test.
- [ ] Các feature ngoài phạm vi chưa được thêm vào domain.

