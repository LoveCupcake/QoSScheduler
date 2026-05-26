# HƯỚNG DẪN CHẾ TẠO GÓI TIN MẠNG BẰNG TAY
## (Dành cho người mới bắt đầu - Từng bước, từng byte)

---

# PHẦN 0: TRƯỚC KHI BẮT ĐẦU - NHỚ 2 QUY TẮC VÀNG

### Quy tắc 1: Big-Endian (Network Byte Order)
Internet quy định byte **QUAN TRỌNG NHẤT** phải đứng TRƯỚC.

Ví dụ: Số `0x1234` được lưu như sau:
```
Little-Endian (máy tính thông thường): [0x34] [0x12]  ← Sai cho mạng!
Big-Endian    (Internet chuẩn)        : [0x12] [0x34]  ← Đúng!
```
→ Đó là lý do ta dùng `ByteBuffer` thay vì viết byte thủ công — nó tự xử lý Big-Endian.

### Quy tắc 2: Mỗi giao thức có một "bản đồ" riêng
Ai cũng phải tuân thủ bản đồ này. Nếu sai 1 byte, gói tin bị hủy.

---

# PHẦN 1: BẢN ĐỒ IPv4 HEADER (RFC 791)

Header IPv4 luôn bắt đầu từ byte 0 và dài tối thiểu **20 byte**. Hãy vẽ nó ra giấy:

```
Byte:  0        1        2        3
      +--------+--------+--------+--------+
   0  |Ver/IHL | ToS    | Total Length    |
      +--------+--------+--------+--------+
   4  |   Identification       |Flg|Frag  |
      +--------+--------+--------+--------+
   8  |  TTL   |Protocol| Header Checksum |
      +--------+--------+--------+--------+
  12  |         Source IP Address         |
      +--------+--------+--------+--------+
  16  |       Destination IP Address      |
      +--------+--------+--------+--------+
```

### Giải thích từng ô (từng byte):

**Byte 0 = Version + IHL gộp lại:**
- 4 bit cao = Version IP (4 hoặc 6)
- 4 bit thấp = IHL (Internet Header Length), đơn vị là "số từ 4-byte"
- Nếu version=4, IHL=5 → Ta viết: `4 << 4 | 5 = 0x45`
- Đây là lý do code viết: `buffer.put(0, 0x45.toByte())`

**Byte 1 = Type of Service (ToS):**
- Thường để `0` (không ưu tiên đặc biệt)
- `buffer.put(1, 0)`

**Byte 2-3 = Total Length:**
- Tổng độ dài của TOÀN BỘ gói tin (IP Header + TCP Header + Data)
- Ví dụ: 20 + 20 + 0 = 40 byte → ghi `0x0028`
- `buffer.putShort(2, 40.toShort())`

**Byte 4-5 = Identification:**
- Mã nhận dạng ngẫu nhiên, dùng khi IP bị phân mảnh
- Ta ghi ngẫu nhiên: `buffer.putShort(4, (Random * 65535).toShort())`

**Byte 6-7 = Flags + Fragment Offset:**
- Thường để `0` (không phân mảnh)
- `buffer.putShort(6, 0)`

**Byte 8 = TTL (Time to Live):**
- Số bước nhảy Router tối đa. Linux dùng 64, Windows dùng 128.
- Ta dùng 64: `buffer.put(8, 64)`

**Byte 9 = Protocol:**
- Quan trọng! Xác định Header tiếp theo là gì:
  - `6`  = TCP
  - `17` = UDP
  - `1`  = ICMP (ping)
- `buffer.put(9, 6)` // TCP

**Byte 10-11 = Header Checksum:**
- Tính toán bằng thuật toán RFC 1071 (xem Phần 3)
- Ghi tạm `0` trước, tính sau: `buffer.putShort(10, 0)`

**Byte 12-15 = Source IP:**
- 4 byte cho địa chỉ IP nguồn
- `"192.168.1.1"` → `[192][168][1][1]`
- `buffer.position(12); buffer.put(ipToBytes("192.168.1.1"))`

**Byte 16-19 = Destination IP:**
- Tương tự, 4 byte cho IP đích
- `buffer.put(ipToBytes("8.8.8.8"))`

---

# PHẦN 2: BẢN ĐỒ TCP HEADER (RFC 793)

TCP Header bắt đầu ngay sau IP Header (byte 20 nếu IPv4 không có options).

```
Byte:  0        1        2        3
      +--------+--------+--------+--------+
   0  |    Source Port  |  Destination Port|
      +--------+--------+--------+--------+
   4  |           Sequence Number         |
      +--------+--------+--------+--------+
   8  |        Acknowledgment Number      |
      +--------+--------+--------+--------+
  12  |DO|Rsv | Flags   |   Window Size   |
      +--------+--------+--------+--------+
  16  |    Checksum     |  Urgent Pointer  |
      +--------+--------+--------+--------+
```

### Giải thích từng ô:

**Byte 0-1 (Offset 20-21 tuyệt đối) = Source Port:**
- 2 byte, big-endian
- Ví dụ port 1234 → `0x04D2`
- `buffer.putShort(tcpStart + 0, 1234.toShort())`

**Byte 2-3 (Offset 22-23) = Destination Port:**
- Ví dụ port 80 → `0x0050`
- `buffer.putShort(tcpStart + 2, 80.toShort())`

**Byte 4-7 (Offset 24-27) = Sequence Number:**
- 4 byte, số thứ tự byte đầu tiên trong data
- Khi SYN, đây là ISN (Initial Sequence Number) - chọn ngẫu nhiên
- `buffer.putInt(tcpStart + 4, seqNum.toInt())`

**Byte 8-11 (Offset 28-31) = Acknowledgment Number:**
- 4 byte, số thứ tự byte TIẾP THEO mà ta mong nhận được
- Chỉ có ý nghĩa khi cờ ACK = 1
- `buffer.putInt(tcpStart + 8, ackNum.toInt())`

**Byte 12-13 (Offset 32-33) = Data Offset + Flags:**
Đây là trường phức tạp nhất, ta phải ghép bit thủ công:

```
Bit 15-12: Data Offset (4 bit) = độ dài TCP header / 4
Bit 11-6 : Reserved (6 bit) = luôn là 000000
Bit 5    : URG
Bit 4    : ACK   = 0x10
Bit 3    : PSH   = 0x08
Bit 2    : RST   = 0x04
Bit 1    : SYN   = 0x02
Bit 0    : FIN   = 0x01
```

Ví dụ muốn gửi SYN+ACK, Data Offset=5:
```
flagsBits = 5 shl 12           // = 0x5000 (Data Offset)
flagsBits = flagsBits or 0x02  // = 0x5002 (bật SYN)
flagsBits = flagsBits or 0x10  // = 0x5012 (bật ACK)
```

**Byte 14-15 (Offset 34-35) = Window Size:**
- Kích thước buffer nhận. Ta thường đặt tối đa: `65535`
- `buffer.putShort(tcpStart + 14, 65535.toShort())`

**Byte 16-17 (Offset 36-37) = Checksum:**
- Ghi `0` trước, tính sau (xem Phần 3)
- `buffer.putShort(tcpStart + 16, 0)`

**Byte 18-19 (Offset 38-39) = Urgent Pointer:**
- Thường `0`
- `buffer.putShort(tcpStart + 18, 0)`

---

# PHẦN 3: TÍNH CHECKSUM BẰNG TAY (RFC 1071)

### Ý nghĩa của Checksum:
Checksum là một "dấu vân tay" của gói tin. Nếu có bất kỳ bit nào bị lỗi trong quá trình truyền, checksum sẽ sai và gói tin bị hủy.

### Thuật toán One's Complement Sum - 4 bước:

**Bước 1:** Chia dữ liệu thành các cặp 2-byte (16-bit words).

**Bước 2:** Cộng dồn tất cả vào một biến 32-bit (để chứa carry).

**Bước 3:** Xử lý carry - nếu kết quả > 16-bit, lấy 16-bit cao cộng vào 16-bit thấp:
```
while (sum >> 16 != 0):
    sum = (sum & 0xFFFF) + (sum >> 16)
```

**Bước 4:** Lấy NOT của kết quả.

### Ví dụ tính tay - IP Checksum cho header đơn giản:

Giả sử ta có IP Header (rút gọn, chỉ 3 cặp để dễ tính):
```
0x4500  0x0028  0x1234
0x4006  0x0000  ← Checksum đang là 0
```

Bước 2 - Cộng dồn:
```
sum = 0x4500 + 0x0028 + 0x1234 + 0x4006 + 0x0000
sum = 0x9E62
```

Bước 3 - Không có carry (0x9E62 < 0xFFFF, bỏ qua).

Bước 4 - NOT:
```
~0x9E62 = 0x619D
```
→ Checksum = `0x619D` → Ghi vào byte 10-11.

### Tại sao phải dùng Pseudo-header cho TCP?

TCP Checksum không chỉ bảo vệ TCP Header, mà còn bao gồm thêm một "Pseudo-header ảo":

```
+--------+--------+--------+--------+
|       Source IP Address           |
+--------+--------+--------+--------+
|    Destination IP Address         |
+--------+--------+--------+--------+
| 0x00   |Protocol|   TCP Length    |
+--------+--------+--------+--------+
```

**Lý do:** Để đảm bảo gói tin không bị "giao nhầm" địa chỉ ở tầng IP. Nếu Router hoán đổi Source/Dest IP, TCP Checksum sẽ sai → Phát hiện được lỗi.

---

# PHẦN 4: BÀI TẬP LÀM TAY — CHẾ GÓI TIN TỪ SỐ 0

## BÀI 1: TCP SYN (Yêu cầu kết nối)

**Đề bài:** Máy `10.0.0.1` (port 5000) muốn kết nối TCP tới `1.1.1.1` (port 443).
Hãy chế tạo gói tin SYN hoàn chỉnh. Cho ISN = 1, ID = 0xABCD.

### Bước 1: Phân tích đề — Tại sao gói tin dài 40 byte?
```
Gói SYN không mang dữ liệu (payload = 0), nên:
  - IPv4 Header: Luôn tối thiểu 20 byte (IHL=5, 5×4=20)
  - TCP Header:  Luôn tối thiểu 20 byte (Data Offset=5, 5×4=20)
  - Payload:     0 byte
  → Total = 20 + 20 + 0 = 40 byte
```

### Bước 2: Điền IPv4 Header — 20 byte đầu tiên
```
Offset  Hex    Giải thích chi tiết
------  -----  -------------------------------------------------------
  0     0x45   Version=4 (0100), IHL=5 (0101). Ghép: 0100_0101 = 0x45
  1     0x00   Type of Service. Không ưu tiên → 0
  2-3   0x0028 Total Length = 40. Đổi: 40₁₀ = 0x28. Big-Endian: 00 28
  4-5   0xABCD Identification (đề cho). Dùng để ghép phân mảnh
  6-7   0x0000 Flags=0, Fragment Offset=0 (không phân mảnh)
  8     0x40   TTL = 64. Đổi: 64₁₀ = 0x40
  9     0x06   Protocol = TCP. Tra bảng IANA: TCP = 6
 10-11  0x0000 Header Checksum = 0 (GHI TẠM, tính ở Bước 4)
 12-15  0x0A000001  Source IP = 10.0.0.1
               10₁₀=0x0A, 0₁₀=0x00, 0₁₀=0x00, 1₁₀=0x01
 16-19  0x01010101  Dest IP = 1.1.1.1
               1₁₀=0x01, 1₁₀=0x01, 1₁₀=0x01, 1₁₀=0x01
```

### Bước 3: Điền TCP Header — byte 20 đến 39
```
Offset  Hex    Giải thích chi tiết
------  -----  -------------------------------------------------------
 20-21  0x1388 Source Port = 5000. Đổi: 5000₁₀ = 0x1388
 22-23  0x01BB Dest Port = 443. Đổi: 443₁₀ = 0x01BB
 24-27  0x00000001  Sequence Number = 1 (ISN đề cho)
 28-31  0x00000000  ACK Number = 0 (SYN chưa nhận gì, nên = 0)
 32     0x50   Data Offset = 5 (vì TCP header dài 20 byte, 20/4=5)
               Đặt vào 4 bit cao: 5 << 4 = 0101_0000 = 0x50
 33     0x02   Flags: Chỉ bật SYN.
               Tra bảng: FIN=0x01, SYN=0x02, RST=0x04, PSH=0x08, ACK=0x10
               Chỉ SYN → 0000_0010 = 0x02
 34-35  0xFFFF Window Size = 65535 (tối đa, báo "tôi nhận được nhiều")
 36-37  0x0000 TCP Checksum = 0 (GHI TẠM, tính ở Bước 5)
 38-39  0x0000 Urgent Pointer = 0 (không dùng)
```

### Bước 4: Tính IP Header Checksum (RFC 1071) — TỪNG DÒNG

Lấy 20 byte IP Header, chia thành 10 cặp 16-bit (checksum đang = 0):
```
Word 0:  0x4500   (Version/IHL + ToS)
Word 1:  0x0028   (Total Length)
Word 2:  0xABCD   (Identification)
Word 3:  0x0000   (Flags/Fragment)
Word 4:  0x4006   (TTL=0x40 + Protocol=0x06)
Word 5:  0x0000   (Checksum placeholder)
Word 6:  0x0A00   (Source IP byte 1-2)
Word 7:  0x0001   (Source IP byte 3-4)
Word 8:  0x0101   (Dest IP byte 1-2)
Word 9:  0x0101   (Dest IP byte 3-4)

Cộng dồn:
  0x4500
+ 0x0028 = 0x4528
+ 0xABCD = 0xF0F5
+ 0x0000 = 0xF0F5
+ 0x4006 = 0x130FB  ← Tràn 16-bit! Có carry
+ 0x0000 = 0x130FB
+ 0x0A00 = 0x13AFB
+ 0x0001 = 0x13AFC
+ 0x0101 = 0x13BFD
+ 0x0101 = 0x13CFE

Xử lý carry (fold):
  0x13CFE → phần cao = 0x1, phần thấp = 0x3CFE
  0x3CFE + 0x0001 = 0x3CFF

Lấy NOT:
  ~0x3CFF = 0xC300

→ IP Checksum = 0xC300. Ghi vào byte 10-11.
```

### Bước 5: Tính TCP Checksum — TỪNG DÒNG

**5a. Tạo Pseudo-header (12 byte ảo, không nằm trong gói tin):**
```
Word P0: 0x0A00   (Source IP byte 1-2)
Word P1: 0x0001   (Source IP byte 3-4)
Word P2: 0x0101   (Dest IP byte 1-2)
Word P3: 0x0101   (Dest IP byte 3-4)
Word P4: 0x0006   (Zero + Protocol TCP=6)
Word P5: 0x0014   (TCP Length = 20 byte = 0x14)
```

**5b. Cộng Pseudo-header + toàn bộ TCP Header:**
```
Pseudo-header:
  0x0A00 + 0x0001 + 0x0101 + 0x0101 + 0x0006 + 0x0014 = 0x0B1D

TCP Header (10 words):
  0x1388 + 0x01BB + 0x0000 + 0x0001 + 0x0000
+ 0x0000 + 0x5002 + 0xFFFF + 0x0000 + 0x0000

TCP cộng dồn:
  0x1388 + 0x01BB = 0x1543
+ 0x0000 = 0x1543
+ 0x0001 = 0x1544
+ 0x0000 = 0x1544
+ 0x0000 = 0x1544
+ 0x5002 = 0x6546
+ 0xFFFF = 0x16545  ← Tràn!
+ 0x0000 = 0x16545
+ 0x0000 = 0x16545

Tổng = Pseudo + TCP = 0x0B1D + 0x16545 = 0x17062

Fold carry:
  0x17062 → 0x7062 + 0x0001 = 0x7063

NOT:
  ~0x7063 = 0x8F9C

→ TCP Checksum = 0x8F9C. Ghi vào byte 36-37.
```

### Bước 6: Gói tin SYN hoàn chỉnh (40 byte)
```
00: 45 00 00 28 AB CD 00 00  40 06 C3 00 0A 00 00 01
10: 01 01 01 01 13 88 01 BB  00 00 00 01 00 00 00 00
20: 50 02 FF FF 8F 9C 00 00
```

---

## BÀI 2: TCP SYN-ACK (Server trả lời)

**Đề bài:** Server `1.1.1.1:443` trả lời SYN-ACK cho `10.0.0.1:5000`.
Cho Server ISN = 5000 (0x1388). ACK = Client ISN + 1 = 2.

### Phân tích sự khác biệt so với SYN:
```
Thay đổi so với Bài 1:
  - Source/Dest IP hoán đổi (server trả về)
  - Source/Dest Port hoán đổi
  - Sequence Number = 5000 (ISN của server)
  - ACK Number = 2 (= Client ISN 1 + 1)
  - Flags = SYN + ACK = 0x02 | 0x10 = 0x12
  - Tất cả Checksum phải tính lại!
```

### Bước 1: Điền IPv4 Header — 20 byte (chú ý IP đảo ngược!)
```
Offset  Hex    Giải thích chi tiết
------  -----  -------------------------------------------------------
  0     0x45   Giống Bài 1: Version=4, IHL=5
  1     0x00   ToS = 0
  2-3   0x0028 Total Length = 40 (giống SYN, không data)
  4-5   0x1234 ID = 0x1234 (server chọn ID khác client)
  6-7   0x0000 Flags/Fragment = 0
  8     0x40   TTL = 64
  9     0x06   Protocol = TCP (6)
 10-11  0x0000 Checksum = 0 (GHI TẠM)
 12     0x01   ┐
 13     0x01   │ Source IP = 1.1.1.1  ← ĐẢO! Server là nguồn
 14     0x01   │
 15     0x01   ┘
 16     0x0A   ┐
 17     0x00   │ Dest IP = 10.0.0.1  ← ĐẢO! Client là đích
 18     0x00   │
 19     0x01   ┘
```

### Bước 2: Điền TCP Header — byte 20 đến 39
```
Offset  Hex    Giải thích chi tiết
------  -----  -------------------------------------------------------
 20-21  0x01BB Source Port = 443 ← ĐẢO! Server port là nguồn
               443₁₀ = 0x01BB
 22-23  0x1388 Dest Port = 5000 ← ĐẢO! Client port là đích
               5000₁₀ = 0x1388
 24-27  0x00001388  Sequence Number = 5000 (ISN CỦA SERVER)
               Server chọn ISN riêng, không liên quan Client ISN
               5000₁₀ = 0x1388
 28-31  0x00000002  ACK Number = 2
               = Client ISN (1) + 1
               Ý nghĩa: "Tôi đã nhận SYN của bạn, byte tiếp theo
               tôi mong là số 2"
 32     0x50   Data Offset = 5 (20 byte / 4 = 5)
 33     0x12   Flags = SYN + ACK
               SYN = 0000_0010 (0x02)
               ACK = 0001_0000 (0x10)
               OR  = 0001_0010 (0x12)  ← HAI cờ cùng bật!
 34-35  0xFFFF Window = 65535
 36-37  0x0000 TCP Checksum = 0 (GHI TẠM)
 38-39  0x0000 Urgent = 0
```

**Điểm quan trọng:** Flags byte 33 = `0x12` vì:
```
SYN = 0000_0010 = 0x02
ACK = 0001_0000 = 0x10
OR  = 0001_0010 = 0x12  ← Cả SYN lẫn ACK đều bật
```

### Đáp án — Tính IP Checksum:
```
Word 0: 0x4500  Word 1: 0x0028  Word 2: 0x1234
Word 3: 0x0000  Word 4: 0x4006  Word 5: 0x0000
Word 6: 0x0101  Word 7: 0x0101  Word 8: 0x0A00
Word 9: 0x0001

Cộng dồn:
  0x4500 + 0x0028 = 0x4528
+ 0x1234 = 0x575C
+ 0x0000 = 0x575C
+ 0x4006 = 0x9762
+ 0x0000 = 0x9762
+ 0x0101 = 0x9863
+ 0x0101 = 0x9964
+ 0x0A00 = 0xA364
+ 0x0001 = 0xA365

Không có carry (< 0xFFFF). NOT:
  ~0xA365 = 0x5C9A

→ IP Checksum = 0x5C9A
```

### Đáp án — Tính TCP Checksum:
```
Pseudo-header (IP đảo so với Bài 1):
  0x0101 + 0x0101 + 0x0A00 + 0x0001 + 0x0006 + 0x0014 = 0x0B1D

TCP Header:
  0x01BB + 0x1388 + 0x0000 + 0x1388 + 0x0000
+ 0x0002 + 0x5012 + 0xFFFF + 0x0000 + 0x0000

Cộng dồn TCP:
  0x01BB + 0x1388 = 0x1543
+ 0x0000 = 0x1543
+ 0x1388 = 0x28CB
+ 0x0000 = 0x28CB
+ 0x0002 = 0x28CD
+ 0x5012 = 0x78DF
+ 0xFFFF = 0x178DE  ← Tràn!
+ 0x0000 = 0x178DE
+ 0x0000 = 0x178DE

Tổng = 0x0B1D + 0x178DE = 0x183FB
Fold: 0x83FB + 0x0001 = 0x83FC
NOT: ~0x83FC = 0x7C03

→ TCP Checksum = 0x7C03
```

### Đáp án — Gói SYN-ACK hoàn chỉnh (40 byte):
```
00: 45 00 00 28 12 34 00 00  40 06 5C 9A 01 01 01 01
10: 0A 00 00 01 01 BB 13 88  00 00 13 88 00 00 00 02
20: 50 12 FF FF 7C 03 00 00
```

---

## BÀI 3: TCP ACK (Hoàn tất bắt tay 3 bước)

**Đề bài:** Client xác nhận SYN-ACK. Gửi ACK thuần túy (không data).

### Phân tích:
```
  - Flags = CHỈ ACK = 0x10
  - SeqNum = 2 (= Client ISN 1 + 1, vì SYN tiêu 1 sequence)
  - AckNum = 5001 (= Server ISN 5000 + 1)
  - Payload = 0 byte
  - Total = 40 byte (giống SYN)
```

### Bước 1: Điền IPv4 Header — 20 byte
```
Offset  Hex    Giải thích chi tiết
------  -----  -------------------------------------------------------
  0     0x45   Version=4, IHL=5 (luôn giống nhau)
  1     0x00   ToS = 0
  2-3   0x0028 Total Length = 40 (không data, giống Bài 1)
  4-5   0xABCD ID = 0xABCD (dùng lại ID của client, tùy chọn)
  6-7   0x0000 Flags/Fragment = 0
  8     0x40   TTL = 64
  9     0x06   Protocol = TCP
 10-11  0x0000 Checksum = 0 (GHI TẠM)
 12-15  0x0A000001  Source IP = 10.0.0.1 (Client gửi → Client là nguồn)
 16-19  0x01010101  Dest IP = 1.1.1.1 (Server là đích)
```
IP Header giống hệt Bài 1 → IP Checksum = 0xC300 (không cần tính lại).

### Bước 2: Điền TCP Header — byte 20 đến 39
```
Offset  Hex    Giải thích chi tiết
------  -----  -------------------------------------------------------
 20-21  0x1388 Source Port = 5000 (Client port, giống Bài 1)
 22-23  0x01BB Dest Port = 443 (Server port, giống Bài 1)
 24-27  0x00000002  Sequence Number = 2
               TẠI SAO LÀ 2? Vì ở Bài 1, Client gửi SYN với ISN=1.
               SYN "tiêu" 1 sequence number (dù không có data).
               → SeqNum tiếp theo = 1 + 1 = 2
 28-31  0x00001389  ACK Number = 5001
               TẠI SAO LÀ 5001? Vì ở Bài 2, Server gửi SYN-ACK
               với ISN=5000. SYN của server cũng tiêu 1 seq.
               → Client xác nhận: "Byte tiếp theo tôi mong = 5001"
               5001₁₀ = 0x1389
 32     0x50   Data Offset = 5
 33     0x10   Flags = CHỈ ACK
               ACK = 0001_0000 = 0x10
               (Không có SYN, không có data → không cần PSH)
 34-35  0xFFFF Window = 65535
 36-37  0x0000 TCP Checksum = 0 (GHI TẠM)
 38-39  0x0000 Urgent = 0
```

### Đáp án — Tính TCP Checksum:
```
Pseudo-header (giống Bài 1, Client→Server):
  0x0A00 + 0x0001 + 0x0101 + 0x0101 + 0x0006 + 0x0014 = 0x0B1D

TCP Header:
  0x1388 + 0x01BB + 0x0000 + 0x0002 + 0x0000
+ 0x1389 + 0x5010 + 0xFFFF + 0x0000 + 0x0000

Cộng dồn TCP:
  0x1388 + 0x01BB = 0x1543
+ 0x0000 = 0x1543
+ 0x0002 = 0x1545
+ 0x0000 = 0x1545
+ 0x1389 = 0x28CE
+ 0x5010 = 0x78DE
+ 0xFFFF = 0x178DD  ← Tràn!
+ 0x0000 = 0x178DD
+ 0x0000 = 0x178DD

Tổng = 0x0B1D + 0x178DD = 0x183FA
Fold: 0x83FA + 0x0001 = 0x83FB
NOT: ~0x83FB = 0x7C04

→ TCP Checksum = 0x7C04
```

### Đáp án — Gói ACK hoàn chỉnh (40 byte):
```
00: 45 00 00 28 AB CD 00 00  40 06 C3 00 0A 00 00 01
10: 01 01 01 01 13 88 01 BB  00 00 00 02 00 00 13 89
20: 50 10 FF FF 7C 04 00 00
```

**Sau bước này, kết nối TCP ở trạng thái ESTABLISHED!**

---

## BÀI 4: TCP DATA (Gửi dữ liệu "Hi")

**Đề bài:** Client gửi 2 byte dữ liệu: "Hi" (0x48 0x69).

### Phân tích kích thước — Tại sao lúc này là 42 byte?
```
  IP Header  = 20 byte
  TCP Header = 20 byte
  Payload    = 2 byte ("Hi" = 0x48 0x69)
  TOTAL      = 20 + 20 + 2 = 42 byte  ← Thay đổi!
```

### Các trường thay đổi so với gói ACK trước:
```
  - Total Length (byte 2-3): 42 = 0x002A  (trước là 0x0028)
  - Flags = PSH + ACK = 0x08 | 0x10 = 0x18
    (PSH = "đẩy data lên App ngay", ACK = "tôi vẫn xác nhận")
  - SeqNum vẫn = 2 (chưa gửi data nào trước đó)
  - AckNum vẫn = 5001
  - Byte 40-41: 0x48 0x69 = "Hi"
```

### Bước 1: Điền IPv4 Header — 20 byte
```
Offset  Hex    Giải thích chi tiết
------  -----  -------------------------------------------------------
  0     0x45   Version=4, IHL=5
  1     0x00   ToS = 0
  2-3   0x002A Total Length = 42 ← KHÁC Bài 1!
               42₁₀ = 0x2A. Tại sao 42? Vì 20(IP) + 20(TCP) + 2(data)
  4-5   0xABCE ID = 0xABCE (mỗi gói tin phải có ID khác nhau)
  6-7   0x0000 Flags/Fragment = 0
  8     0x40   TTL = 64
  9     0x06   Protocol = TCP
 10-11  0x0000 Checksum = 0 (GHI TẠM, phải tính lại vì Total Length đổi!)
 12-15  0x0A000001  Source IP = 10.0.0.1
 16-19  0x01010101  Dest IP = 1.1.1.1
```

### Bước 2: Điền TCP Header — byte 20 đến 39
```
Offset  Hex    Giải thích chi tiết
------  -----  -------------------------------------------------------
 20-21  0x1388 Source Port = 5000
 22-23  0x01BB Dest Port = 443
 24-27  0x00000002  Sequence Number = 2
               TẠI SAO VẪN LÀ 2? Vì gói ACK ở Bài 3 không mang data,
               nên không tiêu sequence number. SeqNum chỉ tăng khi
               gửi data hoặc SYN/FIN.
 28-31  0x00001389  ACK Number = 5001 (server chưa gửi gì mới)
 32     0x50   Data Offset = 5
 33     0x18   Flags = PSH + ACK ← MỚI!
               PSH = 0000_1000 = 0x08  ("Đẩy data lên App ngay!")
               ACK = 0001_0000 = 0x10
               OR  = 0001_1000 = 0x18
 34-35  0xFFFF Window = 65535
 36-37  0x0000 TCP Checksum = 0 (GHI TẠM)
 38-39  0x0000 Urgent = 0
```

### Bước 3: Điền Payload — byte 40 đến 41
```
Offset  Hex    Giải thích chi tiết
------  -----  -------------------------------------------------------
 40     0x48   Ký tự 'H' trong bảng ASCII. 'H' = 72₁₀ = 0x48
 41     0x69   Ký tự 'i' trong bảng ASCII. 'i' = 105₁₀ = 0x69
```
Payload nằm ngay SAU TCP Header. Đây là data thật sự mà App gửi.

### Đáp án — Tính IP Checksum:
```
Lưu ý: Total Length thay đổi thành 42 = 0x002A, ID = 0xABCE

Word: 0x4500 + 0x002A + 0xABCE + 0x0000 + 0x4006
    + 0x0000 + 0x0A00 + 0x0001 + 0x0101 + 0x0101

Cộng dồn:
  0x4500 + 0x002A = 0x452A
+ 0xABCE = 0xF0F8
+ 0x0000 = 0xF0F8
+ 0x4006 = 0x130FE  ← Tràn!
+ 0x0000 = 0x130FE
+ 0x0A00 = 0x13AFE
+ 0x0001 = 0x13AFF
+ 0x0101 = 0x13C00
+ 0x0101 = 0x13D01

Fold: 0x3D01 + 0x0001 = 0x3D02
NOT: ~0x3D02 = 0xC2FD

→ IP Checksum = 0xC2FD
```

### Đáp án — Tính TCP Checksum (có Payload!):
```
Pseudo-header:
  0x0A00 + 0x0001 + 0x0101 + 0x0101 + 0x0006 + 0x0016 = 0x0B1F
  (Chú ý: TCP Length = 22 = 0x0016, không phải 20 nữa!)

TCP Header (10 words) + Payload (1 word):
  0x1388 + 0x01BB + 0x0000 + 0x0002 + 0x0000
+ 0x1389 + 0x5018 + 0xFFFF + 0x0000 + 0x0000
+ 0x4869   ← ĐÂY LÀ PAYLOAD "Hi" (H=0x48, i=0x69)

Cộng dồn TCP:
  0x1388 + 0x01BB = 0x1543
+ 0x0000 = 0x1543
+ 0x0002 = 0x1545
+ 0x0000 = 0x1545
+ 0x1389 = 0x28CE
+ 0x5018 = 0x78E6
+ 0xFFFF = 0x178E5  ← Tràn!
+ 0x0000 = 0x178E5
+ 0x0000 = 0x178E5
+ 0x4869 = 0x1C14E  ← Tràn lần 2!

Tổng = 0x0B1F + 0x1C14E = 0x1CC6D
Fold: 0xCC6D + 0x0001 = 0xCC6E
NOT: ~0xCC6E = 0x3391

→ TCP Checksum = 0x3391
```

### Đáp án — Gói DATA hoàn chỉnh (42 byte):
```
00: 45 00 00 2A AB CE 00 00  40 06 C2 FD 0A 00 00 01
10: 01 01 01 01 13 88 01 BB  00 00 00 02 00 00 13 89
20: 50 18 FF FF 33 91 00 00  48 69
```

**Lưu ý khi payload lẻ byte (ví dụ 3 byte "Hi!" = 48 69 21):**
```
Byte cuối được pad thêm 0x00 khi tính checksum:
  "Hi!" → 0x4869 + 0x2100 (byte 0x21 dịch trái 8 bit)
Nhưng gói tin thật KHÔNG chứa byte pad này.
```

---

## BÀI 5: TCP FIN-ACK (Đóng kết nối)

**Đề bài:** Client đóng kết nối sau khi đã gửi 2 byte.

### Phân tích:
```
  - Flags = FIN + ACK = 0x01 | 0x10 = 0x11
  - SeqNum = 4 (= 2 ban đầu + 2 byte data đã gửi)
  - AckNum = 5001 (server chưa gửi gì mới)
  - Payload = 0 byte
  - Total = 40 byte
```

### Bước 1: Điền IPv4 Header — 20 byte
```
Offset  Hex    Giải thích chi tiết
------  -----  -------------------------------------------------------
  0     0x45   Version=4, IHL=5
  1     0x00   ToS = 0
  2-3   0x0028 Total Length = 40 (quay lại 40, không có payload)
  4-5   0xABCF ID = 0xABCF (tăng thêm 1 so với Bài 4)
  6-7   0x0000 Flags/Fragment = 0
  8     0x40   TTL = 64
  9     0x06   Protocol = TCP
 10-11  0x0000 Checksum = 0 (GHI TẠM)
 12-15  0x0A000001  Source IP = 10.0.0.1
 16-19  0x01010101  Dest IP = 1.1.1.1
```

### Bước 2: Điền TCP Header — byte 20 đến 39
```
Offset  Hex    Giải thích chi tiết
------  -----  -------------------------------------------------------
 20-21  0x1388 Source Port = 5000
 22-23  0x01BB Dest Port = 443
 24-27  0x00000004  Sequence Number = 4
               TẠI SAO LÀ 4?
               Bài 1: SYN với ISN=1       → tiêu 1 seq → next = 2
               Bài 3: ACK (không data)    → tiêu 0 seq → next = 2
               Bài 4: DATA gửi 2 byte    → tiêu 2 seq → next = 4
               Vậy SeqNum hiện tại = 4
 28-31  0x00001389  ACK Number = 5001
               Server vẫn chưa gửi thêm data,
               nên AckNum không đổi.
 32     0x50   Data Offset = 5
 33     0x11   Flags = FIN + ACK
               FIN = 0000_0001 = 0x01  ("Tôi muốn ngắt kết nối")
               ACK = 0001_0000 = 0x10
               OR  = 0001_0001 = 0x11
 34-35  0xFFFF Window = 65535
 36-37  0x0000 TCP Checksum = 0 (GHI TẠM)
 38-39  0x0000 Urgent = 0
```

### Đáp án — Tính TCP Checksum:
```
Pseudo-header:
  0x0A00 + 0x0001 + 0x0101 + 0x0101 + 0x0006 + 0x0014 = 0x0B1D

TCP Header:
  0x1388 + 0x01BB + 0x0000 + 0x0004 + 0x0000
+ 0x1389 + 0x5011 + 0xFFFF + 0x0000 + 0x0000

Cộng dồn TCP:
  0x1388 + 0x01BB = 0x1543
+ 0x0000 = 0x1543
+ 0x0004 = 0x1547
+ 0x0000 = 0x1547
+ 0x1389 = 0x28D0
+ 0x5011 = 0x78E1
+ 0xFFFF = 0x178E0  ← Tràn!
+ 0x0000 = 0x178E0
+ 0x0000 = 0x178E0

Tổng = 0x0B1D + 0x178E0 = 0x183FD
Fold: 0x83FD + 0x0001 = 0x83FE
NOT: ~0x83FE = 0x7C01

→ TCP Checksum = 0x7C01
```

### Đáp án — Gói FIN-ACK hoàn chỉnh (40 byte):
```
00: 45 00 00 28 AB CF 00 00  40 06 C2 FE 0A 00 00 01
10: 01 01 01 01 13 88 01 BB  00 00 00 04 00 00 13 89
20: 50 11 FF FF 7C 01 00 00
```

**Quy tắc SeqNum:** Mỗi byte data làm tăng SeqNum thêm 1.
SYN và FIN cũng tiêu 1 sequence number (dù không mang data).

**Tổng kết dòng đời SeqNum của Client:**
```
SYN:     SeqNum = 1         (ISN)           → SYN tiêu 1 seq
ACK:     SeqNum = 2         (1 + 1)         → Không tiêu seq
DATA:    SeqNum = 2         (gửi 2 byte)    → Data tiêu 2 seq
FIN:     SeqNum = 4         (2 + 2)         → FIN tiêu 1 seq
→ Tổng sequence đã dùng: 1(SYN) + 2(data) + 1(FIN) = 4
```

---

## BÀI 6: UDP DATAGRAM (Gửi DNS query)

**Đề bài:** Gửi DNS query "example.com" (giả sử payload = 30 byte)
từ `10.0.0.1:12345` tới DNS server `8.8.8.8:53`.

### Phân tích kích thước:
```
  IP Header  = 20 byte
  UDP Header = 8 byte (LUÔN cố định, đơn giản hơn TCP)
  Payload    = 30 byte (DNS query)
  TOTAL      = 20 + 8 + 30 = 58 byte
```

### Sự khác biệt giữa UDP và TCP:
```
  - UDP Header CHỈ CÓ 8 byte (TCP có 20 byte)
  - UDP KHÔNG có Sequence, ACK, Flags, Window
  - UDP KHÔNG cần bắt tay
  - Cấu trúc UDP Header:
    Byte 0-1: Source Port
    Byte 2-3: Dest Port
    Byte 4-5: UDP Length (Header 8 + Payload)
    Byte 6-7: Checksum
```

### Bước 1: Điền IPv4 Header — 20 byte
```
Offset  Hex    Giải thích chi tiết
------  -----  -------------------------------------------------------
  0     0x45   Version=4, IHL=5 (giống TCP!)
  1     0x00   ToS = 0
  2-3   0x003A Total Length = 58
               58₁₀ = 0x3A. Tại sao 58? Vì 20(IP) + 8(UDP) + 30(data)
  4-5   0x5678 ID = 0x5678 (ngẫu nhiên)
  6-7   0x0000 Flags/Fragment = 0
  8     0x40   TTL = 64
  9     0x11   Protocol = UDP = 17 ← KHÁC TCP!
               TCP = 6 (0x06), UDP = 17 (0x11)
               Đây là cách Router biết header tiếp theo là UDP chứ
               không phải TCP.
 10-11  0x0000 Checksum = 0 (GHI TẠM)
 12     0x0A   ┐
 13     0x00   │ Source IP = 10.0.0.1
 14     0x00   │
 15     0x01   ┘
 16     0x08   ┐
 17     0x08   │ Dest IP = 8.8.8.8 (Google DNS Server!)
 18     0x08   │ Tại sao 8.8.8.8? Vì đây là DNS public của Google.
 19     0x08   ┘
```

### Bước 2: Điền UDP Header — CHỈ 8 byte (byte 20-27)
```
Offset  Hex    Giải thích chi tiết
------  -----  -------------------------------------------------------
 20-21  0x3039 Source Port = 12345
               12345₁₀ = 0x3039 (client chọn port ngẫu nhiên)
 22-23  0x0035 Dest Port = 53 ← ĐÂY LÀ DNS!
               53₁₀ = 0x35. Port 53 là port chuẩn quốc tế cho DNS.
               Mọi DNS query trên thế giới đều gửi tới port 53.
 24-25  0x0026 UDP Length = 38
               38₁₀ = 0x26. Tính: 8(UDP Header) + 30(Payload) = 38
               Chú ý: UDP Length bao gồm CẢ header, không chỉ payload!
 26-27  0x0000 UDP Checksum = 0 (GHI TẠM)
```

UDP Header đơn giản hơn TCP RẤT NHIỀU:
- Không có Sequence Number (không đảm bảo thứ tự)
- Không có ACK (không xác nhận)
- Không có Flags (không cần bắt tay)
- Không có Window Size (không kiểm soát luồng)

### Bước 3: Điền Payload — byte 28 đến 57
```
 28-57: [30 byte DNS query data]
        Payload chứa DNS query theo định dạng RFC 1035.
        Ví dụ: query "example.com" sẽ được mã hóa thành:
        07 65 78 61 6D 70 6C 65  03 63 6F 6D 00 ...
        ↑7 ↑e  x  a  m  p  l  e  ↑3 ↑c  o  m  ↑kết thúc
```

### Đáp án — Tính IP Checksum:
```
Word: 0x4500 + 0x003A + 0x5678 + 0x0000 + 0x4011
    + 0x0000 + 0x0A00 + 0x0001 + 0x0808 + 0x0808

Cộng dồn:
  0x4500 + 0x003A = 0x453A
+ 0x5678 = 0x9BB2
+ 0x0000 = 0x9BB2
+ 0x4011 = 0xDBC3
+ 0x0000 = 0xDBC3
+ 0x0A00 = 0xE5C3
+ 0x0001 = 0xE5C4
+ 0x0808 = 0xEDCC
+ 0x0808 = 0xF5D4

Không có carry. NOT:
  ~0xF5D4 = 0x0A2B

→ IP Checksum = 0x0A2B
```

### Đáp án — Tính UDP Checksum:
```
Pseudo-header:
  0x0A00 + 0x0001 + 0x0808 + 0x0808 + 0x0011 + 0x0026 = 0x1A48
  (Protocol=17=0x11, UDP Length=38=0x26)

UDP Header (4 words):
  0x3039 + 0x0035 + 0x0026 + 0x0000 = 0x3094

Payload (30 byte = 15 words):
  [Cộng dồn 15 cặp 2-byte của DNS query data]
  Giả sử tổng payload = 0x1234 (tùy nội dung DNS thật)

Tổng = 0x1A48 + 0x3094 + 0x1234 = 0x5D10
Không carry. NOT: ~0x5D10 = 0xA2EF

→ UDP Checksum = 0xA2EF (giá trị thay đổi tùy payload thật)

Đặc biệt: Nếu kết quả = 0x0000 sau NOT,
phải ghi 0xFFFF thay vì 0x0000 (RFC 768 quy định).
Vì 0x0000 có nghĩa "không tính checksum", gây nhầm lẫn.
```

### Đáp án — Gói UDP hoàn chỉnh (58 byte):
```
00: 45 00 00 3A 56 78 00 00  40 11 0A 2B 0A 00 00 01
10: 08 08 08 08 30 39 00 35  00 26 A2 EF [30 byte DNS
20: query payload ở đây...]
```

---

# PHẦN 5: BẢN ĐỒ IPv6 HEADER (RFC 8200)

IPv6 đơn giản hơn IPv4 vì không có Checksum và không có Fragmentation ở header chính.

```
Byte:  0        1        2        3
      +--------+--------+--------+--------+
   0  |Ver|Traffic Cls |    Flow Label    |
      +--------+--------+--------+--------+
   4  |  Payload Length |NextHdr| Hop Lmt |
      +--------+--------+--------+--------+
   8  |                                   |
  12  |         Source Address            |
  16  |         (128 bit = 16 byte)       |
  20  |                                   |
      +--------+--------+--------+--------+
  24  |                                   |
  28  |       Destination Address         |
  32  |         (128 bit = 16 byte)       |
  36  |                                   |
      +--------+--------+--------+--------+
```

### Điểm khác biệt quan trọng so với IPv4:

| | IPv4 | IPv6 |
|---|---|---|
| Kích thước header | 20-60 byte (biến đổi) | 40 byte (cố định) |
| Checksum | Có (byte 10-11) | Không có |
| Địa chỉ | 32 bit (4 byte) | 128 bit (16 byte) |
| TTL | "Time to Live" | "Hop Limit" (byte 7) |
| Next Header | "Protocol" | "Next Header" - linh hoạt hơn |

**Byte 0-3:** `0x60000000`
- 4 bit cao = `0110` = Version 6
- 8 bit tiếp = Traffic Class (QoS ở tầng IP)
- 20 bit cuối = Flow Label (nhận dạng luồng)

**Byte 4-5:** Payload Length
- Chỉ tính phần SAU fixed header (không tính 40 byte header)

**Byte 6:** Next Header
- Giống Protocol của IPv4: `6` = TCP, `17` = UDP

**Byte 7:** Hop Limit
- Giống TTL của IPv4, thường là `64`

**Byte 8-23:** Source Address (16 byte)
- `::1` = `00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01`

**Byte 24-39:** Destination Address (16 byte)

---

# PHẦN 6: CHECKLIST TRƯỚC KHI GỬI GÓI TIN

Trước khi đẩy gói tin vào TUN, hãy tự kiểm tra:

- [ ] Version đúng chưa? (4 hoặc 6)
- [ ] Total Length / Payload Length đúng chưa?
- [ ] Protocol/Next Header đúng chưa? (6=TCP, 17=UDP)
- [ ] IP Source và Destination đã đặt đúng chưa?
- [ ] IP Checksum đã tính chưa? (chỉ IPv4)
- [ ] Port Source/Destination đúng chưa?
- [ ] Sequence Number và Ack Number hợp lý chưa?
- [ ] TCP Flags đúng với trạng thái kết nối chưa?
- [ ] Window Size đặt chưa?
- [ ] TCP Checksum đã tính với Pseudo-header chưa?

---

# TÀI LIỆU PHẢI ĐỌC (THEO THỨ TỰ)

1. **RFC 791** - "Internet Protocol" (IPv4 spec gốc, 1981)
   → https://www.rfc-editor.org/rfc/rfc791
2. **RFC 793** - "Transmission Control Protocol" (TCP spec gốc, 1981)
   → https://www.rfc-editor.org/rfc/rfc793
3. **RFC 8200** - "Internet Protocol, Version 6" (IPv6, 2017)
   → https://www.rfc-editor.org/rfc/rfc8200
4. **RFC 1071** - "Computing the Internet Checksum" (1988)
   → https://www.rfc-editor.org/rfc/rfc1071
5. **Wireshark** - Tool để kiểm tra gói tin của chính mình
   → Mở lên, bật capture, chạy App, xem gói tin có màu xanh không!
