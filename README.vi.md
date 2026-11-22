# E-commerce Microservices

[English](README.md) | [Tiếng Việt](README.vi.md)

Đây là backend thương mại điện tử dùng Java 17, Spring Boot và Spring Cloud. Hệ thống dùng một đơn hàng thật trong môi trường sandbox để minh họa ba vấn đề của microservices: ai sở hữu dữ liệu, retry thế nào khi lời gọi HTTP bị ngắt và khi nào có thể xác nhận khách đã trả tiền.

[Chạy hệ thống](#chạy-các-service-ở-local) · [Thử checkout sandbox](docs/stripe-sandbox-design.md#local-sandbox-verification) · [Tìm tài liệu theo service](docs/README.md#implemented)

## Tìm đúng nơi để sửa

| Thư mục | Trách nhiệm |
| --- | --- |
| `services/customer`, `services/product` | Customer lưu hồ sơ trong MongoDB. Product sở hữu catalog SPU/SKU, giá và tồn kho trong PostgreSQL, đồng bộ chỉ mục tìm kiếm sang Elasticsearch. |
| `services/order`, `services/payment` | Order điều phối checkout và lưu snapshot. Payment quản lý lần thanh toán, xác minh webhook Stripe và phát kết quả qua Kafka. |
| `services/notification` | Nhận sự kiện, lưu email job trong MongoDB và gửi SMTP. |
| `cloud/` | Config Server cấp cấu hình, Eureka đăng ký service, API Gateway nhận request từ client. |
| `support/` | Các thư viện Java được nhúng vào ứng dụng, gồm HTTP client, cache, Kafka, mail và data model dùng chung. |

## Theo dấu một đơn hàng

1. Client gửi `POST /api/v1/orders` kèm `Idempotency-Key`. Order đọc Customer, gọi Product để mua SKU theo giá phía server rồi lưu snapshot của đơn.
2. Order gọi API nội bộ của Payment bằng machine token. Client nhận Order ID và gọi `GET /api/v1/orders/{orderId}/payment` để lấy link Stripe. Lần tạo đơn có thể đã hoàn tất trong khi khách vẫn chưa trả tiền.
3. Với đơn có thu tiền, Stripe gửi webhook qua Gateway. Payment xác minh chữ ký, ghi kết quả và sự kiện thành công vào outbox. Order nhận sự kiện qua Kafka để cập nhật trạng thái thanh toán. Notification xử lý email riêng.

Product hiện trừ tồn khi mua SKU. Reservation có thời hạn và Saga hoàn kho là các chủ đề dự kiến. [Checkout và idempotency](docs/checkout-idempotency-design.md) · [Thiết kế Stripe](docs/stripe-sandbox-design.md)

## Chạy các service ở local

Cần JDK 17 và Docker Compose. Chạy các lệnh từ thư mục gốc repo. Để thử catalog trước, bật profile Elasticsearch:

Lần đầu cài đặt, sao chép `.env.example` thành `.env` rồi điền ba mật khẩu quản trị local. Với database đã có dữ liệu, dùng mật khẩu hiện tại. Đặt cùng giá trị `MONGO_INITDB_ROOT_PASSWORD` trong cấu hình chạy IDE của Customer và Notification. Nếu đã có container Keycloak, [sao lưu realm trước lần cập nhật Compose đầu tiên](docs/ecom-oauth-setup.md#keycloak-volume).

Nếu broker đang dùng ZooKeeper, hãy chuyển topic và consumer offsets cần giữ trước khi chạy Kafka KRaft với volume mới. [Cấu hình Kafka local](support/kafka-module/README.md)

```sh
docker compose --profile search up -d
```

1. Import `pom.xml` gốc vào IDE. Khởi động lần lượt Config Server, Discovery, Product và API Gateway. Chọn Spring profile `local` cho Gateway để mở Swagger.
2. Chờ Product xuất hiện trong [Eureka](http://localhost:8761), rồi đọc danh sách sản phẩm qua Gateway:

   ```sh
   curl -i 'http://localhost:8222/api/public/v1/products?page=1&size=5'
   ```

   Kết quả mong đợi là HTTP 200 và danh sách trong `data.content`. Danh sách rỗng là hợp lệ với database mới. Để có SKU thử checkout, dùng [hướng dẫn nạp catalog mẫu](scripts/README.md). Nếu cần khách hàng để đặt đơn, xem [cách seed Customer local](scripts/customer-data.md).
3. Để chạy checkout, bật Redis bằng `docker compose --profile caching up -d redis`, cấu hình `ECOM_OAUTH_CLIENT_SECRET` cho Order rồi khởi động Customer, Payment, Order và Notification. Dùng thư mục gốc repo làm working directory nếu nạp file cấu hình local của Order và Payment. [Thiết lập Keycloak](docs/ecom-oauth-setup.md) · [Thử thanh toán Stripe và chuyển tiếp webhook](docs/stripe-sandbox-design.md#local-sandbox-verification)

Swagger qua Gateway: [Product](http://localhost:8222/docs/product) · [Customer](http://localhost:8222/docs/customer) · [Order](http://localhost:8222/docs/order)

## Build và test

Docker cần chạy để Testcontainers có thể tạo PostgreSQL cho các bài test tích hợp.

```sh
# Kiểm tra Order cùng các module phụ thuộc
./services/order/mvnw -f pom.xml -pl services/order -am --batch-mode verify

# Kiểm tra toàn bộ repo
./services/order/mvnw -f pom.xml --batch-mode verify
```

[Tra cứu tài liệu theo tác vụ](docs/README.md) · [Hiện trạng triển khai](docs/roadmap/implementation-status.md) · [Lộ trình học distributed systems](docs/distributed-systems/README.md)
