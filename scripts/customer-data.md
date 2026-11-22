# Clear và seed Customer local

Chạy từ thư mục gốc repository, MongoDB trong Docker Compose phải đang chạy.
Dừng Customer trước khi clear/seed. Các lệnh dưới đây dùng tài khoản local
mà Compose nạp từ file `.env` ở thư mục gốc.

## 1. Clear dữ liệu cũ

Lệnh này xóa vĩnh viễn **toàn bộ document**, kể cả soft-delete, trong
`customer.customer`. Order cũ có thể vẫn tham chiếu ID Customer đã xóa.
Không chạy nếu cần giữ các customer hiện tại. Không xóa database, index,
collection lịch sử hoặc lock của Mongock.

```sh
docker compose exec mongodb sh -c 'exec mongosh --quiet --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin customer "$@"' sh --eval 'db.customer.deleteMany({})'
```

## 2. Nạp 100 customer mẫu

```sh
docker compose exec -T mongodb sh -c 'exec mongosh --quiet --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin customer "$@"' sh < scripts/seed_customers.js
```

Script tạo unique index email giống migration, rồi upsert 100 email từ
`customer001@example.com` đến `customer100@example.com`. MongoDB tự cấp ObjectId;
Spring Data đọc ID này dưới dạng String. Có địa chỉ, audit timestamps và trạng
thái chưa xóa. Dữ liệu là giả lập.

Chạy lại seed không ghi đè customer đã có cùng email, kể cả customer soft-delete.
Nếu chưa clear và còn email trùng, tạo index sẽ thất bại trước khi nạp.
Seed không có transaction bao trùm; nếu bị ngắt có thể chạy lại để nạp phần còn
thiếu. Không chạy đồng thời với tiến trình ghi Customer.

## 3. Kiểm tra và khởi động lại

```sh
docker compose exec mongodb sh -c 'exec mongosh --quiet --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin customer "$@"' sh --eval 'printjson({count: db.customer.countDocuments({}), indexes: db.customer.getIndexes()})'
```

Sau clear + seed thành công, count phải là 100 và có unique index
`uk_customer_email`. Khởi động lại Customer; Mongock sẽ dùng lại index tương
thích này. Không cần xóa lịch sử migration.
