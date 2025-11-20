# 🚀 Hướng dẫn chạy chương trình LumiScan

## 📋 Yêu cầu hệ thống

- **Java 17+** (kiểm tra: `java -version`)
- **MySQL 8.0+** (hoặc MariaDB)
- **Maven 3.6+** (đã có sẵn trong project: `mvnw`)

---

## 🔧 Bước 1: Cài đặt Database

### 1.1. Tạo database MySQL

Mở MySQL Command Line hoặc MySQL Workbench và chạy:

```sql
CREATE DATABASE IF NOT EXISTS lumiscan;
```

**Lưu ý:** Tên database phải là `lumiscan` (chữ thường) theo cấu hình trong `application.properties`

### 1.2. Import dữ liệu (tùy chọn)

Nếu bạn có file `lumiscan.sql`, import vào database:

```bash
mysql -u root -p lumiscan < lumiscan.sql
```

Hoặc trong MySQL Workbench: File → Run SQL Script → chọn file `lumiscan.sql`

---

## ⚙️ Bước 2: Cấu hình Database

Mở file `src/main/resources/application.properties` và kiểm tra:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/lumiscan?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=        # Điền mật khẩu MySQL của bạn (nếu có)
```

**Nếu MySQL của bạn có mật khẩu**, sửa dòng `spring.datasource.password=` thành:
```properties
spring.datasource.password=your_password
```

---

## 🏃 Bước 3: Chạy ứng dụng

### Cách 1: Sử dụng Maven Wrapper (Khuyến nghị)

**Trên Windows:**
```bash
.\mvnw.cmd spring-boot:run
```

**Trên Linux/Mac:**
```bash
./mvnw spring-boot:run
```

### Cách 2: Sử dụng IDE (IntelliJ IDEA / Eclipse)

1. Mở project trong IDE
2. Tìm file `LumiApplication.java`
3. Click chuột phải → Run `LumiApplication.main()`

### Cách 3: Build JAR và chạy

```bash
# Build
.\mvnw.cmd clean package

# Chạy JAR
java -jar target/Lumi-0.0.1-SNAPSHOT.jar
```

---

## 🌐 Bước 4: Truy cập ứng dụng

Sau khi chạy thành công, bạn sẽ thấy log:
```
Started LumiApplication in X.XXX seconds
```

Truy cập các URL sau:

- **Trang chủ**: http://localhost:8080
- **Đăng nhập**: http://localhost:8080/login
- **Dashboard**: http://localhost:8080/dashboard
- **Admin Tables**: http://localhost:8080/admin/tables

---

## 🔐 Tài khoản mặc định

Theo README, hệ thống sẽ tự động tạo các tài khoản:

| Role | Username | Password | Quyền hạn |
|------|----------|----------|-----------|
| **Admin** | `admin` | `admin123` | Quản lý toàn bộ hệ thống |
| **Employee** | `employee` | `emp123` | Quản lý bàn, xem đơn hàng |

**⚠️ Lưu ý:** Nếu chưa có user, bạn cần tạo user ADMIN trước khi truy cập `/admin/tables`

---

## 🐛 Xử lý lỗi 500 tại `/admin/tables`

### Nguyên nhân phổ biến:

#### 1. **Chưa đăng nhập với quyền ADMIN**

**Triệu chứng:** Lỗi 403 Forbidden hoặc redirect về `/login`

**Giải pháp:**
- Đăng nhập với tài khoản ADMIN: `admin` / `admin123`
- Kiểm tra trong database xem user có role `ADMIN` không:

```sql
SELECT * FROM user WHERE username = 'admin';
-- Kiểm tra cột `role` phải là 'ADMIN'
```

#### 2. **Lỗi kết nối Database**

**Triệu chứng:** Lỗi trong console:
```
Cannot create PoolableConnectionFactory
Communications link failure
```

**Giải pháp:**
- Kiểm tra MySQL đang chạy: `mysql -u root -p`
- Kiểm tra database `lumiscan` đã tồn tại chưa
- Kiểm tra username/password trong `application.properties`
- Kiểm tra port MySQL (mặc định 3306)

#### 3. **Database chưa có bảng `tables`**

**Triệu chứng:** Lỗi SQL trong console:
```
Table 'lumiscan.tables' doesn't exist
```

**Giải pháp:**
- Hibernate sẽ tự động tạo bảng nếu `spring.jpa.hibernate.ddl-auto=update`
- Hoặc import file `lumiscan.sql` để tạo sẵn bảng

#### 4. **Exception trong TableService**

**Triệu chứng:** Lỗi trong console có stack trace

**Giải pháp:**
- Xem log chi tiết trong console để biết lỗi cụ thể
- Kiểm tra method `findAllTables()` trong `TableService.java`
- Kiểm tra `TableRepository` có hoạt động đúng không

---

## 🔍 Cách Debug lỗi 500

### 1. Xem log chi tiết

Trong console khi chạy ứng dụng, tìm dòng có:
```
ERROR com.example.Lumi.controller.TableController - Error in listTables:
```

Hoặc xem toàn bộ exception stack trace.

### 2. Kiểm tra Database connection

Thêm vào `application.properties` để xem SQL queries:
```properties
spring.jpa.show-sql=true
logging.level.org.hibernate.SQL=DEBUG
```

### 3. Test kết nối database thủ công

Tạo file test đơn giản hoặc dùng MySQL Workbench để kiểm tra:
```sql
USE lumiscan;
SELECT * FROM tables;
```

### 4. Kiểm tra Security Config

Đảm bảo bạn đã đăng nhập với role ADMIN. Kiểm tra trong `SecurityConfig.java`:
- Route `/admin/tables` yêu cầu `hasAuthority("ROLE_ADMIN")`
- User phải có role `ADMIN` trong database

---

## ✅ Checklist trước khi chạy

- [ ] Java 17+ đã cài đặt (`java -version`)
- [ ] MySQL đang chạy
- [ ] Database `lumiscan` đã được tạo
- [ ] Cấu hình `application.properties` đúng (username, password)
- [ ] User ADMIN đã tồn tại trong database
- [ ] Port 8080 không bị chiếm dụng

---

## 📞 Nếu vẫn gặp lỗi

1. **Xem log đầy đủ** trong console khi chạy ứng dụng
2. **Kiểm tra database** bằng MySQL Workbench hoặc command line
3. **Kiểm tra Security** - đảm bảo đã đăng nhập với role ADMIN
4. **Kiểm tra Network** - đảm bảo MySQL đang lắng nghe trên port 3306

---

## 🎯 Quick Start (Tóm tắt)

```bash
# 1. Tạo database
mysql -u root -p
CREATE DATABASE lumiscan;

# 2. Cấu hình application.properties (sửa password nếu cần)

# 3. Chạy ứng dụng
.\mvnw.cmd spring-boot:run

# 4. Truy cập và đăng nhập
# http://localhost:8080/login
# Username: admin
# Password: admin123

# 5. Truy cập admin tables
# http://localhost:8080/admin/tables
```

---

**Chúc bạn chạy thành công! 🎉**

