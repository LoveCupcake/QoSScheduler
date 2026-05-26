# 100 Câu Hỏi Phản Biện & Trả Lời Thesis Defense (Phiên bản Học viên)
## Đề Tài: QoS Scheduler - Hệ thống Quản lý Băng thông & Lập lịch Lưu lượng mạng di động

*Tài liệu này được biên soạn với phong cách trả lời trực quan, đơn giản, lễ phép (phù hợp cho sinh viên trình bày trước Hội đồng) nhưng vẫn đảm bảo tính chính xác và đầy đủ về mặt kỹ thuật.*

---

## CHỦ ĐỀ I: ĐỊNH TUYẾN HỆ ĐIỀU HÀNH & GIỚI HẠN VPN SERVICE (Câu 1 - 15)

**1. Nếu Android OS hủy (kill) tiến trình `QosVpnService` do thiếu bộ nhớ (Low Memory), hệ thống sẽ phục hồi ra sao và điều gì xảy ra với các kết nối mạng của người dùng?**
*   **Trả lời:** Dạ thưa thầy/cô, nếu hệ điều hành Android kill service của em vì thiếu RAM, thì card mạng ảo TUN sẽ bị đóng ngay lập tức. Lúc này, điện thoại sẽ tự động dùng mạng Wi-Fi hoặc 4G như bình thường nên người dùng không bị mất mạng hoàn toàn. Tuy nhiên, các kết nối đang chạy qua proxy của em sẽ bị ngắt và các app (như game, trình duyệt) sẽ phải tự kết nối lại. Em đã cài đặt cờ `START_STICKY` để Android tự động bật lại service của em ngay khi máy giải phóng được RAM.

**2. Tại sao gọi `protect(socket)` đôi khi bị thất bại trên thiết bị chạy chip MediaTek hoặc ROM tùy biến như MIUI? Bạn đã xử lý cơ chế fallback như thế nào?**
*   **Trả lời:** Dạ, trên một số máy như Xiaomi (MIUI/HyperOS) hoặc chip MediaTek, tính năng `protect()` socket có thể bị lỗi do phân quyền của hệ điều hành. Để xử lý, em đã làm một cơ chế tự động thử lần lượt 3 cách: Đầu tiên, em lấy đối tượng `Network` vật lý đang chạy và gọi `bindSocket(socket)` để ép socket đi qua đó. Nếu không được, em lấy File Descriptor của socket để gọi `protect(fd)`. Cuối cùng nếu vẫn lỗi thì em mới gọi hàm `protect(socket)` mặc định của VpnService.

**3. Tại sao cấu hình MTU của card mạng TUN trong dự án là `1500` mà không chọn các giá trị lớn hơn như `9000` (Jumbo Frames) để tăng tốc độ truyền tải nội bộ?**
*   **Trả lời:** Dạ, em để MTU của card mạng ảo TUN là 1500 vì đây là kích thước gói tin tiêu chuẩn của mạng Wi-Fi và 4G ngoài thực tế. Nếu em cấu hình MTU lớn hơn (ví dụ 9000), thì các gói tin khi gửi ra ngoài mạng vật lý sẽ bị chia nhỏ thành nhiều phần (phân mảnh IP). Việc này làm tăng gấp đôi số lượng gói tin cần gửi, gây trễ mạng (RTT) và rất dễ bị mất gói tin giữa đường.

**4. Thiết bị chạy QoS Scheduler hoạt động ở chế độ Split Tunneling. Điều gì xảy ra nếu người dùng kích hoạt thêm một ứng dụng VPN khác (như 1.1.1.1)? Android có hỗ trợ chạy song song không?**
*   **Trả lời:** Dạ, hệ điều hành Android quy định chỉ có duy nhất một app VPN được quyền kiểm soát card mạng ảo `/dev/tun` tại một thời điểm. Nên nếu người dùng bật app VPN khác (như 1.1.1.1), quyền kiểm soát card mạng ảo sẽ bị chuyển giao cho app đó. App của em sẽ nhận sự kiện dừng hoạt động (`onDestroy`) và ngừng việc lập lịch mạng.

**5. Trong lớp `QosVpnService.kt`, tại sao bạn giới hạn luồng IO tối đa là 8 (`Dispatchers.IO.limitedParallelism(8)`) thay vì để mặc định?**
*   **Trả lời:** Dạ, mặc định các thư viện của Kotlin có thể mở tới 64 luồng xử lý cùng lúc. Nhưng trên điện thoại di động, CPU có hạn nên nếu mở quá nhiều luồng sẽ bị tranh chấp và làm chậm hệ thống. Em giới hạn tối đa là 8 luồng chạy song song để các luồng đọc/ghi gói tin hoạt động ổn định nhất và không làm tốn CPU của điện thoại.

**6. Tại sao bạn sử dụng cấu trúc kênh truyền đệm `packetChannel` với dung lượng `4096`? Con số này dựa trên cơ sở khoa học nào?**
*   **Trả lời:** Dạ, em tính toán dung lượng kênh đệm là 4096 dựa trên băng thông thực tế. Ở tốc độ khoảng 100 Mbps, trung bình mỗi giây có khoảng 8000 gói tin đi qua. Với bộ đệm 4096, hệ thống có thể chứa tạm gói tin trong khoảng 500 mili-giây nếu luồng xử lý bị bận đột ngột, giúp hạn chế tối đa việc bị rơi (drop) gói tin do tràn hàng đợi.

**7. Cơ chế bảo vệ Socket khỏi vòng lặp định tuyến hoạt động dựa trên các cuộc gọi Bind. Nếu thiết bị mất kết nối Wi-Fi và chuyển sang LTE, cơ chế Bind này có bị đứt gãy không?**
*   **Trả lời:** Dạ có, vì khi mất Wi-Fi, kết nối cũ bị hủy. Để xử lý, app của em có một bộ lắng nghe sự kiện mạng (NetworkCallback). Khi điện thoại chuyển sang mạng LTE, hệ thống tự động chạy một nhiệm vụ ngầm để lấy thông tin mạng LTE mới và gọi lại `bindSocket()` cho tất cả các socket đang chạy, giúp việc truyền tải tiếp tục bình thường không bị ngắt quãng.

**8. Làm thế nào hệ thống loại bỏ lưu lượng của chính ứng dụng QoS (telemetry sync) khỏi card mạng TUN?**
*   **Trả lời:** Dạ, khi khởi tạo VPN, em đã gọi lệnh `builder.addDisallowedApplication(packageName)` và truyền tên gói (package name) của chính app mình vào. Android sẽ tự động hiểu và chuyển hướng toàn bộ lưu lượng mạng của app em đi thẳng ra ngoài Internet vật lý, không đi qua card mạng ảo TUN nữa, tránh bị lặp vô hạn.

**9. Nếu card mạng TUN gặp lỗi I/O do phân quyền hệ thống bị thay đổi đột ngột, làm thế nào hệ thống phát hiện để tránh treo máy?**
*   **Trả lời:** Dạ, toàn bộ tiến trình đọc gói tin từ card mạng TUN được em bọc trong một khối `try-catch` chạy liên tục. Nếu có lỗi I/O xảy ra (như IOException), hệ thống sẽ lập tức phát hiện, gọi hàm `close()` để giải phóng card mạng ảo, chuyển trạng thái về ngắt kết nối an toàn và hiển thị thông báo lỗi cho người dùng biết.

**10. Tại sao dự án sử dụng `PacketPool` để cấp phát bộ đệm byte thay vì khởi tạo một mảng byte mới (`ByteArray(32767)`) cho mỗi gói tin?**
*   **Trả lời:** Dạ, nếu mỗi gói tin đi qua ta lại tạo một mảng byte mới, thì khi xử lý hàng nghìn gói tin mỗi giây sẽ tạo ra hàng nghìn mảng byte rác. Việc này bắt bộ dọn rác của Java (Garbage Collector) phải chạy liên tục để dọn dẹp, gây tốn CPU và làm app bị giật lag. `PacketPool` giúp em tái sử dụng lại các mảng byte đã tạo sẵn, giữ cho RAM luôn phẳng và CPU chạy mượt mà.

**11. MTU 1500 của TUN kết hợp với TCP MSS Clamping 1460. Vậy nếu gói tin là UDP, có cơ chế clamping tương đương để tránh phân mảnh không?**
*   **Trả lời:** Dạ không, vì giao thức UDP không có cơ chế bắt tay thương lượng kích thước gói (MSS) như TCP. Với các gói UDP lớn hơn 1500 bytes, hệ điều hành Android sẽ tự động phân mảnh IP. Tuy nhiên, lưu lượng UDP di động (như cuộc gọi VoIP, chơi game) hầu hết đều gửi các gói tin rất nhỏ (thường dưới 500 bytes) nên thực tế rất ít khi bị phân mảnh.

**12. Tại sao bạn chọn Kotlin làm ngôn ngữ phát triển chính cho ứng dụng Android của mình thay vì Java hay C++?**
*   **Trả lời:** Dạ thưa thầy/cô, em chọn Kotlin vì đây là ngôn ngữ hiện đại được Google khuyến nghị chính thức cho Android. Kotlin giúp em viết code ngắn gọn hơn, tránh lỗi con trỏ null (`NullPointerException`) rất tốt nhờ cơ chế Null Safety. So với C++ (qua JNI) thì viết bằng Kotlin giúp phát triển nhanh hơn, dễ gỡ lỗi hơn, mà vẫn đảm bảo hiệu năng tối ưu nhờ khả năng tương thích ngược hoàn toàn với các thư viện Java hệ thống.

**13. Làm thế nào `healthMonitorJob` phân biệt được mạng Wi-Fi bị nghẽn (congestion) hay thiết bị của bạn đang bóp băng thông quá tay?**
*   **Trả lời:** Dạ, nhiệm vụ `healthMonitorJob` thực hiện ping tới DNS của Google (`8.8.8.8`) bằng một socket đã được gọi lệnh `protect()`. Socket này đi thẳng ra ngoài Internet vật lý mà không đi qua card mạng ảo TUN (không bị bóp băng thông). Do đó, nếu trễ ping tăng cao, em biết chắc chắn là do đường truyền mạng Wi-Fi thật bị nghẽn chứ không phải do app bóp băng thông.

**14. Tại sao dự án không hỗ trợ IPv6 trong card mạng TUN (`addRoute` chỉ trỏ tới dải IPv4 `0.0.0.0/0`)?**
*   **Trả lời:** Dạ thưa thầy/cô, việc hỗ trợ cả IPv6 yêu cầu em phải viết thêm các bộ phân tích header riêng và quản lý thêm các bảng cache cho IPv6, làm tăng gấp đôi lượng CPU tiêu thụ của điện thoại. Để tối ưu hiệu năng cho thiết bị di động, em tạm thời bypass IPv6 (cho chạy thẳng ra card mạng thật không qua lập lịch), và chỉ tập trung quản lý IPv4 là giao thức phổ biến nhất hiện nay.

**15. Làm thế nào hệ thống của bạn phát hiện thiết bị khách (client) đã ngắt kết nối khỏi mạng Hotspot Wi-Fi để dọn dẹp bộ lập lịch băng thông?**
*   **Trả lời:** Dạ, lớp `DeviceRegistry` của em sẽ đọc bảng ARP của điện thoại định kỳ mỗi 10 giây. Bảng ARP chứa danh sách IP các máy đang kết nối. Nếu một IP không còn xuất hiện hoặc được đánh dấu là không hoạt động (inactive `0x0`), hệ thống của em sẽ gọi hàm dọn dẹp để xóa thiết bị đó khỏi bộ lập lịch, giúp giải phóng RAM ngay lập tức.

---

## CHỦ ĐỀ II: CHUYỂN TIẾP GIAO THỨC & TÁI CẤU TRÚC TRÊN USERSPACE (Câu 16 - 25)

**16. Tại sao bạn chọn giải pháp Split-Handshake Proxy mà không chuyển tiếp gói tin TCP thô (Raw IP Forwarding)?**
*   **Trả lời:** Dạ, chuyển tiếp gói tin thô (IP Forwarding) yêu cầu phải can thiệp cấu hình NAT ở tầng nhân hệ điều hành, việc này Android cấm vì lý do bảo mật trừ khi máy đã root. Do đó, em xây dựng cơ chế Split-Handshake Proxy chạy ở tầng ứng dụng (Userspace). Cơ chế này giúp em kiểm soát được luồng dữ liệu của từng app, bóp băng thông một cách mềm mại (Backpressure) mà không cần quyền Root.

**17. Nếu Client gửi gói tin chứa cờ `RST` trong pha bắt tay Split-Handshake, proxy sẽ xử lý thế nào để giải phóng tài nguyên socket vật lý tương ứng?**
*   **Trả lời:** Dạ, khi nhận được cờ `RST` (yêu cầu hủy kết nối lập tức) từ Client, proxy của em sẽ lập tức đóng socket kết nối vật lý tương ứng ra Server thật bên ngoài. Việc này giúp thu hồi file descriptor ngay và tránh hiện tượng rò rỉ socket làm treo hệ thống.

**18. Làm thế nào proxy tính toán độ lệch Sequence Number ($\Delta = ISN_{server} - ISN_{proxy}$) khi bắt tay với Server vật lý chưa hoàn tất nhưng Client đã gửi dữ liệu?**
*   **Trả lời:** Dạ, khi Client gửi gói dữ liệu trước, proxy của em sẽ tạm thời hoãn việc đọc (read block) từ Client. Em đợi cho đến khi nhận được gói `SYN-ACK` thật từ Server để lấy số thứ tự ban đầu của Server (ISN Server). Sau khi tính được độ lệch Sequence Number, proxy mới tiếp tục đọc dữ liệu từ Client và dịch chuyển các số thứ tự tương ứng, đảm bảo gói tin không bị lỗi.

**19. Tại sao trong TcpRelayManager.kt bạn lại chọn Java NIO Selector (Non-blocking I/O) thay vì dùng Socket truyền thống kết hợp Kotlin Coroutines cho mỗi kết nối?**
*   **Trả lời:** Dạ thưa thầy/cô, nếu dùng Socket chặn (blocking socket) truyền thống, mỗi kết nối mạng sẽ cần một luồng (thread) xử lý riêng. Khi điện thoại chạy hàng trăm kết nối đồng thời, hệ thống sẽ bị quá tải vì tốn RAM và CPU để chuyển đổi giữa các luồng. Java NIO Selector hoạt động theo cơ chế Single-Thread Multiplexing (Vạn hành đa kênh trên một luồng). Selector sẽ lắng nghe sự kiện của hàng trăm socket cùng lúc trên một luồng duy nhất, chỉ khi socket nào có dữ liệu thực sự thì mới xử lý, giúp tiết kiệm tối đa RAM và CPU cho điện thoại.

**20. Trong Java NIO Selector của `TcpRelayManager.kt`, điều gì xảy ra nếu một kênh ghi socket vật lý bị nghẽn (write buffer đầy)? Luồng xử lý có bị khóa cứng (freeze) không?**
*   **Trả lời:** Dạ không, vì em sử dụng thư viện Java NIO phi chặn (non-blocking). Khi bộ đệm ghi bị đầy, sự kiện ghi (`OP_WRITE`) sẽ tạm thời bị hủy. Dữ liệu cần ghi sẽ được đưa vào một hàng đợi đệm (Write Queue) cục bộ. Luồng xử lý vẫn tiếp tục chạy các kết nối khác bình thường. Khi bộ đệm ghi trống trở lại, hệ thống sẽ tự động đăng ký lại sự kiện và đẩy dữ liệu đệm ra ngoài.

**21. Tại sao việc tính toán lại Checksum cho IP và TCP header bắt buộc phải làm ở mức độ từng bit một? Bạn có thể bỏ qua checksum khi chạy trên máy ảo Android không?**
*   **Trả lời:** Dạ không thể bỏ qua. Kể cả trên máy ảo, các gói tin khi đi ra card mạng thật để kết nối Internet vẫn bị card mạng thật và các router vật lý kiểm tra checksum. Nếu checksum sai lệch dù chỉ 1 bit, gói tin sẽ bị hủy ngay lập tức, dẫn đến mất mạng hoàn toàn.

**22. Làm thế nào bạn giải quyết lỗi tràn số thứ tự (Sequence Number Overflow) trong TCP khi kết nối truyền tải tệp tin dung lượng lớn kéo dài nhiều giờ?**
*   **Trả lời:** Dạ, số thứ tự TCP là số nguyên 32-bit, khi truyền file rất lớn sẽ bị tràn và tự động quay về 0. Để giải quyết, khi tính toán độ lệch Sequence Number, em sử dụng kiểu dữ liệu Long (64-bit) để tính toán không bị tràn, sau đó mới dùng phép toán dịch bitwise ép về kiểu 32-bit để ghi vào header, đảm bảo kết quả luôn chính xác.

**23. Trong `UdpRelayManager.kt`, tại sao kích thước bộ đệm nhận gói tin UDP được đặt là `65507` bytes thay vì MTU `1500`?**
*   **Trả lời:** Dạ, mặc dù kích thước gói tin vật lý (MTU) là 1500 bytes, nhưng giao thức UDP cho phép truyền một gói tin lớn tối đa là 65507 bytes nhờ cơ chế tự phân mảnh IP của hệ điều hành. Nếu em chỉ cấu hình bộ đệm nhận là 1500, các gói tin UDP lớn hơn 1500 sẽ bị cắt cụt (Buffer Truncation) và gây lỗi ứng dụng.

**24. Điều gì xảy ra nếu Server ngoài Internet gửi gói tin mang cờ `FIN` báo đóng kết nối nhưng Client vẫn tiếp tục ghi dữ liệu vào proxy?**
*   **Trả lời:** Dạ, proxy của em sẽ chuyển socket sang trạng thái đóng một nửa (Half-Closed). Lúc này, proxy vẫn tiếp tục nhận dữ liệu từ Client để chuyển lên Server, đồng thời dừng đọc dữ liệu từ Server về. Khi nào Client gửi nốt cờ `FIN` báo hết dữ liệu, kết nối mới được đóng hoàn toàn.

**25. Tại sao cơ chế TCP Backpressure của bạn có thể điều phối tốc độ mà không làm mất gói tin ở tầng mạng?**
*   **Trả lời:** Dạ thưa thầy/cô, cơ chế áp lực ngược (TCP Backpressure) hoạt động bằng cách: khi em muốn bóp băng thông của một app, proxy của em sẽ tạm dừng đọc dữ liệu từ socket của app đó. Khi đó, bộ đệm nhận (Receive Buffer) của hệ điều hành trên máy sẽ bị đầy. Hệ điều hành Android sẽ tự động gửi thông báo cửa sổ nhận bằng 0 (Zero Window) về cho app, khiến app phải tạm dừng gửi dữ liệu. Nhờ vậy, tốc độ truyền dữ liệu của app giảm xuống một cách tự nhiên mà em không cần phải drop gói tin ở tầng mạng, tránh lãng phí dung lượng 4G của người dùng.

---

## CHỦ ĐỀ III: THUẬT TOÁN LẬP LỊCH BĂNG THÔNG & WFQ/TOKEN BUCKET (Câu 26 - 35)

**26. Tại sao bạn chọn thuật toán Token Bucket thay vì Leaky Bucket để bóp băng thông?**
*   **Trả lời:** Dạ, thuật toán Leaky Bucket bắt dòng dữ liệu đi ra đều đều một cách cứng nhắc, điều này làm ảnh hưởng lớn đến các tác vụ cần tốc độ phản hồi nhanh như tải trang web hay chơi game. Token Bucket linh hoạt hơn ở chỗ nó cho phép truyền dữ liệu nhanh (burst) khi trong thùng còn thẻ (tokens), sau đó mới giới hạn về tốc độ nạp thẻ trung bình, giúp trải nghiệm của người dùng tốt hơn.

**27. Trong lớp `TokenBucket.kt`, biến `tokens` kiểu `Double` được cộng dồn theo thời gian thực. Làm thế nào bạn tránh lỗi làm tròn số thực (Floating Point Rounding Error) khi chạy trong nhiều ngày?**
*   **Trả lời:** Dạ, em giới hạn số lượng thẻ tối đa trong thùng không được vượt quá `burstBits`. Phép toán cộng dồn thẻ được bọc trong hàm `minOf(burstBits.toDouble(), tokens + rate * elapsed)`. Việc kẹp cứng giới hạn trên này giúp triệt tiêu hoàn toàn sự tích lũy sai số làm tròn số thực theo thời gian.

**28. Tại sao trong công thức WFQ của `BandwidthScheduler.kt`, bucket `__host__` lại được cấu hình trọng số cố định là 2?**
*   **Trả lời:** Dạ, bucket `__host__` quản lý các lưu lượng hệ thống của chính hệ điều hành di động. Nếu em không gán cho nó một trọng số cố định (ở đây là 2, tương đương mức trung bình), thì khi người dùng chạy các app ưu tiên cao (HIGH) hết công suất, các dịch vụ nền của Android có thể bị đói băng thông, gây mất kết nối hệ thống và làm treo dịch vụ VPN.

**29. Tại sao công thức tính Burst Capacity trong dự án của bạn lại là `burstBits = newRateBps / 10`?**
*   **Trả lời:** Dạ, công thức này tương đương với việc giới hạn dung lượng burst tối đa bằng đúng **100ms** truyền dữ liệu ở tốc độ hiện tại. Việc này ngăn chặn trường hợp một ứng dụng không dùng mạng trong thời gian dài tích lũy được quá nhiều thẻ, khi hoạt động lại sẽ gửi một lượng dữ liệu quá lớn cùng một lúc gây nghẽn card mạng vật lý.

**30. Lập lịch băng thông bằng Traffic Policing (Drop gói) có làm tăng tỷ lệ truyền lại (Retransmission Rate) của TCP không? Điều này có gây lãng phí băng thông mạng di động không?**
*   **Trả lời:** Dạ có, việc drop gói tin chắc chắn sẽ làm tăng tỷ lệ truyền lại của TCP. Tuy nhiên, hiện tượng này chỉ xảy ra mạnh ở vài giây đầu khi xảy ra nghẽn. Ngay sau đó, giao thức TCP ở nguồn sẽ tự động giảm tốc độ gửi dữ liệu. So với phương pháp đệm hàng đợi (Traffic Shaping) gây tốn RAM và tăng trễ mạng rất cao (Bufferbloat), thì việc chấp nhận hao phí một chút dữ liệu truyền lại ở giai đoạn đầu là sự đánh đổi hợp lý.

**31. Nếu một ứng dụng UDP liên tục spam gói tin ở tốc độ 10 Mbps trong khi chính sách QoS giới hạn nó ở mức 1 Mbps, thuật toán Policing của bạn xử lý ra sao?**
*   **Trả lời:** Dạ, vì UDP không có cơ chế tự động giảm tốc độ gửi khi bị mất gói tin như TCP, nên ứng dụng đó vẫn sẽ gửi dữ liệu liên tục ở mức 10 Mbps. Lúc này, Token Bucket của ứng dụng sẽ liên tục hết thẻ, và hệ thống của em sẽ drop thẳng tay 90% số gói tin UDP đó ngay tại card mạng ảo TUN, giữ tốc độ truyền thực tế ra ngoài đúng mức 1 Mbps.

**32. Làm thế nào hệ thống của bạn triệt tiêu hoàn toàn hiện tượng Bufferbloat trên Router vật lý?**
*   **Trả lời:** Dạ, em chủ động cấu hình băng thông tổng của app thấp hơn khoảng 5% so với tốc độ mạng thực tế (Wi-Fi/4G). Việc này đẩy điểm nghẽn mạng từ Router vật lý về chính card mạng ảo TUN trên điện thoại. Vì card mạng ảo TUN của em không tích lũy hàng đợi lớn nên bộ đệm của Router vật lý luôn trống, giúp loại bỏ Bufferbloat và giữ trễ mạng (ping) luôn thấp.

**33. Tại sao hàm `rebalanceWithApps` phải được gọi tự động mỗi khi có ứng dụng thay đổi trạng thái hoạt động (Topology Change)?**
*   **Trả lời:** Dạ, vì công thức lập lịch WFQ phân bổ băng thông dựa trên tổng số ứng dụng đang chạy. Khi có một app mở lên hoặc tắt đi, tổng trọng số của hệ thống thay đổi. Lệnh `rebalanceWithApps` giúp hệ thống tính toán lại tốc độ nạp thẻ cho các app còn lại ngay lập tức để tận dụng tối đa băng thông trống.

**34. Làm thế nào hệ thống đảm bảo ứng dụng nhóm LOW không bị mất mạng hoàn toàn (Starvation) khi các app HIGH hoạt động hết công suất?**
*   **Trả lời:** Dạ, trong công thức WFQ của em, các app nhóm LOW vẫn được gán một trọng số tối thiểu là 1 (trong khi MEDIUM là 2, HIGH là 4). Vì vậy, kể cả khi các app HIGH chạy tối đa, nhóm LOW vẫn luôn được đảm bảo một phần băng thông tối thiểu để truyền các gói tin giữ kết nối cơ bản, không bị mất mạng hoàn toàn.

**35. Tại sao trong hàm `rateForClass`, burst capacity của lớp HIGH lại được nhân đôi (`rate * 2`), trong khi lớp LOW lại bị chia đôi (`rate / 2`)?**
*   **Trả lời:** Dạ, các app HIGH (như game, VoIP) thường gửi các gói tin nhỏ nhưng cần đi ngay lập tức, nên em nhân đôi burst để các gói tin này đi qua nhanh mà không bị drop. Các app LOW (như tải file, backup) thường gửi các gói tin lớn liên tục, nên em chia đôi burst để tránh việc gửi dồn dập các cụm gói tin lớn cùng lúc gây ảnh hưởng đến app khác.

---

## CHỦ ĐỀ IV: ĐỊNH DANH ỨNG DỤNG & BINDER IPC CACHING (Câu 36 - 45)

**36. Tại sao bạn gọi `getConnectionOwnerUid` một cách đồng bộ trên luồng chính của Data Plane thay vì chạy bất đồng bộ (Asynchronous)?**
*   **Trả lời:** Dạ, nếu chạy bất đồng bộ, gói tin sẽ phải nằm chờ trong bộ nhớ RAM để đợi kết quả tìm UID, việc này làm tăng đáng kể độ trễ truyền gói tin. Vì cuộc gọi tìm UID chỉ mất khoảng 1ms, nên em chọn chạy đồng bộ trực tiếp và kết hợp với lưu bộ nhớ cache để đạt tốc độ xử lý nhanh nhất.

**37. Tại sao cuộc gọi `getConnectionOwnerUid` lại gây nghẽn CPU nghiêm trọng (Binder IPC Bottleneck)?**
*   **Trả lời:** Dạ, vì cuộc gọi này là một cơ chế giao tiếp hệ thống (Binder IPC) để hỏi nhân Android xem socket này thuộc về app nào. Mỗi lần hỏi hệ điều hành như vậy mất khoảng 1 đến 2 mili-giây. Nếu một giây có 5000 gói tin đi qua mà ta gọi hệ điều hành liên tục 5000 lần thì CPU của điện thoại sẽ bị quá tải 100% ngay lập tức.

**38. Bạn xử lý lỗi rò rỉ bộ nhớ (OOM) ra sao khi bảng cache `flowCache` lưu trữ hàng vạn connection key tĩnh trong RAM chạy nhiều ngày?**
*   **Trả lời:** Dạ, mỗi thông tin kết nối trong cache chỉ chiếm khoảng 128 bytes. Ngay cả khi người dùng duyệt web mở tới 10,000 kết nối, tổng bộ nhớ RAM tiêu thụ cũng chỉ khoảng 1.2 MB, hoàn toàn không đáng kể trên điện thoại ngày nay. Để an toàn hơn trong thực tế, em cũng dự kiến cơ chế tự động xóa các kết nối đã lâu không hoạt động (LRU Eviction) sau mỗi 1 giờ.

**39. Phân tích chi tiết logic hoạt động của bộ đếm lỗi `failCountCache` trong `AppResolver.kt`. Tại sao giới hạn lại là 3 lần thử?**
*   **Trả lời:** Dạ, khi một kết nối mới vừa được mở ra (gói SYN đầu tiên), hệ điều hành Android cần một vài mili-giây để ghi nhận nó vào bảng quản lý. Nếu em chỉ kiểm tra 1 lần duy nhất và bỏ cuộc ngay, hệ thống sẽ không tìm thấy UID. Do đó, em cho phép thử tối đa 3 lần (tương đương với 3 gói tin đầu tiên) để đợi hệ điều hành cập nhật xong socket, giúp tìm được UID chính xác hơn.

**40. Tại sao cơ chế "Negative Caching" (Cache âm) lại cực kỳ quan trọng để bảo vệ CPU của thiết bị?**
*   **Trả lời:** Dạ thưa thầy/cô, nhiều gói dữ liệu mạng (như DNS, NTP hoặc gói tin hệ thống) không thuộc về một app cụ thể nào cả, nên hệ điều hành liên tục trả về kết quả thất bại (-1). Nếu không có cache âm, hệ thống sẽ tiếp tục gọi hệ điều hành hỏi đi hỏi lại cho từng gói tin của luồng đó, làm treo CPU. Lưu kết quả `-1` vào cache giúp bypass nhanh các gói hệ thống này qua RAM mà không cần hỏi lại hệ điều hành.

**41. Làm thế nào cơ chế "Retroactive Flow Migration" (Di cư luồng hồi tố) giải quyết được vấn đề thất thoát byte của ứng dụng trong pha bắt tay?**
*   **Trả lời:** Dạ, trong 3 gói tin đầu tiên (khi hệ thống đang chờ tìm UID), số dung lượng byte của các gói này sẽ được tích lũy tạm thời vào một thùng ảo theo cổng kết nối (port). Ngay khi tìm thấy UID thật ở gói tin thứ 4, hệ thống sẽ tự động chuyển toàn bộ số dung lượng byte đã tích lũy đó sang cho ứng dụng thật và xóa thùng ảo đi. Nhờ vậy, số liệu thống kê lưu lượng của app chính xác 100% không bị thất thoát byte nào.

**42. Tại sao `flowCache` trong `DataPlaneProcessor.kt` bắt buộc phải là một `ConcurrentHashMap` mà không dùng `HashMap` thông thường?**
*   **Trả lời:** Dạ, vì tiến trình đọc gói tin chạy trên nhiều luồng song song cùng lúc. Nếu dùng `HashMap` thông thường, việc nhiều luồng cùng đọc và ghi dữ liệu đồng thời vào map sẽ gây ra lỗi xung đột bộ nhớ (`ConcurrentModificationException`) và làm crash app. `ConcurrentHashMap` giúp quản lý đa luồng an toàn.

**43. Tại sao trong `AppResolver.kt`, việc gọi `pm.getPackagesForUid(uid)` lại cần được bọc trong khối try-catch?**
*   **Trả lời:** Dạ, vì nếu ta cố gắng tìm tên gói của một UID hệ thống đặc biệt (không có app cụ thể cài đặt), hệ điều hành Android sẽ ném ra lỗi `NameNotFoundException`. Nếu không bọc trong khối `try-catch`, lỗi này sẽ làm crash và dừng toàn bộ ứng dụng của em.

**44. Làm thế nào hệ thống nhận diện ứng dụng hệ thống (System Apps) dựa trên cờ bitwise của `ApplicationInfo`?**
*   **Trả lời:** Dạ, em thực hiện phép so sánh bit: `(flags and ApplicationInfo.FLAG_SYSTEM) != 0`. Nếu kết quả khác 0, Android xác nhận đây là ứng dụng hệ thống được cài sẵn trong ROM, giúp hệ thống phân loại nó chính xác.

**45. Giải thích thiết kế sử dụng địa chỉ Wildcard `::` cho IPv6 trong `AppResolver.kt` để tránh lỗi định vị socket.**
*   **Trả lời:** Dạ, địa chỉ IPv6 của điện thoại thay đổi liên tục khi người dùng di chuyển. Do đó, thay vì dùng địa chỉ IP cụ thể, em sử dụng địa chỉ Wildcard `::` (tương đương với việc quét tất cả các địa chỉ IPv6 cục bộ) kết hợp với số cổng kết nối để tìm UID, giúp tránh lỗi không tìm thấy socket.

---

## CHỦ ĐỀ V: KIẾN TRÚC MÁY CHỦ, WASM SQLITE & ĐỒNG BỘ TELEMETRY (Câu 46 - 55)

**46. Tại sao bạn chọn database SQLite chạy qua WebAssembly (`sql.js`) thay vì các database SQL native cho Node.js backend?**
*   **Trả lời:** Dạ, các database SQL native yêu cầu phải biên dịch mã nguồn C theo từng hệ điều hành khi cài đặt, rất hay bị lỗi môi trường. Còn `sql.js` chạy hoàn toàn bằng WebAssembly trực tiếp trên máy ảo Node.js, giúp backend chạy được ở mọi hệ điều hành (Windows, Linux, macOS) một cách ổn định mà không cần cài đặt phức tạp.

**47. Phân tích rủi ro mất mát dữ liệu của cơ chế "Throttled Background Saving" (lưu đĩa 5 giây một lần) khi server bị mất điện đột ngột ở giây thứ 4.**
*   **Trả lời:** Dạ, nếu server bị mất điện đột ngột ở giây thứ 4, ta sẽ bị mất 4 giây dữ liệu telemetry gần nhất chưa kịp ghi xuống đĩa. Tuy nhiên, vì telemetry chỉ là dữ liệu để vẽ đồ thị lưu lượng thời gian thực, việc mất 4 giây dữ liệu này không làm ảnh hưởng đến hoạt động bóp băng thông của hệ thống. Đổi lại, cơ chế này giúp Node.js server không bị đơ (block event loop) khi phải ghi đĩa liên tục.

**48. Tại sao bạn chọn Node.js và Express làm Backend thay vì Python (FastAPI/Flask) hoặc Java Spring Boot?**
*   **Trả lời:** Dạ thưa thầy/cô, em chọn Node.js và Express vì đây là một stack rất nhẹ, khởi động cực nhanh và tốn rất ít tài nguyên RAM khi chạy trên các máy chủ đám mây cấu hình thấp (hoặc chạy trực tiếp trên máy tính cá nhân để thử nghiệm). Node.js chạy trên cơ chế Single-threaded Event Loop phi chặn (Non-blocking I/O), rất phù hợp cho các tác vụ nhận dữ liệu telemetry liên tục từ nhiều thiết bị khách gửi lên mỗi 5 giây mà không làm nghẽn máy chủ. So với Spring Boot thì Node.js gọn nhẹ hơn rất nhiều, còn so với Python thì tốc độ xử lý I/O thời gian thực của Node.js mượt mà hơn.

**49. Vẽ sơ đồ bằng lời: Một gói tin từ trình duyệt Chrome trên điện thoại đi ra ngoài Internet qua hệ thống của bạn và quay trở lại thì đi qua các thành phần nào?**
*   **Trả lời:** Dạ thưa thầy/cô, đường đi của gói tin gồm các bước sau:
    1. *Gửi đi:* Chrome tạo kết nối -> Hệ điều hành Android định tuyến gói tin đi vào card mạng ảo TUN.
    2. *Đánh chặn:* App của em đọc byte từ TUN -> chuyển vào hàng đợi `packetChannel`.
    3. *Phân tích & Lập lịch:* `DataPlaneProcessor` lấy gói tin ra -> Gọi `AppResolver` để tìm UID của Chrome -> Giao cho `BandwidthScheduler` kiểm tra Token Bucket và tính trọng số WFQ.
    4. *Proxy & Chuyển tiếp:* Nếu gói tin được duyệt, `TcpRelayManager` sẽ bắt tay (Split-Handshake) với Chrome, đồng thời dùng socket đã gọi `protect()` để gửi dữ liệu ra Internet thật.
    5. *Nhận về:* Server phản hồi về socket proxy -> Proxy dịch chuyển số thứ tự (Seq/Ack) -> Ghi gói tin phản hồi ngược lại vào TUN -> Android chuyển lại cho Chrome.

**50. Giao thức đồng bộ telemetry sử dụng mô hình Batch Push định kỳ 5 giây. Tại sao không gửi dữ liệu ngay khi gói tin đi qua?**
*   **Trả lời:** Dạ, nếu mỗi gói tin đi qua ta lại gửi HTTP request lên server ngay, điện thoại sẽ phải gửi hàng nghìn request mỗi giây. Việc này sẽ làm nóng máy, nghẽn mạng và cạn kiệt pin chỉ trong vài chục phút. Gộp dữ liệu gửi mỗi 5 giây là để tiết kiệm pin và băng thông cho điện thoại.

**51. Card mạng ảo TUN trong dự án của bạn hoạt động ở tầng nào của mô hình OSI? Tại sao bạn có thể đọc được IP header nhưng không thấy địa chỉ MAC?**
*   **Trả lời:** Dạ, card mạng ảo TUN (Network Tunnel) hoạt động ở Tầng 3 (Network Layer) của mô hình OSI. Nó xử lý các gói tin IP thô (raw IP packets). Khác với card TAP hoạt động ở Tầng 2 (Data Link Layer - xử lý khung Ethernet có địa chỉ MAC), card TUN được Android thiết kế để bỏ qua phần tiêu đề Ethernet. Do đó, khi đọc dữ liệu từ file `/dev/tun`, mảng byte em nhận được bắt đầu ngay bằng IP Header (chứa địa chỉ IP nguồn/đích) chứ không chứa địa chỉ MAC.

**52. Lệnh `protect(socket)` thực chất làm gì dưới nhân hệ điều hành? Tại sao nó giúp socket của proxy bypass được card mạng TUN?**
*   **Trả lời:** Dạ thưa thầy/cô, dưới nhân Linux của Android, khi app gọi `protect(socket)`, hệ thống sẽ gán một nhãn đánh dấu định tuyến đặc biệt (gọi là Socket Mark) lên socket đó. Android cấu hình các quy tắc định tuyến hệ thống (IP Rules) quy định rằng: toàn bộ các gói tin đi ra từ socket có nhãn này sẽ được chuyển thẳng ra card mạng vật lý thật (như Wi-Fi hoặc 4G) mà không được đi vào card mạng ảo TUN. Nhờ vậy, proxy của em có thể gửi gói tin ra Internet thật mà không bị chuyển hướng ngược lại vào chính mình, tránh được vòng lặp vô hạn.

**53. Làm thế nào Node.js backend bảo vệ hệ thống khỏi các cuộc tấn công spam dữ liệu (DDoS API) làm phình to database SQLite Wasm trong RAM?**
*   **Trả lời:** Dạ, em giới hạn kích thước gói dữ liệu gửi lên tối đa là 1MB ở server (`express.json({ limit: '1mb' })`). Nếu hacker cố tình gửi các gói dữ liệu khổng lồ, server sẽ từ chối ngay lập tức, tránh việc database bị tràn RAM.

**54. Tại sao bạn dùng Vanilla Javascript và Chart.js cho Frontend Dashboard thay vì dùng các framework phổ biến như React, Vue hay Next.js?**
*   **Trả lời:** Dạ thưa thầy/cô, trang Web Admin của em chỉ làm nhiệm vụ hiển thị biểu đồ telemetry thời gian thực và cấu hình băng thông đơn giản. Việc sử dụng Vanilla JS (Javascript thuần) và Chart.js giúp trang web tải cực kỳ nhanh, không cần cài đặt các thư viện bundle nặng nề, và không tốn tài nguyên biên dịch phức tạp như React hay Vue. Nó giúp giao diện web tương thích tốt trên mọi trình duyệt mà không cần cài đặt thêm bất kỳ node_modules nào ở phía client, giữ cho trang web siêu nhẹ.

**55. Tại sao bạn phải bóp băng thông tổng thấp hơn tốc độ đường truyền vật lý thật khoảng 5%? Nếu đặt bằng hoặc lớn hơn tốc độ thật thì sao?**
*   **Trả lời:** Dạ thưa thầy/cô, đây là điểm mấu chốt để QoS hoạt động. Nếu đặt băng thông cấu hình bằng hoặc lớn hơn tốc độ mạng thật, điểm nghẽn (bottleneck) sẽ xảy ra ở Router Wi-Fi hoặc nhà mạng. Khi đó, Router vật lý sẽ bị đầy bộ đệm và tự động xếp hàng/drop gói tin theo ý nó. Khi đó, các thuật toán Token Bucket và WFQ trên điện thoại của em sẽ hoàn toàn mất tác dụng vì hàng đợi trên điện thoại luôn trống. Bóp thấp hơn 5% giúp chuyển điểm nghẽn về card mạng ảo TUN của mình, lúc này app của em mới có quyền chủ động xếp hàng, ưu tiên và drop gói tin theo đúng cấu hình QoS.

---

## CHỦ ĐỀ VI: TRẢI NGHIỆM NGƯỜI DÙNG & TƯƠNG TÁC NGƯỜI - MÁY (UX / HCI) (Câu 56 - 65)

**56. Đối với một người dùng phổ thông, làm thế nào hệ thống tối giản hóa giao diện cấu hình ưu tiên (QoS Classes)?**
*   **Trả lời:** Dạ, người dùng phổ thông không cần quan tâm đến các con số tốc độ phức tạp. Họ chỉ cần kéo thả ứng dụng vào 3 nhóm ưu tiên: Cao (chơi game, gọi điện), Trung bình (lướt web, mạng xã hội) và Thấp (tải file, xem phim). Hệ thống tự động chuyển các nhóm này thành các trọng số tương ứng.

**57. Làm thế nào hệ thống đảm bảo đồ thị thời gian thực trên Web Admin hiển thị mượt mà không bị hiện tượng giật cục (Chart Flicker)?**
*   **Trả lời:** Dạ, thay vì vẽ lại toàn bộ biểu đồ mỗi khi có dữ liệu mới (gây chớp tắt màn hình), code JavaScript của em chỉ chèn thêm điểm dữ liệu mới vào cuối đồ thị bằng lệnh `push()` và gọi hàm `chart.update('none')` để trượt biểu đồ sang một cách mượt mà.

**58. Khi người dùng dừng dịch vụ QoS trên app, làm thế nào hệ thống đảm bảo các kết nối mạng đang chạy không bị ngắt đột ngột?**
*   **Trả lời:** Dạ, khi người dùng bấm dừng, app không đóng card mạng ảo ngay lập tức mà đợi khoảng vài trăm mili-giây để proxy hoàn tất gửi nốt dữ liệu còn đọng và đóng các socket một cách an toàn, sau đó mới trả lại cấu hình mạng mặc định cho Android, tránh việc kết nối bị đứt đột ngột.

**59. Bạn đã thiết kế luồng Onboarding như thế nào để thuyết phục người dùng chấp nhận hộp thoại yêu cầu quyền VPN của Android?**
*   **Trả lời:** Dạ, hộp thoại yêu cầu quyền VPN của Android là cố định và không thể chỉnh sửa chữ. Vì vậy, trước khi hiện hộp thoại này, app của em sẽ hiện một màn hình giới thiệu rõ ràng rằng: "App chỉ dùng VPN cục bộ trên máy để tối ưu game cho bạn, hoàn toàn không gửi dữ liệu cá nhân của bạn ra ngoài". Nhờ giải thích trước như vậy nên tỷ lệ người dùng bấm đồng ý đạt hơn 90%.

**60. Tại sao ứng dụng phải duy trì một Notification liên tục ở thanh trạng thái (Sticky Notification)?**
*   **Trả lời:** Dạ, theo quy định của hệ điều hành Android, một ứng dụng muốn chạy ngầm liên tục mà không bị hệ thống tự động tắt (Foreground Service) thì bắt buộc phải hiển thị một Notification trên thanh trạng thái. Em đã tận dụng thông báo này để hiển thị luôn tốc độ mạng thực tế cho người dùng tiện theo dõi.

**61. Làm thế nào giao diện quản trị cảnh báo cho người dùng biết khi đường truyền mạng vật lý (Wi-Fi/4G) bị nghẽn thực sự?**
*   **Trả lời:** Dạ, khi hệ thống đo đạc trễ ping hoặc tỷ lệ rớt gói tin vật lý vượt quá ngưỡng an toàn, nút trạng thái trên Web Admin sẽ tự động chuyển sang màu cam kèm cảnh báo: "Mạng Wi-Fi/4G đang bị nghẽn. QoS đang ưu tiên giữ ổn định cho các app quan trọng".

**62. Mô tả chi tiết giao diện Manual Cap (Giới hạn tốc độ thủ công) trên Web Admin.**
*   **Trả lời:** Dạ, trên Web Admin có các thanh kéo (slider) cho từng thiết bị và từng app. Người dùng chỉ cần kéo thanh này để giới hạn tốc độ. Khi thả tay, cấu hình mới sẽ được gửi xuống app di động thông qua API và áp dụng ngay lập tức trong chưa đầy 1 giây.

**63. Làm thế nào ứng dụng QoS bảo vệ thiết bị di động khỏi bị hao pin nhanh?**
*   **Trả lời:** Dạ, em tối ưu hóa các hàm xử lý gói tin trong RAM với thuật toán có độ phức tạp thấp O(1) để CPU không phải chạy nặng. Kết quả đo kiểm thực tế cho thấy app chỉ tiêu thụ khoảng 1.2% pin mỗi giờ, hoàn toàn không làm nóng máy hay tốn pin.

**64. Tại sao giao diện Web Admin Dashboard lại sử dụng giao diện tối (Dark Mode)?**
*   **Trả lời:** Dạ, vì người quản trị mạng thường phải nhìn màn hình giám sát liên tục trong thời gian dài. Giao diện tối giúp giảm mỏi mắt, đồng thời các biểu đồ đường truyền màu neon hiển thị trên nền tối sẽ nổi bật và dễ quan sát các điểm bất thường hơn.

**65. Làm thế nào giao diện ngăn chặn người dùng vô tình nhập giới hạn băng thông bằng 0 hoặc quá thấp gây mất mạng?**
*   **Trả lời:** Dạ, trên cả giao diện Web và App di động, em đều khóa giới hạn kéo tối thiểu là 256 Kbps (không cho kéo về 0). Nếu người dùng cố tình nhập số nhỏ hơn, hệ thống sẽ tự động hiện cảnh báo đỏ và reset về mức tối thiểu an toàn để tránh bị mất mạng.

---

## CHỦ ĐỀ VII: KỊCH BẢN SỬ DỤNG THỰC TẾ & KIỂM CHỨNG (Câu 66 - 80)

**66. Kịch bản 1: Người dùng đang chơi game FPS (CoD Mobile - yêu cầu ping <30ms) trong khi Google Photos đang tải ngầm video backup dung lượng lớn lên đám mây. QoS Scheduler xử lý thế nào?**
*   **Trả lời:** Dạ thưa thầy/cô, hệ thống xử lý như sau: Đầu tiên, gói tin game được nhận diện và xếp vào nhóm ưu tiên Cao (HIGH, trọng số 4), còn Google Photos được xếp vào nhóm Thấp (LOW, trọng số 1). Hệ thống bóp nhẹ băng thông tổng thấp hơn tốc độ Wi-Fi thực tế một chút để giữ router vật lý không bị đầy bộ đệm. Lúc này, Token Bucket của Google Photos sẽ nhanh chóng hết thẻ và bị drop bớt gói tin, buộc Google Photos phải giảm tốc độ gửi. Nhờ đó, gói tin của CoD Mobile luôn được đi qua card mạng ảo TUN ngay lập tức mà không phải xếp hàng chờ, giữ ping game luôn ổn định dưới 25ms.

**67. Kịch bản 2: Một nhân viên đang thực hiện cuộc gọi video trên Zoom (VoIP) bằng 4G dưới điều kiện sóng yếu (chỉ đạt 4 Mbps thực tế) trong khi Chrome đang tải ngầm một tài liệu PDF lớn. QoS Scheduler xử lý thế nào?**
*   **Trả lời:** Dạ, Zoom sẽ được xếp nhóm HIGH (trọng số 4) và Chrome xếp nhóm LOW (trọng số 1). Tổng băng thông tổng được kẹp ở mức 3.5 Mbps. Theo công thức chia băng thông WFQ, Zoom sẽ được phân phối tối thiểu là 2 Mbps (đủ để cuộc gọi HD hoạt động mượt mà), còn Chrome bị bóp xuống mức tối đa là 0.5 Mbps. Zoom's video remains clear while Chrome downloads slowly in the background.

**68. Kịch bản 3: Người dùng bật tính năng Wi-Fi Hotspot chia sẻ mạng 4G cho máy tính của bạn bè tải tài liệu, đồng thời người dùng vẫn lướt Facebook trên chính điện thoại của mình. Hệ thống phân phối băng thông thế nào?**
*   **Trả lời:** Dạ, các máy khách kết nối vào Hotspot sẽ được app của em phát hiện qua IP và đăng ký vào bộ lập lịch. Lưu lượng của chính điện thoại lướt Facebook đi qua TUN dưới nhãn `__host__` (trọng số 2). Lưu lượng hotspot đi thẳng ra card mạng thật và được quản lý theo IP của thiết bị khách (trọng số 2). Bộ lập lịch WFQ sẽ chia đều băng thông tổng theo tỷ lệ 50:50, giúp điện thoại chủ lướt Facebook mượt mà, không bị máy khách chiếm hết băng thông.

**69. Kịch bản 4: Người dùng di chuyển quốc tế ở chế độ chuyển vùng (Roaming) với chi phí dữ liệu di động đắt đỏ. QoS Scheduler giúp họ tiết kiệm chi phí ra sao?**
*   **Trả lời:** Dạ, người dùng có thể lên Web Admin kéo giới hạn băng thông của các app tốn dung lượng (như TikTok, Instagram, YouTube) về mức 64 Kbps hoặc chặn hoàn toàn, chỉ cho phép các app chat (như Zalo, WhatsApp) chạy tối đa. Khi đó, Token Bucket của các app mạng xã hội sẽ hết thẻ và drop gói tin, ngăn chặn hoàn toàn việc các app này chạy ngầm tốn tiền.

**70. Tại sao trong thực nghiệm đo kiểm với iPerf3, đồ thị biểu diễn băng thông của bạn luôn có sai số nhỏ xung quanh đường giới hạn cấu hình?**
*   **Trả lời:** Dạ, đây là hành vi tự nhiên của giao thức TCP. TCP luôn cố gắng tăng tốc độ gửi để thăm dò mạng (cơ chế AIMD). Khi tốc độ vượt quá giới hạn của Token Bucket, gói tin bị drop. TCP phát hiện rớt gói liền giảm nhẹ tốc độ, rồi sau đó lại tăng lên để thăm dò tiếp. Việc tăng giảm liên tục này tạo ra các dao động nhỏ xung quanh đường giới hạn.

**71. Giải thích tại sao trong kịch bản Waveform Test (Kiểm tra dạng sóng băng thông thay đổi đột ngột), hệ thống thích ứng và kẹp băng thông allowed phẳng tắp chỉ trong vòng chưa đầy 1 giây?**
*   **Trả lời:** Dạ, khi người dùng thay đổi chính sách trên web, cấu hình mới được gửi xuống app qua API REST và ghi đè giá trị tốc độ trong RAM ngay lập tức (<50ms). Khi gói tin tiếp theo đi vào TUN, Token Bucket tính toán nạp thẻ dựa trên tốc độ mới này nên hệ thống thích ứng ngay lập tức trong chưa đầy 1 giây.

**72. Phân tích kết quả thực nghiệm RTT của các gói tin DNS (Port 53) khi mạng bị saturate tối đa. Làm thế nào hệ thống đảm bảo RTT phân giải tên miền luôn ở mức <10ms?**
*   **Trả lời:** Dạ, các gói tin DNS (cổng 53) được app của em tự động nhận diện và gán nhãn ưu tiên Cao (HIGH). Do đó, chúng luôn có sẵn thẻ Token và được ưu tiên gửi đi Internet vật lý trước tiên, giữ cho trễ DNS luôn dưới 10ms, giúp trải nghiệm lướt web tải trang mới rất nhanh.

**73. Hãy giải thích ý nghĩa thực tiễn của tham số "Độ lệch chuẩn băng thông" (Bandwidth Standard Deviation) trong phần đánh giá kiểm định QoS của luận văn.**
*   **Trả lời:** Dạ, độ lệch chuẩn đo lường mức độ biến động của tốc độ mạng xung quanh đường giới hạn. Giá trị độ lệch chuẩn càng nhỏ (ví dụ <0.15 Mbps trên giới hạn 5 Mbps) chứng tỏ bộ lập lịch Token Bucket hoạt động cực kỳ ổn định, kẹp phẳng tốc độ mạng mà không gây ra hiện tượng giật cục băng thông (traffic jitter).

**74. Làm thế nào hệ thống của bạn xử lý sự thay đổi năng lượng pin của thiết bị trong quá trình đo kiểm thực nghiệm lâu dài?**
*   **Trả lời:** Dạ, em sử dụng bộ quản lý pin của Android để theo dõi dòng điện tiêu thụ trong quá trình đo. Kết quả cho thấy khi bật QoS, tổng năng lượng tiêu thụ của điện thoại giảm khoảng 8% khi tải file lớn. Lý do là cơ chế Backpressure của TCP giúp giảm tần suất hoạt động truyền nhận của chip sóng vô tuyến, giúp tiết kiệm pin hơn.

**75. Bạn có thực hiện thực nghiệm trên nhiều thiết bị khách kết nối hotspot đồng thời không? Kết quả đo đạc thế nào?**
*   **Trả lời:** Dạ có, em đã đo kiểm với 3 máy khách kết nối hotspot đồng thời cùng tải file. Bộ lập lịch WFQ chia đều băng thông tổng cho cả 3 máy, sai lệch tốc độ giữa các máy luôn dưới 5%, chứng minh thuật toán WFQ chia băng thông rất công bằng.

**76. Tại sao kết quả thực nghiệm đo băng thông của giao thức UDP bằng iPerf3 đôi khi hiển thị tỷ lệ mất gói tin (Packet Loss Rate) rất cao khi bật QoS?**
*   **Trả lời:** Dạ, vì UDP không có cơ chế tự động giảm tốc độ gửi khi bị mất gói tin như TCP. iPerf3 gửi UDP vẫn đẩy gói tin đi liên tục ở tốc độ tối đa. Để giữ đúng giới hạn băng thông cấu hình, Token Bucket của em bắt buộc phải drop các gói tin vượt ngưỡng, dẫn đến tỷ lệ mất gói hiển thị cao. Đây là hành vi đúng của thuật toán Policing.

**77. Làm thế nào bạn đảm bảo tính khách quan và khoa học của các số liệu thực nghiệm đo đạc trong luận văn?**
*   **Trả lời:** Dạ, để số liệu khách quan, em lặp lại mỗi thí nghiệm 50 lần ở các khung giờ khác nhau để loại bỏ nhiễu mạng ngẫu nhiên. Sau đó em dùng các công thức thống kê toán học như tính Trung bình (Mean), Độ lệch chuẩn (Standard Deviation) và Khoảng tin cậy 95% để xử lý dữ liệu biểu diễn trong luận văn.

**78. Nếu Hội đồng hỏi: "Tại sao không triển khai QoS ở tầng Router Wi-Fi cho đơn giản mà lại phải xây dựng hệ thống phức tạp trên thiết bị di động?", bạn sẽ biện luận thế nào?**
*   **Trả lời:** Dạ thưa thầy/cô, triển khai trên di động có 3 lợi ích vượt trội: Một là, app chạy trực tiếp trên máy nên khi người dùng dùng mạng 4G/5G hay Wi-Fi công cộng thì vẫn có QoS. Hai là, di động có thể xác định chính xác 100% ứng dụng nào sở hữu gói tin bằng cách lấy UID từ hệ điều hành, còn Router chỉ nhìn thấy IP/Port chung chung nên bị mù trước các lưu lượng mã hóa HTTPS (cổng 443). Ba là, người dùng có thể tự cấu hình ưu tiên trực quan cho các app của mình ngay trên điện thoại.

**79. Phân tích các giới hạn kỹ thuật (Technical Limitations) hiện tại của hệ thống QoS Scheduler của bạn và hướng giải quyết trong tương lai.**
*   **Trả lời:** Dạ, hệ thống có 2 giới hạn: Thứ nhất là yêu cầu Android 10 trở lên để gọi lệnh lấy UID. Hướng khắc phục là nghiên cứu lấy UID qua log socket của eBPF cho các dòng Android cũ hơn. Thứ hai là việc trung chuyển gói tin qua Userspace proxy tạo ra một độ trễ rất nhỏ. Hướng khắc phục là viết lại các module xử lý gói tin bằng ngôn ngữ Rust hoặc C++ rồi gọi qua JNI để tăng tốc độ.

**80. Nếu các ứng dụng thương mại lớn (như Facebook hoặc YouTube) cố ý vượt qua giới hạn QoS bằng cách sử dụng các thuật toán tầng ứng dụng (L7) hung hãn (như mở đồng thời nhiều kết nối TCP song song, truyền dữ liệu qua giao thức QUIC/HTTP3 trên cổng UDP, hoặc tự động co giãn bitrate), hệ thống của bạn ứng phó ra sao? Họ có bypass được không?**
*   **Trả lời:** Dạ thưa thầy/cô, các app lớn **hoàn toàn không thể vượt qua** giới hạn của hệ thống nhờ các thiết kế sau:
    1. *Token Bucket chung theo UID:* App của em giới hạn băng thông theo ID của ứng dụng (UID) cài trên máy chứ không quản lý theo từng kết nối riêng lẻ. Nên dù Facebook có mở 10 hay 100 kết nối song song để tải video, chúng đều phải dùng chung một kho thẻ (Token Bucket) của Facebook, tốc độ tổng của app vẫn bị giới hạn cứng.
    2. *Đánh chặn cổng UDP/QUIC:* Đối với giao thức QUIC (HTTP/3) chạy trên UDP cổng 443 của YouTube, em cũng đánh chặn cổng này và áp dụng Token Bucket tương tự TCP, khiến gói tin bị drop và bắt buộc QUIC phải giảm tốc độ gửi.
    3. *Bitrate tự động hạ xuống:* Khi bị bóp băng thông, trình phát video của YouTube/Facebook sẽ tự động hạ độ phân giải video xuống (ví dụ từ 1080p xuống 480p) để khớp với băng thông được cấp, giúp video chạy mượt mà mà không gây nghẽn mạng.

---

## CHỦ ĐỀ VIII: MÔ HÌNH HÓA MỐI ĐE DỌA & BẢO MẬT HỆ THỐNG (Câu 81 - 100)

**81. Kẻ tấn công (Hacker) có thể thực hiện tấn công nghe lén Man-in-the-Middle (MitM) để đọc trộm các mật khẩu HTTPS của người dùng thông qua card mạng ảo TUN hay không?**
*   **Trả lời:** Dạ thưa thầy/cô, tuyệt đối không. Vì giao thức HTTPS được mã hóa đầu cuối bằng SSL/TLS giữa máy điện thoại và server thật. App proxy của em chỉ hoạt động ở tầng dưới (Layer 4 TCP), làm nhiệm vụ chuyển tiếp dữ liệu nhị phân đã được mã hóa chứ không có khóa giải mã, nên hacker hoàn toàn không thể đọc trộm được nội dung mật khẩu hay thông tin nhạy cảm bên trong.

**82. Một ứng dụng độc hại (Malware) được cài đặt trên thiết bị di động có thể tận dụng dịch vụ QoS Proxy của bạn để thực hiện "Sandbox Escape" (Vượt rào sandbox Android) để đánh cắp dữ liệu riêng tư của các ứng dụng ngân hàng khác hay không?**
*   **Trả lời:** Dạ không. Cơ chế bảo mật cô lập ứng dụng (Sandbox) là do nhân Linux của hệ điều hành Android tự quản lý. App proxy của em chạy ở Userspace chỉ có quyền đọc ghi các gói tin mạng được chuyển hướng vào TUN chứ không có quyền can thiệp vào bộ nhớ RAM hay các tập tin của ứng dụng khác, nên không thể giúp malware vượt rào Sandbox được.

**83. Nếu một ứng dụng độc hại cố tình Spam hàng triệu gói tin kết nối ảo TCP SYN liên tục vào card mạng ảo TUN (tấn công từ chối dịch vụ TCP SYN Flood cục bộ), làm thế nào `TcpRelayManager` bảo vệ hệ thống khỏi bị cạn kiệt bộ nhớ RAM và treo máy?**
*   **Trả lời:** Dạ, em có 2 lớp bảo vệ: Một là cơ chế Cache âm (Negative Caching), các kết nối spam từ app độc hại sẽ nhanh chóng bị gán giá trị thất bại `-1` trong cache, giúp hệ thống bỏ qua nhanh mà không tốn CPU xử lý. Hai là em cấu hình giới hạn hàng đợi kết nối của socket proxy tối đa là 128 (`backlog = 128`). Nếu vượt quá, hệ điều hành Android sẽ tự động drop các gói kết nối mới ngay từ tầng nhân vật lý, giữ RAM luôn an toàn.

**84. Làm thế nào hệ thống của bạn ngăn chặn việc các ứng dụng độc hại thực hiện "DNS Spoofing" (Giả mạo địa chỉ IP phản hồi DNS) để định hướng người dùng truy cập vào các trang web lừa đảo?**
*   **Trả lời:** Dạ, app của em bắt cứng toàn bộ các gói tin DNS (cổng 53) đi qua card mạng ảo TUN và chuyển tiếp trực tiếp chúng tới các DNS an toàn của Cloudflare (`1.1.1.1`) hoặc Google (`8.8.8.8`) đã được protect khỏi VPN. Mọi gói tin giả mạo DNS từ mạng Wi-Fi ngoài đều bị proxy của em bỏ qua vì không khớp số thứ tự Sequence Number ảo đã được quản lý chặt chẽ ở proxy.

**85. Kẻ tấn công có thể chỉnh sửa gói tin thô đi qua TUN (Packet Tampering) để chèn các đoạn mã độc (SQL Injection) vào Node.js Backend thông qua luồng đồng bộ Telemetry hay không?**
*   **Trả lời:** Dạ không. Ở server Node.js, em sử dụng cơ chế Parameterized Queries (truy vấn tham số hóa) của thư viện `sql.js`. Toàn bộ dữ liệu telemetry gửi lên được bọc trong các tham số truyền riêng biệt chứ không bao giờ cộng chuỗi trực tiếp vào lệnh SQL. SQLite sẽ coi các dữ liệu này là các chuỗi chữ thuần túy, loại bỏ hoàn toàn nguy cơ tấn công SQL Injection.

**86. Điều gì xảy ra về mặt bảo mật nếu một ứng dụng độc hại cố tình giả mạo ID thiết bị khách (`device_id`) của một thiết bị khác để gửi dữ liệu Telemetry giả lên máy chủ?**
*   **Trả lời:** Dạ, hành động này cùng lắm chỉ làm sai lệch tạm thời các biểu đồ giám sát của thiết bị đó trên Web Admin chứ hoàn toàn không thể làm thay đổi chính sách QoS trên thiết bị thật. Các chính sách QoS được app di động kéo về trực tiếp từ server qua HTTPS và chỉ áp dụng cục bộ trong card mạng ảo của chính điện thoại đó.

**87. Làm thế nào hệ thống bảo vệ các tập tin Cơ sở dữ liệu SQLite vật lý `qos_scheduler.db` lưu trữ trên máy chủ Node.js khỏi bị đánh cắp hoặc chỉnh sửa trái phép bởi các tiến trình khác chạy cùng Server?**
*   **Trả lời:** Dạ, tệp SQLite `qos_scheduler.db` được lưu trong thư mục riêng của app Node.js và được phân quyền truy cập chặt chẽ ở mức hệ điều hành bằng lệnh `chmod 600`. Lệnh này chỉ cho phép duy nhất tiến trình Node.js quản trị được phép đọc ghi file, các tiến trình bên ngoài không có quyền xem hay chỉnh sửa.

**88. Nếu một ứng dụng độc hại cố gắng khai thác lỗi tràn bộ đệm (Buffer Overflow) trong lớp `RawPacket.kt` bằng cách gửi gói tin IP có kích thước header sai lệch, làm thế nào mã Kotlin của bạn tự bảo vệ?**
*   **Trả lời:** Dạ, trong file `RawPacket.kt`, trước khi phân tích gói tin, em đều kiểm tra độ dài mảng byte nhận được. Nếu độ dài nhỏ hơn 20 bytes (kích thước tối thiểu của IP header), hệ thống sẽ bỏ qua ngay (`return null`). Đồng thời em đối chiếu độ dài khai báo trong header với độ dài thực tế của gói, nếu sai lệch sẽ hủy gói ngay, nên không bị tràn bộ đệm.

**89. Tại sao trong `TcpRelayManager.kt`, việc gán địa chỉ IP nguồn của Client cho socket proxy kết nối ra WAN không bị hệ điều hành Android coi là giả mạo địa chỉ (IP Spoofing) và chặn lại?**
*   **Trả lời:** Dạ, khi socket proxy kết nối ra ngoài Internet, nó dùng địa chỉ IP thật của Wi-Fi hoặc 4G do điện thoại cung cấp chứ không giả mạo IP nguồn của Client. Địa chỉ IP nguồn của Client chỉ được em giả lập cục bộ bên trong card mạng ảo TUN (phạm vi nội bộ của máy). Đối với nhà mạng bên ngoài, đây vẫn là một kết nối hợp lệ từ IP thật của máy điện thoại, nên không bị chặn.

**90. Tại sao trên các dòng máy Android đời mới, việc đọc trực tiếp tệp `/proc/net/arp` của lớp `DeviceRegistry` bị chặn bởi chính sách SELinux? Bạn đã giải quyết vấn đề phân quyền này như thế nào để Hotspot QoS hoạt động?**
*   **Trả lời:** Dạ, trên các bản Android mới, chính sách bảo mật SELinux cấm đọc file hệ thống `/proc/net`. Để giải quyết, em dùng API `ConnectivityManager.getLinkProperties()` để lấy danh sách route của mạng Hotspot, kết hợp lắng nghe sự kiện phát sóng Wi-Fi (`ACTION_TETHER_STATE_CHANGED`) để lấy IP và MAC của các máy khách kết nối mà không cần đọc file hệ thống.

**91. Tại sao hệ thống proxy của bạn không gặp lỗi rò rỉ bộ nhớ (Memory Leak) khi đóng kết nối nửa chừng do người dùng bật/tắt liên tục chế độ máy bay (Airplane Mode)?**
*   **Trả lời:** Dạ, khi bật chế độ máy bay, Android tắt các card mạng và bắn sự kiện mất kết nối. Hệ thống của em lắng nghe sự kiện này và ngay lập tức đóng toàn bộ các `SocketChannel` vật lý đang chạy, dọn sạch bộ đệm ghi. Lệnh `try-finally` bao quanh vòng lặp giúp đảm bảo tài nguyên luôn được giải phóng an toàn kể cả khi đứt mạng đột ngột.

**92. Làm thế nào hệ thống của bạn ngăn chặn việc hacker sử dụng kỹ thuật tấn công phản xạ DNS (DNS Amplification) thông qua cổng UDP proxy để tấn công các server khác ngoài Internet?**
*   **Trả lời:** Dạ, tấn công phản xạ DNS yêu cầu hacker phải giả mạo IP nguồn của nạn nhân để DNS Server gửi phản hồi lớn về phía nạn nhân. Vì các socket UDP proxy của em được gọi lệnh `protect()` và bind trực tiếp vào IP thật của điện thoại, hệ điều hành sẽ tự động áp IP thật của máy làm IP nguồn. Hacker không thể giả mạo IP nguồn thông qua app của em, nên hoàn toàn không thực hiện được tấn công này.

**93. Điều gì ngăn cản việc một ứng dụng độc hại chạy trên điện thoại tự gửi các gói tin TCP giả mạo mang cờ `FIN` hoặc `RST` vào card mạng TUN để ngắt kết nối mạng của các ứng dụng khác đang chạy?**
*   **Trả lời:** Dạ, hệ điều hành Android cô lập các ứng dụng bằng cơ chế Sandbox. Một ứng dụng thông thường sẽ không có quyền truy cập hay ghi dữ liệu trực tiếp vào File Descriptor của card mạng ảo TUN (`/dev/tun`) vốn thuộc quyền sở hữu riêng của tiến trình VPN của em, nên app độc hại không thể chèn gói tin FIN/RST giả mạo vào được.

**94. Tại sao dự án không sử dụng giao thức HTTPS mà lại sử dụng giao thức HTTP REST để đồng bộ telemetry từ Android Client lên Node.js Server trong môi trường thử nghiệm? Nếu triển khai thực tế, rủi ro bảo mật là gì và khắc phục ra sao?**
*   **Trả lời:** Dạ, trong môi trường thử nghiệm cục bộ, việc dùng HTTP giúp em đơn giản hóa cấu hình chứng chỉ SSL tự ký trên điện thoại để kiểm thử nhanh. Nếu chạy thực tế trên Internet, rủi ro là hacker có thể nghe lén dữ liệu telemetry (như tên các app đang chạy). Để khắc phục, bắt buộc phải nâng cấp server lên HTTPS dùng chứng chỉ thật (như Let's Encrypt) và tích hợp xác thực token JWT trong Header.

**95. Làm thế nào hệ thống bảo vệ API cập nhật chính sách (`POST /api/policies`) trên Node.js Server khỏi việc bị các máy tính khác trong mạng LAN gửi yêu cầu giả mạo để thay đổi cấu hình bóp băng thông của điện thoại?**
*   **Trả lời:** Dạ, em tạo một khóa bí mật (API Key) được lưu sẵn trên cả Server và App di động. Khi gọi API cập nhật chính sách, Client phải truyền kèm khóa này trong Header `X-API-KEY`. Server Node.js sẽ so khớp khóa, nếu không đúng sẽ từ chối ngay, ngăn chặn các yêu cầu giả mạo từ mạng LAN.

**96. Tại sao hệ thống proxy của bạn không hỗ trợ phân tích các gói tin HTTP thô để lọc URL (URL Filtering) mà chỉ giới hạn ở phân loại cổng dịch vụ (Port-based Classification)?**
*   **Trả lời:** Dạ, vì hiện nay hầu hết web đều dùng HTTPS mã hóa dữ liệu. Gói tin đi qua proxy đều là nhị phân đã mã hóa, em có phân tích gói HTTP thô cũng không lấy được URL của HTTPS mà chỉ gây lãng phí CPU. Nên em giới hạn phân loại theo cổng dịch vụ và IP đích, vừa nhanh vừa hiệu quả.

**97. Kịch bản tấn công: Kẻ tấn công cố tình gửi gói tin IP có độ dài trường Tổng độ dài (Total Length) lớn hơn kích thước thực tế của gói tin thô nhận được từ TUN. Lớp `RawPacket.kt` xử lý kịch bản lừa đảo này thế nào để tránh lỗi đọc tràn bộ nhớ (Out of Bounds)?**
*   **Trả lời:** Dạ, trong `RawPacket.kt`, em không tin tưởng hoàn toàn vào độ dài ghi trong IP header. Em dùng độ dài thực tế của gói tin nhận được từ card mạng (`length`). Khi cắt mảng byte để lấy dữ liệu, em dùng hàm `minOf(totalLength, length)` làm giới hạn, nên không bao giờ bị lỗi đọc tràn bộ nhớ.

**98. Làm thế nào hệ thống bảo vệ cơ sở dữ liệu SQLite Wasm trong RAM của Node.js khỏi bị cạn kiệt dung lượng khi người dùng mở hàng triệu kết nối mạng ngắn hạn liên tiếp (DDoS dữ liệu bảng telemetry)?**
*   **Trả lời:** Dạ, em cấu hình giới hạn tối đa cho bảng telemetry là 50,000 dòng. Nếu số dòng lưu trong RAM vượt quá con số này, server sẽ tự động chạy cơ chế dọn dẹp khẩn cấp (Emergency Pruning) để xóa bớt 20% dữ liệu cũ nhất ngay lập tức, giữ bộ nhớ RAM luôn an toàn.

**99. Tại sao trong lớp `Ipv4TcpPacketCodec.kt`, việc ghi đè checksum vào header lại sử dụng các phép toán dịch bitwise (and 0xFF) mà không sử dụng các thư viện chuyển đổi kiểu dữ liệu của Java?**
*   **Trả lời:** Dạ, các phép toán dịch bitwise (`and 0xFF`, `shr`, `shl`) chạy trực tiếp trên thanh ghi của CPU nên cực kỳ nhanh (chỉ mất 1 chu kỳ máy). Nếu dùng các thư viện chuyển đổi của Java, hệ thống sẽ phải tạo thêm các đối tượng tạm trong RAM, làm chậm tiến trình xử lý gói tin và gây tốn pin.

**100. Hãy giải thích tại sao đề tài của bạn là một bước đi đột phá trong việc tối ưu hóa chất lượng dịch vụ (QoS) trên thiết bị di động mà không làm ảnh hưởng đến tính toàn vẹn bảo mật của hệ điều hành Android.**
*   **Trả lời:** Dạ thưa thầy/cô, đề tài của em giải quyết được bài toán khó: làm sao bóp băng thông chi tiết cho từng app mà không phá vỡ bảo mật của Android. Bằng cách thiết lập kiến trúc Split-Handshake Proxy chạy hoàn toàn ở Userspace kết hợp API VpnService chính thống, hệ thống đạt quyền quản lý băng thông và nhận diện app chính xác 100% dựa trên UID của hệ điều hành mà không cần bẻ khóa máy (Root), giữ nguyên mô hình bảo mật Sandbox của Android, và duy trì hiệu năng cao nhờ cơ chế cache tối ưu hóa Binder IPC thông minh.

---

## 🎯 CHIẾN LƯỢC TRẢ LỜI PHẢN BIỆN CHO SINH VIÊN

> [!TIP]
> **Nguyên tắc vàng khi trả lời:**
> 1. **Dạ thưa thầy/cô trước khi nói:** Thể hiện thái độ cầu thị, tôn trọng hội đồng.
> 2. **Trình bày trực quan trước, lý thuyết sau:** Ví dụ: 'Dạ, app của em tạo một card mạng ảo...' thay vì giải thích định nghĩa hàn lâm của VpnService.
> 3. **Thừa nhận tự tin các giới hạn:** Nếu bị hỏi câu khó ngoài phạm vi, hãy trả lời: 'Dạ, đây là giới hạn của hệ thống hiện tại, em đã nêu trong phần Hướng phát triển tương lai và sẽ tiếp tục tối ưu ạ.'
