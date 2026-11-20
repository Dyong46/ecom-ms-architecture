# Nạp dữ liệu Product từ JSON

`import_products.py` đọc JSON và gọi API Product để tạo SPU/SKU. Script dùng thư
viện chuẩn Python 3, không cần `pip install`, không ghi SQL hoặc tự cấp UUID.

## Dữ liệu mẫu

File [`data/products.json`](data/products.json) có **50 Product và 323 SKU**:

| Nhóm | Product | Thuộc tính biến thể |
| --- | ---: | --- |
| Áo nam | 10 | Màu sắc, chất liệu, size |
| Áo nữ | 10 | Màu sắc, chất liệu, size |
| Điện thoại | 8 | Màu sắc, dung lượng, RAM |
| Laptop | 8 | Màu sắc, RAM, SSD |
| Bàn phím | 7 | Màu sắc, switch, kết nối |
| Màn hình | 7 | Màu sắc, kích thước, tần số quét |

Mỗi Product có 3–10 SKU với tổ hợp khác nhau. Tồn kho là số nguyên từ 1 đến 100.
Tên, cấu hình và giá là dữ liệu mô phỏng; giá minh họa bằng VND. Slug có tiền tố
`demo-`, mã SKU có tiền tố `DEMO-`.

`group` chỉ là thông tin phân nhóm trong file và báo cáo; không được gửi sang API
vì model Product hiện tại chưa có category. UUID của Product, SKU, Option và
OptionValue được service sinh khi tạo dữ liệu.

## Kiểm tra JSON trước khi nạp

Chạy từ thư mục gốc project:

```sh
python3 scripts/import_products.py --dry-run
```

Hoặc chỉ định file JSON khác:

```sh
python3 scripts/import_products.py /duong/dan/products.json --dry-run
```

Dry run kiểm tra toàn bộ file và in thống kê, không cần token và không gọi HTTP.
Script kiểm tra slug/mã SKU duy nhất, 3–10 biến thể mỗi Product, tổ hợp không trùng,
cùng bộ thuộc tính trong mỗi SPU, giá hợp lệ với decimal(12,2), tồn kho 1–100 và
JSON không có key bị lặp. Không bắt buộc file tùy chỉnh phải có đúng 50 Product.

## Nạp qua API

1. Database Product phải được khởi tạo theo schema SPU/SKU mới. Nếu còn database
   cũ, làm theo phần khởi tạo trong [thiết kế catalog](../docs/product-catalog-design.md).
2. Khởi động Product và các dependency của nó, bao gồm PostgreSQL, Config Server
   và Elasticsearch. URL mặc định của importer là `http://localhost:8050`.
3. Chuẩn bị Keycloak access token có quyền gọi API ghi Product/Option.
4. Đọc token vào biến môi trường: chạy `read` bên dưới, dán token rồi nhấn Enter.
   Token không hiển thị khi nhập. Không thêm tiền tố `Bearer` vào giá trị token.

```sh
read -r -s PRODUCT_API_TOKEN
export PRODUCT_API_TOKEN
python3 scripts/import_products.py scripts/data/products.json
unset PRODUCT_API_TOKEN
```

Nếu token đã được cấp vào môi trường thì chỉ cần chạy lệnh Python. Token chỉ được
gửi trong Authorization header, không được ghi vào file JSON hoặc in trong log.

Để đi qua Gateway đang chạy ở port 8222:

```sh
python3 scripts/import_products.py scripts/data/products.json --base-url http://localhost:8222
```

Có thể đổi timeout HTTP, mặc định 30 giây cho mỗi request:

```sh
python3 scripts/import_products.py --timeout 60
```

Script thực hiện tuần tự:

1. Kiểm tra toàn bộ JSON trước khi gửi request.
2. Đọc các trang Product hiện có để kiểm tra xung đột slug/mã SKU trước khi ghi.
3. Đọc các trang Option/OptionValue hiện có và dùng lại UUID theo tên chính xác.
4. Tạo Option/OptionValue còn thiếu.
5. Chuyển `options` thành `optionValueIds` và POST mỗi Product cùng toàn bộ SKU
   trong một request. Các request giữ nguyên cơ chế cập nhật search sau commit.

## Chạy lại sau khi gián đoạn

Mặc định importer dừng trước khi ghi nếu có slug hoặc SKU xung đột với dữ liệu
đã tồn tại. Nếu lần chạy trước bị ngắt, lấy token mới nếu cần rồi chạy:

```sh
python3 scripts/import_products.py --skip-existing
```

`--skip-existing` bỏ qua **toàn bộ Product có cùng slug**. Nó không so sánh hay cập
nhật nội dung/biến thể, không ghi đè tồn kho đã thay đổi sau khi mua hàng. SKU trùng
với một Product khác vẫn làm script dừng. Nếu muốn sửa Product đã nạp, đây không
phải chế độ đồng bộ hoặc cập nhật dữ liệu.

Mỗi Product được tạo trong transaction riêng của API, không có transaction bao
trùm toàn bộ file. Khi lỗi, các Product/Option/OptionValue đã tạo vẫn tồn tại.
Importer dừng ở lỗi đầu tiên, không tự retry POST, không tự xóa hoặc rollback dữ
liệu trước đó. Nếu timeout xảy ra sau khi server commit, kiểm tra dữ liệu và dùng
`--skip-existing` để tiếp tục. Không chạy nhiều importer đồng thời: kiểm tra xung
đột trước khi ghi không khóa được các request từ tiến trình khác.

Nếu Elasticsearch lỗi trong lúc nạp, Product có thể vẫn được tạo thành công.
Sau khi Elasticsearch phục hồi, chạy API reindex có xác thực:

```http
POST /api/v1/products/search/reindex
Authorization: Bearer <access-token>
```

Reindex hiện trả số SKU đã nạp vào `products_v2` (323 SKU cho file mẫu), còn
search gộp kết quả theo SPU. Tạm dừng ghi catalog và mua hàng trong lúc reindex theo
[thiết kế search](../docs/product-search-design.md). Importer không tự reindex.

## Định dạng JSON

File chứa object có trường `products`. Mỗi SKU dùng tên option/value, không dùng
UUID cứng. Ví dụ trích một Product (cần tối thiểu 3 SKU trong mỗi Product):

```json
{
  "products": [
    {
      "group": "Áo nam",
      "name": "Áo thun nam demo",
      "slug": "demo-ao-thun-nam",
      "description": "Dữ liệu minh họa",
      "variants": [
        {"sku":"DEMO-AO-01","price":199000,"stockQuantity":10,"options":{"Màu sắc":"Đen","Size":"M"}},
        {"sku":"DEMO-AO-02","price":199000,"stockQuantity":20,"options":{"Màu sắc":"Đen","Size":"L"}},
        {"sku":"DEMO-AO-03","price":209000,"stockQuantity":30,"options":{"Màu sắc":"Trắng","Size":"M"}}
      ]
    }
  ]
}
```

Tên option/value phân biệt hoa thường theo dữ liệu hiện tại. Script tái sử dụng
OptionValue đã có mà không thay đổi synonyms của nó.

## Kiểm thử

```sh
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts -p 'test_*.py' -v
```

Tests kiểm tra dữ liệu, validation trước khi gọi mạng, request HTTP và UUID mapping,
phân trang, tái sử dụng option, xung đột, chạy lại không ghi đè stock, token hết hạn
và tiếp tục sau một lần POST thất bại. HTTP server test chỉ chạy trên loopback với
port ngẫu nhiên; không gọi Product thật hoặc thay đổi database của bạn.

## Checkout idempotency verification

`python3 scripts/test_checkout_idempotency.py` runs the three packaged Java services
against disposable PostgreSQL, injects lost Product/Payment responses after commit,
restarts Order and verifies recovery with no duplicate writes. It requires Docker,
Java 17 and OpenSSL. Build jars first using the command in
[checkout idempotency](../docs/checkout-idempotency-design.md). Existing service
processes and databases are not used or reset.
