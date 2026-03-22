# E-commerce Microservices (Spring Boot)

Day la du an microservice cho bai toan e-commerce, duoc xay dung bang Spring Boot va Spring Cloud.
He thong tach theo domain (customer, product, order, payment, notification) va giao tiep qua REST + Kafka.

## Kien truc tong quan

- `config-server`: Quan ly cau hinh tap trung cho tat ca services.
- `discovery`: Eureka Server de service discovery.
- `gateway`: API Gateway cho routing request vao cac service ben trong.
- `customer`: Quan ly khach hang (MongoDB).
- `product`: Quan ly san pham, ton kho (PostgreSQL + Flyway).
- `order`: Tao don hang, goi customer/product/payment, phat su kien order qua Kafka.
- `payment`: Xu ly thanh toan, phat su kien payment qua Kafka.
- `notification`: Consume su kien Kafka va gui email thong bao (MailDev), luu lich su vao MongoDB.

## Diagram

### Kien truc he thong

![System Architecture](diagrams/diagram.png)

### Domain diagram

![Domain Diagram](diagrams/domain_diagram.png)

## Cong nghe su dung

- Java 17
- Spring Boot 3
- Spring Cloud (Config Server, Eureka, Gateway, OpenFeign)
- PostgreSQL (order, payment, product)
- MongoDB (customer, notification)
- Apache Kafka + Zookeeper (event-driven messaging)
- Flyway (database migration cho product service)
- MailDev (test email local)
- Docker Compose (chay ha tang local)

## Ha tang local (docker-compose)

`docker-compose.yml` cung cap:

- PostgreSQL + pgAdmin
- MongoDB + mongo-express
- Kafka + Zookeeper
- MailDev

## Luong nghiep vu chinh (don gian)

1. Client goi API tao don qua `gateway`.
2. `order-service` validate customer, tru ton kho product, tao order.
3. `order-service` goi `payment-service` de xu ly thanh toan.
4. `order-service` va `payment-service` phat event len Kafka.
5. `notification-service` consume event, gui email va luu notification.

## Luu y

- Du an mang tinh chat hoc kien truc microservice, can bo sung them testing va hardening truoc khi dua vao production.
- Nhieu gia tri cau hinh hien dang hardcode cho moi truong local (DB/Kafka/Mail).

