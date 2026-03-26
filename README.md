# E-commerce Microservices (Spring Boot)

Dự án mô phỏng hệ thống e-commerce theo kiến trúc microservices, tập trung vào việc áp dụng các nguyên tắc **system design** trong thực tế: phân tách domain, giao tiếp đồng bộ/bất đồng bộ, cấu hình tập trung, service discovery và API gateway.

Mục tiêu chính của repository này là đóng vai trò như một tài liệu tham khảo kỹ thuật cho việc thiết kế và triển khai hệ thống phân tán bằng Spring Boot/Spring Cloud.

## 1) Giới thiệu

Hệ thống được tách theo từng domain nghiệp vụ độc lập:

- Quản lý khách hàng
- Quản lý sản phẩm và tồn kho
- Quản lý đơn hàng
- Thanh toán
- Gửi thông báo

Các service giao tiếp theo 2 hướng:

- **REST (đồng bộ)**: cho các thao tác cần phản hồi ngay.
- **Kafka (bất đồng bộ)**: cho event nghiệp vụ, giảm coupling giữa các service.

## 2) Mục tiêu dự án

- Xây dựng một kiến trúc microservice rõ ràng, dễ mở rộng.
- Minh hoạ cách tổ chức hệ thống theo domain-driven boundaries.
- Áp dụng pattern phổ biến trong hệ phân tán:
	- Centralized Configuration (`config-server`)
	- Service Discovery (`discovery` - Eureka)
	- API Gateway (`gateway`)
	- Event-Driven Communication (Kafka)
- Làm nền tảng học tập/phỏng vấn/thực hành system design cho bài toán e-commerce.

## 3) Tổng quan hệ thống

Các thành phần chính:

- `config-server`: Quản lý cấu hình tập trung cho toàn bộ services.
- `discovery`: Eureka Server để đăng ký và tìm kiếm service.
- `gateway`: Điểm vào duy nhất từ client, định tuyến request vào service nội bộ.
- `customer`: Quản lý thông tin khách hàng (MongoDB).
- `product`: Quản lý catalog sản phẩm, tồn kho (PostgreSQL + Flyway).
- `order`: Xử lý tạo đơn, điều phối luồng nghiệp vụ đặt hàng.
- `payment`: Xử lý nghiệp vụ thanh toán.
- `notification`: Nhận event từ Kafka, gửi email và lưu lịch sử thông báo.

## 4) Thiết kế kiến trúc (System Design)

### 4.1 Kiến trúc microservices theo domain

Mỗi service sở hữu một phạm vi nghiệp vụ riêng, giảm phụ thuộc trực tiếp và hỗ trợ scale độc lập theo tải của từng domain.

### 4.2 API Gateway Pattern

`gateway` đóng vai trò entry point cho client:

- Ẩn topology nội bộ của hệ thống.
- Tập trung routing.
- Thuận tiện để mở rộng thêm auth/rate limit/logging ở một điểm chung.

### 4.3 Service Discovery Pattern

`discovery` (Eureka) giúp các service tự đăng ký và tìm thấy nhau động, tránh hardcode địa chỉ service.

### 4.4 Centralized Configuration Pattern

`config-server` cung cấp cấu hình tập trung cho các service, hỗ trợ thay đổi cấu hình nhất quán theo môi trường.

### 4.5 Event-Driven Architecture

Kafka được dùng cho luồng bất đồng bộ:

- `order` và `payment` phát sinh event nghiệp vụ.
- `notification` consume event để gửi email/lưu lịch sử.

Lợi ích:

- Giảm coupling giữa producer/consumer.
- Tăng khả năng mở rộng theo chiều ngang.
- Dễ tích hợp thêm consumer mới trong tương lai.

## 5) Luồng nghiệp vụ chính

Kịch bản tạo đơn hàng (rút gọn):

1. Client gọi API tạo đơn qua `gateway`.
2. `order` kiểm tra dữ liệu khách hàng và sản phẩm.
3. `order` cập nhật tồn kho và tạo đơn.
4. `order` gọi `payment` để xử lý thanh toán.
5. `order`/`payment` phát event lên Kafka.
6. `notification` nhận event, gửi email xác nhận và lưu thông tin thông báo.

## 6) Sơ đồ kiến trúc

### 6.1 System Architecture

![System Architecture](diagrams/diagram.png)

### 6.2 Domain Diagram

![Domain Diagram](diagrams/domain_diagram.png)

## 7) Công nghệ sử dụng

- Java 17
- Spring Boot 3
- Spring Cloud (Config Server, Eureka, Gateway, OpenFeign)
- PostgreSQL (order, payment, product)
- MongoDB (customer, notification)
- Apache Kafka + Zookeeper
- Flyway (DB migration cho product service)
- MailDev (test email local)
- Docker Compose

## 8) Hạ tầng local

File `docker-compose.yml` cung cấp các thành phần phục vụ chạy local:

- PostgreSQL + pgAdmin
- MongoDB + mongo-express
- Kafka + Zookeeper
- MailDev

## 9) Cách chạy dự án (tham khảo)

Thứ tự khuyến nghị:

1. Khởi động hạ tầng bằng Docker Compose.
2. Chạy `config-server`.
3. Chạy `discovery`.
4. Chạy lần lượt các business services (`customer`, `product`, `order`, `payment`, `notification`).
5. Chạy `gateway` và bắt đầu gọi API từ phía client.

> Gợi ý: Có thể dùng Maven Wrapper (`./mvnw spring-boot:run`) trong từng service.

## 10) Giá trị học thuật về System Design

Dự án phù hợp để học và thảo luận các chủ đề:

- Bounded Context và ranh giới domain trong microservices.
- Trade-off giữa giao tiếp đồng bộ (REST) và bất đồng bộ (event).
- Tính nhất quán dữ liệu trong hệ phân tán.
- Khả năng chịu lỗi, retry, idempotency (có thể mở rộng thêm).
- Quan sát hệ thống (logging/metrics/tracing) cho production-ready architecture.

## 11) Hạn chế hiện tại và hướng mở rộng

Hạn chế hiện tại:

- Dự án thiên về mục tiêu học kiến trúc, chưa tối ưu cho production.
- Một số cấu hình đang thiên về môi trường local.

Hướng mở rộng đề xuất:

- Bổ sung test coverage (unit/integration/contract).
- Bổ sung bảo mật (OAuth2/JWT, secret management, TLS).
- Thêm resilience patterns (circuit breaker, retry, timeout, DLQ).
- Bổ sung observability (OpenTelemetry, Prometheus, Grafana).
- Chuẩn hoá CI/CD và chiến lược deploy đa môi trường.

## 12) Đối tượng phù hợp

- Người mới bắt đầu với Spring Microservices.
- Backend engineer muốn thực hành system design qua bài toán e-commerce.
- Sinh viên/người tự học cần một dự án end-to-end để phân tích kiến trúc.

