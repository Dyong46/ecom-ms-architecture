# Tạo client Keycloak và cấu hình Ecom OAuth cho service

Hướng dẫn dành cho thành viên mới cần dùng `EcomAuthClient` hoặc
`EcomTokenService` trong `support/proxy-module`. Client `ecom-services` dùng
`client_credentials`: client ID và client secret được dùng để lấy access token
đại diện cho ứng dụng, không phải tài khoản người dùng.

## Khi nào cần cấu hình?

Hiện tại Order gọi Customer, Product và Payment qua `/internal/**`, không gửi
user token hoặc Ecom token. Order chưa bật `@EnableEcomAuth`, nên tạo order
không yêu cầu secret này. Có thể khai báo biến môi trường trước ở Order để
chuẩn bị; việc đó không tự kích hoạt lấy token.

Chỉ bật Ecom OAuth ở service có integration thực sự cần machine token. Các
service được phép dùng chung danh tính có thể nhận cùng credential; mỗi tiến
trình có cache token riêng. Các API internal phải được giữ trong mạng riêng.
Xem [mô hình giao tiếp và ranh giới internal](../support/proxy-module/README.md).

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

Service cần có dependency `proxy-module` và bật đăng ký token client/cache:

```java
import com.duong.module.proxy.annotation.EnableEcomAuth;

@SpringBootApplication
@EnableEcomAuth
public class IntegrationApplication {
    // main method của ứng dụng
}
```

Inject `EcomTokenService`, gọi `getAccessToken()` khi cần và gắn
`Authorization: Bearer <access token>` vào request của integration tương ứng.
Annotation không tự gắn token vào mọi Feign client. Không thêm vào Order chỉ
để phục vụ các lời gọi internal hiện tại.

## Chạy local bằng IntelliJ

1. Vào **Run → Edit Configurations**, chọn application của service cần dùng OAuth.
   Nếu chỉ muốn khai báo sẵn như buổi setup local, có thể chọn **OrderApplication**.
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

Tên `order-service` chỉ minh họa cách truyền biến; áp dụng cho service thực sự
bật OAuth. Pipeline hoặc server cung cấp giá trị thật từ kho secret. Với VPS
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
