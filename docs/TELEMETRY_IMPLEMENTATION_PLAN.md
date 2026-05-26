# Kế hoạch Xây dựng Trang "Statistics" (Chứng minh Lý thuyết QoS)

Trang này sẽ là "Vũ khí bí mật" dành riêng cho buổi bảo vệ Luận văn. Nó không chỉ hiển thị số liệu khô khan mà sẽ trực quan hóa các lý thuyết phức tạp như Token Bucket, WFQ và Packet Loss thành các biểu đồ động (Dynamic Charts) chạy theo thời gian thực.

## User Review Required

> [!IMPORTANT]
> Dưới đây là 5 đề xuất biểu đồ để đưa vào trang Statistics mới. Bạn hãy xem qua các ý tưởng này. Nếu bạn thấy biểu đồ nào không cần thiết hoặc muốn thêm/bớt ý tưởng nào, hãy để lại feedback nhé!

## Đề xuất 5 Biểu đồ / Bảng Thống kê (QoS Validation)

### 1. 📈 Token Bucket Enforcer (Requested vs Allowed Bandwidth)
- **Loại:** Line Chart (Biểu đồ đường thời gian thực).
- **Ý nghĩa:** Chứng minh thuật toán bóp băng thông Token Bucket hoạt động.
- **Nội dung:** Có 2 đường chạy song song:
  - Đường màu đỏ: Tốc độ mạng thực sự mà App *muốn* dùng (Gói tin gửi đến VPN).
  - Đường màu xanh: Tốc độ mạng *được phép* đi qua Token Bucket (Max BPS).
  - -> Giám khảo sẽ thấy rõ đường màu xanh bị "cắt ngang" (phẳng lỳ) ở mức Max BPS, chứng tỏ thuật toán đã chặn đứng phần băng thông dư thừa.

### 2. 🥧 WFQ Fair Share Allocation (Phân bổ băng thông theo Trọng số)
- **Loại:** Doughnut/Pie Chart (Biểu đồ tròn).
- **Ý nghĩa:** Chứng minh thuật toán Weighted Fair Queuing (WFQ) hoạt động đúng lúc nghẽn mạng.
- **Nội dung:** Hiển thị % băng thông đang được chia cho 3 nhóm: HIGH (Trọng số 4), MEDIUM (Trọng số 2), LOW (Trọng số 1).
  - -> Chứng minh rằng khi mạng quá tải, App nhóm HIGH luôn chiếm phần bánh to nhất.

### 3. 📉 Packet Drop Rate Timeline (Lịch sử Bỏ gói)
- **Loại:** Line Chart có vùng màu (Area Chart).
- **Ý nghĩa:** Giải thích "Cơ chế" đằng sau việc bóp băng thông.
- **Nội dung:** Vẽ biểu đồ tỷ lệ Packet Loss (%). Tỷ lệ này sẽ là 0% khi mạng bình thường, nhưng sẽ vọt lên (màu đỏ rực) ngay khi một App vượt quá Max BPS.

### 4. 📊 Traffic Classification Matrix (Ma trận Phân loại DPI)
- **Loại:** Bar Chart (Biểu đồ cột) hoặc Bảng động (Dynamic Table).
- **Ý nghĩa:** Chứng minh Deep Packet Inspection (DPI) và bộ đếm Packet hoạt động.
- **Nội dung:** Bảng thống kê tổng số lượng TCP Flows, UDP Flows, ICMP Flows.

### 5. ⚡ Active QoS Flow Health (Bảng sức khỏe luồng)
- **Loại:** Dynamic Table (Bảng số liệu cập nhật từng giây).
- **Ý nghĩa:** Minh bạch hóa trạng thái của các luồng mạng.
- **Nội dung:** Hiển thị `UID`, `App Name`, `Priority`, `Max Limit`, `Current BPS`, và `Drop Rate %`.

---

## Proposed Technical Implementation (Thay đổi kỹ thuật)

Để vẽ được 5 biểu đồ trên, ta cần thay đổi cả Android và Backend:

### 1. Android Client (Gửi thêm dữ liệu)
- **Cập nhật `AppTraffic`**: Tính toán thêm `requestedBps` (tốc độ gốc trước khi qua Token Bucket) và `allowedBps` (tốc độ sau khi qua Token Bucket).
- **Cập nhật `QosVpnService`**: Đếm tổng số TCP/UDP Flows và truyền vào Payload.
- **Gửi Telemetry Mở rộng**: Gửi mảng dữ liệu chi tiết chứa `{ packageName, priority, requestedBps, allowedBps, droppedPkts }` mỗi 2 giây.

### 2. Node.js Backend (Xử lý & Lưu trữ)
- **Tạo bảng `qos_validation`**: Bảng mới trong SQLite chuyên lưu trữ lịch sử QoS để vẽ biểu đồ cho mượt.
- **Tạo API `/api/statistics/live`**: API trả về dữ liệu 5 phút gần nhất cho Chart.js.

### 3. Web Admin Frontend (UI Mới)
- **Thêm mục "Statistics"** vào Sidebar, nằm trên mục Connect App.
- **Tạo trang `page-statistics`** trong `index.html`.
- **Dùng Chart.js** lập trình 4 biểu đồ chạy theo thời gian thực (update 2s/lần) trong `app.js`.

---

## Open Questions

1. Bạn có muốn giữ lại biểu đồ số 4 (TCP/UDP) không, hay chỉ cần tập trung vào 3 biểu đồ đầu tiên (Token Bucket, WFQ, Drop Rate) để tránh bị rối mắt giám khảo?
2. Giống như cảnh báo trước, tôi sẽ cần làm sạch/xóa DB cũ để tạo bảng `qos_validation`. Bạn OK chứ?
