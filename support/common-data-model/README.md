# Common data model

Thư viện nền cho JPA; product-service là service áp dụng thử.

- Entity cụ thể kế thừa `BaseEntity`, giữ ID riêng. Soft delete dùng `markDeleted()`
  trong transaction rồi save để cập nhật auditing; entity cần cấu hình SQL restriction
  và SQL delete như Product. Không dùng bulk delete/native delete cho soft delete.
- Product tự loại bản ghi đã xóa khi tìm ID, phân trang và purchase. Không có API restore.
- Created/updated user lưu String. Product dùng Spring Security principal name (JWT sub),
  fallback `system` khi không có authentication. Không tin header user ID từ client.
- Product xác thực JWT cho request ghi; issuer/JWK endpoint cấu hình bằng
  `JWT_ISSUER_URI` và `JWT_JWK_SET_URI`. GET sản phẩm vẫn public.
- Filter hỗ trợ Eq, Ne, Isnull, In, Notin, Lk, Gt, Gte, Lt, Lte.
  Các field trong một filter kết hợp AND, nhiều filter kết hợp OR;
  luôn loại deletedAt. Field không hợp lệ báo lỗi thay vì âm thầm bỏ qua.
- Lk dùng LOWER/LIKE, tìm substring không phân biệt hoa thường, escape `%`, `_`, `\`.
  Có phân biệt dấu; không yêu cầu custom function hay extension PostgreSQL.
- Envers chưa bật trên BaseEntity. Các lớp revision được giữ để phát triển sau.
- DELETE `/api/v1/products/{id}` thực hiện soft delete; GET danh sách hỗ trợ `name`.
- ProductService triển khai BaseService: GET chi tiết trả Product, GET danh sách trả
  PageResponse<Product>. Category nằm trong object `category`, không còn các field
  categoryId/categoryName/categoryDescription dạng phẳng của ProductResponse.
  `/api/v1/products/{id}/entity` là alias của GET chi tiết.
- BaseService cung cấp findAll(Pageable), findAllAndMap(Pageable, mapper), filter và
  softDelete(ID). Purchase giữ query khóa PESSIMISTIC_WRITE riêng để bảo vệ tồn kho.

Kiểm tra với Docker đang chạy:

```sh
./services/product/mvnw -f pom.xml -pl services/product -am verify
```

ProductPostgresTest dùng PostgreSQL 16 Testcontainers và Flyway migrations thực tế.
Không tự skip integration test khi thiếu Docker; CI phải có Docker.
