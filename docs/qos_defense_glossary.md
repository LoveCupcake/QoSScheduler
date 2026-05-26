# Thuật Ngữ Chuyên Ngành Hệ Thống Mạng, QoS & Hệ Quản Trị Cực Cao
## Tài Liệu Ôn Luyện Phản Biện Bảo Vệ Luận Văn - Đề Tài: QoS Scheduler

Tài liệu này định nghĩa, giải thích chi tiết và liên hệ thực tế toàn bộ các thuật ngữ chuyên ngành nâng cao xuất hiện trong **100 Câu Hỏi Phản Biện QoS Scheduler**. Việc làm chủ các khái niệm này sẽ giúp bạn sử dụng thuật ngữ học thuật một cách chuẩn xác, tự tin trước Hội đồng phản biện.

---

## 🧭 PHẦN I: THUẬT NGỮ MẠNG DỮ LIỆU & GIAO THỨC (NETWORKING & PROTOCOLS)

### 1. MTU (Maximum Transmission Unit - Đơn vị truyền tải tối đa)
*   **Định nghĩa học thuật:** Kích thước gói tin lớn nhất (tính bằng bytes) có thể được truyền tải qua một giao diện mạng (Physical or Virtual Interface) ở tầng liên kết dữ liệu (Layer 2) mà không cần phân mảnh (Fragmentation).
*   **Giải thích bình dân:** Chiều dài tối đa của một thùng hàng gửi đi trên xe tải. Nếu hàng dài hơn xe, ta buộc phải cưa đôi thùng hàng ra để chở làm nhiều chuyến.
*   **Thực tế trong Project:** Card mạng ảo TUN được cấu hình MTU đúng bằng `1500` bytes để khớp với MTU Ethernet vật lý tiêu chuẩn của mạng Wi-Fi/LTE, ngăn chặn hiện tượng phân mảnh gói tin trên đường truyền mạng.

### 2. TCP MSS Clamping (Kẹp kích thước phân đoạn TCP tối đa)
*   **Định nghĩa học thuật:** Kỹ thuật sửa đổi trường MSS (Maximum Segment Size) trong tùy chọn của IP/TCP Header của gói tin bắt tay `SYN` hoặc `SYN-ACK`. Mục đích là giới hạn kích thước Payload TCP tối đa của luồng truyền để đảm bảo tổng kích thước gói IP (Payload + TCP Header + IP Header) không vượt quá MTU của toàn bộ lộ trình (Path MTU).
*   **Giải thích bình dân:** Chủ động dán cờ khống chế kích thước tối đa của từng hộp hàng nhỏ gửi đi bên trong xe tải, đảm bảo khi xếp vỏ hộp bên ngoài vào thì tổng thể tích vẫn vừa vặn xe tải mà không bị phình to.
*   **Thực tế trong Project:** Proxy của dự án kẹp MSS ở mức `1460` bytes ($1500 - 40$ bytes IP/TCP header) để triệt tiêu hiện tượng phân mảnh gói tin thô.

### 3. IP Fragmentation (Phân mảnh gói tin IP)
*   **Định nghĩa học thuật:** Quá trình một IP Router hoặc Interface chia nhỏ một gói tin IP lớn thành các gói tin nhỏ hơn (mảnh - fragments) khi gói tin đó cần đi qua một mạng có MTU nhỏ hơn kích thước gói. Mỗi mảnh sẽ được chèn IP Header riêng và có cùng trường Identification để thiết bị nhận cuối có thể tái hợp lại.
*   **Giải thích bình dân:** Cưa nhỏ tủ quần áo khổng lồ ra thành 3 mảnh nhỏ để bê vừa thang máy, rồi lên phòng chung cư lắp ráp lại từ đầu.
*   **Thực tế trong Project:** Việc phân mảnh gây sụt giảm hiệu năng nghiêm trọng và trễ RTT cao. Project triệt tiêu phân mảnh bằng cách Clamping MSS và gán MTU hợp lý.

### 4. Split-Handshake (Bắt tay phân tách / giả lập)
*   **Định nghĩa học thuật:** Kỹ thuật chia một kết nối TCP duy nhất giữa hai đầu mút (End-to-End) thành hai kết nối độc lập chạy song song: một kết nối nội bộ giữa Client và Proxy, một kết nối ngoài Internet giữa Proxy và Server vật lý, sau đó thực hiện đồng bộ Sequence/Acknowledgment giữa chúng.
*   **Giải thích bình dân:** Làm "cò trung gian" đứng ở giữa. Thay vì để người mua và người bán nói chuyện trực tiếp, cò ký hợp đồng giả với người mua trước để lấy lòng tin, sau đó chạy đi ký hợp đồng thật với người bán, rồi đứng ở giữa dịch chuyển thông tin và tiền tệ cho hai bên.
*   **Thực tế trong Project:** Triển khai trong `TcpRelayManager.kt` để proxy hoàn tất bắt tay ảo với Client trước, giúp đo lường và bóp băng thông ngay tại thiết bị di động mà không cần quyền Root.

### 5. TCP Backpressure (Áp lực ngược TCP)
*   **Định nghĩa học thuật:** Cơ chế kiểm soát dòng chảy (Flow Control) tự nhiên của giao thức TCP. Khi bộ đệm nhận (Receive Buffer) của thiết bị trung gian hoặc thiết bị nhận bị đầy, nó sẽ gửi gói tin quảng cáo kích thước cửa sổ nhận bằng 0 ($WindowSize = 0$) về phía thiết bị gửi, buộc thiết bị gửi phải tạm dừng truyền tải dữ liệu.
*   **Giải thích bình dân:** Khi bồn chứa nước ở nhà bị đầy phao, hệ thống tự động khóa van ống nước ở nhà máy để nước không chảy tràn ra ngoài, chặn nước từ nguồn phát một cách tự nhiên.
*   **Thực tế trong Project:** Proxy trì hoãn việc gửi ACK hoặc trì hoãn đọc dữ liệu của Server, kích hoạt áp lực ngược tự nhiên ép ứng dụng Client tự động giảm tốc độ gửi mà không cần trực tiếp drop gói tin vật lý.

### 6. Jitter (Độ biến động trễ mạng)
*   **Định nghĩa học thuật:** Sự sai lệch về mặt thời gian (độ trễ RTT) giữa các gói tin liên tiếp được truyền tải trong cùng một luồng mạng. Jitter cao gây ra hiện tượng giật cục cho các ứng dụng thời gian thực.
*   **Giải thích bình dân:** Xe buýt chuyến 1 đến trễ 10 phút, chuyến 2 đến trễ 1 phút, chuyến 3 đến trễ 30 phút. Khoảng cách thời gian không đồng đều giữa các chuyến chính là Jitter.
*   **Thực tế trong Project:** Thuật toán WFQ và Token Bucket giữ trễ RTT của lớp HIGH phẳng tắp để đạt được Jitter tiệm cận mức 0 cho Game thủ.

---

## 📱 PHẦN II: THUẬT NGỮ HỆ ĐIỀU HÀNH DI ĐỘNG & INTERACTIVE SYSCALLS

### 7. Virtual TUN Interface (Card mạng ảo TUN)
*   **Định nghĩa học thuật:** Giao diện mạng ảo (Virtual Network Interface) hoạt động ở tầng Mạng (Layer 3 - Network Layer) do hệ điều hành giả lập. Không giống như card mạng vật lý truyền dữ liệu ra anten Wi-Fi/4G, card mạng TUN truyền/nhận trực tiếp các gói tin IP dưới dạng mảng byte thô với một tiến trình chạy ở Userspace.
*   **Giải thích bình dân:** Một "cổng USB ảo" trên phần mềm. Thay vì cắm dây mạng thật, hệ điều hành tạo ra một đường ống ảo trích xuất toàn bộ luồng byte của hệ thống ra một ứng dụng con để xử lý.
*   **Thực tế trong Project:** `VpnService` của Android khởi tạo `/dev/tun` để chuyển hướng toàn bộ traffic của các app vào lớp xử lý `DataPlaneProcessor`.

### 8. Socket Protect (Bảo vệ Socket)
*   **Định nghĩa học thuật:** Thao tác gọi hàm hệ thống (`setsockopt` hoặc `setsockopt` với cờ `SO_MARK`) để đánh dấu một socket vật lý cụ thể được miễn trừ khỏi bảng định tuyến mặc định của VPN ảo, buộc gói tin đi ra từ socket này định tuyến trực tiếp ra card mạng WAN vật lý thật.
*   **Giải thích bình dân:** Cấp thẻ "Ưu tiên đặc biệt" cho xe cứu hỏa, cho phép xe này đi qua con đường tắt vật lý riêng mà không bị giữ lại ở chốt kiểm dịch VPN ảo.
*   **Thực tế trong Project:** Lớp `QosVpnService.kt` gọi `protect(fd)` để bảo vệ socket proxy kết nối ra Server thật, ngăn chặn triệt để thảm họa lặp định tuyến vô hạn (Routing Loop).

### 9. Binder IPC Bottleneck (Nghẽn cổ chai giao tiếp Binder)
*   **Định nghĩa học thuật:** Hiện tượng sụt giảm hiệu năng hệ thống nghiêm trọng do một tiến trình thực hiện quá nhiều lời gọi hệ thống Binder (Inter-Process Communication) đồng bộ xuyên biên giới giữa Userspace và Kernelspace trong một đơn vị thời gian ngắn, khiến CPU bị quá tải bởi chi phí tráo đổi ngữ cảnh (Context Switching Overhead).
*   **Giải thích bình dân:** Một văn phòng thủ tục hành chính chỉ có một ô cửa giao dịch. Nếu mỗi giây có 5000 người vào hỏi thủ tục và nhân viên phải chạy vào kho lục hồ sơ liên tục, ô cửa sẽ bị tắc nghẽn hoàn toàn và không ai làm được việc.
*   **Thực tế trong Project:** Cuộc gọi `getConnectionOwnerUid` tra cứu UID của gói tin bị nghẽn do Binder IPC, được giải quyết triệt để bằng hai tầng cache tĩnh trong RAM.

### 10. Negative Caching (Cache âm / Cực âm)
*   **Định nghĩa học thuật:** Kỹ thuật lưu trữ các kết quả tra cứu **thất bại** (ví dụ lỗi không tìm thấy hoặc giá trị ảo `-1`) vào bộ nhớ cache tạm thời trong RAM. Mục đích để khi có các yêu cầu tra cứu tiếp theo đối với cùng một khóa dữ liệu, hệ thống trả về kết quả thất bại lập tức từ RAM mà không cần thực hiện các thao tác truy vấn nặng xuống hệ thống lớp dưới.
*   **Giải thích bình dân:** Khi bạn đi tìm mua một món đồ lạ ở chợ, hỏi 3 cửa hàng đều bảo không có. Bạn ghi nhớ luôn là chợ này không bán món đồ đó để lần sau không tốn công đi hỏi nữa.
*   **Thực tế trong Project:** Lưu giá trị `-1` cho các luồng hệ thống thô (DNS, NTP) trong `flowCache` để bypass nhanh không gọi Binder IPC.

### 11. Retroactive Flow Migration (Di cư luồng hồi tố)
*   **Định nghĩa học thuật:** Mô hình thiết kế (Design Pattern) trong xử lý luồng mạng dữ liệu. Khi luồng dữ liệu bắt đầu, danh tính (UID) chưa phân giải được ngay lập tức, hệ thống tạm thời tích lũy số liệu vào một ngăn chứa ảo (synthetic bucket). Ngay khi danh tính thật được phân giải thành công ở các gói tin sau, hệ thống hồi tố bằng cách di dời toàn bộ số liệu thống kê tích lũy cũ sang ngăn chứa thật và hủy ngăn chứa ảo đi.
*   **Giải thích bình dân:** Một khách hàng vào quán ăn lúc đông, nhân viên chưa kịp ghi tên hóa đơn nên tạm ghi vào tờ nháp "Bàn số 5". Lúc thanh toán, nhân viên đối chiếu thấy tên khách hàng thật, chép toàn bộ món ăn từ tờ nháp vào hóa đơn chính thức để không bị sót tiền.
*   **Thực tế trong Project:** Xử lý dịch chuyển byte từ synthetic port UID (`-(port+1)`) sang app UID thật trong `DataPlaneProcessor.kt` để đạt độ chính xác telemetry 100%.

---

## 🧮 PHẦN III: THUẬT TOÁN QoS & LẬP LỊCH BĂNG THÔNG

### 12. Token Bucket (Thuật toán Thùng chứa thẻ)
*   **Định nghĩa học thuật:** Thuật toán quản lý lưu lượng và định hình băng thông (Traffic Shaping/Policing) hoạt động dựa trên cơ chế tích lũy thẻ (tokens) vào một thùng chứa ảo có dung lượng $B$ ở tốc độ nạp thẻ cố định $r$ (refill rate). Khi gói tin kích thước $L$ đi qua, nó phải tiêu thụ lượng thẻ tương ứng với kích thước gói tin. Nếu không đủ thẻ, gói tin bị drop (Policing) hoặc đưa vào hàng đợi chờ (Shaping).
*   **Giải thích bình dân:** Một khu vui chơi phát vé trò chơi tự động. Cứ 1 phút máy phát ra 1 vé, thùng chứa tối đa 10 vé. Nếu bạn có sẵn 10 vé, bạn chơi liên tục 10 trò lập tức (Burst). Khi hết vé, bạn bắt buộc phải đợi đúng 1 phút để máy nhè ra 1 vé mới chơi tiếp được.
*   **Thực tế trong Project:** Triển khai thuật toán dạng Policing trong `TokenBucket.kt` để kẹp tốc độ truyền của từng ứng dụng di động chính xác theo cấu hình bps.

### 13. WFQ (Weighted Fair Queuing - Lập lịch công bằng có trọng số)
*   **Định nghĩa học thuật:** Thuật toán lập lịch mạng phân bổ tài nguyên băng thông động cho các hàng đợi khác nhau dựa trên trọng số ưu tiên ($W_i$) được cấu hình trước. WFQ đảm bảo các hàng đợi có mức ưu tiên cao nhận được nhiều tài nguyên hơn, đồng thời các hàng đợi ưu tiên thấp vẫn nhận được một lượng tài nguyên tối thiểu không bị "chết đói" (starvation).
*   **Giải thích bình dân:** Một chiếc bánh ngọt được chia cho 3 đứa trẻ: Đứa lớn (HIGH) được 4 phần, đứa nhỡ (MEDIUM) được 2 phần, đứa út (LOW) được 1 phần. Dù đứa lớn ăn khỏe đến mấy thì đứa út vẫn luôn được đảm bảo có đúng 1 phần ăn của mình.
*   **Thực tế trong Project:** Phân chia băng thông tổng `uplinkBps` cho các Token Bucket theo tỷ lệ ưu tiên 4:2:1 trong `BandwidthScheduler.kt`.

### 14. Bufferbloat (Tràn nghẽn bộ đệm router)
*   **Định nghĩa học thuật:** Hiện tượng tăng trễ (RTT) mạng và suy hao hiệu năng nghiêm trọng do các bộ đệm hàng đợi (Queue Buffers) của các router vật lý trên đường truyền có kích thước quá lớn, chúng giữ lại các gói tin thay vì drop khi xảy ra tắc nghẽn, làm mất khả năng kích hoạt cơ chế kiểm soát tắc nghẽn tự nhiên của giao thức TCP.
*   **Giải thích bình dân:** Đường cao tốc bị tắc đường, nhưng cảnh sát giao thông (Router) không chặn xe lại từ xa mà cứ cho hàng vạn xe xếp hàng nối đuôi nhau nhích từng mét trong vô vọng, khiến thời gian di chuyển tăng gấp 100 lần.
*   **Thực tế trong Project:** QoS Scheduler bóp băng thông tổng thấp hơn thực tế 5% để giữ hàng đợi của Router Wi-Fi luôn trống, kéo RTT ping game từ 1200ms xuống còn 18ms.

---

## 💾 PHẦN IV: THIẾT KẾ CƠ SỞ DỮ LIỆU & KIẾN TRÚC BACKEND

### 15. WebAssembly SQLite (sql.js)
*   **Định nghĩa học thuật:** Phiên bản của hệ quản trị cơ sở dữ liệu SQLite được biên dịch từ mã nguồn C thô sang mã nhị phân WebAssembly (Wasm) chạy trực tiếp trong máy ảo JavaScript (V8) của Node.js hoặc trình duyệt, lưu trữ cơ sở dữ liệu hoàn toàn trong bộ nhớ RAM và hỗ trợ xuất nhập tệp tin nhị phân.
*   **Giải thích bình dân:** Đưa toàn bộ động cơ xe hơi thật vào một môi trường giả lập phần mềm siêu nhẹ. Động cơ chạy cực nhanh trong RAM và không cần lắp ráp phức tạp vào khung vỏ xe vật lý.
*   **Thực tế trong Project:** Sử dụng `sql.js` trong `database.js` để server chạy không cần cài đặt dependencies native, đảm bảo tương thích chéo 100%.

### 16. Throttled Background Saving (Ghi ngầm giảm nghẽn)
*   **Định nghĩa học thuật:** Mô hình tối ưu hóa I/O đĩa cứng. Các thao tác ghi đè cơ sở dữ liệu được thực hiện siêu tốc trong bộ nhớ RAM tạm thời và bật cờ dirty flag. Một luồng chạy ngầm định kỳ (Background Worker) quét cờ dirty flag này theo một chu kỳ thời gian cố định và chỉ ghi dữ liệu xuống đĩa cứng vật lý một lần duy nhất nếu cờ được bật.
*   **Giải thích bình dân:** Thay vì mỗi lần viết được 1 chữ là bạn phải chạy đi cất cuốn sổ vào két sắt (rất mất thời gian), bạn cứ viết liên tục lên bảng nháp. Cứ 5 phút bạn mới chép toàn bộ bảng nháp vào cuốn sổ chính và cất đi một lần.
*   **Thực tế trong Project:** Server Node.js sử dụng cờ `dbDirty` và chu kỳ quét 5 giây để lưu file `qos_scheduler.db`, giữ Event Loop luôn mượt mà.

### 17. Event Loop Blocking (Đóng băng vòng lặp sự kiện)
*   **Định nghĩa học thuật:** Hiện tượng luồng thực thi đơn lẻ (Single Thread) của môi trường Node.js bị chiếm dụng hoàn toàn bởi một tác vụ đồng bộ nặng (như ghi đĩa đồng bộ, tính toán CPU chuyên sâu), khiến vòng lặp sự kiện (Event Loop) không thể tiếp nhận và xử lý các yêu cầu I/O không đồng bộ khác từ client.
*   **Giải thích bình dân:** Một quán phở chỉ có duy nhất 1 đầu bếp kiêm bồi bàn. Nếu anh ta dành 15 phút chỉ để cọ rửa thớt gỗ (tác vụ đồng bộ), toàn bộ khách hàng mới vào quán sẽ phải đứng chờ và không ai được phục vụ.
*   **Thực tế trong Project:** Việc ghi đĩa SQLite thô đồng bộ có nguy cơ gây Event Loop Blocking, được giải quyết triệt để nhờ cơ chế ghi ngầm throttled 5 giây.

---

## 🛡️ PHẦN V: BẢO MẬT HỆ THỐNG & TƯƠNG TÁC NGƯỜI - MÁY (UX / HCI)

### 18. Parameterized Queries (Truy vấn tham số hóa)
*   **Định nghĩa học thuật:** Kỹ thuật viết câu lệnh truy vấn cơ sở dữ liệu SQL trong đó các tham số đầu vào được biểu diễn bằng các ký tự đại diện (như `?` hoặc `:param`) và được biên dịch trước bởi công cụ DB. Dữ liệu thực tế từ người dùng được truyền riêng biệt dưới dạng giá trị thô, ngăn chặn việc DB diễn giải dữ liệu đầu vào thành mã SQL thực thi.
*   **Giải thích bình dân:** Tạo ra một mẫu đơn in sẵn có các ô trống để điền thông tin. Người dùng chỉ được viết chữ vào ô trống đó, họ có viết các câu lệnh phá hoại thì hệ thống cũng chỉ coi đó là chữ viết thông thường chứ không bao giờ thực thi.
*   **Thực tế trong Project:** Sử dụng trong các API đồng bộ telemetry để triệt tiêu hoàn toàn nguy cơ tấn công **SQL Injection**.

### 19. SSL/TLS End-to-End Encryption (Mã hóa đầu cuối SSL/TLS)
*   **Định nghĩa học thuật:** Giao thức bảo mật mật mã thiết lập một kênh truyền được mã hóa bảo mật tuyệt đối giữa hai ứng dụng ở đầu mút (Client và Server thật) qua Internet. Khóa giải mã chỉ được lưu trữ ở hai thiết bị đầu mút này, các thiết bị trung chuyển hoặc cổng proxy lớp dưới hoàn toàn không có khóa và không thể đọc được nội dung payload.
*   **Giải thích bình dân:** Bỏ bức thư vào một hộp sắt khóa số bảo mật cao và gửi qua bưu điện. Chỉ có người gửi và người nhận có mật mã mở hộp. Nhân viên bưu điện (Proxy) chỉ bê vác chiếc hộp đi chứ không cách nào mở ra đọc trộm thư được.
*   **Thực tế trong Project:** Đảm bảo an toàn tuyệt đối cho người dùng khi lướt web ngân hàng, đăng nhập qua QoS Proxy mà không sợ bị rò rỉ dữ liệu mật.

### 20. HCI / Instant UI Response (Tương tác người máy & Phản hồi UI tức thời)
*   **Định nghĩa học thuật:** Lĩnh vực nghiên cứu thiết kế tương tác giữa người dùng và máy tính (Human-Computer Interaction). Giao diện đạt chuẩn HCI xuất sắc khi các thao tác cấu hình của người dùng trên UI nhận được phản hồi phản hồi vật lý hoặc cập nhật trực quan ngay lập tức (<100ms), loại bỏ hoàn toàn cảm giác đơ hoặc trễ mạng.
*   **Giải thích bình dân:** Bạn nhấn công tắc và bóng đèn bật sáng ngay lập tức. Nếu bạn nhấn công tắc mà 5 giây sau đèn mới sáng, giao diện đó đã thất bại về mặt trải nghiệm HCI.
*   **Thực tế trong Project:** Thay đổi slider trên Web Admin đẩy policy xuống Token Bucket của điện thoại và áp dụng bóp băng thông phẳng tắp chỉ trong vòng <1 giây, mang lại trải nghiệm HCI cực nhạy.
