# 🍽️ LumiScan - Hệ thống quản lý nhà hàng thông minh

## 📋 Tổng quan

LumiScan là hệ thống quản lý nhà hàng hiện đại với các tính năng:
- **Quét QR code** để xem menu và đặt món
- **Phân quyền người dùng** (Admin, Employee, Customer)
- **Quản lý bàn** và sinh mã QR tự động
- **Dashboard** thống kê real-time

## 🚀 Tính năng đã hoàn thành

### ✅ **Hệ thống Authentication & Authorization**
- Đăng nhập với Spring Security
- Phân quyền theo role (ADMIN, EMPLOYEE, CUSTOMER)
- Mã hóa password với BCrypt
- Tự động tạo user mặc định

### ✅ **Quản lý bàn (Table Management)**
- **Admin**: Tạo bàn mới, sinh QR code tự động
- **Employee**: Bật/tắt trạng thái bàn
- Hiển thị danh sách bàn với trạng thái real-time
- Dashboard thống kê số bàn

### ✅ **Giao diện người dùng**
- **Trang chủ**: Quét QR code hoặc nhập số bàn thủ công
- **Login**: Giao diện đăng nhập đẹp với Tailwind CSS
- **Dashboard**: Phân quyền hiển thị chức năng theo role
- **Responsive**: Tương thích mobile và desktop

## 🔐 Tài khoản mặc định

Khi chạy lần đầu, hệ thống sẽ tự động tạo:

| Role | Username | Password | Quyền hạn |
|------|----------|----------|-----------|
| **Admin** | `admin` | `admin123` | Tạo bàn, quản lý toàn bộ hệ thống |
| **Employee** | `employee` | `emp123` | Bật/tắt bàn, xem đơn hàng |

## 🛠️ Cài đặt và chạy

### Yêu cầu hệ thống
- Java 17+
- MySQL 8.0+
- Maven 3.6+

### Bước 1: Cài đặt database
```sql
CREATE DATABASE LumiScan;
```

### Bước 2: Cấu hình database
Chỉnh sửa `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/LumiScan?useSSL=false&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=your_password
```

### Bước 3: Chạy ứng dụng
```bash
# Windows
./mvnw spring-boot:run

# Linux/Mac
./mvnw spring-boot:run
```

### Bước 4: Truy cập ứng dụng
- **Trang chủ**: http://localhost:8080
- **Đăng nhập**: http://localhost:8080/login
- **Dashboard**: http://localhost:8080/dashboard

## 📱 Hướng dẫn sử dụng

### 👥 **Khách hàng (Chưa đăng nhập)**
1. Truy cập trang chủ
2. Quét QR code trên bàn hoặc nhập số bàn
3. Xem menu và đặt món (sẽ implement tiếp)

### 👨‍💼 **Admin**
1. Đăng nhập với `admin/admin123`
2. **Tạo bàn mới**: Dashboard → "Thêm bàn mới"
3. **Quản lý bàn**: Dashboard → "Quản lý bàn"
4. Xem thống kê tổng quan

### 👨‍💻 **Employee**
1. Đăng nhập với `employee/emp123`
2. **Quản lý bàn**: Dashboard → "Quản lý bàn"
3. **Bật/tắt bàn**: Click nút "Bật/Tắt" trong danh sách bàn
4. Xem đơn hàng (sẽ implement tiếp)

## 🏗️ Cấu trúc project

```
src/main/java/com/example/Lumi/
├── config/                 # Cấu hình Spring Security
├── controller/             # REST Controllers
│   ├── AuthController.java
│   ├── HomeController.java
│   ├── TableController.java
│   └── UserController.java
├── model/                  # JPA Entities
│   ├── User.java
│   ├── Employee.java
│   ├── TableEntity.java
│   ├── MenuItem.java
│   ├── Order.java
│   ├── OrderItem.java
│   ├── CheckInOut.java
│   └── Payment.java
├── repository/             # JPA Repositories
├── service/                # Business Logic
│   ├── UserService.java
│   ├── TableService.java
│   ├── QrCodeService.java
│   └── CustomUserDetailsService.java
└── LumiApplication.java

src/main/resources/
├── templates/              # Thymeleaf Templates
│   ├── index.html         # Trang chủ
│   ├── login.html         # Đăng nhập
│   ├── dashboard.html     # Dashboard
│   ├── register.html      # Đăng ký
│   └── tables/            # Quản lý bàn
└── static/qrcodes/        # QR Code images
```

## 🔄 Workflow hiện tại

### 1. **Khách hàng**
```
Trang chủ → Quét QR/Nhập số bàn → [Sẽ implement: Menu & Order]
```

### 2. **Admin**
```
Login → Dashboard → Tạo bàn → Sinh QR → Quản lý bàn
```

### 3. **Employee**
```
Login → Dashboard → Quản lý bàn → Bật/Tắt trạng thái bàn
```

## 🎯 Tính năng sắp tới

### 📋 **Menu & Order Management**
- [ ] CRUD món ăn
- [ ] Hiển thị menu cho khách
- [ ] Đặt món và quản lý giỏ hàng
- [ ] Xử lý đơn hàng cho nhân viên

### 📊 **Thống kê & Báo cáo**
- [ ] Thống kê doanh thu
- [ ] Báo cáo giờ làm nhân viên
- [ ] Phân tích đơn hàng

### 🔔 **Real-time Features**
- [ ] Thông báo đơn hàng mới
- [ ] WebSocket cho real-time updates
- [ ] Push notifications

### 📱 **Mobile Features**
- [ ] Check-in/Check-out với GPS
- [ ] Chụp ảnh điểm danh
- [ ] PWA support

## 🐛 Troubleshooting

### Lỗi kết nối database
```bash
# Kiểm tra MySQL đang chạy
mysql -u root -p

# Tạo database nếu chưa có
CREATE DATABASE LumiScan;
```

### Lỗi compile
```bash
# Clean và compile lại
./mvnw clean compile

# Chạy với debug
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
```

## 📞 Hỗ trợ

Nếu gặp vấn đề, hãy kiểm tra:
1. Java version (cần Java 17+)
2. MySQL đang chạy và có database `LumiScan`
3. Port 8080 không bị chiếm dụng
4. Logs trong console để xem lỗi chi tiết

---

**LumiScan** - Hệ thống quản lý nhà hàng thông minh 🍽️✨
