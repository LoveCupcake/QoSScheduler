# CHƯƠNG 2: CƠ SỞ LÝ THUYẾT

## 2.1. Mô hình phân tầng mạng OSI và TCP/IP

### 2.1.1. Tầng Mạng (Network Layer - Layer 3)
Tầng Mạng chịu trách nhiệm định tuyến gói tin từ nguồn tới đích thông qua các bộ định tuyến trung gian. Giao thức chủ đạo tại tầng này là **Internet Protocol (IP)**, tồn tại ở hai phiên bản chính: IPv4 và IPv6.

Trong dự án QoS Scheduler, toàn bộ hoạt động can thiệp mạng diễn ra tại tầng này. Ứng dụng sử dụng giao diện mạng ảo TUN (Network TUNnel) để chặn bắt gói tin IP trước khi chúng được hệ điều hành Android gửi đi. Điều này cho phép hệ thống "nhìn thấy" mọi gói tin đi qua thiết bị và đưa ra quyết định điều phối.

### 2.1.2. Tầng Giao vận (Transport Layer - Layer 4)
Tầng Giao vận cung cấp kênh truyền dữ liệu giữa hai ứng dụng. Hai giao thức chính tại tầng này là:

- **TCP (Transmission Control Protocol):** Giao thức hướng kết nối, đảm bảo dữ liệu được truyền đúng thứ tự và không bị mất. TCP sử dụng cơ chế bắt tay ba bước (Three-way Handshake) để thiết lập kết nối và cơ chế số thứ tự (Sequence Number) để kiểm soát luồng dữ liệu.
- **UDP (User Datagram Protocol):** Giao thức không kết nối, không đảm bảo truyền tin nhưng có độ trễ thấp hơn TCP. Thích hợp cho các ứng dụng thời gian thực như VoIP và Gaming.

---

## 2.2. Giao thức Internet phiên bản 4 (IPv4 - RFC 791)

### 2.2.1. Cấu trúc Header IPv4
Header IPv4 có độ dài tối thiểu 20 byte và tối đa 60 byte. Cấu trúc chi tiết như sau:

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|Version|  IHL  |    DSCP   |ECN|         Total Length          |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|         Identification        |Flags|     Fragment Offset     |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|  Time to Live |    Protocol   |        Header Checksum        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       Source IP Address                       |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                    Destination IP Address                     |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

Các trường quan trọng mà dự án sử dụng:
- **Version (4 bit):** Xác định phiên bản IP. Giá trị 4 cho IPv4, 6 cho IPv6. Trong mã nguồn, giá trị này được trích xuất bằng phép dịch bit: `(byte[0] >> 4)`.
- **IHL - Internet Header Length (4 bit):** Độ dài header tính theo đơn vị 32-bit word. Giá trị tối thiểu là 5 (tương đương 20 byte). Công thức chuyển đổi sang byte: `IHL × 4`. Giá trị này cực kỳ quan trọng vì nó cho hệ thống biết vị trí bắt đầu của TCP/UDP Header.
- **Protocol (8 bit, vị trí byte thứ 9):** Xác định giao thức tầng trên. Giá trị 6 = TCP, 17 = UDP. Dự án sử dụng trường này để quyết định cách xử lý gói tin (chuyển cho TcpRelayManager hay UdpRelayManager).
- **Source IP (32 bit, byte 12-15):** Địa chỉ IP nguồn. Dự án đọc 4 byte này và chuyển thành chuỗi dạng "a.b.c.d" bằng phép `AND 0xFF` để loại bỏ dấu của kiểu Byte trong Java/Kotlin.
- **Destination IP (32 bit, byte 16-19):** Địa chỉ IP đích. Cách xử lý tương tự Source IP.

### 2.2.2. Kỹ thuật thao tác Bit (Bitwise Operations)
Trong lập trình mạng tầng thấp, việc thao tác bit là bắt buộc vì các trường thông tin trong header IP không được đóng gói theo ranh giới byte mà theo ranh giới bit. Ví dụ, byte đầu tiên của IPv4 chứa đồng thời cả Version (4 bit cao) và IHL (4 bit thấp):

```
Byte[0] = 0x45 = 0100 0101 (nhị phân)
           ^^^^------------ Version = 4 (IPv4)
                ^^^^-------- IHL = 5 (Header dài 20 byte)
```

Các phép toán bit được sử dụng:
- `AND 0xFF`: Chuyển byte có dấu (-128..127) sang số không dấu (0..255). Cần thiết vì Java/Kotlin sử dụng kiểu Byte có dấu.
- `SHR 4` (Shift Right 4): Dịch phải 4 bit để lấy 4 bit cao (Version).
- `AND 0x0F`: Lấy 4 bit thấp (IHL).
- `SHL 8 OR byte2`: Ghép 2 byte thành số 16-bit (dùng khi đọc Port).

---

## 2.3. Giao thức Internet phiên bản 6 (IPv6 - RFC 8200)

### 2.3.1. Cấu trúc Fixed Header (40 byte cố định)

```
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|Version| Traffic Class |           Flow Label                  |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|         Payload Length        |  Next Header  |   Hop Limit   |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                                                               |
+                     Source Address (128 bit)                   +
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                                                               |
+                  Destination Address (128 bit)                 +
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

Khác biệt lớn nhất so với IPv4: Header IPv6 luôn có độ dài cố định 40 byte, không có trường IHL. Thay vào đó, IPv6 sử dụng cơ chế **Extension Header Chaining**.

### 2.3.2. Cơ chế Extension Header Chaining
Theo RFC 8200:
> *"Extension headers are not examined or processed by any node along a packet's delivery path, until the packet reaches the node identified in the Destination Address field."*

Trường **Next Header** (byte thứ 6) đóng vai trò là "mắt xích" liên kết các header với nhau. Nếu Next Header = 6 (TCP), hệ thống biết rằng ngay sau 40 byte header cố định là TCP Header. Tuy nhiên, nếu Next Header = 0 (Hop-by-Hop), hệ thống phải "nhảy" qua header mở rộng đó trước khi tìm được TCP/UDP.

**Bảng mã Next Header:**

| Mã | Tên Header | Độ dài |
|:---|:-----------|:-------|
| 0 | Hop-by-Hop Options | `(HdrExtLen × 8) + 8` byte |
| 6 | TCP | Theo Data Offset |
| 17 | UDP | 8 byte cố định |
| 43 | Routing Header | `(HdrExtLen × 8) + 8` byte |
| 44 | Fragment Header | 8 byte cố định |
| 58 | ICMPv6 | Biến đổi |
| 60 | Destination Options | `(HdrExtLen × 8) + 8` byte |

Công thức tính độ dài Extension Header:
$$ExtensionLength = (HdrExtLen \times 8) + 8 \text{ (byte)}$$

Ngoại lệ: Fragment Header luôn có độ dài cố định 8 byte, không có trường HdrExtLen.

---

## 2.4. Giao thức TCP (RFC 9293)

### 2.4.1. Cấu trúc Header TCP

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|          Source Port          |       Destination Port        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                        Sequence Number                        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                    Acknowledgment Number                      |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|  Data |           |U|A|P|R|S|F|                               |
| Offset| Reserved  |R|C|S|S|Y|I|            Window             |
|       |           |G|K|H|T|N|N|                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|           Checksum            |         Urgent Pointer        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

Các trường mà dự án trích xuất và sử dụng:
- **Source/Destination Port (16 bit mỗi trường):** Định danh ứng dụng tầng trên. Dự án đọc 2 byte đầu tiên sau IP Header để lấy Source Port, 2 byte tiếp theo để lấy Destination Port.
- **Sequence Number (32 bit):** Số thứ tự byte đầu tiên trong dữ liệu. Module `TcpRelayManager` phải duy trì giá trị này chính xác cho mỗi kết nối để tránh lỗi "TCP Out of Order".
- **Acknowledgment Number (32 bit):** Xác nhận byte tiếp theo mà phía gửi mong đợi nhận được.
- **Data Offset (4 bit):** Độ dài TCP Header tính theo 32-bit word. Trích xuất: `(byte[12] >> 4) × 4`.
- **Control Flags (6 bit):** Các cờ điều khiển trạng thái kết nối TCP:
  - **SYN (bit 1):** Yêu cầu thiết lập kết nối mới.
  - **ACK (bit 4):** Xác nhận đã nhận dữ liệu.
  - **FIN (bit 0):** Yêu cầu đóng kết nối.
  - **RST (bit 2):** Hủy kết nối đột ngột.
  - **PSH (bit 3):** Yêu cầu đẩy dữ liệu lên tầng ứng dụng ngay lập tức.
- **Window Size (16 bit):** Kích thước cửa sổ nhận. Dự án đọc trường này để thực hiện cơ chế Backpressure.

### 2.4.2. Quy trình bắt tay ba bước (Three-way Handshake)
Đây là quy trình thiết lập kết nối TCP mà module `TcpRelayManager` phải giả lập:

1. **SYN:** Ứng dụng gửi gói tin với cờ SYN=1, kèm theo Sequence Number ban đầu (ISN).
2. **SYN-ACK:** Relay trả về gói tin SYN=1, ACK=1 với Sequence Number riêng và Acknowledgment = ISN + 1.
3. **ACK:** Ứng dụng gửi ACK=1, xác nhận kết nối đã thiết lập.

Sau bước 3, kết nối chuyển sang trạng thái ESTABLISHED và dữ liệu bắt đầu được truyền.

---

## 2.5. Thuật toán tính Checksum (RFC 1071)

### 2.5.1. Nguyên lý
Theo RFC 1071:
> *"The checksum algorithm is simply to form the one's complement sum of the data, and then take the one's complement of the result."*

### 2.5.2. Các bước thực hiện
1. Chia dữ liệu cần kiểm tra thành các khối 16-bit (2 byte).
2. Cộng dồn tất cả các khối theo phép cộng bù 1 (one's complement addition).
3. Nếu kết quả vượt quá 16-bit (có carry), cộng phần carry ngược lại vào kết quả:
   $$\text{while } (sum >> 16 \neq 0): sum = (sum \text{ AND } 0xFFFF) + (sum >> 16)$$
4. Lấy phủ định (NOT) của kết quả cuối cùng.

### 2.5.3. Pseudo-header cho TCP/UDP Checksum
Khi tính checksum cho TCP hoặc UDP, ngoài bản thân header và payload, cần bổ sung thêm một Pseudo-header gồm: Source IP (4 byte), Destination IP (4 byte), Zero (1 byte), Protocol (1 byte), và TCP/UDP Length (2 byte).

---

## 2.6. Thuật toán Token Bucket

### 2.6.1. Nguyên lý hoạt động
Token Bucket là thuật toán kiểm soát tốc độ (Rate Limiting) được sử dụng rộng rãi trong các bộ định tuyến và tường lửa. Ý tưởng cốt lõi: Mỗi gói tin muốn đi qua phải "trả phí" bằng các token. Token được nạp vào xô với tốc độ cố định.

### 2.6.2. Công thức toán học
Số lượng token tại thời điểm $t$:
$$T(t) = \min\Big(C,\ T(t_{prev}) + (t - t_{prev}) \times R\Big)$$

Trong đó:
- $C$: Dung lượng tối đa của xô (Burst Size) - byte.
- $R$: Tốc độ nạp token (Refill Rate) - byte/giây.
- $T(t_{prev})$: Số token còn lại tại thời điểm trước đó.

Điều kiện cho phép gói tin kích thước $S$ đi qua:
$$\text{if } T(t) \geq S: \text{ ALLOW, } T(t) = T(t) - S$$
$$\text{if } T(t) < S: \text{ DROP}$$

### 2.6.3. Ưu điểm
- Cho phép lưu lượng bùng phát (Burst) trong giới hạn dung lượng xô.
- Kiểm soát tốc độ trung bình dài hạn bằng tốc độ nạp token.
- Độ phức tạp O(1) cho mỗi gói tin - phù hợp xử lý thời gian thực.

---

## 2.7. Thuật toán Weighted Fair Queuing (WFQ)

### 2.7.1. Nguyên lý
WFQ phân bổ băng thông cho các luồng dữ liệu dựa trên trọng số (Weight). Luồng có trọng số cao hơn sẽ nhận được tỷ lệ băng thông lớn hơn.

### 2.7.2. Công thức phân bổ
Băng thông thực tế cho luồng $i$:
$$B_i = R_{total} \times \frac{W_i}{\displaystyle\sum_{j=1}^{n} W_j}$$

Trong dự án, các trọng số được định nghĩa:
- **HIGH** (Ưu tiên cao - Gaming, VoIP): $W = 4$
- **MEDIUM** (Ưu tiên trung bình - Web): $W = 2$  
- **LOW** (Ưu tiên thấp - Download): $W = 1$

**Ví dụ:** Nếu $R_{total} = 10$ Mbps, có 1 App HIGH và 1 App LOW:
$$B_{HIGH} = 10 \times \frac{4}{4+1} = 8 \text{ Mbps}$$
$$B_{LOW} = 10 \times \frac{1}{4+1} = 2 \text{ Mbps}$$

---

## 2.8. Công nghệ VPN trên Android

### 2.8.1. VpnService API
Android cung cấp lớp `VpnService` cho phép ứng dụng tạo một giao diện mạng ảo (TUN interface) mà không cần quyền Root. TUN hoạt động ở Layer 3 (IP), cho phép ứng dụng:
- Chặn bắt toàn bộ gói tin IP đi ra từ thiết bị.
- Đọc và phân tích nội dung gói tin.
- Quyết định chuyển tiếp, sửa đổi hoặc hủy bỏ gói tin.

### 2.8.2. Cơ chế bảo vệ Socket (Socket Protection)
Theo Android Developer Documentation:
> *"The protect(Socket) method protects a socket from VPN routing. After a socket is protected, its outgoing packets will be sent directly through one of the physical interfaces instead of through the VPN."*

Đây là cơ chế sống còn để tránh **Infinite Routing Loop**: Khi VPN muốn chuyển tiếp gói tin ra Internet, nó phải mở một Socket thật. Nếu Socket này không được bảo vệ, gói tin sẽ bị VPN chặn lại lần nữa, tạo thành vòng lặp vô tận.

### 2.8.3. Định danh ứng dụng (UID Resolution)
Android cung cấp API `ConnectivityManager.getConnectionOwnerUid()` (từ Android 10) cho phép xác định UID (User ID) của ứng dụng sở hữu một kết nối mạng dựa trên thông tin 5-tuple (Protocol, Source IP, Source Port, Destination IP, Destination Port). Đây là nền tảng cho tính năng Per-App QoS.

---

## 2.9. Kỹ thuật Deep Packet Inspection (DPI)
DPI là kỹ thuật phân tích nội dung gói tin để phân loại loại ứng dụng hoặc dịch vụ. Trong phạm vi dự án, DPI được triển khai ở mức "DPI Lite" - phân loại dựa trên số hiệu Port đích:
- Port 53: DNS
- Port 80, 443: Web/HTTPS
- Port 5060-5061: VoIP (SIP)
- Port 27015-27016: Gaming (Steam)

---

## 2.10. Kotlin Coroutines và lập trình bất đồng bộ
Kotlin Coroutines là framework xử lý bất đồng bộ nhẹ (lightweight concurrency). So với Thread truyền thống, Coroutines tiêu tốn ít tài nguyên hơn (~100 byte/coroutine so với ~1MB/thread) và hỗ trợ structured concurrency. Dự án sử dụng:
- **Channel:** Hàng đợi bất đồng bộ để truyền gói tin giữa các tầng xử lý.
- **StateFlow:** Luồng dữ liệu phản ứng để đồng bộ trạng thái giữa Service và UI.
- **SupervisorJob:** Đảm bảo lỗi ở một coroutine con không làm chết toàn bộ hệ thống.
