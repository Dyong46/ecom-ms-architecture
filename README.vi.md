# E-commerce Microservices

[English](README.md) | [Tiếng Việt](README.vi.md)

Một nền tảng thương mại điện tử được xây dựng dưới dạng hệ thống phân tán dựa trên
microservices và được thiết kế để phát triển theo hướng sẵn sàng cho môi trường
production. Hệ thống bao gồm quản lý customer, product catalog, inventory, order,
payment và notification. Kiến trúc tập trung vào service boundary rõ ràng, quyền sở
hữu dữ liệu độc lập, giao tiếp tin cậy, phục hồi lỗi, bảo mật, observability và khả
năng mở rộng.

## Mục tiêu dự án

- Xây dựng kiến trúc microservices rõ ràng quanh một domain e-commerce nhỏ.
- Xác định rõ quyền sở hữu dữ liệu và trách nhiệm của từng service.
- Tìm hiểu giao tiếp đồng bộ và bất đồng bộ giữa các service.
- Áp dụng các pattern của hệ thống phân tán thông qua những tình huống nghiệp vụ có
  thể quan sát và kiểm chứng.
- Bổ sung dần testing, security, resilience, operation và deployment.
- Ghi lại các quyết định thiết kế, trade-off và bài học trong quá trình phát triển.

## Yêu cầu chức năng

Ở mức tổng quan, hệ thống hỗ trợ:

- Quản lý hồ sơ và địa chỉ khách hàng.
- Quản lý danh mục, category và giá sản phẩm từ phía server.
- Quản lý tồn kho, số lượng khả dụng và inventory reservation.
- Tạo order và theo dõi vòng đời của order.
- Xử lý và lưu thông tin payment.
- Phát order event và payment event.
- Gửi email notification từ các event bất đồng bộ.
- Cung cấp một điểm truy cập tập trung thông qua API Gateway.

Behavior chi tiết, API contract, cách xử lý failure và Non-Functional Requirements
được mô tả trong từng topic triển khai tại
[`docs/distributed-systems`](docs/distributed-systems/README.md).

## Kiến trúc hệ thống

Hệ thống được phân chia theo business capability. Mỗi service sở hữu domain logic và
dữ liệu của mình; không service nào ghi trực tiếp vào database của service khác.

```text
                         +-------------------+
                         |   Config Server   |
                         +---------+---------+
                                   |
Client --> API Gateway ------------+--> Customer Service
              |                    +--> Product Service
              |                    +--> Inventory Service
              |                    +--> Order Service
              |                    +--> Payment Service
              |                    +--> Notification Service
              |
              +--> Service Discovery

Order / Payment Services --> Kafka --> Notification Service
```

### Business services

| Service | Trách nhiệm |
| --- | --- |
| `customer-service` | Hồ sơ, thông tin liên hệ và địa chỉ khách hàng |
| `product-service` | Danh mục, category, mô tả và giá sản phẩm |
| `inventory-service` | Tồn kho, reservation, confirm, release và expiration |
| `order-service` | Vòng đời order và điều phối checkout workflow |
| `payment-service` | Xử lý payment và lưu payment record |
| `notification-service` | Gửi email và lưu lịch sử notification từ event bất đồng bộ |

### Platform services

| Service | Trách nhiệm |
| --- | --- |
| `api-gateway` | Điểm truy cập bên ngoài, routing và bảo mật tại biên hệ thống |
| `config-server` | Quản lý cấu hình ứng dụng tập trung |
| `discovery` | Đăng ký và tìm kiếm service bằng Eureka |

Product và Inventory là hai domain responsibility độc lập:

```text
Product Service   -> sản phẩm là gì và có giá bao nhiêu
Inventory Service -> còn bao nhiêu hàng và số hàng đó có đang được giữ hay không
```

## Luồng nghiệp vụ chính

Workflow chính được giữ ở mức dễ hiểu:

```text
Tạo order
  -> kiểm tra customer
  -> reserve inventory
  -> xử lý payment
  -> confirm inventory và order
  -> phát notification event
```

Compensation flow chính:

```text
Payment thất bại
  -> release inventory
  -> cancel order
```

Flow này là nền tảng để triển khai Order state machine, inventory reservation, Saga
compensation, Transactional Outbox, idempotency, retry, DLQ và distributed tracing.

## Công nghệ sử dụng

### Backend

- Java 17
- Spring Boot 3
- Spring Cloud Config, Gateway, OpenFeign và Netflix Eureka
- Spring Data JPA và Spring Data MongoDB
- Maven multi-module build

### Database và migration

- PostgreSQL cho dữ liệu giao dịch
- MongoDB cho dữ liệu customer và notification
- Flyway cho relational database migration

### Giao tiếp và messaging

- REST cho giao tiếp đồng bộ giữa các service
- Apache Kafka cho event bất đồng bộ
- Zookeeper cho Kafka trong môi trường local hiện tại

### Security

- OAuth 2.0 Resource Server
- JWT
- Keycloak cho identity và access management ở môi trường local

### Testing

- JUnit 5
- Mockito
- Spring Boot Test
- Testcontainers với PostgreSQL

### Observability

- Spring Boot Actuator
- Micrometer Tracing
- Zipkin

### Build và hạ tầng local

- GitHub Actions
- Docker Compose
- MailDev để kiểm thử email ở local

### Công nghệ định hướng phát triển

- Redis cho caching, rate limiting và các coordination use case đã được benchmark
- Elasticsearch cho product search và indexing
- Resilience4j cho retry, circuit breaker, rate limiter và bulkhead
- OpenTelemetry để chuẩn hóa trace và telemetry instrumentation
- Prometheus và Grafana cho metric, dashboard và alert
- Schema Registry để quản lý Kafka event contract có version
- Kubernetes và Helm cho deployment, configuration và release management
- k6 hoặc Gatling cho load test và capacity test có thể lặp lại

## Cấu trúc repository

```text
.
├── cloud/                  # API Gateway và các component hướng ra bên ngoài
├── services/               # Business service và platform service
├── support/                # Các Java module dùng chung
├── docs/                   # Tài liệu thiết kế, roadmap và triển khai
├── diagrams/               # Sơ đồ kiến trúc và domain
├── docker/                 # Khởi tạo hạ tầng local
├── docker-compose.yml      # PostgreSQL, MongoDB, Kafka và công cụ hỗ trợ ở local
└── pom.xml                 # Maven parent và reactor ở root
```

## Sơ đồ kiến trúc

### Kiến trúc hệ thống

![System Architecture](diagrams/diagram.png)

### Sơ đồ domain

![Domain Diagram](diagrams/domain_diagram.png)

Các sơ đồ sẽ tiếp tục thay đổi khi service boundary và các topic được hoàn thiện. Khi
sơ đồ cũ khác với thiết kế hiện tại, quy tắc về quyền sở hữu service trong tài liệu
viết được xem là source of truth.

## Bắt đầu sử dụng

### Yêu cầu môi trường

- Java 17
- Docker với Docker Compose
- Git

### Khởi động hạ tầng local

Từ thư mục root của repository:

```bash
docker compose up -d
```

Lệnh này khởi động PostgreSQL, MongoDB, Kafka, Zookeeper, Zipkin, Keycloak và MailDev.
Credential trong `docker-compose.yml` chỉ được sử dụng cho môi trường development local.

### Build và test repository

```bash
./services/order/mvnw -f pom.xml --batch-mode verify
```

Để build và test một module cùng các dependency của nó:

```bash
./services/order/mvnw -f pom.xml \
  -pl services/product \
  -am \
  --batch-mode verify
```

### Chạy các service ở local

Khởi động ứng dụng theo thứ tự:

1. `config-server`
2. `discovery`
3. Các business service
4. `api-gateway`

Mỗi service đã được triển khai có Maven Wrapper và có thể được chạy từ thư mục của nó:

```bash
./mvnw spring-boot:run
```

Các secret theo môi trường, chẳng hạn OAuth2 client secret, phải được cung cấp từ môi
trường local và không được commit vào repository.

## Testing và Continuous Integration

Repository có unit test, web-layer test, application-context test và PostgreSQL
integration test. Các inventory concurrency test sử dụng PostgreSQL container thật để
kiểm chứng database locking và stock constraint.

GitHub Actions chạy Maven verification cho business, platform và support module khi
push hoặc tạo pull request. Branch rule của repository nên yêu cầu các check này phải
thành công trước khi code được merge vào default branch.

Xem checklist triển khai hiện tại tại
[`docs/roadmap/implementation-status.md`](docs/roadmap/implementation-status.md).

## Lộ trình phát triển

Quá trình phát triển được chia thành các giai đoạn theo thứ tự phụ thuộc:

1. **Tính đúng đắn của nghiệp vụ:** Order state machine, inventory reservation,
   Saga compensation, Outbox, Inbox và idempotency.
2. **Event contract:** Schema Registry, Kafka schema có version, compatibility policy
   và kiểm tra contract trong CI.
3. **Khả năng phục hồi:** timeout, bounded retry, circuit breaker và bulkhead bằng
   Resilience4j; bổ sung DLQ và quy trình replay.
4. **Observability:** OpenTelemetry xuyên suốt HTTP và Kafka, Prometheus metric,
   Grafana dashboard và alert, SLI/SLO cùng runbook vận hành.
5. **Bảo mật và bảo vệ dữ liệu:** authorization ở từng service, secret management,
   TLS, backup và restore đã được kiểm chứng.
6. **Hiệu năng và mở rộng:** kịch bản k6 hoặc Gatling có thể lặp lại, capacity
   baseline; chỉ thêm Redis, search, partitioning hoặc sharding dựa trên số liệu.
7. **Deployment và delivery:** Kubernetes, Helm, cấu hình theo môi trường, health
   probe, autoscaling, release verification và rollback.

Mỗi giai đoạn xác định capability và bằng chứng hoàn thành; chỉ cài thêm công nghệ
không có nghĩa là giai đoạn đã hoàn tất. Xem chi tiết tại
[`docs/roadmap/feature-roadmap.md`](docs/roadmap/feature-roadmap.md).

## Tài liệu

- [Lộ trình học Distributed Systems](docs/distributed-systems/README.md)
- [Mẫu tài liệu cho một topic](docs/distributed-systems/TOPIC_TEMPLATE.md)
- [Quy ước API](docs/api-conventions.md)
- [Lộ trình phát triển tính năng](docs/roadmap/feature-roadmap.md)
- [Trạng thái triển khai](docs/roadmap/implementation-status.md)
- [Thiết kế Authentication và Authorization](docs/authentication-authorization-design.md)
