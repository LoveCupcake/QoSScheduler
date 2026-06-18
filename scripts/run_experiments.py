import subprocess
import time
import re
import json
import argparse
import sys
from datetime import datetime

PACKAGE_NAME = "com.qos.scheduler"

def run_adb(cmd):
    try:
        result = subprocess.run(["adb"] + cmd, capture_output=True, text=True, check=True)
        return result.stdout.strip()
    except subprocess.CalledProcessError as e:
        print(f"ADB Error: {e.stderr}")
        return None
    except FileNotFoundError:
        print("Error: adb command not found. Please ensure ADB is installed and in your PATH.")
        sys.exit(1)

def measure_overhead(duration_sec=10):
    print(f"\n--- Đo lường System Overhead trong {duration_sec} giây ---")
    mem_measurements = []
    
    for _ in range(duration_sec):
        # Đo RAM
        mem_out = run_adb(["shell", f"dumpsys meminfo {PACKAGE_NAME}"])
        if mem_out:
            # Lấy dòng chứa TOTAL
            for line in mem_out.split('\n'):
                if "TOTAL" in line and ":" not in line:
                    parts = line.strip().split()
                    if len(parts) >= 2:
                        try:
                            mem_kb = int(parts[1])
                            mem_measurements.append(mem_kb / 1024.0) # Convert KB to MB
                        except:
                            pass
                    break
        time.sleep(1)
        
    avg_mem = sum(mem_measurements) / len(mem_measurements) if mem_measurements else 0
    print(f"-> RAM Trung bình (Average Memory): {avg_mem:.2f} MB")
    print("-> (CPU overhead: Cần kiểm tra bằng Android Studio Profiler để có con số chính xác nhất do khác biệt giữa các dòng máy Android)")
    return {"avg_mem_mb": avg_mem}

def measure_latency(pings=20):
    print(f"\n--- Đo lường Latency/Bufferbloat (Gửi {pings} gói Ping) ---")
    # Ping trực tiếp từ shell điện thoại
    out = run_adb(["shell", f"ping -c {pings} 8.8.8.8"])
    if not out:
        return None
    
    # Parse kết quả RTT: rtt min/avg/max/mdev = 34.123/38.567/45.123/2.345 ms
    match = re.search(r"rtt min/avg/max/mdev = ([\d\.]+)/([\d\.]+)/([\d\.]+)/([\d\.]+)", out)
    if match:
        min_rtt = float(match.group(1))
        avg_rtt = float(match.group(2))
        max_rtt = float(match.group(3))
        mdev_rtt = float(match.group(4))
        print(f"-> Latency Trung bình (Mean RTT): {avg_rtt} ms")
        print(f"-> Độ giật (Jitter / mdev): {mdev_rtt} ms")
        return {"avg": avg_rtt, "jitter": mdev_rtt}
    else:
        print("Không thể parse kết quả ping. Có thể do máy tính/điện thoại không có mạng.")
        return None

def measure_throughput(server_ip, time_sec=10):
    print(f"\n--- Đo lường Băng thông (Throughput) qua iPerf3 (Server: {server_ip}) ---")
    print("Vui lòng đảm bảo:")
    print("1. Điện thoại đã cài đặt Termux và gói iperf3 (apt install iperf3)")
    print("2. Đang chạy lệnh `iperf3 -s` trên PC này.")
    print(f"Đang gọi ADB: adb shell iperf3 -c {server_ip} -t {time_sec} -J")
    
    out = run_adb(["shell", f"iperf3 -c {server_ip} -t {time_sec} -J"])
    if not out:
        print("Lỗi: Không thể chạy iperf3 qua adb. Hãy chắc chắn điện thoại có lệnh iperf3.")
        return None
    
    try:
        # Cố gắng tìm phần JSON hợp lệ (bỏ qua các warning đầu ra của iperf3 nếu có)
        json_start = out.find('{')
        if json_start != -1:
            clean_json = out[json_start:]
            data = json.loads(clean_json)
            bits_per_sec = data['end']['sum_received']['bits_per_second']
            mbps = bits_per_sec / 1_000_000
            print(f"-> Băng thông thực tế (Throughput): {mbps:.2f} Mbps")
            return mbps
        else:
            raise ValueError("Không tìm thấy JSON output.")
    except Exception as e:
        print(f"Lỗi parse JSON kết quả iperf3: {e}")
        print("Gợi ý: Kiểm tra xem iperf3 server trên PC đã được bật chưa?")
        return None

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Script tự động hóa đo đạc luận văn QoS Scheduler")
    parser.add_argument("--server", type=str, help="Địa chỉ IP LAN của PC (để test iPerf3). Vd: 192.168.1.10")
    parser.add_argument("--quick", action="store_true", help="Chạy chế độ test nhanh (5 ping, 5 giây)")
    args = parser.parse_args()

    print("============================================================")
    print(" BẮT ĐẦU AUTOMATION SCRIPT ĐO ĐẠC LUẬN VĂN ")
    print("============================================================")

    print("Đang kiểm tra kết nối ADB...")
    devices = run_adb(["devices"])
    if "device\n" not in devices and "device\r\n" not in devices:
        print("LỖI: Không tìm thấy thiết bị ADB! Vui lòng cắm cáp và bật USB Debugging.")
        sys.exit(1)
    print("✅ Đã kết nối với thiết bị Android.")
        
    duration = 5 if args.quick else 15
    ping_count = 5 if args.quick else 50
    
    results = {}
    
    results['overhead'] = measure_overhead(duration_sec=duration)
    results['latency'] = measure_latency(pings=ping_count)
    
    if args.server:
        results['throughput'] = measure_throughput(args.server, time_sec=duration)
    else:
        print("\n--- Bỏ qua bài đo iPerf3 ---")
        print("Lý do: Bạn chưa cung cấp IP máy tính. Lần tới hãy thêm cờ: --server <IP_CỦA_MÁY_TÍNH>")

    # Xuất báo cáo Markdown
    print("\n\n============================================================")
    print(" BÁO CÁO KẾT QUẢ (Copy dán thẳng vào LaTeX Chapter 6)")
    print("============================================================")
    
    print(f"Ngày test: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("\n\\begin{table}[H]")
    print("\\centering")
    print("\\caption{Automated Experimental Results}")
    print("\\label{tab:auto_results}")
    print("\\begin{tabular}{lc}")
    print("\\toprule")
    print("\\textbf{Metric} & \\textbf{Measured Value} \\\\")
    print("\\midrule")
    
    if results.get('overhead') and results['overhead'].get('avg_mem_mb'):
        print(f"Average Memory Overhead & {results['overhead']['avg_mem_mb']:.2f} MB \\\\")
    if results.get('latency'):
        print(f"Average Latency (Mean RTT) & {results['latency']['avg']} ms \\\\")
        print(f"Jitter (mdev) & {results['latency']['jitter']} ms \\\\")
    if results.get('throughput'):
        print(f"Measured Throughput (TCP) & {results['throughput']:.2f} Mbps \\\\")
        
    print("\\bottomrule")
    print("\\end{tabular}")
    print("\\end{table}")
    print("\nHoàn tất. Chúc bạn bảo vệ đồ án thành công!")
