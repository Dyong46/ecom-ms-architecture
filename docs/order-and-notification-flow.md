# Từ đặt hàng đến email xác nhận

Có hai sự kiện độc lập. Order phát xác nhận tạo đơn sau khi commit trạng thái `COMPLETED`. Payment phát `PAYMENT_SUCCEEDED` khi đã ghi nhận thanh toán thành công. Notification nhận mỗi sự kiện để tạo email job tương ứng.

## 1. Luồng tạo đơn hàng

```mermaid
sequenceDiagram
    participant C as Client
    participant O as Order
    participant P as Product
    participant Pay as Payment
    participant K as Kafka
    participant N as Notification
    C->>O: POST /api/v1/orders qua Gateway, kèm Idempotency-Key
    Note over O: Đọc thông tin Customer
    O->>P: Mua SKU với khóa ổn định cho lần mua
    P-->>O: Giá và số lượng đã mua
    Note over O: Commit snapshot PROCESSING
    O->>Pay: POST /internal/payments
    Pay-->>O: Payment ID
    Note over O: Commit COMPLETED
    O->>K: Xác nhận tạo đơn sau commit
    O-->>C: Order ID
    K-->>N: Tạo email job xác nhận đơn
    Note over C,Pay: Với đơn có thu tiền, khách mở link Stripe
    Note over Pay: Xác minh webhook, lưu inbox, xử lý settlement
    Pay->>K: Worker phát PAYMENT_SUCCEEDED từ outbox
    K-->>O: Cập nhật paymentStatus
    K-->>N: Tạo email job xác nhận thanh toán
```

### Khi một bước lỗi

- **Client mất response.** Gửi lại cùng request và `Idempotency-Key`. Order có thể tiếp tục từ snapshot đã lưu và dùng lại khóa của thao tác downstream. Product trừ kho trong transaction riêng. Hệ thống hiện chưa tự hoàn kho khi bước sau lỗi. [Thiết kế idempotency](checkout-idempotency-design.md)
- **Đơn đã tạo, khách chưa trả tiền.** Client đọc `GET /api/v1/orders/{id}/payment` để lấy link và trạng thái. Với đơn 0 VND, Payment ghi nhận thành công ngay và phát sự kiện qua cùng outbox. [Checkout Stripe](stripe-sandbox-design.md)
- **Kafka gián đoạn.** Payment lưu settlement và outbox trong cùng transaction để worker có thể gửi lại. Xác nhận tạo đơn của Order được gửi sau commit và chưa có outbox riêng. Vì vậy có thể thấy đơn đã tạo nhưng thiếu sự kiện email xác nhận đơn.

## 2. Luồng gửi email bất đồng bộ

Order và Payment phát sự kiện qua Kafka. Notification nhận sự kiện, chuẩn bị nội
dung email và lưu job; worker trong Notification thực hiện gửi SMTP sau đó.

```mermaid
flowchart TD
    E[Order / Payment event] --> K[Kafka]
    K --> C[Notification Consumer]
    C --> R[Render template và chuẩn bị email]
    R --> M[(MongoDB: lưu email job)]
    M --> A[Acknowledge Kafka sau khi lưu bền vững]
    M --> W[Worker atomic claim job đến hạn]
    W --> P[PROCESSING: tăng attemptCount và cấp lease]
    P --> L[mail-module: gửi qua SMTP]
    L --> S{Kết quả gửi}
    S -->|SMTP chấp nhận| OK[SENT: ghi submittedAt]
    S -->|Lỗi tạm thời, còn lượt| RE[RETRY: ghi nextRetryAt]
    S -->|Lỗi vĩnh viễn hoặc hết lượt| F[FAILED]
    RE --> M
```

Nếu chưa thấy email, tra job theo `eventId`. MongoDB lỗi có thể khiến consumer chưa acknowledge Kafka; job đã lưu có thể đang chờ worker thử lại. Nếu SMTP đã nhận email nhưng worker mất lease trước khi lưu kết quả, lần gửi sau có thể tạo email trùng. [Trạng thái job và retry](../services/notification/notification-delivery.md#stored-state-and-retry-policy)

## 3. Code và tài liệu liên quan

- [OrderService](../services/order/src/main/java/com/duong/ecommerce/order/service/OrderService.java)
- [PaymentService](../services/payment/src/main/java/com/duong/ecommerce/payment/service/PaymentService.java)
- [NotificationConsumer](../services/notification/src/main/java/com/duong/ecommerce/notification/integration/kafka/consumer/NotificationConsumer.java)
- [EmailJobStore](../services/notification/src/main/java/com/duong/ecommerce/notification/repository/EmailJobStore.java)
- [EmailDeliveryWorker](../services/notification/src/main/java/com/duong/ecommerce/notification/service/EmailDeliveryWorker.java)
- [Chi tiết thiết kế, cấu hình và theo dõi email job](../services/notification/notification-delivery.md)
- [Thiết kế mail-module](../support/mail-module/mail-module-design.md)
- [Quy tắc kiến trúc và ranh giới module](architecture-rules.md)
