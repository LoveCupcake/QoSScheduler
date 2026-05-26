# CHƯƠNG 3: PHÂN TÍCH VÀ THIẾT KẾ HỆ THỐNG

## 3.1. Yêu cầu hệ thống

### 3.1.1. Yêu cầu chức năng
1. **Chặn bắt gói tin:** Hệ thống phải chặn bắt được 100% lưu lượng IP đi ra/vào thiết bị Android thông qua giao diện TUN ảo.
2. **Phân tích gói tin:** Hệ thống phải trích xuất được các trường: IP nguồn/đích, Port nguồn/đích, Giao thức (TCP/UDP) từ cả gói tin IPv4 và IPv6.
3. **Định danh ứng dụng:** Hệ thống phải xác định được ứng dụng nào (UID) sở hữu từng kết nối mạng.
4. **Phân loại lưu lượng:** Hệ thống phải phân loại gói tin theo loại dịch vụ (Gaming, Streaming, Web, Download) dựa trên Port đích.
5. **Điều phối băng thông:** Hệ thống phải phân bổ băng thông theo trọng số ưu tiên (HIGH/MEDIUM/LOW) cho từng ứng dụng.
6. **Chuyển tiếp dữ liệu:** Hệ thống phải chuyển tiếp (Relay) gói tin TCP và UDP ra Internet thật mà không làm gián đoạn kết nối.
7. **Thay đổi cấu hình thời gian thực:** Người dùng có thể thay đổi mức ưu tiên và giới hạn băng thông mà không cần khởi động lại VPN.

### 3.1.2. Yêu cầu phi chức năng
1. **Hiệu năng:** Xử lý gói tin phải hoàn tất trong vòng dưới 5ms để không gây ảnh hưởng đến trải nghiệm người dùng.
2. **Ổn định:** VPN Service phải chạy liên tục dưới dạng Foreground Service, không bị Android tự động tắt.
3. **An toàn bộ nhớ:** Hệ thống phải tái sử dụng buffer (Buffer Pooling) để tránh lỗi OOM (Out of Memory) khi xử lý lưu lượng lớn.
4. **Không cần Root:** Toàn bộ chức năng phải hoạt động trên thiết bị Android chưa Root.

---

## 3.2. Kiến trúc tổng thể (High-level Architecture)

### 3.2.1. Mô hình Data Plane / Control Plane
Hệ thống được thiết kế theo mô hình tách biệt hai mặt phẳng xử lý, tương tự kiến trúc của các bộ định tuyến (Router) chuyên nghiệp:

- **Data Plane (Mặt phẳng Dữ liệu):** Chịu trách nhiệm xử lý gói tin ở tốc độ cao. Bao gồm các module: `RawPacket` (Phân tích), `DataPlaneProcessor` (Điều phối), `TcpRelayManager`/`UdpRelayManager` (Chuyển tiếp), `PacketComposer` (Tái tạo gói tin).
- **Control Plane (Mặt phẳng Điều khiển):** Chịu trách nhiệm quản lý cấu hình và hiển thị thông tin. Bao gồm: `MainViewModel` (Logic UI), `BandwidthScheduler` (Cấu hình QoS), `DeviceRegistry` (Quản lý thiết bị).

### 3.2.2. Sơ đồ luồng dữ liệu (Data Flow)

```
┌─────────────────────────────────────────────────────────────┐
│                    ANDROID APPLICATION                       │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐               │
│  │ YouTube  │    │  Chrome  │    │   Game   │               │
│  └────┬─────┘    └────┬─────┘    └────┬─────┘               │
│       │               │               │                      │
│       └───────────────┼───────────────┘                      │
│                       ▼                                      │
│              ┌────────────────┐                               │
│              │  TUN Interface │  (Giao diện mạng ảo)         │
│              │   10.0.0.1/24  │                               │
│              └───────┬────────┘                               │
│                      ▼                                       │
│         ┌────────────────────────┐                            │
│         │   QosVpnService        │                            │
│         │   runTunReadLoop()     │                            │
│         └───────────┬────────────┘                            │
│                     ▼                                        │
│         ┌────────────────────────┐                            │
│         │  DataPlaneProcessor    │                            │
│         │  ┌──────────────────┐  │                            │
│         │  │ 1. RawPacket     │  │  Phân tích Header         │
│         │  │    .parse()      │  │                            │
│         │  ├──────────────────┤  │                            │
│         │  │ 2. AppResolver   │  │  Định danh App (UID)      │
│         │  │    .getUid()     │  │                            │
│         │  ├──────────────────┤  │                            │
│         │  │ 3. DpiClassifier │  │  Phân loại Traffic        │
│         │  │    .classify()   │  │                            │
│         │  ├──────────────────┤  │                            │
│         │  │ 4. Bandwidth     │  │  Quyết định Allow/Drop    │
│         │  │    Scheduler     │  │                            │
│         │  └──────────────────┘  │                            │
│         └───────────┬────────────┘                            │
│                     │                                        │
│            ┌────────┴────────┐                                │
│            ▼                 ▼                                │
│    ┌──────────────┐  ┌──────────────┐                        │
│    │ TcpRelay     │  │ UdpRelay     │                        │
│    │ Manager      │  │ Manager      │                        │
│    └──────┬───────┘  └──────┬───────┘                        │
│           │                  │                                │
│           └────────┬─────────┘                                │
│                    ▼                                         │
│           ┌────────────────┐                                  │
│           │  Real Internet │  (Qua Wi-Fi/4G)                 │
│           └────────────────┘                                  │
└─────────────────────────────────────────────────────────────┘
```

---

## 3.3. Thiết kế chi tiết các Module

### 3.3.1. Module phân tích gói tin (`model/RawPacket.kt`)

**Trách nhiệm:** Chuyển đổi mảng byte thô từ TUN thành đối tượng `RawPacket` có cấu trúc.

**Thuật toán xử lý IPv4:**
1. Đọc byte[0], dịch phải 4 bit để lấy Version.
2. Đọc 4 bit thấp của byte[0], nhân 4 để tính IHL (độ dài header tính bằng byte).
3. Đọc byte[9] để xác định Protocol (TCP=6, UDP=17).
4. Đọc byte[12-15] để lấy Source IP, byte[16-19] để lấy Destination IP.
5. Nhảy tới vị trí `offset = IHL` để đọc Source Port (2 byte) và Destination Port (2 byte).

**Thuật toán xử lý IPv6:**
1. Đọc byte[6] để lấy Next Header.
2. Đặt `headerOffset = 40` (vì Fixed Header luôn 40 byte).
3. Vòng lặp: Nếu Next Header là Extension Header (0, 43, 44, 60):
   - Tính độ dài Extension bằng công thức `(HdrExtLen × 8) + 8`.
   - Cập nhật `headerOffset += extensionLength`.
   - Đọc byte đầu tiên của Extension để lấy Next Header tiếp theo.
4. Khi thoát vòng lặp, `headerOffset` chỉ đúng vào vị trí TCP/UDP Header.

**Thuật toán nén địa chỉ IPv6:**
Hàm `formatIpv6()` triển khai thuật toán nén "::" (RFC 5952):
1. Chia 128 bit thành 8 segment, mỗi segment 16 bit.
2. Tìm chuỗi segment zero liên tiếp dài nhất.
3. Thay thế chuỗi đó bằng "::".

### 3.3.2. Module định danh ứng dụng (`util/AppResolver.kt`)

**Trách nhiệm:** Xác định ứng dụng nào sở hữu một kết nối mạng.

**Quy trình xử lý:**
1. Kiểm tra bộ nhớ đệm (`uidCache`) dựa trên key `protocol:srcIp:srcPort:dstIp:dstPort`.
2. Nếu cache miss, gọi `ConnectivityManager.getConnectionOwnerUid()` — đây là System Call xuống Kernel, thời gian thực thi khoảng 1ms.
3. Xử lý đặc biệt cho IPv6: Sử dụng wildcard address `::` thay vì `0.0.0.0`.
4. Nếu tìm được UID, lưu vào cache. Nếu không, gói tin được theo dõi tạm thời dưới dạng "Synthetic Port Bucket" (xô ảo theo Port).

**Cơ chế Retroactive Migration:**
Khi một gói tin ban đầu không xác định được UID (cache miss), nó được gán vào một "xô ảo" theo Port. Khi gói tin tiếp theo của cùng kết nối đó được giải mã thành công UID, hệ thống sẽ di chuyển (migrate) toàn bộ thống kê lưu lượng từ xô ảo sang App thật. Điều này đảm bảo số liệu thống kê luôn chính xác.

### 3.3.3. Module phân loại lưu lượng (`classifier/DpiClassifier.kt`)

**Trách nhiệm:** Phân loại gói tin vào các danh mục dịch vụ.

**Bảng quy tắc phân loại:**

| Điều kiện | Danh mục | Mức ưu tiên mặc định |
|:----------|:---------|:---------------------|
| UDP, Port 5060-5061 hoặc 10000-20000 | VoIP | HIGH |
| Port 27015-27016, 5000, 5500, 8001-8002 | Gaming | HIGH |
| Port 53 | DNS/Web | MEDIUM |
| Port 1935, 8080 | Streaming | MEDIUM |
| Port 80, 443 | Web Browsing | MEDIUM |
| Port 21, 22, 143, 993, 110, 995 | File Transfer | LOW |
| Khác | Unknown | MEDIUM |

### 3.3.4. Module điều phối băng thông (`scheduler/BandwidthScheduler.kt`)

**Trách nhiệm:** Phân bổ băng thông và quyết định cho phép/hủy bỏ gói tin.

**Cấu trúc dữ liệu chính:**
- `buckets: ConcurrentHashMap<String, TokenBucket>` — Mỗi App (hoặc nhóm traffic) có một TokenBucket riêng.
- `manualCaps: ConcurrentHashMap<String, Long>` — Giới hạn băng thông thủ công do người dùng thiết lập.

**Thuật toán `rebalanceWithApps()`:**
1. Đếm số App ở mỗi mức ưu tiên: `highCount`, `medCount`, `lowCount`.
2. Tính tổng trọng số: `totalWeight = highCount × 4 + medCount × 2 + lowCount × 1 + 2` (cộng 2 cho Host bucket).
3. Với mỗi App: `rate = uplinkBps × weight / totalWeight`.
4. Cập nhật `TokenBucket.setRate(rate)` cho từng App.

**Thuật toán `processPacket(key, packetSize)`:**
1. Tìm TokenBucket tương ứng với key.
2. Gọi `bucket.consume(packetSize)`.
3. Trả về `true` (Allow) hoặc `false` (Drop).

### 3.3.5. Module Token Bucket (`scheduler/TokenBucket.kt`)

**Trách nhiệm:** Kiểm soát tốc độ truyền tải cho từng App.

**Thuật toán `consume(bytes)`:**
1. Gọi `refill()`:
   - `elapsed = (System.nanoTime() - lastRefillTime) / 1_000_000_000.0` (giây).
   - `tokens = min(burstBytes, tokens + rateBps × elapsed)`.
   - `lastRefillTime = now`.
2. Kiểm tra: Nếu `tokens >= bytes` → trừ tokens, trả về `true`. Ngược lại trả về `false`.

**An toàn luồng (Thread Safety):** Sử dụng annotation `@Synchronized` để đảm bảo chỉ có một luồng truy cập vào xô tại một thời điểm.

### 3.3.6. Module chuyển tiếp TCP (`service/relay/TcpRelayManager.kt`)

**Trách nhiệm:** Đóng vai trò Proxy TCP, giả lập kết nối giữa App và Internet.

**Máy trạng thái (State Machine) của `TcpFlow`:**

```
         App gửi SYN
              │
              ▼
       ┌──────────────┐
       │ SYN_RECEIVED  │  Relay trả SYN-ACK cho App
       └──────┬───────┘
              │ App gửi ACK
              ▼
       ┌──────────────┐
       │  ESTABLISHED  │  Relay mở Socket thật ra Internet
       └──────┬───────┘
              │ App gửi FIN hoặc RST
              ▼
       ┌──────────────┐
       │    CLOSED     │  Giải phóng Socket và tài nguyên
       └──────────────┘
```

**Cơ chế Socket Protection:**
Trước khi gọi `Socket.connect()`, Relay phải gọi `VpnService.protect(socket)`. Lệnh này báo cho Linux Kernel: "Miễn trừ socket này khỏi bảng định tuyến VPN". Nếu thiếu bước này, gói tin từ Relay sẽ bị VPN chặn lại → tạo Infinite Routing Loop → điện thoại mất mạng hoàn toàn.

### 3.3.7. Module tái tạo gói tin (`model/PacketComposer.kt`)

**Trách nhiệm:** Tạo ra các gói tin IP/TCP/UDP hợp lệ để bơm ngược vào TUN Interface.

**Quy trình tạo gói tin TCP IPv4:**
1. Cấp phát ByteBuffer với kích thước = 20 (IP Header) + TCP Header + Payload.
2. Ghi IP Header: Version=4, IHL=5, Protocol=6, TTL=64, Source/Dest IP.
3. Tính IP Header Checksum bằng thuật toán RFC 1071.
4. Ghi TCP Header: Source/Dest Port, Sequence Number, Ack Number, Flags.
5. Tính TCP Checksum bằng Pseudo-header + TCP Header + Payload.
6. Trả về ByteArray hoàn chỉnh.

---

## 3.4. Thiết kế cơ sở dữ liệu trạng thái

### 3.4.1. Các cấu trúc dữ liệu chính

| Cấu trúc | Kiểu | Mục đích |
|:----------|:-----|:---------|
| `appTraffic` | `ConcurrentHashMap<Int, AppTraffic>` | Lưu trữ thống kê traffic theo UID |
| `flowCache` | `ConcurrentHashMap<FlowKey, Int>` | Ánh xạ 5-tuple → UID |
| `uidCache` | `ConcurrentHashMap<String, Int>` | Cache kết quả tra cứu UID |
| `buckets` | `ConcurrentHashMap<String, TokenBucket>` | Xô token cho từng App |

### 3.4.2. Mô hình dữ liệu

**FlowKey** (Định danh luồng):
- `srcIp: String`, `dstIp: String`, `srcPort: Int`, `dstPort: Int`, `protocol: Protocol`
- Đây là bộ 5 (5-tuple) duy nhất xác định một kết nối mạng.

**AppTraffic** (Thống kê App):
- `uid: Int`, `appName: String`, `packageName: String`
- `bytesIn: Long`, `bytesOut: Long` (Tổng lưu lượng)
- `priorityClass: TrafficClass` (HIGH/MEDIUM/LOW)
- `activeFlows: ConcurrentHashMap<FlowKey, PacketFlow>` (Các kết nối đang hoạt động)
- `qosAllowedPackets: Int`, `qosDroppedPackets: Int` (Thống kê QoS)

---

## 3.5. Thiết kế giao diện người dùng (UI)

### 3.5.1. Kiến trúc MVVM
Giao diện sử dụng mô hình MVVM (Model-View-ViewModel) với Jetpack Compose:
- **Model:** `AppTraffic`, `ConnectedDevice`, `RelayHealthSnapshot`.
- **ViewModel:** `MainViewModel` — sử dụng `combine` operator để trộn 8 nguồn StateFlow thành một `UiState` duy nhất.
- **View:** Các Composable function hiển thị Dashboard, danh sách App và Settings.

### 3.5.2. Cơ chế đồng bộ dữ liệu thời gian thực
`QosApplication` (Global Singleton) đóng vai trò trung gian:
- Service cập nhật dữ liệu vào `StateFlow`.
- ViewModel lắng nghe `StateFlow` và tự động trigger recomposition.
- UI vẽ lại mà không cần polling hoặc callback thủ công.
