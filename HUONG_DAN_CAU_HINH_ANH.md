# Hướng dẫn cấu hình hiển thị ảnh từ folder uploads

## Vấn đề
Ảnh từ folder `uploads` không hiển thị được trên web.

## Nguyên nhân
1. **SecurityConfig** chưa cho phép truy cập công khai `/uploads/**`
2. **WebConfig** đã cấu hình nhưng cần kiểm tra path

## Cách fix đã thực hiện

### 1. Sửa SecurityConfig.java
Thêm `/uploads/**` vào danh sách `permitAll()`:

```java
.requestMatchers(
    "/", "/home", "/login", "/users/register", "/error",
    "/static/**", "/css/**", "/js/**", "/images/**", 
    "/qrcodes/**", "/uploads/**",  // ← Đã thêm dòng này
    "/webjars/**", "/favicon.ico"
).permitAll()
```

### 2. Kiểm tra WebConfig.java
Đảm bảo có cấu hình:

```java
String uploadPath = System.getProperty("user.dir") + "/uploads/";
registry.addResourceHandler("/uploads/**")
        .addResourceLocations("file:" + uploadPath);
```

### 3. Cấu trúc thư mục
Đảm bảo có folder `uploads` ở root project:
```
Lumi/
├── uploads/
│   └── table_in_use.png  ← File ảnh ở đây
├── src/
└── pom.xml
```

### 4. Cách sử dụng trong template
Sử dụng Thymeleaf `@{}` để tạo URL đúng:

```html
<!-- Đúng -->
<img th:src="@{/uploads/table_in_use.png}" alt="...">

<!-- Hoặc -->
<img src="/uploads/table_in_use.png" alt="...">
```

## Kiểm tra

1. **Kiểm tra folder tồn tại:**
   - Đường dẫn: `D:\WORKSPACE\Java_project\Lumi\uploads\`
   - File: `table_in_use.png` phải có trong folder này

2. **Kiểm tra URL:**
   - Mở browser: `http://localhost:8080/uploads/table_in_use.png`
   - Nếu thấy ảnh = OK
   - Nếu 404 = kiểm tra lại cấu hình

3. **Kiểm tra log:**
   - Khi start server, xem log có dòng: `=== WebConfig: Upload path = ... ===`
   - Đảm bảo path đúng

## Lưu ý

- **Path tuyệt đối:** `System.getProperty("user.dir")` trả về thư mục gốc project
- **Security:** Phải có `/uploads/**` trong `permitAll()` để truy cập công khai
- **Cache:** Đã set cache 1 giờ để tối ưu performance

## Troubleshooting

### Nếu vẫn không hiển thị:

1. **Kiểm tra file có tồn tại:**
   ```bash
   # Windows
   dir D:\WORKSPACE\Java_project\Lumi\uploads\table_in_use.png
   ```

2. **Kiểm tra quyền truy cập:**
   - Đảm bảo file có quyền đọc

3. **Kiểm tra log server:**
   - Xem có lỗi 404 không
   - Xem path trong log có đúng không

4. **Thử URL trực tiếp:**
   - `http://localhost:8080/uploads/table_in_use.png`
   - Nếu không được = vấn đề cấu hình
   - Nếu được = vấn đề template

