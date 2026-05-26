# ĐẠI GIÁO TRÌNH KỸ THUẬT QOS SCHEDULER (GRAND TECHNICAL MANUAL)

Tài liệu này là bản đặc tả đầy đủ nhất về mã nguồn của dự án QoS Scheduler.

---

## CHƯƠNG 1: TẦNG KHỞI TẠO VÀ QUẢN LÝ TRẠNG THÁI (APPLICATION & UI)

### 1.1. `QosApplication.kt` (Global Singleton)
*   **Mục đích:** Là linh hồn của ứng dụng, giữ cho dữ liệu không bị mất khi Android dọn dẹp bộ nhớ.
*   **Hàm `updateDevices(devices)`:**
    1. Tiếp nhận danh sách thiết bị từ `DeviceRegistry`.
    2. Cập nhật vào `_devicesFlow`.
    3. UI (Compose) đang quan sát Flow này sẽ tự động Trigger hàm `recompose` để vẽ lại danh sách.
*   **Hàm `updateRelayHealth(snapshot)`:**
    1. Nhận thông tin từ `TcpRelayManager` (số lượng flow, lỗi).
    2. Đẩy vào `_relayHealth` flow.
    3. Ý nghĩa: Giúp người dùng biết VPN đang chạy ổn định hay đang bị quá tải.

### 1.2. `MainViewModel.kt` (Business Logic for UI)
*   **Hàm `init`:**
    1. Khởi chạy một Coroutine chạy ngầm với chu kỳ 5 giây.
    2. Liên tục kiểm tra trạng thái Hotspot thông qua `QosVpnService.getInstance()`.
    3. Cập nhật `_hotspotState` để hiển thị biểu tượng Hotspot trên Dashboard.
*   **Hàm `startScheduler()`:**
    1. Lấy giá trị Mbps từ UI.
    2. Đóng gói vào Intent: `EXTRA_UPLINK_BPS` và `EXTRA_INITIAL_MODE`.
    3. Gọi `startForegroundService()` - Điều này cực kỳ quan trọng vì Android sẽ giết các service chạy ngầm nếu không có Notification.
*   **Hàm `uiState` (Combine Operator):**
    1. Sử dụng thuật toán `combine` của Kotlin Flow để trộn 8 nguồn dữ liệu khác nhau.
    2. Đảm bảo UI luôn hiển thị dữ liệu "mới nhất" và "đồng bộ nhất".

---

## CHƯƠNG 2: TRÁI TIM VPN (CORE SERVICE)

### 2.1. `QosVpnService.kt`
*   **Hàm `setupTunnel()`:**
    1. Gọi `Builder()`. 
    2. `addAddress("10.0.0.1", 24)`: Thiết lập IP cho Card mạng ảo.
    3. `addRoute("0.0.0.0", 0)`: Ép toàn bộ traffic đi vào VPN.
    4. `setMtu(1500)`: Thiết lập kích thước gói tin tối đa để tránh phân mảnh (fragmentation).
    5. `establish()`: Trả về một `ParcelFileDescriptor` (đường ống dẫn byte).
*   **Hàm `runTunReadLoop()`:**
    1. Sử dụng vòng lặp `while(isActive)`.
    2. Đọc byte từ `FileInputStream` của TUN.
    3. Mỗi gói tin đọc được sẽ được bọc vào đối tượng `ByteArray` và đẩy vào `packetChannel`.
    4. Sử dụng `capacity = Channel.BUFFERED` để tránh treo luồng đọc khi xử lý không kịp.

### 2.2. `DataPlaneProcessor.kt` (The Intelligent Filter)
*   **Hàm `process(bytes, length, isOutbound)`:**
    1. **Bước 1 (Parsing):** Gọi `RawPacket.parse`. Trích xuất 5 thông số: IP Nguồn/Đích, Port Nguồn/Đích, Giao thức.
    2. **Bước 2 (ID):** Tạo `FlowKey`. Nếu là kết nối mới, gọi `AppResolver` để tìm UID của App (VD: YouTube).
    3. **Bước 3 (Classification):** Nếu không tìm thấy UID, dùng `DpiClassifier` để đoán dựa trên Port.
    4. **Bước 4 (Scheduler):** Nếu là traffic đi ra, gọi `scheduler.processPacket(uid, length)`.
    5. **Bước 5 (Result):** Trả về kết quả Boolean. Nếu `false`, `QosVpnService` sẽ im lặng hủy bỏ gói tin (Drop).

---

## CHƯƠNG 3: THUẬT TOÁN ĐIỀU PHỐI (QOS SCHEDULING)

### 3.1. `BandwidthScheduler.kt` (Resource Manager)
*   **Hàm `ensureBucket(key, priority)`:**
    1. Kiểm tra trong `ConcurrentHashMap` xem đã có xô cho App này chưa.
    2. Nếu chưa, tạo một `TokenBucket` mới.
    3. Cập nhật `priority` và gọi `rebalance()`.
*   **Hàm `rebalance()` (The Heart of QoS):**
    1. Tính `totalWeight`: Duyệt qua tất cả các xô, cộng dồn Weight (HIGH=4, MEDIUM=2, LOW=1).
    2. Tính `bpsPerWeight = totalUplinkBps / totalWeight`.
    3. Với mỗi xô: `bucket.updateRate(bucket.weight * bpsPerWeight)`.
    4. Ý nghĩa: Đảm bảo băng thông được chia đúng tỉ lệ 4:2:1 ngay cả khi tổng băng thông thay đổi.

### 3.2. `TokenBucket.kt` (The Rate Limiter)
*   **Hàm `consume(tokens)`:**
    1. `now = System.currentTimeMillis()`.
    2. `elapsed = now - lastRefill`.
    3. `tokensToAdd = (elapsed * rateBps) / 1000`.
    4. `currentTokens = min(capacity, currentTokens + tokensToAdd)`.
    5. `lastRefill = now`.
    6. Nếu `currentTokens >= tokens`: Trả về `true` (Trừ xu). Ngược lại trả về `false`.

---

## CHƯƠNG 4: GIAO THỨC VÀ CHUYỂN TIẾP (NETWORK STACK)

### 4.1. `TcpRelayManager.kt` (The Proxy Engine)
*   **Lớp `TcpFlow`:** Quản lý một phiên kết nối TCP.
*   **Hàm `onTunPacket(packet)`:**
    1. Đọc cờ TCP (SYN, ACK, PSH, FIN, RST).
    2. **Bắt tay giả:** Khi nhận SYN từ App, Relay lập tức trả về SYN-ACK. App sẽ chuyển sang trạng thái "Connected".
    3. **Mở Socket thật:** Relay gọi `Socket.connect()` tới Server thật trên Internet.
    4. **Duy trì Seq/Ack:** Relay phải tự tính toán số Sequence và Acknowledgment để gói tin đi và về khớp nhau 100%.
*   **Hàm `protectSocket()`:** Gọi `VpnService.protect()`. Đây là bước sống còn để Socket đi ra ngoài mạng mà không quay ngược lại VPN.

### 4.2. `PacketComposer.kt` (Packet Factory)
*   **Hàm `computeTcpChecksum(...)`:**
    1. Tạo Pseudo-header (Source IP, Dest IP, Protocol 6, TCP Length).
    2. Gom toàn bộ Header và Data thành các khối 16-bit.
    3. Cộng dồn tất cả.
    4. Xử lý phần dư (Carry): `while (sum >> 16 != 0) { sum = (sum & 0xFFFF) + (sum >> 16) }`.
    5. Trả về kết quả đảo bit (`~sum`).

---

## CHƯƠNG 5: TIỆN ÍCH HỆ THỐNG (SYSTEM UTILS)

### 5.1. `AppResolver.kt` (Identity Finder)
*   **Hàm `getUidForConnection()`:**
    1. Tiếp nhận 5-tuple của gói tin.
    2. Gọi `connectivityManager.getConnectionOwnerUid(protocol, source, destination)`.
    3. Đây là một hàm chặn (blocking), nên phải chạy trong `Dispatchers.IO`.
    4. Nếu tìm được UID, trả về. Nếu không, trả về -1 (System traffic).

### 5.2. `HotspotManager.kt` (Environment Detector)
*   **Hàm `getHotspotSubnet()`:**
    1. Duyệt qua tất cả các `NetworkInterface` của điện thoại.
    2. Tìm interface có tên `ap0` hoặc `swlan0`.
    3. Đọc địa chỉ IP (thường là `192.168.43.1`).
    4. Trả về prefix `192.168.43`. Điều này giúp hệ thống biết gói tin nào đến từ thiết bị đang kết nối vào Hotspot.

---

## CHƯƠNG 6: TƯ DUY KIẾN TRÚC MICROSERVICES
1. **Separation of Concerns:** `BandwidthScheduler` lo về công bằng, `TokenBucket` lo về tốc độ, `TcpRelay` lo về kết nối. Không ai dẫm chân lên ai.
2. **Event-Driven:** Gói tin là sự kiện. Các module phản ứng với gói tin đó theo dây chuyền.
3. **Stateless Logic:** Phần lớn các module xử lý gói tin không lưu trạng thái lâu dài, giúp tiết kiệm RAM và tăng tốc độ xử lý.
4. **Resilience:** Hệ thống có các cơ chế "Lưới an toàn" (Lớp bảo vệ) để nếu một thành phần lỗi, VPN vẫn không bị treo.
