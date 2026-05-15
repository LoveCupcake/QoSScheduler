# Hướng dẫn Thực nghiệm & Kiểm chứng (Experimental Validation)

Tài liệu này hướng dẫn cách thực hiện các thí nghiệm để lấy số liệu cho luận văn. Chúng ta sử dụng phương pháp **"Tam giác xác thực"**: Dùng Máy tính để đo đạc khách quan những gì Điện thoại thực hiện.

---

## 1. Chuẩn bị Tổng quát (Setup)

### Thiết bị:
*   **Điện thoại (Host):** Đã cài App QoS Scheduler.
*   **Máy tính (Thước đo):** Cùng mạng Wi-Fi với điện thoại.
*   **Kết nối:** Cắm cáp USB (để lấy Log qua lệnh `adb`).

### Công cụ trên Máy tính:
1.  **iPerf3:** Để đo tốc độ thực tế.
2.  **Wireshark:** Để soi gói tin.
3.  **Terminal (CMD/PowerShell):** Để chạy lệnh thu thập log.

---

## 2. Quy trình Thực hiện (Master Workflow)

Mỗi thí nghiệm sẽ gồm 3 giai đoạn: **Chuẩn bị Máy tính** -> **Thao tác Điện thoại** -> **Trích xuất số liệu**.

---

## 3. Các kịch bản Thí nghiệm Chi tiết

### Kịch bản 1: Giới hạn Băng thông (TCP Throttling)

**Mục tiêu:** Chứng minh App có khả năng bóp tốc độ một App khác (iPerf3) từ 15Mbps xuống 1Mbps.

#### Bước 1: Chuẩn bị trên Máy tính (PC)
Mở 3 cửa sổ riêng biệt:
1.  **Cửa sổ 1 (iPerf Server):** Gõ lệnh `iperf3 -s` (PC sẵn sàng nhận dữ liệu).
2.  **Cửa sổ 2 (Ghi Log):** Gõ lệnh `adb logcat -s QoS-Stats > scenario1_app_logs.txt`.
3.  **Cửa sổ 3 (Wireshark):** Mở Wireshark, chọn Wi-Fi, gõ filter `ip.addr == [IP_DIEN_THOAI]` rồi nhấn Start.

#### Bước 2: Thao tác trên Điện thoại (Android)
1.  **Chạy iPerf (Lúc chưa bóp):** Mở App iPerf3 trên điện thoại, gõ IP của Máy tính và nhấn Start. (Quan sát thấy tốc độ ~15-20Mbps).
2.  **Kích hoạt QoS:** 
    *   Mở App QoS Scheduler.
    *   Tìm iPerf3 trong danh sách -> Chỉnh tốc độ xuống **1.0 Mbps**.
    *   Nhấn **Start/Apply**.
3.  **Quan sát:** Quay lại App iPerf3, thấy tốc độ tụt về đúng ~1Mbps.
4.  **Kết thúc:** Dừng iPerf3 trên điện thoại.

#### Bước 3: Thu hoạch số liệu (Trích xuất)
| File tạo ra | Công cụ mở | Thông tin cần lấy |
|:---|:---|:---|
| `scenario1_app_logs.txt` | Notepad++ | Tìm dòng `DATA_POINT`. Lấy cột `Mbps` và `DropRate`. |
| `iperf_output` (trên màn hình) | Console | Chụp ảnh màn hình đoạn tốc độ bị tụt từ 15 xuống 1. |
| Wireshark capture | Wireshark | Vào `Statistics -> TCP Stream Graph -> Round Trip Time` để thấy độ trễ tăng vọt. |

---

### Kịch bản 2: Chia sẻ băng thông theo trọng số (WFQ)

**Mục tiêu:** Chứng minh khi 2 App cùng chạy, App ưu tiên cao sẽ chiếm nhiều băng thông hơn theo tỷ lệ (ví dụ 4:1).

#### Bước 1: Chuẩn bị trên Máy tính (PC)
1.  **iPerf Server:** Chạy lệnh `iperf3 -s` (giữ nguyên).
2.  **Ghi Log:** Gõ `adb logcat -s QoS-Stats > scenario2_app_logs.txt`.

#### Bước 2: Thao tác trên Điện thoại (Android)
1.  **Cấu hình ưu tiên:**
    *   App A (iPerf3): Đặt mức **HIGH** (Trọng số 8).
    *   App B (Chrome/Download): Đặt mức **LOW** (Trọng số 2).
2.  **Chạy đồng thời:**
    *   Bật iPerf3 chạy gửi dữ liệu sang PC.
    *   Mở Chrome bắt đầu tải một file nặng (ví dụ file ISO Linux).
3.  **Quan sát:** App A vẫn mượt, App B chạy rất chậm.

#### Bước 3: Thu hoạch số liệu
*   Mở file `scenario2_app_logs.txt`.
*   So sánh cột `Mbps` của App A và App B. 
*   **Kết quả kỳ vọng:** Tốc độ App A / Tốc độ App B $\approx$ 4.

---

### Kịch bản 3: Chống nghẽn (Bufferbloat Prevention)

**Mục tiêu:** Chứng minh App ưu tiên cao (Ping/Game) không bị lag khi có App khác đang tải nặng.

#### Bước 1: Chuẩn bị trên Máy tính (PC)
1.  Mở Terminal gõ: `ping -t [IP_DIEN_THOAI] > ping_test.txt` (Để đo độ trễ thực tế).

#### Bước 2: Thao tác trên Điện thoại (Android)
1.  **Giai đoạn 1 (Không có QoS):** Tắt App QoS. Bật iPerf3 chạy tối đa công suất. Nhìn vào màn hình PC thấy Ping tăng vọt lên 500ms-1000ms (Lag).
2.  **Giai đoạn 2 (Có QoS):** Bật App QoS. Đặt iPerf3 mức **LOW**. Nhìn vào màn hình PC thấy Ping tụt về mức ổn định 20ms-50ms.

#### Bước 3: Thu hoạch số liệu
*   Mở file `ping_test.txt`.
*   Vẽ biểu đồ: Trục tung là thời gian Ping (ms), trục hoành là thời gian thực hiện. 
*   Biểu đồ sẽ cho thấy một "ngọn núi" cao vút lúc tắt QoS và một "thung lũng" phẳng lặng lúc bật QoS.

---

## 4. Tổng kết cho Luận văn

Mỗi kịch bản trên ông cần chụp 3 tấm hình:
1.  **Màn hình App QoS** đang hoạt động.
2.  **Biểu đồ tốc độ/độ trễ** (vẽ từ file log).
3.  **Bằng chứng Wireshark** (chứng minh tính chuyên sâu).

---
*Lưu ý: Luôn đảm bảo cáp USB kết nối ổn định để log không bị ngắt quãng.*
