# 📋 Hướng dẫn: Lưu trữ và quản lý đơn hàng cho khách hàng

## 🎯 Mục tiêu

Cho phép khách hàng:
- ✅ Vào lại website sau khi đã đặt món
- ✅ Xem lại đơn hàng của mình
- ✅ Thêm món mới vào đơn hàng hiện có
- ❌ **KHÔNG** được xóa món khi nhà hàng đã bắt đầu chuẩn bị (chỉ nhân viên mới được xóa)

## 🏗️ Cách hệ thống hoạt động

### 1. **Lưu trữ dữ liệu trong Database**

Dữ liệu được lưu trong 2 bảng chính:

#### Bảng `orders`
- `id`: ID đơn hàng
- `table_id`: ID bàn
- `status`: Trạng thái đơn hàng (`pending`, `preparing`, `served`, `done`, `paid`)
- `created_at`: Thời gian tạo
- `updated_at`: Thời gian cập nhật

#### Bảng `order_items`
- `id`: ID món trong đơn
- `order_id`: ID đơn hàng (foreign key)
- `menu_item_id`: ID món ăn (foreign key)
- `quantity`: Số lượng
- `price`: Giá tại thời điểm đặt

### 2. **Luồng hoạt động**

#### **Bước 1: Khách hàng vào trang order**
```
GET /order/{tableNumber}
```

**Backend (OrderController.showMenu):**
1. Kiểm tra bàn có tồn tại không
2. Tìm order hiện tại của bàn (chưa thanh toán)
3. Nếu có order → truyền `orderId` và `orderStatus` vào template
4. Nếu không có → `orderId = null`

**Frontend (order.html):**
1. Khi trang load, gọi API `/order/current/{tableNumber}` để lấy order hiện tại
2. Nếu có order → load các món đã đặt vào giỏ hàng
3. Hiển thị thông báo nếu order status là `preparing` hoặc `served`

#### **Bước 2: Khách hàng thêm món mới**

**Frontend:**
- Khách hàng click "Thêm" món → món được thêm vào giỏ hàng (biến `cart` trong JavaScript)

**Backend (OrderController.submitOrder):**
```java
// Kiểm tra có order hiện tại không
Optional<Order> currentOrderOpt = orderService.getCurrentOrderByTable(tableNumber);

if (currentOrderOpt.isPresent()) {
    // Nếu có → thêm món vào order hiện có
    order = orderService.addItemsToOrder(currentOrderOpt.get().getId(), orderItems);
} else {
    // Nếu chưa có → tạo order mới
    order = orderService.createOrder(tableNumber, orderItems);
}
```

**OrderService.addItemsToOrder:**
1. Kiểm tra order chưa thanh toán
2. Với mỗi món mới:
   - Nếu món đã có trong order → tăng số lượng
   - Nếu món chưa có → tạo OrderItem mới
3. Cập nhật `updated_at`

#### **Bước 3: Kiểm tra quyền xóa món**

**Frontend (order.html):**
```javascript
// Kiểm tra xem có thể xóa món không
function canDeleteItems() {
    return currentOrderStatus === null || currentOrderStatus === 'pending';
}
```

**Logic:**
- Nếu `orderStatus = null` (chưa có order) → ✅ Cho phép xóa
- Nếu `orderStatus = 'pending'` (chưa chuẩn bị) → ✅ Cho phép xóa
- Nếu `orderStatus = 'preparing'` hoặc `'served'` → ❌ Không cho phép xóa

**UI:**
- Nút xóa bị disable và hiển thị icon 🔒
- Nút giảm số lượng bị disable
- Hiển thị tooltip: "Không thể xóa vì nhà hàng đã chuẩn bị"

### 3. **Các API Endpoints**

#### `GET /order/current/{tableNumber}`
- **Mục đích**: Lấy order hiện tại của bàn (cho khách hàng)
- **Response**: 
  ```json
  {
    "success": true,
    "order": {
      "id": 1,
      "status": "preparing",
      "items": [...],
      "total": 150000
    }
  }
  ```

#### `POST /order/submit`
- **Mục đích**: Tạo order mới hoặc thêm món vào order hiện có
- **Request**:
  ```json
  {
    "tableNumber": "1",
    "items": {
      "1": 2,  // menuItemId: quantity
      "3": 1
    }
  }
  ```
- **Logic**: Tự động kiểm tra có order hiện tại không và xử lý phù hợp

### 4. **Các Service Methods**

#### `OrderService.getCurrentOrderByTable(String tableNumber)`
```java
// Tìm order mới nhất của bàn, chưa thanh toán
Optional<Order> getCurrentOrderByTable(String tableNumber)
```

#### `OrderService.addItemsToOrder(Long orderId, List<OrderItemRequest> items)`
```java
// Thêm món vào order hiện có
// - Nếu món đã có → tăng số lượng
// - Nếu món chưa có → tạo mới
Order addItemsToOrder(Long orderId, List<OrderItemRequest> items)
```

## 🔐 Phân quyền

### Khách hàng (Customer)
- ✅ Xem menu
- ✅ Xem order hiện tại của bàn
- ✅ Thêm món vào order
- ✅ Xóa món khi `status = pending`
- ❌ Xóa món khi `status = preparing/served`
- ❌ Xóa món khi `status = paid/done`

### Nhân viên (Employee)
- ✅ Xem tất cả orders
- ✅ Cập nhật status order
- ✅ **Xóa món bất kỳ lúc nào** (thông qua endpoint `/order/update-items`)

## 📝 Ví dụ sử dụng

### Scenario 1: Khách hàng đặt món lần đầu
1. Khách quét QR code → vào `/order/1`
2. Chọn món và click "Đặt món"
3. Hệ thống tạo order mới với `status = pending`
4. Bàn chuyển sang `status = occupied`

### Scenario 2: Khách hàng quay lại sau khi đã đặt
1. Khách quét QR code lại → vào `/order/1`
2. Hệ thống tự động load order hiện tại
3. Giỏ hàng hiển thị các món đã đặt
4. Khách có thể thêm món mới
5. Nếu order `status = preparing` → không thể xóa món

### Scenario 3: Nhân viên cập nhật status
1. Nhân viên vào dashboard
2. Thấy order mới với `status = pending`
3. Click "Bắt đầu chuẩn bị" → `status = preparing`
4. Khách hàng quay lại → thấy thông báo và không thể xóa món

## 🎨 UI/UX Features

1. **Thông báo trạng thái**: Hiển thị banner màu vàng khi order đang được chuẩn bị
2. **Icon khóa**: Thay thế nút xóa bằng icon 🔒 khi không được phép xóa
3. **Tooltip**: Hiển thị lý do tại sao không thể xóa
4. **Auto-reload**: Sau khi đặt món thành công, tự động reload để cập nhật order mới nhất

## 🔄 Data Flow

```
Khách hàng vào trang
    ↓
Load order hiện tại (nếu có)
    ↓
Hiển thị món đã đặt trong giỏ hàng
    ↓
Khách thêm món mới
    ↓
Click "Đặt món"
    ↓
Backend kiểm tra:
    - Có order hiện tại? → Thêm món vào order đó
    - Chưa có order? → Tạo order mới
    ↓
Cập nhật database
    ↓
Reload trang để hiển thị order mới nhất
```

## 💡 Lưu ý kỹ thuật

1. **Transaction**: Tất cả operations đều dùng `@Transactional` để đảm bảo data consistency
2. **Eager Loading**: Order items được load với `FetchType.EAGER` để tránh LazyInitializationException
3. **Status Check**: Luôn kiểm tra order status trước khi cho phép xóa
4. **Table Status**: Bàn chỉ chuyển về `available` khi order được thanh toán (`status = paid`)

---

**Tóm lại**: Hệ thống lưu trữ order trong database, tự động load khi khách vào lại, và kiểm tra quyền xóa dựa trên order status. Chỉ nhân viên mới có quyền xóa món khi order đã được chuẩn bị.

