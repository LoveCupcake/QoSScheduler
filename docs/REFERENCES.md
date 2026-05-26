# ĐẶC TẢ LÝ THUYẾT CHUYÊN SÂU (ADVANCED THEORETICAL SPECIFICATION)

Tài liệu này cung cấp các thông số kỹ thuật và trích dẫn chuẩn mực nhất từ các tiêu chuẩn Internet toàn cầu.

---

## 1. PHÂN TÍCH CẤU TRÚC GÓI TIN IPV6 (RFC 8200)

### 1.1. Cấu trúc Fixed Header (40 octets)
> "The IPv6 header is a fixed length of 40 octets (320 bits)."
> 
> *   **Version (4 bits):** Phải bằng 6.
> *   **Traffic Class (8 bits):** Dùng cho cơ chế DiffServ (DSCP).
> *   **Flow Label (20 bits):** Định danh luồng dữ liệu để xử lý QoS nhanh.
> *   **Payload Length (16 bits):** Độ dài phần dữ liệu sau 40 byte header.
> *   **Next Header (8 bits):** Xác định loại header tiếp theo.
> *   **Hop Limit (8 bits):** Giảm dần qua mỗi router (tương đương TTL).

### 1.2. Cơ chế xử lý Extension Headers
> "Hdr Ext Len: Length of the header in 8-octet units, not including the first 8 octets."
> 
> **Bảng mã Next Header phổ biến:**
> - 0: Hop-by-Hop Options
> - 6: TCP (Transmission Control Protocol)
> - 17: UDP (User Datagram Protocol)
> - 43: Routing Header
> - 44: Fragment Header (Độ dài cố định 8 octets)
> - 58: ICMPv6

---

## 2. PHÂN TÍCH CẤU TRÚC GÓI TIN IPV4 (RFC 791)

### 2.1. Internet Header Format
> "The minimum value for IHL is 5, which means a header length of 20 bytes."
> 
> *   **Version (4 bits):** Phải bằng 4.
> *   **IHL (4 bits):** Internet Header Length (đơn vị 32-bit words).
> *   **Total Length (16 bits):** Tổng độ dài gói tin (tối đa 65,535 bytes).
> *   **Protocol (8 bits):** TCP=6, UDP=17.

---

## 3. MÁY TRẠNG THÁI TCP - TCP STATE MACHINE (RFC 9293)

### 3.1. Quy trình bắt tay 3 bước (Three-way Handshake)
Trong dự án QoS Scheduler, `TcpRelayManager` phải giả lập quy trình này:
1. **LISTEN -> SYN-SENT:** App gửi SYN (`serverSeq = random`).
2. **SYN-SENT -> SYN-RECEIVED:** Relay trả về SYN-ACK (`ack = appSeq + 1`).
3. **SYN-RECEIVED -> ESTABLISHED:** App gửi ACK (`seq = appSeq + 1`).

### 3.2. Flow Control (Điều khiển luồng)
> "The window field in the TCP header indicates the number of octets the sender is willing to accept."
> 
> **Ứng dụng:** Code của dự án đọc trường `windowSize` để thực hiện cơ chế **Backpressure** (nếu window = 0, VPN sẽ ngừng đọc từ Internet).

---

## 4. TOÁN HỌC TRONG ĐIỀU PHỐI BĂNG THÔNG (QOS MATHEMATICS)

### 4.1. Thuật toán Token Bucket
Công thức tính toán số dư Token tại thời điểm $t$:
$$Tokens(t) = \min(BucketSize, Tokens(t_{last}) + (t - t_{last}) \times RefillRate)$$

*   Nếu $PacketSize \leq Tokens(t)$: Cho phép (Allow).
*   Nếu $PacketSize > Tokens(t)$: Từ chối (Drop).

### 4.2. Weighted Fair Queuing (WFQ)
Công thức tính toán băng thông thực tế cho dòng lưu lượng $i$:
$$Bandwidth_i = R_{total} \times \frac{W_i}{\sum_{j=1}^{n} W_j}$$

*   $W_i$: Trọng số của App $i$ (HIGH=4, MEDIUM=2, LOW=1).
*   $R_{total}$: Tổng băng thông Uplink người dùng thiết lập.

---

## 5. CƠ CHẾ CAN THIỆP MẠNG ANDROID (VPN SERVICE INTERNALS)

### 5.1. Interface TUN (Network Layer)
> "A TUN interface acts as a virtual point-to-point network device. It operates at the Layer 3 (IP) of the OSI model."
> 
> **Cấu hình chuẩn trong dự án:**
> - `builder.addAddress("10.0.0.1", 24)`: Thiết lập mạng nội bộ ảo cho VPN.
> - `builder.addRoute("0.0.0.0", 0)`: Đánh chặn toàn bộ lưu lượng IPv4.

### 5.2. Socket Protection (VpnService.protect)
> "Protects the provided socket from being routed through the VPN interface, allowing it to reach the underlying network directly."
> 
> **Logic thực thi:** Ứng dụng gọi `protect(fd)` trước khi thực hiện `connect()`. Điều này đảm bảo dữ liệu từ Relay thoát ra ngoài Internet thật thay vì bị quay ngược lại chính nó.

---

## 6. TIÊU CHUẨN ĐO KIỂM BĂNG THÔNG (IPERF3 METRICS)

*   **Throughput:** Tốc độ truyền tải thực tế (bps).
*   **Jitter:** Sự biến động về độ trễ giữa các gói tin (ms). Quan trọng đối với các luồng traffic HIGH (Gaming).
*   **Packet Loss:** Tỉ lệ gói tin bị mất (%). Hệ thống QoS chủ động gây ra Packet Loss trên các dòng traffic LOW để bảo vệ traffic HIGH.
