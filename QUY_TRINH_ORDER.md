# Quy Trình Order và Cấu Trúc Database

## 📋 Tổng Quan Quy Trình

### Quy trình mong muốn:
1. **Khách hàng quét QR code** → Hiển thị menu của bàn đó
2. **Chọn món và thêm vào giỏ hàng** → Lưu vào database
3. **Người khác quét cùng QR code** → Vẫn thấy được các món đang order của bàn đó
4. **Cập nhật món ăn** → Cập nhật order hiện tại, KHÔNG tạo order mới

---

## 🗄️ Cấu Trúc Database

### 1. Bảng `tables` (Bàn)
```sql
CREATE TABLE `tables` (
  `id` bigint(20) NOT NULL,
  `qr_code` varchar(255) DEFAULT NULL,      -- Đường dẫn đến file QR code
  `status` varchar(255) NOT NULL,          -- 'available' hoặc 'occupied'
  `table_number` varchar(255) NOT NULL      -- Số bàn (unique)
)
```

**Mối quan hệ:**
- Một bàn có thể có nhiều orders (qua `table_id` trong bảng `orders`)

---

### 2. Bảng `orders` (Đơn hàng)
```sql
CREATE TABLE `orders` (
  `id` bigint(20) NOT NULL,
  `table_id` bigint(20) NOT NULL,          -- Foreign key đến bảng `tables`
  `status` enum('pending','preparing','served','done','paid') DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL
)
```

**Mối quan hệ:**
- Một order thuộc về 1 bàn (`table_id`)
- Một order có nhiều order_items (qua `order_id` trong bảng `order_items`)

**Trạng thái (Status):**
- `pending`: Đơn hàng mới, khách đang chọn món
- `preparing`: Nhà hàng đang chuẩn bị món
- `served`: Món đã được phục vụ
- `done`: Hoàn thành
- `paid`: Đã thanh toán

---

### 3. Bảng `order_items` (Chi tiết món trong đơn hàng)
```sql
CREATE TABLE `order_items` (
  `id` bigint(20) NOT NULL,
  `order_id` bigint(20) NOT NULL,          -- Foreign key đến bảng `orders`
  `menu_item_id` bigint(20) NOT NULL,     -- Foreign key đến bảng `menu_items`
  `quantity` int(11) NOT NULL,             -- Số lượng món
  `price` decimal(10,2) NOT NULL           -- Giá tại thời điểm đặt (để tránh thay đổi giá sau này)
)
```

**Mối quan hệ:**
- Một order_item thuộc về 1 order (`order_id`)
- Một order_item thuộc về 1 menu_item (`menu_item_id`)

---

## 🔄 Quy Trình Hoạt Động

### **Bước 1: Khách hàng quét QR code**
```
QR Code → Table Number → GET /order/{tableNumber}
```

**Backend xử lý:**
1. Tìm bàn theo `tableNumber`
2. Kiểm tra xem bàn có order hiện tại không:
   ```java
   Optional<Order> currentOrder = orderService.getCurrentOrderByTable(tableNumber);
   ```
   - Tìm order mới nhất của bàn (`findTopByTableIdOrderByIdDesc`)
   - Kiểm tra order có status = `pending` và có items
3. Nếu có order hiện tại → Load các món vào giỏ hàng
4. Nếu không có → Giỏ hàng trống

**Database query:**
```sql
SELECT o.* FROM orders o 
WHERE o.table_id = ? 
  AND o.status = 'pending'
ORDER BY o.id DESC 
LIMIT 1;

SELECT oi.* FROM order_items oi 
WHERE oi.order_id = ?;
```

---

### **Bước 2: Khách hàng chọn món và thêm vào giỏ hàng**
```
Frontend: Thêm món vào cart (JavaScript object)
→ Chưa gửi lên server (chỉ lưu trong bộ nhớ trình duyệt)
```

**Lưu ý:** Giỏ hàng chỉ là JavaScript object, chưa lưu vào database.

---

### **Bước 3: Khách hàng nhấn "Đặt món"**

#### **Trường hợp A: Chưa có order (lần đầu đặt)**
```
POST /order/submit
{
  "tableNumber": "1",
  "items": {
    "1": 2,  // menu_item_id: quantity
    "3": 1
  }
}
```

**Backend xử lý:**
1. Kiểm tra bàn có order hiện tại không → Không có
2. Tạo order mới:
   ```java
   Order order = Order.builder()
       .table(table)
       .status(Order.Status.pending)
       .createdAt(LocalDateTime.now())
       .build();
   orderRepository.save(order);
   ```
3. Tạo các order_items:
   ```java
   for (OrderItemRequest req : orderItemRequests) {
       OrderItem item = OrderItem.builder()
           .order(order)
           .menuItem(menuItem)
           .quantity(req.getQuantity())
           .price(menuItem.getPrice())
           .build();
       orderItemRepository.save(item);
   }
   ```
4. Cập nhật trạng thái bàn: `table.status = "occupied"`

**Database:**
```sql
-- Tạo order
INSERT INTO orders (table_id, status, created_at, updated_at) 
VALUES (1, 'pending', NOW(), NOW());

-- Tạo order_items
INSERT INTO order_items (order_id, menu_item_id, quantity, price) 
VALUES 
  (1, 1, 2, 50000.00),
  (1, 3, 1, 75000.00);
```

---

#### **Trường hợp B: Đã có order (cập nhật món)**
```
POST /order/update-items
{
  "orderId": 1,
  "items": [
    {"menuItemId": 1, "quantity": 3},  // Tăng từ 2 lên 3
    {"menuItemId": 5, "quantity": 1}    // Thêm món mới
  ]
}
```

**Backend xử lý:**
1. Tìm order theo `orderId`
2. Kiểm tra order có status = `pending` (chỉ cho phép cập nhật khi pending)
3. **Xóa tất cả order_items cũ:**
   ```java
   orderItemRepository.deleteByOrder(order);
   ```
4. **Tạo lại order_items mới:**
   ```java
   for (OrderItemRequest itemReq : request.getItems()) {
       OrderItem item = OrderItem.builder()
           .order(order)
           .menuItem(menuItem)
           .quantity(itemReq.getQuantity())
           .price(menuItem.getPrice())
           .build();
       orderItemRepository.save(item);
   }
   ```
5. Cập nhật `updated_at`

**Database:**
```sql
-- Xóa order_items cũ
DELETE FROM order_items WHERE order_id = 1;

-- Tạo lại order_items mới
INSERT INTO order_items (order_id, menu_item_id, quantity, price) 
VALUES 
  (1, 1, 3, 50000.00),  -- Cập nhật số lượng
  (1, 5, 1, 90000.00); -- Thêm món mới
```

---

#### **Trường hợp C: Đã có order nhưng status = 'preparing' hoặc 'served' (thêm món mới)**
```
POST /order/submit
{
  "tableNumber": "1",
  "items": {
    "7": 2  // Chỉ gửi món mới muốn thêm
  }
}
```

**Backend xử lý:**
1. Tìm order hiện tại → Có order với status = 'preparing'
2. Gọi `addItemsToOrder()` để **THÊM** món mới (không xóa món cũ):
   ```java
   // Kiểm tra món đã có trong order chưa
   if (existingItem.getMenuItem().getId().equals(menuItem.getId())) {
       // Nếu đã có, tăng số lượng
       existingItem.setQuantity(existingItem.getQuantity() + req.getQuantity());
   } else {
       // Nếu chưa có, tạo mới
       OrderItem item = OrderItem.builder()...
   }
   ```

**Database:**
```sql
-- Kiểm tra món đã có chưa
SELECT * FROM order_items 
WHERE order_id = 1 AND menu_item_id = 7;

-- Nếu có: UPDATE
UPDATE order_items 
SET quantity = quantity + 2 
WHERE order_id = 1 AND menu_item_id = 7;

-- Nếu không có: INSERT
INSERT INTO order_items (order_id, menu_item_id, quantity, price) 
VALUES (1, 7, 2, 120000.00);
```

---

### **Bước 4: Người khác quét cùng QR code**
```
GET /order/{tableNumber}
```

**Backend xử lý:**
1. Tìm order hiện tại của bàn (giống Bước 1)
2. Load các món đã order vào giỏ hàng
3. Người này thấy được tất cả món đã order của bàn

**Lưu ý:** Tất cả người quét cùng QR code đều thấy cùng một order (cùng `order_id`).

---

## ⚠️ Vấn Đề Đã Sửa

### **Vấn đề cũ:**
- Khi khách hàng cập nhật món (thay đổi số lượng hoặc xóa món), frontend gọi `/order/submit`
- Backend chỉ **THÊM** món vào order hiện tại (tăng số lượng) thay vì **CẬP NHẬT** toàn bộ order
- Kết quả: Order bị sai (ví dụ: muốn giảm từ 3 xuống 2, nhưng lại thành 5)

### **Giải pháp:**
- Khi có order hiện tại và status = `pending`, frontend gọi `/order/update-items` để cập nhật toàn bộ order
- Khi chưa có order hoặc order đã ở trạng thái `preparing`/`served`, frontend gọi `/order/submit` để tạo mới hoặc thêm món

---

## 📊 Sơ Đồ Quan Hệ Database

```
tables (1) ──────< (N) orders (1) ──────< (N) order_items (N) ──────> (1) menu_items
   │                    │                        │
   │                    │                        │
   └─ qr_code           └─ status               └─ quantity, price
```

**Giải thích:**
- Một bàn có thể có nhiều orders (theo thời gian)
- Một order có nhiều order_items (nhiều món)
- Một order_item thuộc về một menu_item (món ăn)

---

## 🔍 Các API Endpoint

### 1. `GET /order/{tableNumber}`
- **Mục đích:** Hiển thị menu và load order hiện tại (nếu có)
- **Response:** HTML page với menu và giỏ hàng

### 2. `POST /order/submit`
- **Mục đích:** Tạo order mới HOẶC thêm món vào order hiện tại (nếu order đã ở trạng thái preparing/served)
- **Request:**
  ```json
  {
    "tableNumber": "1",
    "items": {
      "1": 2,
      "3": 1
    }
  }
  ```

### 3. `POST /order/update-items`
- **Mục đích:** Cập nhật toàn bộ order (chỉ khi status = pending)
- **Request:**
  ```json
  {
    "orderId": 1,
    "items": [
      {"menuItemId": 1, "quantity": 3},
      {"menuItemId": 5, "quantity": 1}
    ]
  }
  ```

### 4. `GET /order/current/{tableNumber}`
- **Mục đích:** Lấy order hiện tại của bàn (JSON)
- **Response:**
  ```json
  {
    "success": true,
    "order": {
      "id": 1,
      "status": "pending",
      "items": [...]
    }
  }
  ```

---

## ✅ Kết Luận

Quy trình đã được sửa để đảm bảo:
1. ✅ Nhiều người quét cùng QR code đều thấy cùng một order
2. ✅ Khi cập nhật món, hệ thống cập nhật order hiện tại thay vì tạo order mới
3. ✅ Giỏ hàng luôn đồng bộ với database

