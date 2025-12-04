# Giải thích cách lưu và nhận diện số bàn (Table Number) trong trình duyệt

## Cách hoạt động hiện tại

Hệ thống sử dụng **localStorage** của trình duyệt để lưu số bàn mà khách hàng đã quét QR code hoặc nhập thủ công.

### 1. Khi khách hàng quét QR code hoặc nhập số bàn

**File: `src/main/resources/templates/index.html`**

Khi khách hàng quét QR code thành công hoặc nhập số bàn thủ công:

```javascript
// Lưu tableNumber vào localStorage
localStorage.setItem('currentTableNumber', tableNumber);
console.log('Saved table number to localStorage:', tableNumber);
```

**Vị trí trong code:**
- Dòng 333: Khi quét QR thành công (`onScanSuccess`)
- Dòng 463: Khi nhập số bàn thủ công (`goToTableFromModal`)

### 2. Khi khách hàng vào trang order

**File: `src/main/resources/templates/customer/order.html`**

Khi khách hàng vào trang order, hệ thống tự động lưu số bàn vào localStorage:

```javascript
// Lưu tableNumber vào localStorage khi vào trang order
if (tableNumber) {
    localStorage.setItem('currentTableNumber', tableNumber);
    console.log('Saved table number to localStorage:', tableNumber);
}
```

**Vị trí trong code:** Dòng 936

### 3. Khi khách hàng gọi nhân viên

**File: `src/main/resources/templates/index.html`**

Khi khách hàng click "Gọi nhân viên", hệ thống tự động lấy số bàn từ localStorage:

```javascript
function submitCallStaff() {
    // Lấy tableNumber từ localStorage hoặc input
    let tableNumber = localStorage.getItem('currentTableNumber');
    
    if (!tableNumber) {
        tableNumber = document.getElementById('staffTableNumber')?.value.trim();
    }
    
    if (!tableNumber) {
        alert('Vui lòng nhập số bàn');
        return;
    }
    
    // Gửi request đến backend với tableNumber
    fetch('/order/call-staff', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            tableNumber: tableNumber
        })
    })
    // ...
}
```

**Vị trí trong code:** Dòng 385-420

### 4. Khi khách hàng thanh toán

**File: `src/main/resources/templates/index.html`**

Tương tự, khi thanh toán, hệ thống cũng lấy số bàn từ localStorage:

```javascript
function submitPayment() {
    // Lấy tableNumber từ localStorage
    const savedTableNumber = localStorage.getItem('currentTableNumber');
    // ...
}
```

**Vị trí trong code:** Dòng 503-530

## Ưu điểm của localStorage

1. **Lưu trữ cục bộ**: Dữ liệu được lưu trên trình duyệt của khách hàng, không cần server
2. **Tự động nhận diện**: Khi khách hàng quét QR hoặc nhập số bàn một lần, hệ thống nhớ số bàn đó
3. **Không cần đăng nhập**: Khách hàng không cần tài khoản để sử dụng
4. **Hoạt động offline**: Dữ liệu vẫn tồn tại ngay cả khi mất kết nối (chỉ mất khi xóa cache)

## Cách kiểm tra localStorage

Bạn có thể kiểm tra localStorage trong trình duyệt:

1. Mở **Developer Tools** (F12)
2. Vào tab **Application** (Chrome) hoặc **Storage** (Firefox)
3. Chọn **Local Storage** > `http://localhost:8080`
4. Tìm key `currentTableNumber` để xem giá trị

Hoặc trong Console:

```javascript
// Xem số bàn hiện tại
localStorage.getItem('currentTableNumber')

// Xóa số bàn (nếu cần)
localStorage.removeItem('currentTableNumber')

// Xem tất cả localStorage
console.log(localStorage)
```

## Lưu ý quan trọng

1. **Mỗi trình duyệt riêng biệt**: localStorage chỉ hoạt động trong cùng một trình duyệt. Nếu khách hàng dùng trình duyệt khác, họ cần quét QR lại.

2. **Xóa khi clear cache**: Nếu khách hàng xóa cache trình duyệt, localStorage cũng bị xóa.

3. **Không chia sẻ giữa các tab**: localStorage được chia sẻ giữa các tab của cùng một trình duyệt (cùng domain).

4. **Bảo mật**: localStorage không an toàn 100%, nhưng đủ cho mục đích lưu số bàn (không phải thông tin nhạy cảm).

## Cải tiến có thể thêm

1. **Session Storage**: Nếu muốn dữ liệu chỉ tồn tại trong một session (đóng tab là mất), có thể dùng `sessionStorage` thay vì `localStorage`.

2. **Cookie**: Nếu muốn dữ liệu tồn tại lâu hơn và có thể chia sẻ giữa các subdomain, có thể dùng cookie.

3. **IndexedDB**: Nếu cần lưu nhiều dữ liệu phức tạp hơn, có thể dùng IndexedDB.

## Tóm tắt

- **localStorage** là cách đơn giản và hiệu quả để lưu số bàn trên trình duyệt
- Khi khách hàng quét QR hoặc nhập số bàn, hệ thống tự động lưu vào `localStorage.setItem('currentTableNumber', tableNumber)`
- Khi cần sử dụng (gọi nhân viên, thanh toán), hệ thống tự động lấy từ `localStorage.getItem('currentTableNumber')`
- Nhân viên sẽ nhận được thông báo với số bàn chính xác qua WebSocket

