# Caching Module

`com.duong:caching-module:1.0.0` là thư viện dùng chung, không phải service chạy riêng.
Module tham khảo `support/caching-module-mau`, giữ cách chia scope và đóng gói cache,
nhưng không mang DTO thẻ/vé, credential hoặc tên key nghiệp vụ MB vào Ecom.

Module đã vào Maven reactor và dependency management. Chưa service nghiệp vụ nào
thêm dependency hoặc bật module; `EcomTokenService` hiện vẫn cache trong bộ nhớ
như trước. Muốn chuyển OAuth sang Redis cần tích hợp riêng, không xảy ra tự động.

## Thành phần và phạm vi

| Thành phần | Vai trò | Phạm vi |
| --- | --- | --- |
| `ecomRedisCacheManager` | Spring Cache, TTL mặc định/theo region, JSON, không cache null, statistics | Redis theo service và môi trường |
| `ecomMemoryCacheManager` | Caffeine, TTL sau khi ghi, giới hạn số entry mỗi cache, statistics | Trong từng tiến trình |
| `RedisJsonCache` | Đọc/ghi JSON theo kiểu, TTL trên mọi lần ghi, evict, atomic put-if-absent | Chọn `SERVICE` hoặc `GLOBAL` |
| `AccessTokenCache` | Cache machine token theo issuer/client/scope, kiểm tra hạn và trừ margin | Redis global trong cùng namespace/môi trường |

Redis dùng Spring Data Redis/Lettuce do BOM Spring Boot quản lý, thay Redisson
trong mẫu. Memory dùng Caffeine thay ConcurrentMapCacheManager để có TTL và
maximum size. Không ghim lại Jackson 2.14.2 của mẫu.

Hai cache manager hoạt động độc lập, không tự thành L1/L2 và không tự fallback
sang memory khi Redis lỗi. `ecomRedisCacheManager` là `@Primary`; nên chỉ rõ
`cacheManager` ở annotation để người đọc biết dữ liệu được lưu ở đâu. Nếu ứng dụng
đã có manager `@Primary`, phải giải quyết lựa chọn mặc định trước khi bật module.
Có thể cung cấp bean cùng tên để thay manager, hoặc bean cùng type để thay helper;
không cần bật bean-definition-overriding.

## Bật cho một service

Thêm dependency vào POM của consumer, version do root quản lý:

```xml
<dependency>
    <groupId>com.duong</groupId>
    <artifactId>caching-module</artifactId>
</dependency>
```

```java
import com.duong.module.caching.annotation.EnableCachingModule;

@SpringBootApplication
@EnableCachingModule
public class ProductApplication {
    // main method của service
}
```

Consumer phải có `spring.application.name`. Module được import chủ động, không
cần component scan `com.duong.module.*`. Spring Boot vẫn có thể tạo hạ tầng Redis
thông thường khi dependency có trên classpath; các helper/manager riêng của module
chỉ được đăng ký khi bật annotation.

Cấu hình chung đã được chuẩn bị ở Config Server:
`cloud/config-server/src/main/resources/configurations/application.yml`.

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      username: ${REDIS_USERNAME:}
      password: ${REDIS_PASSWORD:}
      connect-timeout: 2s
      timeout: 2s

caching-config:
  namespace: ${CACHE_NAMESPACE:ecom}
  environment: ${APP_ENV:local}
  default-ttl: ${CACHE_DEFAULT_TTL:5m}
  memory-max-size: ${CACHE_MEMORY_MAX_SIZE:1000}
  regions:
    product-summary-v1:
      ttl: 30s
      memory-max-size: 200
```

`regions` là ví dụ override trong cấu hình service; config chung chưa bật cache
catalog hoặc cache dữ liệu nghiệp vụ nào. TTL phải ít nhất 1ms; memory size phải
lớn hơn 0. Region không khai báo dùng mặc định; region chỉ khai báo một thuộc tính
sẽ kế thừa thuộc tính còn lại. `memory-max-size` chỉ giới hạn Caffeine, không giới
hạn entry Redis. Chính sách ở đây là expire-after-write, không phải max-idle.

`namespace`, `environment`, application name và region dùng 1–128 ký tự chữ/số,
dấu chấm, gạch ngang hoặc gạch dưới; ký tự đầu phải là chữ/số. Đặt `APP_ENV` rõ
ràng cho staging/prod, không suy luận tự động từ active profiles. Dùng các thuộc
tính chuẩn `spring.data.redis.*` của Boot cho database, cluster hoặc TLS/SSL
khi deploy; dùng secret runtime cho Redis password. Module chưa kiểm thử cluster.

## Chạy Redis local

Tại thư mục root:

```bash
docker compose --profile caching up -d redis
```

Service Redis mới có profile `caching`, chỉ bind `127.0.0.1:6379`, giới hạn 128MB
và dùng `allkeys-lru`. Đây là cache local có thể bị evict, không bật persistence
hoặc password. Redis sẽ không được khởi động khi chạy Compose mặc định mà không
chọn profile/service này. Nếu port đã bận, đổi host port và `REDIS_PORT` tương ứng.

Sau khi bật module cho consumer, reload Maven/rebuild và restart Config Server
rồi consumer. Thử khởi động không chứng minh Redis reachable; kiểm tra bằng một
request đọc/ghi cache thật hoặc health check phù hợp.

## Spring Cache theo service

Ví dụ trong một Spring bean thuộc package `com.duong.*`:

```java
@Cacheable(cacheNames = "product-summary-v1",
           cacheManager = "ecomRedisCacheManager", key = "#p0", unless = "#result == null")
public ProductSummary findProduct(Integer id) {
    return loadSummaryFromDatabase(id);
}

@CacheEvict(cacheNames = "product-summary-v1",
            cacheManager = "ecomRedisCacheManager", key = "#p0")
public void updateProduct(Integer id, UpdateProductRequest request) {
    // cập nhật database; chọn thời điểm evict phù hợp với transaction của nghiệp vụ
}
```

Đổi sang `ecomMemoryCacheManager` nếu dữ liệu chỉ cần cache trong tiến trình.
Spring AOP yêu cầu gọi method qua Spring proxy; self-invocation không kích hoạt
annotation. Không cache JPA entity/proxy hoặc đối tượng request/security context;
ưu tiên DTO đơn giản. Với method có thể trả null, dùng `unless` như ví dụ.

Serializer Spring Cache giữ thông tin kiểu để đọc lại record/collection đúng
kiểu. Allowlist gồm `com.duong.*`, các kiểu giá trị số/chuỗi, ngày giờ và collection
List/Map/Set; object ngoài allowlist sẽ không được đọc lại. Chỉ cache DTO tin cậy;
Redis cần được bảo vệ vì đây không phải ranh giới cho dữ liệu từ bên không tin cậy.
Serializer của module không thay ObjectMapper của ứng dụng.

Redis clear-cache dùng SCAN theo prefix của cache, không FLUSHDB. Redis cache
manager không cung cấp transaction phân tán với DB; module không tự giải quyết
race giữa cập nhật DB, đọc cache và invalidation. Quyết định evict sau commit/outbox
thuộc nghiệp vụ. Statistics được bật tại manager, nhưng chưa tự tạo dashboard
Grafana hoặc instrument từng lời gọi `RedisJsonCache`.

## RedisJsonCache: service hoặc global

```java
import com.duong.module.caching.core.CacheScope;
import com.duong.module.caching.core.RedisJsonCache;
import com.fasterxml.jackson.core.type.TypeReference;

// Inject RedisJsonCache vào service qua constructor.
cache.put(CacheScope.SERVICE, "product-summary-v1", "1051", summary);
var found = cache.get(CacheScope.SERVICE, "product-summary-v1", "1051", ProductSummary.class);

// Ghi nguyên list dưới một key; không ghi đè từng phần tử vào cùng một key.
cache.put(CacheScope.GLOBAL, "catalog-v1", "featured", summaries, Duration.ofMinutes(1));
var list = cache.get(CacheScope.GLOBAL, "catalog-v1", "featured",
        new TypeReference<List<ProductSummary>>() {});

cache.evict(CacheScope.SERVICE, "product-summary-v1", "1051");
```

`get` trả `Optional.empty()` khi không có entry. Redis lỗi sẽ ném lỗi Redis;
JSON không đọc được ném `CacheSerializationException` với thông báo không chứa
payload. Caller tự quyết định fallback/load lại; module không giả lỗi thành miss.
Null value bị từ chối. API này không dùng polymorphic JSON; kiểu do caller cung cấp.

Key có dạng:

```text
{namespace}:{environment}:v1:service:{application}:json:{region}:{base64url-key}
{namespace}:{environment}:v1:global:json:{region}:{base64url-key}
{namespace}:{environment}:v1:service:{application}:spring:{region}:{spring-key}
```

Dấu `{}` trong ví dụ trên là placeholder mô tả, không phải Redis Cluster hash tag.
Global không chứa application name; chỉ chia sẻ khi các consumer cùng Redis,
namespace, môi trường, region và key. Spring Cache và RedisJsonCache cố tình dùng
không gian key riêng; evict bên này không xóa bên kia. Key encoding chỉ tránh
va chạm delimiter, không mã hóa thông tin bí mật. Namespace cũng không thay Redis ACL.

Với global cache, team phải thống nhất owner, DTO, TTL và invalidation; writer
cuối cùng quyết định dữ liệu/TTL. Thay schema nên đổi region version. Không dùng
cache để thay API nghiệp vụ tạo payment hoặc sửa tồn kho.

`putIfAbsent(scope, region, key, value, ttl)` dùng một lệnh SET NX có TTL, kiểm
tra nguyên tử việc entry chưa tồn tại. Nó không phải distributed lock và không
đủ làm idempotency thanh toán: entry có thể hết hạn, bị evict hoặc Redis restart.
Không dùng nó làm bảo đảm chống thu tiền hai lần.

## AccessTokenCache

```java
var identity = new OAuthTokenKey(
        "http://localhost:9098/realms/micro-services", "ecom-services", "");
var cached = accessTokenCache.getValidToken(identity); // Optional<String>

// Khi caller thực sự lấy token mới:
Instant requestedAt = clock.instant(); // đo trước network request
var response = authClient.oauthToken(/* realm và form của integration */);
accessTokenCache.put(identity,
        new CachedAccessToken(response.accessToken(), requestedAt.plusSeconds(response.expiresIn())));
```

Đoạn trên là minh họa cách tích hợp, chưa thay code của EcomTokenService.
Bean mặc định dùng UTC clock và expiry margin 30s. TTL Redis = thời gian còn lại
trừ margin; token đã gần hết hạn sẽ không được ghi (`put` trả false). `getValidToken`
kiểm tra lại absolute expiry dù Redis vẫn còn entry. Scope được chuẩn hóa thứ tự;
issuer, client ID và requested scope khác nhau dùng key khác nhau.

Component dành cho machine token với cùng token audience/resource contract.
Nếu cùng issuer/client/scope nhưng yêu cầu token cho resource/audience khác nhau,
phải mở rộng identity trước khi dùng. Không dùng wrapper này để cache token từng
người dùng: identity hiện không gồm user subject, tenant, token exchange hoặc
credential version. Khi đổi credential/thu hồi token cần evict; cache không tự
biết sự kiện revoke. Redis lưu token như dữ liệu nhạy cảm, không mã hóa giá trị
ở tầng module; production cần TLS, ACL và bảo vệ backup/log.

Module không gọi Keycloak, không gắn Authorization header, không có automatic
retry, lock refresh liên instance hoặc tự phục hồi từ Redis lỗi. Những phần đó
thuộc token acquisition service. `CachedAccessToken.toString()` che token, nhưng
không log JSON, request/response token hoặc dữ liệu cache.

## Kiểm tra

Unit tests không cần Redis. Integration tests chỉ chạy khi có
`CACHE_TEST_REDIS_PORT`; nếu thiếu sẽ được báo skipped. Dùng Redis riêng, không
chỉ port của Redis production/shared để chạy test.

```bash
docker run --rm -d --name ecom-cache-test -p 127.0.0.1:16379:6379 redis:7.4 redis-server --save '' --appendonly no
docker exec ecom-cache-test redis-cli ping
CACHE_TEST_REDIS_PORT=16379 ./services/product/mvnw -f pom.xml -pl support/caching-module -am test
docker stop ecom-cache-test
```

Tests dùng namespace ngẫu nhiên, không FLUSHDB. Chúng kiểm tra chia sẻ/isolation,
TTL thực tế, JSON record/list/map/Unicode/dấu nháy, ghi NX đồng thời, Spring
Cacheable/evict, expiry token và lỗi đọc dữ liệu. Chưa chứng minh Redis Cluster,
failover, TLS/ACL production, tải cao hoặc tính nhất quán của nghiệp vụ sử dụng cache.

## Khác với module mẫu

- Giữ opt-in annotation, scope service/global, cache policy theo region và wrapper token.
- TTL rõ ràng trên cả Spring Cache lẫn thao tác trực tiếp; cache memory có giới hạn.
- Không copy helper Lua nối dữ liệu vào script, vòng lặp SET mất phần tử hoặc API nhận tham số nhưng bỏ qua.
- Chưa cung cấp Redis Hash/Set helper, arbitrary Lua runner, distributed lock, cache thẻ/vé hoặc cơ chế warm-up nghiệp vụ.
- Không thay đổi folder mẫu; folder mẫu không thuộc reactor.

Tham khảo [Spring Data Redis Cache](https://docs.spring.io/spring-data/redis/reference/redis/redis-cache.html).
