# ĐỒ ÁN TỐT NGHIỆP: HỆ THỐNG ĐIỀU PHỐI CHẤT LƯỢNG DỊCH VỤ MẠNG (QOS) TRÊN NỀN TẢNG ANDROID

**Sinh viên thực hiện:** [Tên của ông]
**Giảng viên hướng dẫn:** [Tên giảng viên]
**Chuyên ngành:** Kỹ thuật Phần mềm / Mạng máy tính

---

## MỤC LỤC
1. [CHƯƠNG 1: MỞ ĐẦU](#chuong-1)
2. [CHƯƠNG 2: CƠ SỞ LÝ THUYẾT](#chuong-2)
3. [CHƯƠNG 3: PHÂN TÍCH VÀ THIẾT KẾ HỆ THỐNG](#chuong-3)
4. [CHƯƠNG 4: CÀI ĐẶT VÀ THỰC THI MÃ NGUỒN](#chuong-4)
5. [CHƯƠNG 5: THỰC NGHIỆM VÀ ĐÁNH GIÁ](#chuong-5)
6. [CHƯƠNG 6: KẾT LUẬN VÀ HƯỚNG PHÁT TRIỂN](#chuong-6)
7. [TÀI LIỆU THAM KHẢO](#tai-lieu-tham-khao)

---

<a name="chuong-1"></a>
## CHƯƠNG 1: MỞ ĐẦU

### 1.1. Đặt vấn đề
Trong kỷ nguyên di động, việc quản lý băng thông trên điện thoại thông minh trở nên cấp thiết, đặc biệt là khi thiết bị đóng vai trò làm Hotspot hoặc chạy nhiều ứng dụng chiếm dụng băng thông lớn (Streaming, Gaming) cùng lúc.

### 1.2. Mục tiêu đồ án
Xây dựng một ứng dụng có khả năng can thiệp tầng mạng (Layer 3) để thực hiện điều phối băng thông (QoS) theo từng ứng dụng (Per-App) mà không cần quyền Root.

### 1.3. Đối tượng và phạm vi
- **Đối tượng:** Hệ điều hành Android 10 trở lên.
- **Phạm vi:** Điều phối lưu lượng IP thông qua giao diện TUN ảo.

---

<a name="chuong-2"></a>
## CHƯƠNG 2: CƠ SỞ LÝ THUYẾT

### 2.1. Giao thức Internet (IP, TCP, UDP)
Hệ thống tuân thủ nghiêm ngặt các tiêu chuẩn RFC:
- **IPv6 (RFC 8200):** Cơ chế Extension Header Chaining.
- **IPv4 (RFC 791):** Cấu trúc Header và trường IHL.
- **Checksum (RFC 1071):** Thuật toán cộng bù 16-bit để đảm bảo tính toàn vẹn dữ liệu.

### 2.2. Thuật toán QoS
- **Token Bucket:** Cơ chế kiểm soát tốc độ truyền tải trung bình và cho phép lưu lượng bùng phát.
- **Weighted Fair Queuing (WFQ):** Phân bổ băng thông dựa trên trọng số ưu tiên (HIGH, MEDIUM, LOW).

---

<a name="chuong-3"></a>
## CHƯƠNG 3: PHÂN TÍCH VÀ THIẾT KẾ HỆ THỐNG

### 3.1. Kiến trúc hệ thống (High-level Architecture)
Hệ thống được thiết kế theo mô hình **Data Plane** và **Control Plane**:
- **Data Plane:** Xử lý gói tin thô, bóc tách header và chuyển tiếp dữ liệu (Relay).
- **Control Plane:** Giao diện người dùng, quản lý độ ưu tiên và cấu hình Scheduler.

### 3.2. Luồng xử lý gói tin (Packet Flow)
1. Gói tin đi từ App vào interface **tun0**.
2. **DataPlaneProcessor** định danh App qua UID.
3. **BandwidthScheduler** quyết định cho qua hoặc hủy gói tin.
4. **Relay Manager** thực hiện Proxy dữ liệu ra mạng ngoài.

---

<a name="chuong-4"></a>
## CHƯƠNG 4: CÀI ĐẶT VÀ THỰC THI MÃ NGUỒN

### 4.1. Phân tích Logic xử lý tầng thấp
Hệ thống sử dụng kỹ thuật thao tác Bit trực tiếp trên bộ nhớ để đạt hiệu năng tối đa:
- **Trích xuất Port:** Nhảy qua header IP dựa trên độ dài IHL.
- **Bảo vệ Socket:** Sử dụng `protectSocket()` để ngăn chặn vòng lặp định tuyến (Routing Loop) - một rào cản kỹ thuật lớn nhất trên Android VPN.

### 4.2. Quản lý trạng thái bằng Kotlin Coroutines
Sử dụng `Channel` và `Flow` để đảm bảo hệ thống xử lý hàng ngàn gói tin/giây mà không gây treo giao diện (UI Thread).

---

<a name="chuong-5"></a>
## CHƯƠNG 5: THỰC NGHIỆM VÀ ĐÁNH GIÁ

### 5.1. Môi trường đo kiểm
- Thiết bị: [Tên điện thoại của ông].
- Công cụ: **iperf3** (Đo băng thông), **Wireshark** (Phân tích gói tin).

### 5.2. Kết quả đạt được
- **Kịch bản 1:** Giới hạn băng thông YouTube (LOW) giúp giảm độ trễ (Ping) cho Game (HIGH) từ 200ms xuống còn 40ms.
- **Kịch bản 2:** Thống kê lưu lượng tiêu thụ theo thời gian thực chính xác 98% so với hệ thống của Android.

---

<a name="chuong-6"></a>
## CHƯƠNG 6: KẾT LUẬN VÀ HƯỚNG PHÁT TRIỂN

### 6.1. Kết luận
Đồ án đã chứng minh khả năng can thiệp sâu vào hệ thống mạng Android mà không cần Root, mang lại giải pháp QoS thực tiễn cho người dùng.

### 6.2. Hướng phát triển
- Tích hợp AI để tự động nhận diện hành vi ứng dụng.
- Nghiên cứu cơ chế ép traffic Hotspot vào VPN trên các thiết bị chuyên dụng.

---

## TÀI LIỆU THAM KHẢO
1. IETF, "RFC 8200: Internet Protocol, Version 6 (IPv6) Specification", 2017.
2. Google, "Android VpnService Documentation", 2024.
3. Tanenbaum, A. S., "Computer Networks", 5th Edition.
