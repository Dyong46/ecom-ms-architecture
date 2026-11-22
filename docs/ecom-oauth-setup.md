# Tạo client Keycloak và cấu hình Ecom OAuth cho service

Hướng dẫn dành cho thành viên mới cần dùng HTTP client `EcomAuthClient` trong
`support/proxy-module` và lớp điều phối `EcomTokenService` trong `services/order`. Client `ecom-services` dùng
`client_credentials`: client ID và client secret được dùng để lấy access token
đại diện cho ứng dụng, không phải tài khoản người dùng.

## Khi nào cần cấu hình?

Hiện tại Order gọi Customer và Product qua `/internal/**` không gửi token.
Riêng Order → Payment gọi `/internal/payments` bằng Bearer token lấy từ Keycloak.
Order đã cấu hình `OrderOAuthConfiguration` và **bắt buộc có client secret khi khởi động**.
Payment kiểm tra JWT và chỉ chấp nhận client ID đã cấu hình.

Chỉ bật Ecom OAuth ở service có integration thực sự cần machine token. Các
service được phép dùng chung danh tính có thể nhận cùng credential. Order lưu
token trong Redis; các consumer cùng namespace/môi trường và OAuth identity có
thể dùng chung token. Các API internal phải được giữ trong mạng riêng.
Xem [mô hình giao tiếp và ranh giới internal](../support/proxy-module/README.md).

<a id="keycloak-volume"></a>
## Giữ realm khi cập nhật Compose local

Compose lưu dữ liệu Keycloak ở volume `keycloak_data`. Với container cũ dùng
database dev trong filesystem của container, sao lưu **trước** khi chạy
`docker compose up` hoặc `down`. Thực hiện từ thư mục gốc repo, với Keycloak
24.0.2 và file `.env` đã cấu hình. Dừng nếu một bước báo lỗi.

```sh
backup_dir=$(mktemp -d) &&
docker compose stop keycloak &&
docker cp -a ms_kaycloak:/opt/keycloak/data/. - > "$backup_dir/keycloak-data.tar" &&
tar -tf "$backup_dir/keycloak-data.tar" | grep 'keycloakdb.mv.db'
```

Giữ bản sao ở nơi riêng tư vì database chứa thông tin xác thực. Với volume mới
trống, tạo container, khôi phục dữ liệu rồi mới khởi động:

```sh
docker compose create --force-recreate keycloak &&
docker cp -a - ms_kaycloak:/opt/keycloak/data < "$backup_dir/keycloak-data.tar" &&
docker compose start keycloak
```

Mở Admin Console, kiểm tra realm `micro-services` và client `ecom-services`.
Giữ bản sao đến khi Order lấy được token. Các lần tạo lại container sau đó
dùng lại volume. Lệnh `docker compose down -v` sẽ xóa các named volume.

## Tạo client và lấy secret trên Keycloak

Điều kiện: có quyền quản trị client trong realm. Với môi trường local, Keycloak
ở `http://localhost:9098`, realm của project là `micro-services`.

1. Mở Keycloak Admin Console, chọn realm **micro-services** → **Clients** →
   **Create client**.
2. Trong **General settings**, chọn **OpenID Connect**, nhập Client ID
   **ecom-services**, rồi bấm **Next**.
3. Trong **Capability config**, thiết lập:

   | Tùy chọn | Giá trị |
   | --- | --- |
   | Client authentication | **ON** |
   | Authorization | OFF |
   | Standard flow | Bỏ chọn |
   | Direct access grants | Bỏ chọn |
   | Service accounts roles | **Chọn** |
   | Implicit flow | Bỏ chọn |
   | OAuth 2.0 Device Authorization Grant | Bỏ chọn |
   | OIDC CIBA Grant | Bỏ chọn |

   Bật **Client authentication** trước để mở lựa chọn **Service accounts roles**.
4. Bấm **Next**. Trong **Login settings**, để trống các URL redirect, root URL
   và web origins vì client này không dùng đăng nhập qua trình duyệt. Bấm **Save**.
5. Mở tab **Credentials**, chọn Client Authenticator **Client Id and Secret**,
   copy **Client secret** vào nơi quản lý secret của môi trường.
6. Nếu API đích kiểm tra role, gán đúng role cần thiết trong **Service account
   roles** và cấu hình role scope tương ứng. Không cần cấp quyền quản trị realm
   chỉ để lấy token. Lấy được token chưa chứng minh client được phép gọi API đích.

Tham khảo [Keycloak: Service accounts](https://www.keycloak.org/docs/latest/server_admin/index.html#_service_accounts).
Mỗi môi trường nên có credential riêng; không dùng secret production để chạy local.

## Cấu hình chung và service sử dụng

Config Server đã khai báo tại
`cloud/config-server/src/main/resources/configurations/application.yml`:

```yaml
integration-config:
  ecom:
    oauth-base-url: ${ECOM_OAUTH_BASE_URL:http://localhost:9098}
    oauth-realm: ${ECOM_OAUTH_REALM:micro-services}
    oauth-username: ${ECOM_OAUTH_CLIENT_ID:ecom-services}
    oauth-password: ${ECOM_OAUTH_CLIENT_SECRET:}
```

`oauth-username` là **client ID**, `oauth-password` là **client secret**, không
phải username/password của người dùng. Giữ placeholder trong Git; cung cấp giá trị
thật cho tiến trình service sử dụng OAuth, không cần đặt secret chung vào Config Server.

Các biến môi trường cần dùng:

```dotenv
ECOM_OAUTH_BASE_URL=http://localhost:9098
ECOM_OAUTH_REALM=micro-services
ECOM_OAUTH_CLIENT_ID=ecom-services
ECOM_OAUTH_CLIENT_SECRET=<secret thật lấy từ tab Credentials>
```

Order dùng dependency `proxy-module` và `caching-module`, bật Redis token cache:

```java
import com.duong.module.proxy.annotation.EnableEcomAuth;
import com.duong.module.caching.annotation.EnableCachingModule;

@Configuration(proxyBeanMethods = false)
@EnableEcomAuth
@EnableCachingModule
public class OrderOAuthConfiguration {
    // Order tự khai báo bean EcomTokenService và RedisEcomTokenCache.
    // Xem implementation trong services/order/.../integration/oauth/.
}
```

`OrderPaymentGateway` gọi `EcomTokenService.getAccessToken()` rồi truyền
`Authorization: Bearer <access token>` vào `PaymentClient.requestOrderPayment(authorization, key, request)`.
Proxy chỉ gửi HTTP; caching chỉ đọc/ghi Redis. Hai module không phụ thuộc nhau.
Order sở hữu cache-aside, fallback và quyết định dùng credential nào.
Xem [rules kiến trúc](architecture-rules.md).

## Chạy local bằng IntelliJ

1. Vào **Run → Edit Configurations**, chọn application của service cần dùng OAuth.
   Với luồng Order → Payment, chọn **OrderApplication**.
2. Chọn **Modify options → Environment variables** nếu ô này chưa hiển thị.
3. Bấm nút mở bảng ở bên phải ô **Environment variables**.
4. Thêm các biến ở trên; URL, realm và client ID có mặc định nên có thể chỉ cần
   khai báo `ECOM_OAUTH_CLIENT_SECRET` khi giữ nguyên cấu hình local.
5. Bấm **OK → Apply → OK**, rồi restart service.

Không chọn **Store as project file** cho cấu hình chứa secret thật. Cấu hình chạy
local mặc định được IntelliJ lưu trong `.idea/workspace.xml`; repo ignore `.idea/`.
Đây là dữ liệu văn bản, không phải kho secret được mã hóa. Không gửi file chứa
secret cùng project hoặc đưa secret vào ảnh chụp màn hình/log.

Java chạy từ IntelliJ không tự đọc `.env` của Docker Compose. Mỗi thành viên phải
tự cấu hình Run Configuration hoặc được cấp secret qua công cụ quản lý của team.

## Order → Payment qua OAuth2

```text
Order → Keycloak: client_credentials (client ID + secret)
Order → Payment /internal/payments: Authorization: Bearer <access token>
Payment → kiểm tra chữ ký JWT, issuer, thời hạn và azp=ecom-services
```

- **OrderApplication**: khai báo `ECOM_OAUTH_CLIENT_SECRET` như hướng dẫn ở trên.
  `PaymentClient` đăng ký cùng Ecom auth; Order tự ghép hai module qua `OrderOAuthConfiguration`.
- **PaymentApplication**: không cần client secret. Cấu hình Config Server trong
  `payment-service.yml` có các giá trị mặc định sau:

  ```dotenv
  PAYMENT_OAUTH_ISSUER_URI=http://localhost:9098/realms/micro-services
  PAYMENT_OAUTH_JWK_SET_URI=http://localhost:9098/realms/micro-services/protocol/openid-connect/certs
  ECOM_OAUTH_CLIENT_ID=ecom-services
  ```

  Có thể khai báo đè các biến này trong Run Configuration của Payment khi đổi
  môi trường. Issuer phải khớp claim `iss`; JWKS phải truy cập được từ Payment.
- Keycloak: giữ client authentication và service accounts roles bật như phần trên.
  Quy tắc hiện tại kiểm tra signed claim `azp`, chưa yêu cầu role/scope hoặc audience
  riêng của Payment. Các consumer dùng chung client ID có cùng quyền gọi endpoint này;
  không dùng cấu hình này để phân biệt từng service hoặc từng người dùng.
- Không forward token Bruno/user từ Order sang Payment. Token user từ client khác
  như `micro-services-api` không đáp ứng chính sách internal của Payment.
- Token bị thiếu/không hợp lệ: Payment trả `401`; token hợp lệ thuộc client khác:
  `403`. Token endpoint lỗi: Order dừng lời gọi Payment, không fallback anonymous
  và không tự retry thanh toán. Token được cache đến gần lúc hết hạn.
- Gateway chặn API nghiệp vụ `/internal/payments`. Stripe gửi webhook qua Gateway đến Payment để kiểm tra chữ ký. [Ranh giới API Payment](payment-internal-api-design.md)

Khởi động Redis local bằng `docker compose --profile caching up -d redis`.
Order đọc `REDIS_HOST`/`REDIS_PORT` (mặc định localhost/6379), `CACHE_NAMESPACE`
(mặc định ecom) và `APP_ENV` (mặc định local). Cache key gồm issuer, client ID và scope trong namespace của môi trường.

Order kiểm tra Redis trước. Cache miss, lỗi đọc hoặc JSON hỏng khiến Order lấy token mới từ Keycloak.
Order tính expiry từ trước khi gọi HTTP; cache áp dụng margin 30 giây. Nếu ghi Redis lỗi,
Order vẫn dùng token vừa lấy. Refresh được đồng bộ trong từng tiến trình, nên nhiều instance
vẫn có thể cùng gọi Keycloak. Sau khi đổi secret, token cũ có thể che lỗi cấu hình cho đến
lần cấp mới; khi cần, evict đúng key của danh tính đó.

Kiểm tra key rồi dùng `redis-cli TTL <key>` trong cùng container để xem thời hạn mà không in token:

```sh
docker compose --profile caching exec -T redis redis-cli --scan --pattern 'ecom:local:v1:global:json:oauth-tokens:*'
```

Sau khi reload Maven/rebuild, restart **Config Server → Payment → Order**; Keycloak
phải chạy khi thực hiện request. Nếu trước đó override `PAYMENT_SERVICE_URL`, bảo
đảm URL vẫn kết thúc bằng `/internal/payments`.

Dùng các HTTP/JWT fixture test trong repo để kiểm tra an toàn. Khi thử tạo order
thật qua Gateway, dùng dữ liệu thử vì flow có thể thay đổi tồn kho, ghi order và
payment. OAuth không cung cấp transaction phân tán hoặc tự bù tồn kho khi lỗi;
không replay mù một request thất bại.

## Truyền cấu hình khi deploy

Hệ thống deploy thay IntelliJ truyền biến môi trường vào tiến trình Java/container.
Ví dụ phần cấu hình cho service trong Docker Compose (minh họa, chưa phải file
deploy production hoàn chỉnh):

```yaml
services:
  order-service:
    image: your-registry/order-service:your-version
    environment:
      ECOM_OAUTH_BASE_URL: ${ECOM_OAUTH_BASE_URL:?Set Keycloak URL}
      ECOM_OAUTH_REALM: ${ECOM_OAUTH_REALM:-micro-services}
      ECOM_OAUTH_CLIENT_ID: ${ECOM_OAUTH_CLIENT_ID:-ecom-services}
      ECOM_OAUTH_CLIENT_SECRET: ${ECOM_OAUTH_CLIENT_SECRET:?Set client secret}
```

`order-service` hiện là consumer OAuth của Payment. Pipeline hoặc server cung cấp giá trị thật từ kho secret. Với VPS
đơn giản, có thể dùng file riêng ngoài repository, chỉ cho tài khoản deploy đọc:

```bash
docker compose --env-file /etc/ecom/secrets.env -f compose.prod.yml up -d
```

`compose.prod.yml` và đường dẫn trên là ví dụ. `--env-file` cung cấp giá trị cho
Compose nội suy; cần giữ phần `environment` để truyền biến vào container.
URL Keycloak trong container phải là địa chỉ service truy cập được, không dùng
`localhost:9098` của máy dev. Production cần URL/realm khớp cấu hình issuer của hệ thống.

Với Kubernetes, tham chiếu Kubernetes Secret bằng `secretKeyRef`; với cloud có
thể tích hợp kho secret của hạ tầng. Không commit giá trị thật trong manifest,
Dockerfile hoặc image. Docker Compose secrets cấp dữ liệu qua file; nếu chọn
cách này phải cấu hình Spring đọc file tương ứng, không tự biến thành env var.
Xem [Docker: environment variables](https://docs.docker.com/compose/how-tos/environment-variables/set-environment-variables/)
và [Docker: secrets](https://docs.docker.com/compose/how-tos/use-secrets/).

Restart/redeploy các service sử dụng credential sau khi thay biến môi trường.
Khi rotate secret trên Keycloak, cập nhật kho secret và redeploy các consumer
đang dùng chung danh tính; token đã cache có thể che lỗi cấu hình đến lần lấy token tiếp theo.

## Kiểm tra và xử lý lỗi

Để kiểm tra riêng credential bằng Bruno/Postman:

1. Tạo request **POST** tới
   `http://localhost:9098/realms/micro-services/protocol/openid-connect/token`.
2. Chọn **Basic Auth**: username `ecom-services`, password là client secret.
3. Body chọn **application/x-www-form-urlencoded**, thêm
   `grant_type=client_credentials`.
4. Gửi request. Mong đợi HTTP **200**, response có `access_token`,
   `token_type: Bearer` và `expires_in`. Không đưa response token vào log hoặc Git;
   không lưu secret thật trong collection được chia sẻ.

Request này chỉ lấy token, không tạo order/payment hoặc thay đổi tồn kho.

| Dấu hiệu | Kiểm tra |
| --- | --- |
| `401`, `invalid_client` | Client ID/secret đúng realm, Client authentication ON, secret còn hiệu lực |
| Service account bị từ chối | Service accounts roles đã bật, client đang enabled |
| `404` tại token endpoint | Base URL, tên realm và đường dẫn token |
| Timeout / connection refused | Keycloak đã chạy, DNS/port/network từ service |
| `Ecom OAuth URL, realm, client ID and client secret are required` khi startup | Biến môi trường của đúng application/container đã được cấp |
| Có token nhưng API trả `403` | Role, scope, audience và quy tắc phân quyền của API đích |
| Không thấy request lấy token | Chỉ khai báo env chưa kích hoạt OAuth; service phải đăng ký client và gọi token service, hoặc đang dùng token cache |

Kiểm tra bằng Bruno xác nhận credential hoạt động; vẫn cần chạy integration của
service để xác nhận ứng dụng đã đọc đúng config và dùng token đúng API.
