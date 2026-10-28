# Distributed Systems Learning Path

**Status:** Planned  
**Audience:** Backend developers muốn học distributed systems qua một business flow nhỏ  
**Scope:** `order`, `product`, `payment`, `notification` và Kafka

## 1. Mục tiêu

Learning path này dùng một luồng đặt hàng tối giản làm nền để học các vấn đề thực tế
của microservices. Mục tiêu không phải xây một hệ thống e-commerce đầy đủ. Business
chỉ cần đủ rõ để tạo ra concurrency, partial failure, duplicate delivery và nhu cầu
quan sát hệ thống.

Luồng nghiệp vụ chính:

```text
Create order
  -> reserve inventory
  -> process payment
  -> confirm order

Payment failed
  -> release inventory
  -> cancel order
```

Các tính năng như promotion, shipping, tax, multi-currency và partial refund nằm
ngoài phạm vi cho đến khi một topic kỹ thuật thực sự cần chúng.

## 2. Cách sử dụng tài liệu

Mỗi topic là một specification triển khai độc lập, gồm:

- Vấn đề cần giải quyết và kiến thức sẽ học được.
- Business scenario tối thiểu.
- Feature, API và function cần xây dựng.
- Data model và flow chính.
- Non-Functional Requirements (NFR).
- Test strategy và acceptance criteria.
- Trade-off, rủi ro và những phần chưa làm.

Khi bổ sung topic mới, dùng [topic template](TOPIC_TEMPLATE.md) để không bỏ sót API/event
contract, security, observability, rollout và rollback.

Không bắt đầu topic tiếp theo khi acceptance criteria quan trọng của topic trước chưa
đạt. Nếu thiết kế hoặc implementation thay đổi, cập nhật tài liệu trong cùng pull
request để tránh tài liệu và code lệch nhau.

## 3. Thứ tự triển khai

| Thứ tự | Topic | Kết quả chính |
| --- | --- | --- |
| 0 | [Business foundation](00-business-foundation.md) | Luồng business nhỏ, boundary và invariant rõ ràng |
| 1 | [Order state machine](01-order-state-machine.md) | Order có lifecycle hợp lệ và có thể phục hồi |
| 2 | [Inventory reservation](02-inventory-reservation.md) | Giữ hàng có TTL, confirm và release |
| 3 | [Saga và compensation](03-saga-compensation.md) | Payment lỗi thì inventory được hoàn lại |
| 4 | [Transactional Outbox](04-transactional-outbox.md) | DB state và event không bị lệch nhau |
| 5 | [Idempotency và Inbox](05-idempotency-and-inbox.md) | Retry/duplicate không tạo side effect lần hai |
| 6 | [Resilience và DLQ](06-resilience-and-dlq.md) | Dependency failure được giới hạn và phục hồi có kiểm soát |
| 7 | [Observability](07-observability.md) | Theo dõi được một order xuyên suốt hệ thống |
| 8 | [Load test và scale](08-load-testing-and-scaling.md) | Quyết định scale dựa trên số liệu |

## 4. Kiến trúc mục tiêu tối giản

```text
Client
  |
  v
API Gateway
  |
  v
Order Service --------> Product Service
  |                         |
  |                         +--> PostgreSQL: stock + reservations
  |
  +--------------------> Payment Service
  |                         |
  |                         +--> PostgreSQL: payments
  |
  +--> PostgreSQL: orders + outbox
  |
  +--> Kafka --> Notification Service
```

Quyền sở hữu dữ liệu:

- Order service sở hữu order state và orchestration progress.
- Product service sở hữu stock và inventory reservation.
- Payment service sở hữu payment result.
- Notification service chỉ phản ứng với event; không quyết định order thành công.

Không service nào được ghi trực tiếp vào database của service khác.

## 5. Nguyên tắc chung

1. Mỗi transaction chỉ thay đổi database của một service.
2. Không giữ database transaction trong lúc chờ HTTP hoặc Kafka.
3. Mọi operation có thể retry phải có định danh ổn định.
4. Consumer phải giả định event có thể được giao nhiều lần.
5. Failure phải được biểu diễn bằng state có thể quan sát, không chỉ bằng log.
6. Mọi async event cần `eventId`, `occurredAt`, `aggregateId` và `eventType`.
7. Mọi request xuyên service cần truyền `traceId`; business log cần có `orderId`.
8. Chỉ thêm cache, partitioning hoặc sharding sau khi benchmark chỉ ra bottleneck.

## 6. Definition of Done chung

Một topic chỉ hoàn thành khi:

- [ ] Code, migration và configuration đã được review.
- [ ] Unit test cho business rule quan trọng đã có.
- [ ] Integration test dùng dependency thật khi behavior phụ thuộc PostgreSQL/Kafka.
- [ ] Failure path được test, không chỉ happy path.
- [ ] CI chạy test tự động và bắt buộc trước khi merge.
- [ ] Metric/log cần thiết để vận hành feature đã được xác định.
- [ ] Tài liệu phản ánh đúng contract và behavior hiện tại.

