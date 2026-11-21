# Tra cứu tài liệu

## Implemented

| Khi cần | Bắt đầu từ |
| --- | --- |
| Chạy local hoặc thử checkout | [Chạy qua Gateway](../README.vi.md#chạy-các-service-ở-local) · [Cấu hình Keycloak](ecom-oauth-setup.md) · [Thử Stripe sandbox](stripe-sandbox-design.md#local-sandbox-verification) |
| Sửa Customer hoặc Product | [Customer và migration](../services/customer/README.md) · [Catalog](../services/product/product-catalog-design.md) · [Thiết kế search](../services/product/product-search-design.md) · [Thử search](../services/product/product-search-guide.vi.md) |
| Sửa Order hoặc Payment | [Snapshot](../services/order/order-snapshot-design.md) · [Retry và idempotency](checkout-idempotency-design.md) · [API Payment nội bộ](payment-internal-api-design.md) · [Settlement](stripe-sandbox-design.md) |
| Theo dõi sự kiện và email | [Luồng xuyên service](order-and-notification-flow.md) · [Notification delivery](../services/notification/notification-delivery.md) · [SMTP](../support/mail-module/mail-module-design.md) |
| Thay đổi module hoặc API | [Quy tắc kiến trúc](architecture-rules.md) · [Layout và mapping](service-layout-and-mapping.md) · [Quy ước API](api-conventions.md) · [Xử lý lỗi](exception-handling-design.md) |
| Tra OpenAPI | [Product](../openapi.yaml) · [Customer](openapi/customer.yaml) · [Order](openapi/order.yaml) · [Payment nội bộ](openapi/payment.yaml) |
| Dùng thư viện chung | [HTTP](../support/proxy-module/README.md) · [Cache](../support/caching-module/README.md) · [Kafka](../support/kafka-module/README.md) · [JPA](../support/common-data-model/README.md) · [Mongo](../support/mongo-data-model/README.md) |
| Kiểm tra vận hành và tiến độ | [Monitoring local](monitoring.md) · [Checklist hiện trạng](roadmap/implementation-status.md) |

## Proposed

- [Thiết kế phân quyền đang hoãn](authentication-authorization-design.md)
- [Các giai đoạn phát triển dự kiến](roadmap/feature-roadmap.md)

## Learning

- [Lộ trình và bài tập distributed systems](distributed-systems/README.md)
