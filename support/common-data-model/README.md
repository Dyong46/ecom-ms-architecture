# Common data model

Thư viện nền cho JPA; product-service là service áp dụng thử.

- `BaseEntity` cung cấp metadata auditing và `markDeleted()` cho entity dùng soft delete. Service sở hữu chính sách xóa.
- Product xóa vật lý sản phẩm; PostgreSQL xóa các SKU liên quan theo khóa ngoại cascade. [Thiết kế catalog](../../services/product/product-catalog-design.md)
- Filter hỗ trợ Eq, Ne, Isnull, In, Notin, Lk, Gt, Gte, Lt, Lte.
  Các field trong một filter kết hợp AND, nhiều filter kết hợp OR;
  luôn loại deletedAt. Field không hợp lệ báo lỗi thay vì âm thầm bỏ qua.
- Lk dùng LOWER/LIKE, tìm substring không phân biệt hoa thường, escape `%`, `_`, `\`.
  Có phân biệt dấu; không yêu cầu custom function hay extension PostgreSQL.
- Envers chưa bật trên BaseEntity. Các lớp revision được giữ để phát triển sau.
- `BaseService` cung cấp phân trang, filter và soft delete cho các service sử dụng abstraction này.

Kiểm tra với Docker đang chạy:

```sh
./services/product/mvnw -f pom.xml -pl services/product -am verify
```

ProductPostgresTest dùng PostgreSQL 16 Testcontainers và Flyway migrations thực tế.
Không tự skip integration test khi thiếu Docker; CI phải có Docker.
