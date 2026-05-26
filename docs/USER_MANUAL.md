# HƯỚNG DẪN SỬ DỤNG QOS SCHEDULER (USER MANUAL)

Chào mừng bạn đến với hệ thống quản lý chất lượng dịch vụ mạng (QoS) thông minh trên Android. Tài liệu này sẽ giúp bạn làm chủ các tính năng của ứng dụng.

---

## 1. CÀI ĐẶT VÀ CẤP QUYỀN
Để ứng dụng hoạt động chính xác, bạn cần thực hiện các bước sau:
1.  **Cài đặt App:** Cài đặt file APK vào điện thoại (Yêu cầu Android 10 trở lên).
2.  **Cấp quyền VPN:** 
    - Khi nhấn nút **"Start Scheduler"**, hệ thống sẽ hiện thông báo xin quyền thiết lập kết nối VPN.
    - Nhấn **"OK"** hoặc **"Cho phép"**. Đây là bước bắt buộc để App có thể chặn và điều phối gói tin.
3.  **Tắt tối ưu hóa pin (Khuyến nghị):** Để VPN không bị hệ điều hành ngắt khi chạy ngầm, hãy đưa App vào danh sách trắng (Whitelist) trong cài đặt pin.

---

## 2. GIAO DIỆN CHÍNH (DASHBOARD)
Màn hình Dashboard cung cấp cái nhìn tổng thể về hệ thống:
*   **Trạng thái VPN:** Hiển thị Service đang chạy hay đang dừng.
*   **Biểu đồ Traffic:** Quan sát lưu lượng dữ liệu (Mbps) đang đi qua hệ thống theo thời gian thực.
*   **Danh sách Ứng dụng:** Hiển thị các App đang tiêu thụ mạng và mức ưu tiên hiện tại của chúng.

---

## 3. CẤU HÌNH BĂNG THÔNG (SETTINGS)
Trước khi bắt đầu điều phối, bạn cần thiết lập thông số mạng tại màn hình Settings:
1.  **Uplink Bandwidth (Mbps):** Kéo thanh trượt để thiết lập tốc độ mạng tổng của bạn (Ví dụ: Gói cước của bạn là 30Mbps, hãy set khoảng 25-28Mbps để có kết quả tốt nhất).
2.  **Runtime Mode:**
    - **Monitor Mode:** Chỉ quan sát và thống kê, không can thiệp vào tốc độ (Dùng để theo dõi thói quen sử dụng).
    - **Relay Mode:** Kích hoạt toàn bộ bộ máy QoS để bóp băng thông và ưu tiên traffic.

---

## 4. QUẢN LÝ ĐỘ ƯU TIÊN (APP QOS)
Đây là tính năng cốt lõi của ứng dụng:
1.  **Chọn Ứng dụng:** Nhấn vào một App bất kỳ trong danh sách (Ví dụ: YouTube).
2.  **Thiết lập Priority:**
    - **HIGH:** Dành cho Game, VoIP (Zalo Call, Telegram). App sẽ được cấp băng thông tối đa và độ trễ thấp nhất.
    - **MEDIUM:** Mức mặc định cho lướt Web, mạng xã hội.
    - **LOW:** Dành cho các App tải nặng (Download, Update, Video 4K). App sẽ bị bóp tốc độ khi các App HIGH đang cần băng thông.
3.  **Xác nhận:** Thay đổi sẽ có hiệu lực ngay lập tức mà không cần khởi động lại VPN.

---

## 5. KỊCH BẢN SỬ DỤNG THỰC TẾ (DEMO)
**Kịch bản: "Chơi Game mượt mà khi đang xem Video"**
1. Vào Settings, đặt Uplink là 5 Mbps.
2. Tìm App YouTube, set về mức **LOW**.
3. Tìm Game của bạn (hoặc App đo tốc độ), set về mức **HIGH**.
4. **Kết quả:** YouTube sẽ bị giới hạn ở mức thấp (hình ảnh mờ hơn), dành toàn bộ "đường truyền trống" cho Game, giúp giảm Ping và hiện tượng giật lag.

---

## 6. GIẢI QUYẾT SỰ CỐ (TROUBLESHOOTING)
*   **Không có mạng khi bật VPN:** Kiểm tra xem bạn có đang ở chế độ **Relay Stable** không. Thử chuyển sang **Monitor** để kiểm tra kết nối gốc.
*   **App không hiện tên:** Một số traffic hệ thống sẽ hiện là "System/Unknown". Điều này là bình thường do giới hạn bảo mật của Android.
*   **Tốc độ không thay đổi:** Hãy đảm bảo bạn đã nhấn "Start Scheduler" và nút gạt trạng thái đang ở màu Xanh.
