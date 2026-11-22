# Kafka Module

`com.duong:kafka-module:1.0.0` cung cấp publisher dùng chung, annotation bật module
và khai báo topic cho các service Spring Boot. Order, Payment và Notification đã
chuyển sang dependency này. Kafka broker chạy qua Docker Compose của project.

## Kết quả review module mẫu

| Thành phần trong mẫu | Điều chỉnh cho Ecom |
| --- | --- |
| `@EnableKafkaModule` | Giữ cách bật cấu hình qua annotation |
| Factory, template, listener config tự viết | Dùng hạ tầng do Spring Boot cấu hình qua `spring.kafka.*` để hỗ trợ đầy đủ SSL/SASL, timeout, observation |
| `KafkaService.send` trả void, bỏ qua future và bắt lỗi đồng bộ | `KafkaPublisher.send` trả `CompletableFuture<SendResult<Object, Object>>`, giữ lỗi gửi cho caller |
| Consumer trust mọi package | Notification dùng type mapping và các package DTO cụ thể |
| Có property ErrorHandlingDeserializer nhưng factory vẫn tạo JsonDeserializer trực tiếp | Cấu hình ErrorHandlingDeserializer làm wrapper thật cho JsonDeserializer |
| Runner tạo AdminClient, chờ `.get()` và tự tăng partition | Khai báo topic bằng `KafkaAdmin.NewTopics`, bật provisioning qua cấu hình rõ ràng |
| Message và interface gắn với mua vé, thẻ, callback MB | DTO và handler nghiệp vụ đặt trong service sở hữu |
| Phụ thuộc caching/common module của MB | Thư viện dùng Spring Kafka và Jackson theo BOM của project |

Source mẫu cũng tạo AdminClient mà chưa đóng trong runner và bỏ qua future khi
cập nhật partition. Module mới dùng lifecycle quản lý của Spring KafkaAdmin.

## Luồng đang sử dụng

```text
OrderProducer → KafkaPublisher → order-topic → NotificationConsumer
PaymentOutboxPublisher → KafkaPublisher → payment-topic → OrderPaymentEventConsumer / NotificationConsumer
```

Tên topic mặc định và JSON payload được duy trì. Producer gửi object DTO để
JsonSerializer chuyển sang JSON. Key mới dùng `orderReference`, giúp Kafka phân
partition theo cùng reference. Các message cũ có key null vẫn được listener nhận.
Ordering áp dụng trong từng partition, việc tăng partition có thể đổi partition
của một key.

| Alias trong header | DTO phía producer | DTO phía Notification |
| --- | --- | --- |
| `orderConfirmation` | `com.duong.ecommerce.order.payload.event.OrderConfirmation` | `com.duong.ecommerce.notification.payload.event.order.OrderConfirmation` |
| `paymentConfirmation` | `com.duong.ecommerce.payment.payload.event.PaymentNotificationRequest` | `com.duong.ecommerce.notification.payload.event.payment.PaymentConfirmation` |

Các alias giữ tương thích với backlog đã gửi bằng cấu hình trước đây. Khi thay
schema, producer và consumer cần thống nhất compatibility/version. Các trường
bổ sung và thay đổi ý nghĩa nghiệp vụ cần được đánh giá riêng.

## Bật cho service khác

Thêm dependency, version do root parent quản lý.

```xml
<dependency>
    <groupId>com.duong</groupId>
    <artifactId>kafka-module</artifactId>
</dependency>
```

```java
import com.duong.module.kafka.annotation.EnableKafkaModule;

@SpringBootApplication
@EnableKafkaModule
public class ExampleApplication {
    // main method
}
```

Spring Boot tạo ProducerFactory, ConsumerFactory, KafkaTemplate, KafkaAdmin và
listener container factory. Annotation của module đăng ký KafkaPublisher,
SafeProducerListener và các topic được khai báo. Consumer cung cấp Spring Boot
Kafka auto-configuration và cấu hình `spring.kafka.*` phù hợp. Khi có nhiều
KafkaTemplate, cần chọn bean cho publisher tùy chỉnh.

Các test slice chỉ kiểm tra HTTP có thể dùng `@ContextConfiguration` chọn đúng
controller và security config, tránh kéo application configuration vào slice.

## Cấu hình qua Config Server

Cấu hình kết nối và serializer dùng chung nằm trong
`cloud/config-server/src/main/resources/configurations/application.yml`.

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    admin:
      auto-create: ${KAFKA_PROVISION_TOPICS:false}
    producer:
      acks: all
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      properties:
        enable.idempotence: true
    consumer:
      enable-auto-commit: false
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      properties:
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
        allow.auto.create.topics: false
    listener:
      ack-mode: record
      concurrency: ${KAFKA_LISTENER_CONCURRENCY:1}
      observation-enabled: true
    template:
      observation-enabled: true

application:
  events:
    order-topic: ${KAFKA_ORDER_TOPIC:order-topic}
    payment-topic: ${KAFKA_PAYMENT_TOPIC:payment-topic}

kafka-module:
  provision-topics: ${KAFKA_PROVISION_TOPICS:false}
```

Type mapping của producer nằm trong `order-service.yml` và `payment-service.yml`.
Consumer group, type mapping và trusted packages nằm trong
`notification-service.yml`. `acks=all` và producer idempotence giúp giảm duplicate
do retry của producer, độ bền thực tế còn phụ thuộc replication và min.insync.replicas
của broker/topic. Bootstrap broker local hiện chỉ có một replica.

Khi đổi topic bằng env, các producer và consumer liên quan phải nhận cùng cấu hình.
Service chạy trong container cần địa chỉ broker và advertised listeners truy cập
được từ container. SSL/SASL dùng các property chuẩn `spring.kafka.*`, secret được
cấp qua runtime environment hoặc hệ thống quản lý secret của môi trường deploy.

## Gửi sự kiện

Inject `KafkaPublisher` qua constructor và gửi DTO.

```java
CompletableFuture<SendResult<Object, Object>> delivery =
        publisher.send("order-topic", event.orderReference(), event);

delivery.whenComplete((result, error) -> {
    if (error != null) {
        // xử lý delivery failure theo nghiệp vụ
        return;
    }
    // result.getRecordMetadata() chứa partition và offset đã được broker trả về
});
```

Có overload `send(topic, event)` dùng key null. Topic và event được kiểm tra trước
khi enqueue. Lỗi serializer hoặc enqueue đồng bộ được trả thành failed future.
Lỗi delivery bất đồng bộ cũng thể hiện trên future. `SafeProducerListener` ghi
metadata topic/partition/error type khi broker gửi callback lỗi, giúp quan sát
lỗi mà tránh đưa payload khách hàng và key vào log producer của module.

Payment dùng delivery future để xác nhận gửi sự kiện từ outbox. Quy tắc ghi settlement, thử lại và xử lý sự kiện giao trùng thuộc Payment và consumer. [Luồng settlement](../../docs/stripe-sandbox-design.md#flow-and-ownership)

## Nhận sự kiện và consumer group

Service viết handler bằng `@KafkaListener` trên Spring bean.

```java
@KafkaListener(topics = "${application.events.order-topic:order-topic}")
public void consume(OrderConfirmation event) {
    // xử lý nghiệp vụ
}
```

Notification hiện dùng group ID mặc định `paymentGroup,orderGroup`. Đây là **một
chuỗi group ID**, dấu phẩy nằm trong tên. Giá trị được giữ để tiếp tục dùng offsets
cũ. Có thể override bằng `NOTIFICATION_KAFKA_GROUP_ID`, cần lên kế hoạch cho replay
khi đổi sang group mới. `earliest` chỉ áp dụng khi group chưa có offset hợp lệ.

Các instance Notification cùng group chia sẻ partition. Service khác muốn nhận
mỗi sự kiện cho nghiệp vụ riêng phải dùng group riêng. Consumer group và Kafka ACL
được cấu hình trên từng môi trường theo phạm vi sử dụng.

## Topic provisioning và chạy local

Order và Payment khai báo topic của mình trong file cấu hình service.

```yaml
kafka-module:
  topics:
    - name: ${application.events.order-topic}
      partitions: 1
      replicas: 1
```

Provisioning mặc định tắt. Với môi trường local mới, thực hiện các bước sau.

Kafka local chạy một node KRaft kiêm broker và controller. IDE dùng
`localhost:9092`, container cùng mạng Compose dùng `kafka:29092`. Volume
`kafka_kraft_data` lưu metadata, message và consumer offsets. Khi chuyển từ
stack ZooKeeper cũ, volume mới bắt đầu trống. Nếu cần giữ lịch sử hoặc vị trí
đọc của consumer, hoàn tất migration trước khi thay broker đang chạy.

1. Khởi động hạ tầng bằng `docker compose up -d kafka`.
2. Reload Maven và rebuild các service.
3. Restart Config Server để phục vụ cấu hình dùng chung mới.
4. Đặt `KAFKA_PROVISION_TOPICS=true` vào Run Configuration của Order và Payment
   cho lần bootstrap, rồi khởi động hai service để KafkaAdmin tạo topic.
5. Khởi động Notification và kiểm tra consumer group trên Kafka.
6. Khi topic đã được quản lý bằng hạ tầng, chạy service với provisioning tắt.

Giá trị trong `application.yml` đồng thời bật `spring.kafka.admin.auto-create` và
`kafka-module.provision-topics` qua cùng env var. KafkaAdmin có thể tăng partition
khi topic hiện hữu có ít partition hơn khai báo. Replication factor và việc giảm
partition cần được quản lý bằng công cụ Kafka/hạ tầng. Một deployment nên có owner
rõ ràng cho provisioning. Với prod, cấu hình topic được quản lý theo quy trình của team.

Project đã có topic từ cấu hình cũ có thể tiếp tục sử dụng chúng với provisioning
tắt. Các tên topic cần tồn tại trước khi consumer bắt đầu xử lý.

## Phạm vi xử lý lỗi

Consumer dùng `ErrorHandlingDeserializer` để đưa lỗi deserialize vào header cho
listener container xử lý. Service quyết định cách xử lý khi consumer lỗi. Notification dùng error handler riêng để thử lại khi chưa lưu được job.

Module cung cấp transport. Payment sở hữu outbox; Notification lưu job theo `eventId` và quản lý retry SMTP. Kafka có thể giao lại sự kiện. [Delivery và retry email](../../services/notification/notification-delivery.md)

Observation được bật cho template và listener. Khi service có Micrometer tracing
và exporter tương ứng, Kafka instrumentation có thể tham gia trace. Consumer lag,
DLT và broker monitoring cần cấu hình observability riêng theo môi trường.

## Kiểm tra

```bash
./services/product/mvnw -f pom.xml -pl support/kafka-module -am test
```

Tests dùng Kafka KRaft nhúng và topic fixture riêng. Các test kiểm tra gửi/nhận JSON,
key, hai alias producer/consumer khác class, tạo topic có partition cấu hình,
propagate lỗi gửi, lỗi deserialize và cấu hình chung được copy từ Config Server
vào test resources. Context tests của các service kiểm tra wiring sau migration.

```bash
./services/product/mvnw -f pom.xml -DskipTests package
```

Build toàn reactor kiểm tra module và dependency của các service.

Tham khảo tài liệu [Spring Boot Kafka](https://docs.spring.io/spring-boot/3.3/reference/messaging/kafka.html)
và [Spring Kafka serialization](https://docs.spring.io/spring-kafka/reference/kafka/serdes.html).
