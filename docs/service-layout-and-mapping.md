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
  trong [Product catalog](product-catalog-design.md).
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

- Root POM quản lý phiên bản MapStruct và Lombok binding.
- `services/pom.xml` cung cấp dependency MapStruct và annotation processors
  Lombok, Lombok binding, MapStruct cho năm service. Không lặp lại cấu hình ở từng
  service và không áp đặt processors này lên các support module.
- Các mapper dùng `componentModel = "spring"` và
  `unmappedTargetPolicy = ReportingPolicy.ERROR`. Inject mapper qua constructor.
- Implementation do compiler tạo trong `target/generated-sources/annotations`;
  không commit hoặc tự sửa file `*MapperImpl`.
- Trường audit do persistence quản lý được ignore rõ ràng. Giá, tính tổng tiền,
  trừ kho, gửi sự kiện, lựa chọn retry và cấp lease vẫn là trách nhiệm nghiệp vụ,
  không chuyển vào mapper.

| Mapper | Trách nhiệm và hành vi được giữ |
| --- | --- |
| `CustomerMapper` | Request/entity/response; giữ quy tắc null và tham chiếu Address |
| `ProductMapper` | SPU/SKU/option entities sang response DTO và search document theo [Product catalog](product-catalog-design.md) |
| `OrderMapper` | Nhận tổng tiền đã tính; map `totalAmount` thành `amount` trong response; không gán audit hoặc order lines |
| `OrderLineMapper` | Map `orderId` thành Order reference, kể cả ID null; giữ product ID và số lượng |
| `PaymentMapper` | Request/entity/response; không gán audit khi tạo; giữ tên component `Id` hiện có trong response |
| `NotificationMapper` | Cập nhật snapshot email từ MailMessage vào job; không sửa eventId, trạng thái, số lượt, retry hoặc lease |

Notification dùng `@BeanMapping(ignoreByDefault = true)` cho phép cập nhật một
phần có chủ đích: chỉ người gửi, người nhận đầu tiên, tiêu đề, nội dung và cờ HTML.
Metadata giao nhận được giữ nguyên. Việc lấy người nhận đầu tiên giữ hành vi một
người nhận của job hiện tại.

## Loại bỏ ModelMapper

Chủ project đã chọn bỏ ModelMapper sau khi xác nhận repo không có nơi gọi hai API
generic `ListUtils.mapAll(List, Class)` và `ListUtils.mapAll(Set, Class)`.

- Xóa bean `ModelMapperConfig`, dependency và version ModelMapper.
- Bỏ hai API `mapAll` cũ; `ListUtils` trở thành utility thuần, không cần Spring
  bean hoặc mapper lưu trong biến static.
- Với collection, gọi phương thức collection do mapper cụ thể cung cấp, hoặc
  dùng API `transform` đã có, ví dụ:

```java
List<CustomerResponse> responses = ListUtils.transform(customers, customerMapper::fromCustomer);
```

MapStruct xác định kiểu nguồn/đích lúc compile, không cung cấp API thay thế trực
tiếp cho việc truyền một `Class<T>` tùy ý ở runtime. Consumer ngoài repo nếu còn
dùng `mapAll` hoặc bean ModelMapper cần chuyển sang mapper cụ thể khi nâng phiên
bản common-module.

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
[luồng tạo đơn và gửi email](order-and-notification-flow.md),
[thiết kế giao nhận Notification](notification-delivery.md) và
[quyết định MapStruct ban đầu](product-mapstruct.md).

Cập nhật SPU/SKU ngày 2026-11-18 thay thế Category và các mapper Product cũ;
OrderLine hiện lưu snapshot mua hàng. Xem [quyết định catalog](product-catalog-design.md).
