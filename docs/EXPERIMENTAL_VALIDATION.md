# Experimental Validation Guide

**Complete Testing Methodology for QoS Scheduler Thesis**

---

## Table of Contents

1. [Overview](#overview)
2. [Test Environment Setup](#test-environment-setup)
3. [iPerf3 Testing Methodology](#iperf3-testing-methodology)
4. [Wireshark Analysis](#wireshark-analysis)
5. [Test Scenarios](#test-scenarios)
6. [Data Collection](#data-collection)
7. [Results Analysis](#results-analysis)
8. [Graphs and Visualizations](#graphs-and-visualizations)

---

## 1. Overview

### Validation Objectives

1. **Prove QoS effectiveness** - Demonstrate throughput improvement for HIGH priority
2. **Measure latency reduction** - Show reduced delay for prioritized traffic
3. **Quantify jitter improvement** - Demonstrate stability for real-time apps
4. **Validate packet loss** - Show selective dropping for LOW priority
5. **Verify classification** - Use Wireshark to confirm traffic categorization

### Metrics to Measure

| Metric | Tool | Target |
|--------|------|--------|
| Throughput (Mbps) | iPerf3 | 3.2× improvement for HIGH |
| Latency (ms) | iPerf3 | 47% reduction for HIGH |
| Jitter (ms) | iPerf3 | 62% reduction for HIGH |
| Packet Loss (%) | iPerf3 | <1% for HIGH, >50% for LOW |
| Packet Distribution | Wireshark | Verify classification |

---

## 2. Test Environment Setup

### Hardware Requirements

```
Phone (Hotspot):
  - Android 10+ device
  - QoS Scheduler app installed
  - Mobile data or WiFi connection
  - Uplink: 10 Mbps (configured in app)

Test Device 1 (HIGH Priority):
  - Laptop/PC with WiFi
  - iPerf3 installed
  - Wireshark installed
  - IP: 192.168.43.2

Test Device 2 (LOW Priority):
  - Laptop/PC with WiFi
  - iPerf3 installed
  - IP: 192.168.43.3

iPerf3 Server:
  - Cloud server or remote machine
  - Public IP address
  - iPerf3 server running
```

### Software Installation

#### Install iPerf3

**On Test Devices (Windows):**
```bash
# Download from: https://iperf.fr/iperf-download.php
# Or use Chocolatey:
choco install iperf3

# Verify installation:
iperf3 --version
```

**On Test Devices (Linux/Mac):**
```bash
# Ubuntu/Debian:
sudo apt-get install iperf3

# Mac:
brew install iperf3

# Verify:
iperf3 --version
```

**On Server:**
```bash
# Install iPerf3
sudo apt-get install iperf3

# Start server (runs continuously):
iperf3 -s

# Or with specific port:
iperf3 -s -p 5201
```

#### Install Wireshark

**Windows:**
```
Download from: https://www.wireshark.org/download.html
Install with default options
Include: WinPcap/Npcap for packet capture
```

**Linux:**
```bash
sudo apt-get install wireshark
sudo usermod -aG wireshark $USER
```

**Mac:**
```bash
brew install --cask wireshark
```

### Network Topology

```
┌─────────────────────────────────────────────────────────┐
│                    Internet                              │
│                 (iPerf3 Server)                          │
│                  203.0.113.10                            │
└──────────────────────┬──────────────────────────────────┘
                       │
                       │ Mobile Data / WiFi
                       │
┌──────────────────────▼──────────────────────────────────┐
│              Phone (Mobile Hotspot)                      │
│           QoS Scheduler App Running                      │
│              IP: 192.168.43.1                            │
└──────────────────────┬──────────────────────────────────┘
                       │
          ┌────────────┴────────────┐
          │                         │
┌─────────▼─────────┐    ┌─────────▼─────────┐
│  Device 1 (HIGH)  │    │  Device 2 (LOW)   │
│  192.168.43.2     │    │  192.168.43.3     │
│  iPerf3 Client    │    │  iPerf3 Client    │
│  Wireshark        │    │  iPerf3 Client    │
└───────────────────┘    └───────────────────┘
```

---

## 3. iPerf3 Testing Methodology

### Test 1: Baseline (Without QoS)

**Purpose:** Establish baseline performance without QoS

**Steps:**
1. Turn OFF QoS Scheduler app
2. Connect both devices to hotspot
3. Run simultaneous iPerf3 tests

**Commands:**

```bash
# On Device 1 (Terminal 1):
iperf3 -c 203.0.113.10 -t 60 -i 1 -J > baseline_device1.json

# On Device 2 (Terminal 2):
iperf3 -c 203.0.113.10 -t 60 -i 1 -J > baseline_device2.json
```

**Parameters:**
- `-c`: Connect to server
- `-t 60`: Test duration 60 seconds
- `-i 1`: Report interval 1 second
- `-J`: JSON output for analysis

**Expected Results:**
```
Device 1: ~5 Mbps (50% of 10 Mbps)
Device 2: ~5 Mbps (50% of 10 Mbps)
Fair sharing, both devices equal
```

---

### Test 2: With QoS (HIGH vs LOW Priority)

**Purpose:** Demonstrate QoS effectiveness

**Steps:**
1. Turn ON QoS Scheduler app
2. Set Device 1 (192.168.43.2) to HIGH priority
3. Set Device 2 (192.168.43.3) to LOW priority
4. Run simultaneous iPerf3 tests

**Commands:**

```bash
# On Device 1 (HIGH priority):
iperf3 -c 203.0.113.10 -t 60 -i 1 -J > qos_high_priority.json

# On Device 2 (LOW priority):
iperf3 -c 203.0.113.10 -t 60 -i 1 -J > qos_low_priority.json
```

**Expected Results:**
```
Device 1 (HIGH): ~8 Mbps (80% of 10 Mbps)
Device 2 (LOW):  ~2 Mbps (20% of 10 Mbps)
4× throughput advantage for HIGH priority
```

---

### Test 3: Latency and Jitter Measurement

**Purpose:** Measure latency and jitter for real-time applications

**Commands:**

```bash
# UDP test for latency/jitter measurement
# Device 1 (HIGH priority):
iperf3 -c 203.0.113.10 -u -b 3M -t 60 -i 1 -J > qos_high_udp.json

# Device 2 (LOW priority):
iperf3 -c 203.0.113.10 -u -b 3M -t 60 -i 1 -J > qos_low_udp.json
```

**Parameters:**
- `-u`: UDP mode (for latency/jitter)
- `-b 3M`: Bandwidth 3 Mbps (typical video call)

**Expected Results:**
```
HIGH Priority:
  - Latency: 30-50 ms
  - Jitter: 5-10 ms
  - Packet Loss: <1%

LOW Priority:
  - Latency: 100-200 ms
  - Jitter: 30-50 ms
  - Packet Loss: 50-80%
```

---

### Test 4: Three-Device Scenario

**Purpose:** Demonstrate WFQ with multiple priorities

**Setup:**
- Device 1: HIGH priority
- Device 2: MEDIUM priority
- Device 3: LOW priority

**Commands:**

```bash
# Device 1 (HIGH):
iperf3 -c 203.0.113.10 -t 60 -i 1 -J > qos_3dev_high.json

# Device 2 (MEDIUM):
iperf3 -c 203.0.113.10 -t 60 -i 1 -J > qos_3dev_medium.json

# Device 3 (LOW):
iperf3 -c 203.0.113.10 -t 60 -i 1 -J > qos_3dev_low.json
```

**Expected Results (WFQ with weights 4:2:1):**
```
Device 1 (HIGH):   5.7 Mbps (57%)
Device 2 (MEDIUM): 2.9 Mbps (29%)
Device 3 (LOW):    1.4 Mbps (14%)
```

---

## 4. Wireshark Analysis

### Purpose of Wireshark

1. **Verify traffic classification** - Confirm DPI-Lite correctly identifies traffic
2. **Analyze packet timing** - Show packet spacing differences
3. **Visualize packet drops** - Demonstrate selective dropping
4. **Validate flow tracking** - Confirm 5-tuple flow identification

### Capture Setup

#### Start Packet Capture

**On Test Device:**
```
1. Open Wireshark
2. Select WiFi interface (e.g., "Wi-Fi" or "wlan0")
3. Start capture
4. Run iPerf3 test
5. Stop capture after test completes
6. Save as: capture_baseline.pcapng or capture_qos.pcapng
```

#### Capture Filters (Optional)

```
# Capture only traffic to iPerf server:
host 203.0.113.10

# Capture only TCP traffic:
tcp

# Capture only UDP traffic:
udp
```

---

### Wireshark Analysis Tasks

#### Analysis 1: Traffic Classification Verification

**Display Filter:**
```
ip.src == 192.168.43.2 && tcp.dstport == 5201
```

**What to look for:**
- Destination port: 5201 (iPerf3)
- Protocol: TCP
- Packet sizes
- Inter-packet timing

**Screenshot for thesis:**
- Packet list showing classified traffic
- Highlight: Source IP, Destination Port, Protocol

---

#### Analysis 2: Packet Timing Analysis

**Steps:**
1. Statistics → Flow Graph
2. Select flow from Device 1 (HIGH priority)
3. Compare with flow from Device 2 (LOW priority)

**What to show:**
- HIGH priority: Consistent packet spacing (smooth)
- LOW priority: Irregular packet spacing (bursty)

**Screenshot for thesis:**
- Flow graph showing timing differences

---

#### Analysis 3: Throughput Comparison

**Steps:**
1. Statistics → I/O Graph
2. Add filter for Device 1: `ip.src == 192.168.43.2`
3. Add filter for Device 2: `ip.src == 192.168.43.3`
4. Set Y-axis to "Bits/s"
5. Set interval to 1 second

**What to show:**
- Device 1 (HIGH): Higher, stable throughput
- Device 2 (LOW): Lower, variable throughput

**Screenshot for thesis:**
- I/O Graph with both devices overlaid

---

#### Analysis 4: Packet Loss Visualization

**For UDP tests:**

**Display Filter:**
```
udp && ip.dst == 203.0.113.10
```

**Steps:**
1. Statistics → UDP Multicast Streams
2. Or manually count packets
3. Compare sent vs received (from iPerf3 output)

**What to show:**
- HIGH priority: Minimal packet loss
- LOW priority: Significant packet loss

---

#### Analysis 5: Protocol Distribution

**Steps:**
1. Statistics → Protocol Hierarchy
2. Show distribution of TCP vs UDP
3. Show port distribution

**Screenshot for thesis:**
- Protocol hierarchy showing traffic breakdown

---

### Wireshark Filters Cheat Sheet

```
# Show only iPerf3 traffic:
tcp.port == 5201 || udp.port == 5201

# Show traffic from specific device:
ip.src == 192.168.43.2

# Show only data packets (no ACKs):
tcp.len > 0

# Show retransmissions (indicates packet loss):
tcp.analysis.retransmission

# Show packets with specific flags:
tcp.flags.syn == 1  # SYN packets
tcp.flags.fin == 1  # FIN packets

# Time-based filter:
frame.time >= "2024-01-01 10:00:00" && frame.time <= "2024-01-01 10:01:00"
```

---

## 5. Test Scenarios

### Scenario 1: Video Call vs File Download

**Objective:** Demonstrate real-world use case

**Setup:**
- Device 1 (HIGH): Simulate video call (UDP, 3 Mbps)
- Device 2 (LOW): File download (TCP, max speed)

**Commands:**
```bash
# Device 1 (Video call simulation):
iperf3 -c 203.0.113.10 -u -b 3M -t 60 -i 1 -J > scenario1_video.json

# Device 2 (File download):
iperf3 -c 203.0.113.10 -t 60 -i 1 -J > scenario1_download.json
```

**Expected:**
- Video call: Smooth, low latency, no packet loss
- Download: Slower, but doesn't affect video

---

### Scenario 2: Multiple Video Calls

**Objective:** Show fair sharing among same priority

**Setup:**
- Device 1 (HIGH): Video call 1
- Device 2 (HIGH): Video call 2

**Commands:**
```bash
# Both devices:
iperf3 -c 203.0.113.10 -u -b 3M -t 60 -i 1 -J > scenario2_dev1.json
iperf3 -c 203.0.113.10 -u -b 3M -t 60 -i 1 -J > scenario2_dev2.json
```

**Expected:**
- Both get equal share of HIGH priority bandwidth
- Both experience good quality

---

### Scenario 3: Priority Change During Test

**Objective:** Show dynamic rebalancing

**Steps:**
1. Start iPerf3 on both devices
2. Both initially MEDIUM priority
3. After 30 seconds, change Device 1 to HIGH
4. Observe throughput change

**Expected:**
- First 30s: Equal sharing
- After 30s: Device 1 gets more bandwidth

---

## 6. Data Collection

### iPerf3 Output Files

**Save all JSON outputs:**
```
results/
├── baseline_device1.json
├── baseline_device2.json
├── qos_high_priority.json
├── qos_low_priority.json
├── qos_high_udp.json
├── qos_low_udp.json
├── qos_3dev_high.json
├── qos_3dev_medium.json
├── qos_3dev_low.json
├── scenario1_video.json
├── scenario1_download.json
├── scenario2_dev1.json
└── scenario2_dev2.json
```

### Wireshark Capture Files

**Save all captures:**
```
captures/
├── baseline.pcapng
├── qos_high_low.pcapng
├── qos_3devices.pcapng
├── scenario1_video_download.pcapng
└── scenario2_dual_video.pcapng
```

### App Screenshots

**Capture from QoS Scheduler app:**
```
screenshots/
├── dashboard_3devices.png
├── device_detail_high.png
├── device_detail_low.png
├── settings_uplink.png
├── active_flows.png
└── statistics.png
```

---

## 7. Results Analysis

### Parse iPerf3 JSON Output

**Python script to extract metrics:**

```python
import json
import pandas as pd
import matplotlib.pyplot as plt

def parse_iperf3_json(filename):
    with open(filename, 'r') as f:
        data = json.load(f)
    
    # Extract summary
    summary = data['end']['sum_received']
    
    results = {
        'throughput_mbps': summary['bits_per_second'] / 1_000_000,
        'bytes': summary['bytes'],
        'seconds': summary['seconds'],
    }
    
    # For UDP tests, extract latency/jitter
    if 'udp' in filename:
        results['jitter_ms'] = summary.get('jitter_ms', 0)
        results['lost_packets'] = summary.get('lost_packets', 0)
        results['packets'] = summary.get('packets', 0)
        results['lost_percent'] = summary.get('lost_percent', 0)
    
    return results

# Parse all results
baseline_dev1 = parse_iperf3_json('baseline_device1.json')
baseline_dev2 = parse_iperf3_json('baseline_device2.json')
qos_high = parse_iperf3_json('qos_high_priority.json')
qos_low = parse_iperf3_json('qos_low_priority.json')

# Calculate improvements
throughput_improvement = qos_high['throughput_mbps'] / baseline_dev1['throughput_mbps']
print(f"Throughput improvement: {throughput_improvement:.2f}×")
```

---

### Calculate Key Metrics

**Throughput Improvement:**
```
Improvement = (QoS HIGH throughput) / (Baseline throughput)
Target: 3.2× or higher
```

**Latency Reduction:**
```
Reduction = ((Baseline latency - QoS HIGH latency) / Baseline latency) × 100%
Target: 47% or higher
```

**Jitter Reduction:**
```
Reduction = ((Baseline jitter - QoS HIGH jitter) / Baseline jitter) × 100%
Target: 62% or higher
```

---

## 8. Graphs and Visualizations

### Graph 1: Throughput Comparison (Bar Chart)

```python
import matplotlib.pyplot as plt
import numpy as np

categories = ['Baseline\nDevice 1', 'Baseline\nDevice 2', 
              'QoS\nHIGH', 'QoS\nLOW']
throughput = [5.0, 5.0, 8.0, 2.0]  # Example values in Mbps

plt.figure(figsize=(10, 6))
bars = plt.bar(categories, throughput, color=['gray', 'gray', 'green', 'red'])
plt.ylabel('Throughput (Mbps)', fontsize=12)
plt.title('Throughput Comparison: Baseline vs QoS', fontsize=14, fontweight='bold')
plt.ylim(0, 10)
plt.grid(axis='y', alpha=0.3)

# Add value labels on bars
for bar in bars:
    height = bar.get_height()
    plt.text(bar.get_x() + bar.get_width()/2., height,
             f'{height:.1f} Mbps',
             ha='center', va='bottom', fontsize=10)

plt.tight_layout()
plt.savefig('graph1_throughput_comparison.png', dpi=300)
plt.show()
```

---

### Graph 2: Time Series Throughput

```python
import pandas as pd

# Parse interval data from iPerf3 JSON
def parse_intervals(filename):
    with open(filename, 'r') as f:
        data = json.load(f)
    
    intervals = []
    for interval in data['intervals']:
        intervals.append({
            'time': interval['sum']['end'],
            'throughput': interval['sum']['bits_per_second'] / 1_000_000
        })
    
    return pd.DataFrame(intervals)

# Load data
high_df = parse_intervals('qos_high_priority.json')
low_df = parse_intervals('qos_low_priority.json')

# Plot
plt.figure(figsize=(12, 6))
plt.plot(high_df['time'], high_df['throughput'], 
         label='HIGH Priority', color='green', linewidth=2)
plt.plot(low_df['time'], low_df['throughput'], 
         label='LOW Priority', color='red', linewidth=2)
plt.xlabel('Time (seconds)', fontsize=12)
plt.ylabel('Throughput (Mbps)', fontsize=12)
plt.title('Real-Time Throughput: HIGH vs LOW Priority', fontsize=14, fontweight='bold')
plt.legend(fontsize=11)
plt.grid(alpha=0.3)
plt.tight_layout()
plt.savefig('graph2_throughput_timeseries.png', dpi=300)
plt.show()
```

---

### Graph 3: Latency and Jitter Comparison

```python
metrics = ['Latency (ms)', 'Jitter (ms)']
baseline = [68, 18.6]  # Example values
qos_high = [36, 7.1]

x = np.arange(len(metrics))
width = 0.35

fig, ax = plt.subplots(figsize=(10, 6))
bars1 = ax.bar(x - width/2, baseline, width, label='Baseline', color='gray')
bars2 = ax.bar(x + width/2, qos_high, width, label='QoS HIGH', color='green')

ax.set_ylabel('Milliseconds (ms)', fontsize=12)
ax.set_title('Latency and Jitter: Baseline vs QoS', fontsize=14, fontweight='bold')
ax.set_xticks(x)
ax.set_xticklabels(metrics, fontsize=11)
ax.legend(fontsize=11)
ax.grid(axis='y', alpha=0.3)

# Add value labels
for bars in [bars1, bars2]:
    for bar in bars:
        height = bar.get_height()
        ax.text(bar.get_x() + bar.get_width()/2., height,
                f'{height:.1f}',
                ha='center', va='bottom', fontsize=10)

plt.tight_layout()
plt.savefig('graph3_latency_jitter.png', dpi=300)
plt.show()
```

---

### Graph 4: WFQ Bandwidth Allocation (Pie Chart)

```python
labels = ['HIGH\n(57%)', 'MEDIUM\n(29%)', 'LOW\n(14%)']
sizes = [5.7, 2.9, 1.4]
colors = ['#4CAF50', '#FFC107', '#F44336']
explode = (0.1, 0, 0)  # Explode HIGH priority

plt.figure(figsize=(8, 8))
plt.pie(sizes, explode=explode, labels=labels, colors=colors,
        autopct='%1.1f Mbps', shadow=True, startangle=90,
        textprops={'fontsize': 12, 'fontweight': 'bold'})
plt.title('WFQ Bandwidth Allocation (3 Devices, 10 Mbps Total)', 
          fontsize=14, fontweight='bold', pad=20)
plt.axis('equal')
plt.tight_layout()
plt.savefig('graph4_wfq_allocation.png', dpi=300)
plt.show()
```

---

### Graph 5: Packet Loss Comparison

```python
categories = ['HIGH Priority', 'MEDIUM Priority', 'LOW Priority']
packet_loss = [0.5, 5.0, 65.0]  # Example percentages

plt.figure(figsize=(10, 6))
bars = plt.bar(categories, packet_loss, color=['green', 'orange', 'red'])
plt.ylabel('Packet Loss (%)', fontsize=12)
plt.title('Packet Loss by Priority Class', fontsize=14, fontweight='bold')
plt.ylim(0, 100)
plt.grid(axis='y', alpha=0.3)

# Add value labels
for bar in bars:
    height = bar.get_height()
    plt.text(bar.get_x() + bar.get_width()/2., height,
             f'{height:.1f}%',
             ha='center', va='bottom', fontsize=10)

# Add threshold line
plt.axhline(y=1, color='blue', linestyle='--', linewidth=2, 
            label='Acceptable threshold (1%)')
plt.legend(fontsize=10)

plt.tight_layout()
plt.savefig('graph5_packet_loss.png', dpi=300)
plt.show()
```

---

## Summary Checklist

### Before Testing
- [ ] Install iPerf3 on all devices
- [ ] Install Wireshark on test device
- [ ] Set up iPerf3 server
- [ ] Configure phone hotspot
- [ ] Install QoS Scheduler app
- [ ] Verify network connectivity

### During Testing
- [ ] Run baseline tests (no QoS)
- [ ] Run QoS tests (HIGH vs LOW)
- [ ] Run UDP tests (latency/jitter)
- [ ] Run 3-device tests (WFQ)
- [ ] Capture Wireshark traces
- [ ] Take app screenshots
- [ ] Document any issues

### After Testing
- [ ] Parse iPerf3 JSON outputs
- [ ] Analyze Wireshark captures
- [ ] Generate graphs
- [ ] Calculate improvement metrics
- [ ] Write results section
- [ ] Add to thesis Chapter 8

---

## Expected Timeline

| Task | Duration | Notes |
|------|----------|-------|
| Environment setup | 2 hours | Install tools, configure network |
| Baseline tests | 30 minutes | 2-3 test runs |
| QoS tests | 1 hour | Multiple scenarios |
| Wireshark analysis | 1 hour | Capture and analyze |
| Data processing | 2 hours | Parse JSON, generate graphs |
| Documentation | 2 hours | Write results section |
| **Total** | **8-9 hours** | Can be done in 1-2 days |

---

**Good luck with your experiments!** 🚀

