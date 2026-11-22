# Thiết kế xử lý exception

Trạng thái quyết định: **Đã chấp thuận**. Ngày: **2026-11-16**.
Người quyết định: chủ project.

Phạm vi triển khai: Customer, Product, Order và Payment bật `@EnableWebModule`
và dùng handler chung. Format được chủ project chọn: **envelope kiểu MB Bank**
(`data/error_code/extra_data` và `request_id`), ngày 2026-11-16.

## Quyết định hiện tại

Chọn **Cách 1: dùng exception nền để phân loại lỗi và xử lý HTTP tập trung trong
`web-module`**. Service kiểm tra nghiệp vụ, ném exception phù hợp và truyền message
có thể công khai cho client. Handler chung chuyển exception thành HTTP response.

```text
Request
  → Controller
  → Service kiểm tra nghiệp vụ
  → Exception đã phân loại + message
  → Handler chung trong web-module
  → HTTP status + response lỗi
```

Mục tiêu là trả đúng loại lỗi, thống nhất response và giảm code xử lý lỗi kỹ thuật
bị lặp giữa các service. Chưa cần xây thêm resolver, registry hay lớp mapping riêng
cho từng exception chỉ để hỗ trợ những nhu cầu chưa xuất hiện.

## Phân chia trách nhiệm

| Thành phần | Trách nhiệm |
| --- | --- |
| Service | Quyết định nghiệp vụ hợp lệ hay bị từ chối; ném exception phù hợp; sở hữu message và mã lỗi nghiệp vụ nếu cần. |
| `common-module` | Cung cấp exception nền và các nhóm lỗi tổng quát dùng lại được. Không chứa quyết định nghiệp vụ riêng của Product, Order hoặc Payment. |
| `web-module` | Chuyển nhóm exception sang HTTP status và response; xử lý lỗi MVC/validation và fallback cho lỗi bất ngờ. |
| Security filter và gateway | Xử lý lỗi phát sinh tại ranh giới tương ứng, theo contract lỗi được thống nhất. Không chuyển toàn bộ trách nhiệm này cho MVC advice. |

Giữ quan hệ foundation hiện có `web-module → common-module`. Quyết định này không
tạo dependency mới giữa các support capability hoặc từ support tới service.

## Cách dùng và lý do

Ví dụ service từ chối số lượng không hợp lệ:

```java
if (quantity <= 0) {
    throw new BadRequestException("Quantity must be greater than zero");
}
```

Ví dụ service không tìm thấy sản phẩm:

```java
throw new NotFoundException("Product not found");
```

Customer dùng trực tiếp `NotFoundException`; không giữ subclass chỉ để đổi tên
loại lỗi. `ProductPurchaseException` và `BusinessException` hiện kế thừa
`BadRequestException`. `ConflictException` biểu diễn nhóm 409;
không tự đổi các lỗi nghiệp vụ hiện tại từ 400 sang 409 trong lần chuyển này.

`throw` chỉ báo rằng xử lý thất bại; exception và handler tương ứng mới xác định
response HTTP. Không dùng `BadRequestException` cho mọi tình huống, hoặc mặc định
chuyển mọi `RuntimeException` thành 400.

| Tình huống | Phân loại HTTP dự kiến |
| --- | --- |
| Đầu vào không hợp lệ, ví dụ số lượng không dương | 400 |
| Chưa xác thực hoặc thông tin xác thực không hợp lệ | 401, xử lý tại security boundary khi phù hợp |
| Đã xác thực nhưng thiếu quyền | 403 |
| Không tìm thấy tài nguyên | 404 |
| Trạng thái hiện tại xung đột với thao tác, ví dụ đơn đã hủy | 409 |
| Lỗi nội bộ bất ngờ | 500 với thông báo an toàn |

Các ví dụ phân loại không thay thế việc xác định contract của từng use case.
Fallback không được trả nguyên message nội bộ, stack trace hoặc dữ liệu nhạy cảm.
Validation chỉ trả thông tin được chọn lọc, không serialize nguyên binding error
và giá trị đầu vào bị từ chối.

Message là đủ khi client chỉ cần hiển thị thông báo. Khi client cần phản ứng khác
nhau theo tình huống, bổ sung mã lỗi ổn định do service sở hữu, ví dụ
`PRODUCT_OUT_OF_STOCK`. Client so sánh mã lỗi, không phân tích câu chữ của message.
Không gom toàn bộ mã nghiệp vụ của mọi service vào một enum chung.

## Đánh đổi được chấp nhận

- Ít thành phần và luồng xử lý dễ theo dõi; dễ kiểm tra bằng test HTTP.
- Service cần tuân theo hệ exception nền và cách phân loại thống nhất.
- Chưa tối ưu cho việc tiếp nhận nhiều hệ exception bên ngoài hoặc domain độc lập
  hoàn toàn với nền tảng ứng dụng. Chấp nhận giới hạn này cho phạm vi hiện tại.
- Không tạo một exception subclass cho từng câu thông báo nếu nhóm exception nền
  và message đã biểu diễn đủ nhu cầu.

## Hướng mở rộng 1: resolver và các bộ chuyển đổi

**Nhu cầu kích hoạt:** nhiều SDK, thư viện hoặc nhóm tích hợp cung cấp các kiểu
exception không sửa được, và cần bổ sung mapping độc lập mà không liên tục sửa
handler trung tâm.

Ví dụ Payment tích hợp nhiều nhà cung cấp:

```text
BankAException(BALANCE_LOW)  → PAYMENT_INSUFFICIENT_FUNDS
WalletBException(LOCKED)    → PAYMENT_ACCOUNT_UNAVAILABLE
```

Khi số lượng hoặc sự độc lập của các tích hợp thực sự tạo chi phí bảo trì, có thể
đăng ký các bộ chuyển đổi và dùng resolver chọn bộ phù hợp. Bước dựng HTTP response
cuối cùng vẫn cần thống nhất. Một vài exception bên ngoài có thể được xử lý bằng
mapping trực tiếp; sự hiện diện của SDK không tự động đòi hỏi resolver.

Trước khi mở rộng, xác định thứ tự ưu tiên, cách xử lý nhiều bộ cùng khớp, fallback
và test việc chọn bộ chuyển đổi. Resolver chỉ hỗ trợ chuyển lỗi; không quyết định
retry, fallback nghiệp vụ hay transaction.

**Lịch sử triển khai:** resolver và hệ handler của bản MB Bank đã được thay bằng
một `ExceptionControllerAdvice` dùng exception nền. Hai thư mục `*-mau` vẫn là
source tham khảo, không nằm trong reactor. Nếu nhu cầu mở rộng nêu trên xuất hiện,
có thể bổ sung lại cơ chế chọn bộ chuyển đổi với quy tắc ưu tiên và test rõ ràng.

## Hướng mở rộng 2: mapping tại từng ranh giới giao tiếp

**Nhu cầu kích hoạt:** cùng một use case thực sự được dùng qua nhiều giao tiếp như
REST, Kafka và batch job, khiến exception nghiệp vụ cần độc lập với HTTP.

Ví dụ cùng quy tắc kiểm tra tồn kho ném `OutOfStockException`:

```text
OutOfStockException
  ├→ REST adapter: HTTP 409 + mã lỗi PRODUCT_OUT_OF_STOCK
  └→ Kafka consumer: ghi nhận từ chối và phát sự kiện theo thiết kế use case
```

Domain chỉ biểu diễn lỗi nghiệp vụ. Adapter HTTP chuyển lỗi sang response;
consumer hoặc ứng dụng quyết định kết quả xử lý message theo chính sách riêng.
Việc repo có Kafka chưa đủ để chọn hướng này: cần có cùng use case được dùng qua
nhiều ranh giới, hoặc nhu cầu tách domain khỏi HTTP đã được chứng minh.

Đánh đổi là thêm code mapping và test tại từng ranh giới. Không tạo lớp mapping
cho mọi exception trước khi nhu cầu này xuất hiện.

Hai hướng mở rộng giải quyết hai vấn đề khác nhau và có thể kết hợp khi cần;
không phải các bước nâng cấp bắt buộc theo thời gian hoặc kích thước project.

## Contract response đã chọn

Chủ project chọn envelope riêng theo mẫu MB Bank, độc lập với lựa chọn về cách
phân loại exception. Quyết định này thay thế định dạng Problem Details trước đây
cho các lỗi do handler MVC chung của bốn service nêu trên xử lý.

Đối với lỗi nghiệp vụ 4xx, service có thể dùng `.errorCode("PRODUCT_OUT_OF_STOCK")` và chỉ đưa dữ liệu an toàn vào `.extraData(...)`. Caller đọc mã lỗi, chi tiết validation và request ID theo [contract lỗi MVC](api-conventions.md#error-envelope).

`WebModuleConfig` import trực tiếp handler chung, không component-scan toàn module
và không tự bật CORS hoặc ModelMapper. Handler kế thừa xử lý lỗi MVC của Spring,
chuyển body sang envelope và giữ các header như `Allow` của lỗi 405. Status của
exception nền được tìm cả trên superclass để exception service kế thừa đúng nhóm.

Phạm vi này chưa đổi response được tạo trực tiếp bởi Spring Security, gateway
hoặc tầng hạ tầng trước khi request tới MVC. Các ranh giới đó giữ cấu hình hiện tại;
không thêm dependency security vào `web-module` chỉ để đồng nhất response. Nếu cần
đồng nhất toàn bộ các ranh giới, đó là bước triển khai riêng cần xác định phạm vi.

## Kiểm tra triển khai

Các yêu cầu kiểm tra khi thay đổi handler chung:

- Bật module rõ ràng; không kéo theo thay đổi CORS hoặc security ngoài phạm vi.
- Chuyển các exception nghiệp vụ hiện tại sang hệ phân loại đã chọn và loại bỏ
  handler kỹ thuật bị lặp sau khi đã có coverage tương đương.
- Kiểm tra 400/404/409/500, validation, malformed JSON, lỗi 405 cùng header `Allow`,
  error dispatch chỉ có status và các lỗi ở security boundary khi có liên quan.
- Kiểm tra response không lộ rejected value hoặc thông tin nội bộ và log không
  bị lặp ở nhiều tầng.
- Bảo toàn các dependency foundation đã được ghi nhận trong
  [quy tắc kiến trúc](architecture-rules.md).

Mọi mở rộng material sau này cần được chủ project chọn theo quy trình quyết định
trong quy tắc kiến trúc; ghi lại nhu cầu thực tế và đánh đổi trước khi triển khai.
