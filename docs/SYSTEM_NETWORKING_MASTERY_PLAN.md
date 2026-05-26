# LỘ TRÌNH HUẤN LUYỆN: CHUYÊN GIA HỆ THỐNG MẠNG (FROM SCRATCH)

Lộ trình này được thiết kế để biến một lập trình viên bình thường thành một **Networking Specialist** có khả năng làm việc tại các tập đoàn lớn (Viettel, Samsung, Cisco, Google).

---

## GIAI ĐOẠN 1: NỀN TẢNG "NGUYÊN TỬ" (CORE FOUNDATIONS) ✅
*Mục tiêu: Hiểu bản chất của Bit và Byte trong không gian mạng.*

### 1.1. Hệ thống số và Biểu diễn dữ liệu
- [x] Thành thạo chuyển đổi Binary - Decimal - Hexadecimal.
- [x] Hiểu **Endianness**: Tại sao Network luôn dùng Big-Endian?
- [x] **Bài tập:** Tự tay viết một mảng Byte đại diện cho một chuỗi ASCII.

### 1.2. Mô hình OSI & TCP/IP (Thực tế)
- [x] Không học định nghĩa, học **Encapsulation**: Header lồng Header.
- [x] IPv4/IPv6: Cách chia Subnet, Mask và Gateway.
- [x] **Sản phẩm:** Một sơ đồ vẽ tay mô tả hành trình gói tin từ trình duyệt đến Server.

### 1.3. Routing & CIDR
- [x] Hiểu CIDR Notation (/8, /16, /24, /25, /26...).
- [x] Longest Prefix Match: Cách Router chọn đường đi.
- [x] MTU & Fragmentation: Giới hạn kích thước gói tin.

---

## GIAI ĐOẠN 2: LẬP TRÌNH GIAO TIẾP (SOCKET PROGRAMMING) 🔄
*Mục tiêu: Điều khiển luồng dữ liệu bằng code.*

### 2.1. TCP Sockets (Tin cậy)
- [x] Viết Client/Server gửi nhận tin nhắn.
- [x] Hiểu **Blocking vs Non-blocking IO**: Tại sao cần Multi-threading hoặc Coroutines?
- [x] **Dự án:** Ứng dụng Chat nhiều phòng (Chat Room).

### 2.2. UDP Sockets (Tốc độ) ✅
- [x] Viết ứng dụng gửi dữ liệu nhanh (DatagramSocket).
- [x] Tự xây dựng cơ chế **Reliable UDP**: Sequence Number + ACK + Retransmit.
- [x] **Dự án:** Truyền tải file qua UDP có kiểm soát lỗi.

### 2.3. TCP Deep Dive (Chuyên sâu) ✅
- [x] **TCP State Machine:** 11 trạng thái (LISTEN, SYN_SENT, ESTABLISHED, TIME_WAIT...).
- [x] **Sliding Window:** Cơ chế gửi nhiều gói tin cùng lúc mà không cần đợi ACK từng cái.
- [x] **Congestion Control:** Thuật toán Slow Start, AIMD, Fast Retransmit.
- [x] **Bài tập:** Phân tích TCP dump bằng Wireshark, giải thích từng gói tin.

### 2.4. UDP Deep Dive (Chuyên sâu) ✅
- [x] **Multicast & Broadcast:** Gửi cho nhiều người cùng lúc.
- [x] **NAT Traversal:** Làm sao 2 máy sau NAT nói chuyện trực tiếp (P2P)?
- [x] **Bài tập:** Viết ứng dụng phát hiện thiết bị trong mạng LAN (Service Discovery).

---

## GIAI ĐOẠN 3: BẢO MẬT TẦNG SÂU (SECURITY & ENCRYPTION)
*Mục tiêu: Hiểu cách bảo vệ dữ liệu trên đường truyền.*

### 3.1. Mật mã học Cơ bản (Cryptography Foundations) ✅
- [x] **Symmetric Encryption (AES):** Mã hóa đối xứng - 1 chìa khóa cho cả 2 bên.
- [x] **Asymmetric Encryption (RSA/ECC):** Mã hóa bất đối xứng - Public Key & Private Key.
- [x] **Hash Functions (SHA-256):** Tạo "dấu vân tay" cho dữ liệu.
- [x] **Bài tập:** Tự viết code mã hóa/giải mã một chuỗi ký tự bằng AES.

### 3.2. TLS/HTTPS (Transport Layer Security) ✅
- [x] **TLS Handshake:** Certificate Exchange, Key Agreement (Diffie-Hellman).
- [x] **Certificate Chain:** Root CA → Intermediate CA → Server Cert.
- [x] **mTLS (Mutual TLS):** Cả Client và Server đều phải chứng minh danh tính.
- [x] **Bài tập:** Dùng Wireshark bắt TLS Handshake, giải thích từng bước.

### 3.3. Tấn công & Phòng thủ
- [ ] **Man-in-the-Middle (MITM):** Hacker chen giữa 2 người đang nói chuyện.
- [ ] **DNS Spoofing:** Đánh lừa máy tính đi đến trang web giả.
- [ ] **DDoS:** Tràn ngập Server bằng hàng triệu gói tin giả.
- [ ] **Bài tập:** Tự dựng lab MITM trên mạng nội bộ (ethical hacking).

---

## GIAI ĐOẠN 4: GIAO THỨC THẾ HỆ MỚI (MODERN PROTOCOLS)
*Mục tiêu: Nắm vững công nghệ đang thay đổi Internet.*

### 4.1. HTTP/2 & HTTP/3
- [ ] **HTTP/2:** Multiplexing (gửi nhiều request trên 1 kết nối TCP).
- [ ] **HTTP/3:** Chạy trên QUIC thay vì TCP, tại sao nhanh hơn?
- [ ] **Bài tập:** So sánh tốc độ tải trang HTTP/1.1 vs HTTP/2 vs HTTP/3.

### 4.2. QUIC Protocol (Deep Dive)
- [ ] **0-RTT Handshake:** Kết nối lại không cần bắt tay.
- [ ] **Stream Multiplexing:** Giải quyết Head-of-Line Blocking của TCP.
- [ ] **Connection Migration:** Chuyển từ Wi-Fi sang 4G mà không mất kết nối.
- [ ] **Bài tập:** Phân tích QUIC traffic bằng Wireshark.

### 4.3. DNS Deep Dive
- [ ] **DNS Resolution:** Recursive vs Iterative Query.
- [ ] **DoH/DoT:** DNS over HTTPS / DNS over TLS (Bảo mật DNS).
- [ ] **Dự án:** Viết DNS Proxy chặn quảng cáo (giống Pi-hole).

---

## GIAI ĐOẠN 5: CÁC USE CASE KINH ĐIỂN & DỰ ÁN THỰC CHIẾN
*Mục tiêu: Giải quyết các bài toán mang quy mô công việc.*

### 5.1. Dự án: Port Scanner
- [ ] Quét TCP SYN Scan (giống Nmap).
- [ ] Phát hiện dịch vụ đang chạy trên Port.

### 5.2. Dự án: HTTP Proxy Server
- [ ] Đứng giữa Client và Internet, log traffic.
- [ ] Thêm chức năng cache (tăng tốc duyệt web).

### 5.3. Dự án: Simple VPN (Tunneling)
- [ ] Tạo TUN interface, đóng gói gói tin.
- [ ] Mã hóa traffic bằng AES.

### 5.4. Dự án: Packet-Level Firewall
- [ ] Chặn IP/Port dựa trên luật (Rules).
- [ ] Deep Packet Inspection (DPI) cơ bản.

### 5.5. Dự án: QoS Scheduler (Dự án hiện tại - CAPSTONE)
- [ ] Phân loại traffic và ưu tiên băng thông.
- [ ] Thuật toán **Token Bucket**, **Leaky Bucket**, **WFQ**.
- [ ] Tích hợp tất cả kiến thức từ Giai đoạn 1-4.

---

## CÔNG CỤ BẮT BUỘC (TOOLBOX)
1.  **Wireshark:** Con mắt của kỹ sư mạng.
2.  **Linux CLI:** `ip`, `route`, `tcpdump`, `iptables`, `ss`, `netstat`.
3.  **Nmap:** Quét mạng và phát hiện lỗ hổng.
4.  **Python/Kotlin/C:** Các ngôn ngữ tốt nhất để làm việc với Socket.
5.  **OpenSSL:** Công cụ thao tác Certificate và mã hóa.

---

## CHỨNG CHỈ QUỐC TẾ (CREDENTIALS)
- [ ] **CompTIA Network+** (Nền tảng mạng)
- [ ] **Cisco CCNA** (Routing & Switching chuyên sâu)
- [ ] **CompTIA Security+** (Bảo mật cơ bản)
- [ ] **CEH / OSCP** (Ethical Hacking nâng cao)

---

## LỜI KHẾ ƯỚC
> "Tôi sẽ không dùng thư viện cao cấp khi chưa hiểu tầng thấp bên dưới hoạt động như thế nào."
> "Tôi sẽ đọc ít nhất 1 RFC gốc cho mỗi giao thức tôi sử dụng."
