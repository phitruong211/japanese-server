# N3 Backend

Spring Boot API cho tài khoản, bộ thẻ nhập và tiến độ Anki của N3, sử dụng MySQL 8.4.

## Chạy local

Yêu cầu Java 21 và Docker. Project đã có Maven Wrapper nên không cần cài Maven toàn máy.

```powershell
docker compose up -d
.\mvnw.cmd spring-boot:run
```

Nếu dùng MySQL cài trực tiếp thay vì Docker, chạy file khởi tạo bằng tài khoản root:

```powershell
mysql -u root -p --execute="source database/create_database.sql"
```

Project đã có Data Source IntelliJ tên `japanese_learning@localhost`. Trong cửa sổ
Database, nhập user `japanese_app`, password `n3_dev_password`, chọn **Save**, rồi
**Test Connection**. Sau khi backend chạy, Flyway tự tạo toàn bộ bảng từ migration.

## Deploy frontend và backend khác server

Backend nhận một hoặc nhiều frontend origin, phân cách bằng dấu phẩy:

```text
CORS_ALLOWED_ORIGINS=https://app.example.com,https://admin.example.com
```

Nếu API cần cho phép mọi website gọi, có thể đặt `CORS_ALLOWED_ORIGINS=*`. Chế độ
này tự tắt credentialed CORS; JWT trong header `Authorization` vẫn hoạt động. Nên
dùng danh sách domain cụ thể cho production.

Frontend phải được build với URL public của backend:

```text
VITE_API_URL=https://api.example.com/api/v1
```

Thông tin kết nối local nằm trong `.env`: database `japanese_learning`, user `japanese_app`,
port `3306`. Spring tự đọc file này qua `spring.config.import`.

API chạy tại `http://localhost:8080`. Frontend Vite mặc định được cho phép từ
`http://localhost:5173`. Sao chép `.env.example` thành `.env` hoặc cấu hình các biến môi trường
trên hệ thống deploy. Bắt buộc thay `JWT_SECRET` trong production.

## Deploy bằng Docker

Repository có `Dockerfile` tại thư mục gốc: build bằng JDK 21, chạy bằng JRE 21
với user không phải root. `.dockerignore` loại bỏ `.env` và các file khóa khỏi build context.

Trên dịch vụ deploy, chọn **Docker**, để **Root Directory** trống và đặt
**Dockerfile Path** là `./Dockerfile`. Không cần nhập Build Command hoặc Start Command.

Thiết lập các biến môi trường trên dịch vụ deploy:

```text
DB_URL=jdbc:mysql://YOUR_MYSQL_HOST:3306/japanese_learning?sslMode=REQUIRED&connectionTimeZone=UTC
DB_USERNAME=YOUR_DATABASE_USER
DB_PASSWORD=YOUR_DATABASE_PASSWORD
JWT_SECRET=YOUR_RANDOM_SECRET_AT_LEAST_32_BYTES
CORS_ALLOWED_ORIGINS=https://YOUR_FRONTEND_DOMAIN
```

Dùng hostname MySQL truy cập được từ server deploy, không dùng `localhost`.
Database phải tồn tại trước; Flyway tự tạo bảng khi ứng dụng khởi động.
Điều chỉnh TLS trong `DB_URL` theo yêu cầu của nhà cung cấp MySQL.
Ứng dụng tự đọc biến `PORT` do nền tảng cấp; mặc định là `8080`.

Build và chạy thủ công (file biến môi trường chỉ được đọc lúc chạy, không nằm trong image):

```sh
docker build -t japanese-server .
docker run --rm -p 8080:8080 --env-file .env japanese-server
```

Nếu thử trên máy cá nhân với MySQL ngoài container, sửa `DB_URL` trong file môi trường
phù hợp; trên Docker Desktop có thể dùng `host.docker.internal` để truy cập máy host.

## Luồng thử nhanh

Đăng ký:

```http
POST /api/v1/auth/register
Content-Type: application/json

{
  "email": "learner@example.com",
  "password": "strong-password",
  "displayName": "N3 Learner",
  "deviceName": "Chrome Windows"
}
```

Gửi `accessToken` nhận được trong các request tiếp theo:

```http
Authorization: Bearer <accessToken>
```

Tạo/import một bộ thẻ trong một request:

```http
POST /api/v1/decks
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "name": "Từ vựng N3 của tôi",
  "sourceType": "IMPORT",
  "sourceName": "n3.csv",
  "importFormat": "CSV",
  "cards": [
    { "front": "猫", "back": "Con mèo", "reading": "ねこ", "kind": "VOCABULARY" }
  ]
}
```

Lấy hàng đợi và ghi nhận kết quả Anki:

```http
GET /api/v1/anki/due?deckId=<deckId>&limit=50

POST /api/v1/anki/cards/<cardId>/reviews
Content-Type: application/json

{ "rating": "GOOD", "responseTimeMs": 2200 }
```

Các rating: `AGAIN`, `HARD`, `GOOD`, `EASY`. Migration Flyway nằm tại
`src/main/resources/db/migration` và Hibernate chạy ở chế độ `validate`, không tự ý sửa schema.
