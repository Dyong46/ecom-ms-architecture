# Cấu trúc package và mapping trong các service

Trạng thái: đã áp dụng. Ngày: 2026-11-16.

Theo lựa chọn của chủ project, các service dùng MapStruct cho mapping dữ liệu cụ
thể và tổ chức package theo chức năng như Customer. Mapper thuộc service sở hữu
dữ liệu; không tạo module mapper dùng chung hoặc thêm quan hệ giữa các support
module độc lập.

## Cấu trúc thư mục

Mỗi application class nằm trong package gốc của service để Spring tự tìm bean,
repository và entity trong các package con:

```text
com.duong.ecommerce.<service>
├── <Service>Application.java
├── config/
├── controller/
├── service/
├── repository/
├── entity/
│   └── enumeration/
├── mapper/
├── exception/
├── payload/
│   ├── request/
│   ├── response/
│   └── event/
└── integration/
    ├── oauth/
    └── kafka/
        ├── producer/
        └── consumer/
```

Chỉ tạo package khi có class tương ứng. Customer không cần package Kafka;
Notification không có controller nghiệp vụ nên không tạo controller rỗng.

- **Customer:** giữ cấu trúc đã có và mapper MapStruct hiện tại.
- **Product:** chuyển DTO vào `payload/request`, `payload/response`; đưa cấu hình,
  exception và application class vào package gốc `product`. Category nằm trong
  `product/category/entity` trong phiên bản cũ; model SPU/SKU hiện tại được mô tả
  trong [Product catalog](../services/product/product-catalog-design.md).
- **Order:** chia Order theo các chức năng trên. OrderLine được nhóm dưới
  `order/orderline`, tiếp tục chia `controller/service/repository/entity/mapper`
  và `payload/request`, `payload/response`. OAuth và lời gọi HTTP vẫn thuộc Order.
- **Payment:** chia controller, service, repository, entity, mapper, enum và
  payload; Kafka producer thuộc `integration/kafka/producer`.
- **Notification:** lưu Mongo document trong `entity`, truy cập job trong
  `repository`, renderer/worker/phân loại lỗi trong `service`, cấu hình trong
  `config`, Kafka consumer trong `integration/kafka/consumer` và event DTO trong
  `payload/event`.

Test đi theo package của thành phần được kiểm tra; các suite database, HTTP và
security giữ nhóm integration hoặc security phù hợp. Các service trong `cloud/`
và thư viện `support/` vốn đã chia theo chức năng được giữ trong phạm vi sở hữu
hiện tại; không bổ sung MapStruct vào module không có mapping dữ liệu cụ thể.

## Quy tắc đặt tên enum

Chủ project chọn quy ước: mọi enum cấp cao nhất có hậu tố `Enum`, nằm trong
`entity/enumeration` của service hoặc capability sở hữu enum đó. Ví dụ:

```text
payment/entity/enumeration/PaymentMethodEnum.java
notification/entity/enumeration/DeliveryStatusEnum.java
notification/entity/enumeration/EmailTemplatesEnum.java
```

Các support module dùng cùng quy ước, ví dụ `CacheScopeEnum`, `EventStatusEnum`,
`UserStatusEnum`; các tên đã có hậu tố như `PredicateTypeEnum` chỉ đổi package.
Enum cho HTTP contract vẫn thuộc proxy; mỗi service giữ enum nghiệp vụ của mình,
không gom vào common-module hoặc thêm dependency giữa các capability độc lập.
Folder `entity` trong thư viện kỹ thuật không hàm ý class được đánh dấu JPA Entity.
Đổi tên kiểu Java giữ nguyên các enum constant và dữ liệu JSON/database hiện có;
consumer ngoài repo cần cập nhật import và tên kiểu khi nâng phiên bản thư viện.

Đây là quy ước đặt tên của repo. Việc đổi tên class/package giữ nguyên các enum
constant, giá trị JSON, giá trị lưu database và cấu trúc cache key. Code Java sử
dụng thư viện cần cập nhật import và tên kiểu mới khi nâng phiên bản.

## Quy tắc MapStruct

- Mapper thuộc service sở hữu dữ liệu. `services/pom.xml` cấu hình annotation processor cho các business service. Các mapper dùng `ReportingPolicy.ERROR` để field đích mới phải được xử lý rõ ràng.
- Persistence quản lý metadata audit. Service quyết định giá, tồn kho và trạng thái giao nhận. Khi mapping một phần vào email job đã tồn tại, chỉ cập nhật snapshot email để giữ nguyên event ID, retry và lease.
- Consumer nâng phiên bản từ ModelMapper hoặc `ListUtils.mapAll(..., Class)` cần chuyển sang mapper có kiểu nguồn và đích cụ thể.

## Tương thích Kafka và MongoDB

Đổi package Java không đổi URL API, tên JSON field, tên bảng hoặc collection.
Kafka vẫn dùng hai alias ổn định `orderConfirmation` và `paymentConfirmation`.
Các file cấu hình producer của Order/Payment và consumer của Notification trên
Config Server đã được cập nhật sang class mới; danh sách trusted packages được
giới hạn theo package event mới, không dùng `*`.

MongoDB có thể lưu tên class Java trong `_class`. Notification document và các
event DTO được gắn `@TypeAlias` bằng tên class cũ để giữ discriminator đã lưu.
Các chuỗi package cũ trong annotation này là chủ đích tương thích dữ liệu, không
phải import bị bỏ sót. Không cần sửa hàng loạt document đang có.

Sau khi triển khai, restart Config Server với cấu hình mới, rồi restart các
service liên quan. Khi chạy từ IDE, reload Maven và chọn application class trong
package mới. Các tên đầy đủ hiện tại:

```text
com.duong.ecommerce.customer.CustomerApplication
com.duong.ecommerce.product.ProductApplication
com.duong.ecommerce.order.OrderApplication
com.duong.ecommerce.payment.PaymentApplication
com.duong.ecommerce.notification.NotificationApplication
```

## Kiểm tra

```sh
./services/product/mvnw -f pom.xml --batch-mode clean verify
```

Dùng Java 17 và Docker cho các integration test PostgreSQL, MongoDB và Kafka.
Các kiểm tra bao gồm mapper bean được generate, field đổi tên, audit, null source,
order reference, giữ nguyên metadata email, Kafka alias với cấu hình thực tế,
đọc discriminator Mongo cũ, component scan, repository/entity scan và các test API
hiện có. Những test Redis có điều kiện vẫn cần môi trường Redis test theo thiết
lập của từng suite.

Xem thêm [quy tắc kiến trúc](architecture-rules.md),
[luồng tạo đơn và gửi email](order-and-notification-flow.md) và
[thiết kế giao nhận Notification](../services/notification/notification-delivery.md).

Cập nhật SPU/SKU ngày 2026-11-18 thay thế Category và các mapper Product cũ;
OrderLine hiện lưu snapshot mua hàng. Xem [quyết định catalog](../services/product/product-catalog-design.md).
