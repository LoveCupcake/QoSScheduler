# Wireshark Analysis Guide for Thesis

**How to use Wireshark to validate QoS effectiveness**

---

## Why Wireshark?

Wireshark provides **visual proof** that your QoS system works:
1. **Packet timing** - Shows HIGH priority packets sent more frequently
2. **Flow analysis** - Confirms traffic classification
3. **Throughput graphs** - Visual comparison of bandwidth allocation
4. **Protocol distribution** - Validates DPI-Lite classifier

**For thesis:** Wireshark screenshots add credibility and visual appeal!

---

## Capture Setup

### Basic Capture

1. **Open Wireshark**
2. **Select WiFi interface** (the one connected to hotspot)
3. **Start capture** (blue shark fin icon)
4. **Run iPerf3 test** (30-60 seconds)
5. **Stop capture** (red square icon)
6. **Save file**: File → Save As → `capture_name.pcapng`

### Capture with Filter (Recommended)

**Capture only iPerf3 traffic:**

```
Filter: host YOUR_SERVER_IP
```

This reduces file size and focuses on relevant traffic.

**Example:**
```
host 203.0.113.10
```

---

## Analysis 1: I/O Graph (Throughput Visualization)

### Purpose
Show visual difference in throughput between HIGH and LOW priority

### Steps

1. **Open capture file** (HIGH priority)
2. **Statistics → I/O Graph**
3. **Configure:**
   - Y-axis: Bits/s
   - Interval: 1 second
   - Style: Line
4. **Add filter:** `tcp.port == 5201`
5. **Screenshot** for thesis

### Repeat for LOW Priority

1. Open LOW priority capture
2. Same steps as above
3. Compare graphs side-by-side

### What to Show in Thesis

**Figure X.1: Throughput Comparison via Wireshark I/O Graph**

```
HIGH Priority:
┌─────────────────────────────────────┐
│ Throughput (Mbps)                   │
│ 10 ┤                                 │
│  8 ┤████████████████████████████████ │ ← Stable, high
│  6 ┤                                 │
│  4 ┤                                 │
│  2 ┤                                 │
│  0 └─────────────────────────────────│
│    0   10   20   30   40   50   60  │
│              Time (seconds)          │
└─────────────────────────────────────┘

LOW Priority:
┌─────────────────────────────────────┐
│ Throughput (Mbps)                   │
│ 10 ┤                                 │
│  8 ┤                                 │
│  6 ┤                                 │
│  4 ┤                                 │
│  2 ┤██████████████████████████████   │ ← Stable, low
│  0 └─────────────────────────────────│
│    0   10   20   30   40   50   60  │
│              Time (seconds)          │
└─────────────────────────────────────┘
```

**Caption:**
"Figure X.1: Wireshark I/O Graph showing throughput difference. HIGH priority device maintains 8 Mbps while LOW priority device is limited to 2 Mbps, demonstrating effective QoS enforcement."

---

## Analysis 2: Flow Graph (Packet Timing)

### Purpose
Show packet spacing differences between priorities

### Steps

1. **Open capture file**
2. **Statistics → Flow Graph**
3. **Configure:**
   - Flow type: TCP Flows
   - Select specific flow (iPerf3)
4. **Screenshot** showing packet timing

### What to Look For

**HIGH Priority:**
- Packets evenly spaced
- Consistent inter-packet timing
- Smooth flow

**LOW Priority:**
- Packets irregularly spaced
- Bursty transmission
- Gaps in flow

### What to Show in Thesis

**Figure X.2: TCP Flow Graph Comparison**

```
HIGH Priority Flow:
Time →
│ │ │ │ │ │ │ │ │ │ │ │ │ │ │ │
└─┴─┴─┴─┴─┴─┴─┴─┴─┴─┴─┴─┴─┴─┴─┴─
  Even spacing (smooth)

LOW Priority Flow:
Time →
│   │ │     │   │ │ │       │ │
└───┴─┴─────┴───┴─┴─┴───────┴─┴─
  Irregular spacing (bursty)
```

**Caption:**
"Figure X.2: TCP Flow Graph showing packet timing. HIGH priority exhibits consistent packet spacing while LOW priority shows irregular, bursty transmission due to token bucket rate limiting."

---

## Analysis 3: Protocol Hierarchy

### Purpose
Validate traffic classification and protocol distribution

### Steps

1. **Open capture file**
2. **Statistics → Protocol Hierarchy**
3. **Expand tree** to show:
   - IPv4 / IPv6
   - TCP / UDP
   - Port numbers
4. **Screenshot**

### What to Show in Thesis

**Figure X.3: Protocol Hierarchy Statistics**

```
Protocol Hierarchy:
├─ IPv4 (95.2%)
│  ├─ TCP (87.3%)
│  │  └─ Port 5201 (iPerf3) - 85.1%
│  └─ UDP (7.9%)
│     └─ Port 53 (DNS) - 7.5%
└─ IPv6 (4.8%)
   └─ TCP (4.2%)
      └─ Port 5201 (iPerf3) - 4.0%
```

**Caption:**
"Figure X.3: Protocol hierarchy showing traffic distribution. Majority of traffic is TCP on port 5201 (iPerf3), confirming test validity. IPv6 support is demonstrated with 4.8% of packets using IPv6."

---

## Analysis 4: Packet List with Filters

### Purpose
Show specific packets and their properties

### Useful Display Filters

```
# Show only iPerf3 traffic:
tcp.port == 5201

# Show only data packets (no ACKs):
tcp.len > 0

# Show packets from specific device:
ip.src == 192.168.43.2

# Show retransmissions (indicates packet loss):
tcp.analysis.retransmission

# Show packets with specific flags:
tcp.flags.syn == 1  # Connection establishment
tcp.flags.fin == 1  # Connection termination

# Combine filters:
tcp.port == 5201 && tcp.len > 0 && ip.src == 192.168.43.2
```

### What to Show in Thesis

**Figure X.4: Packet List with Classification**

Screenshot showing:
- Packet number
- Time
- Source IP (192.168.43.2)
- Destination IP (server)
- Protocol (TCP)
- Port (5201)
- Length
- Info

**Highlight:**
- Source IP identifies device
- Port 5201 identifies iPerf3 traffic
- Consistent packet sizes

**Caption:**
"Figure X.4: Wireshark packet list showing classified iPerf3 traffic. Source IP 192.168.43.2 and destination port 5201 enable accurate traffic identification for QoS enforcement."

---

## Analysis 5: TCP Stream Analysis

### Purpose
Analyze individual TCP connection performance

### Steps

1. **Right-click any iPerf3 packet**
2. **Follow → TCP Stream**
3. **Analyze → Expert Information**
4. **Statistics → TCP Stream Graphs → Time-Sequence (Stevens)**

### What to Look For

**HIGH Priority:**
- Smooth sequence number progression
- Few retransmissions
- Consistent window size

**LOW Priority:**
- Stepped sequence progression (due to drops)
- More retransmissions
- Variable window size

### What to Show in Thesis

**Figure X.5: TCP Sequence Graph**

```
HIGH Priority:
Sequence Number
    ↑
    │     ╱╱╱╱╱╱╱╱╱╱╱╱╱╱╱╱╱
    │   ╱╱
    │ ╱╱
    │╱
    └────────────────────────→ Time
    Smooth progression

LOW Priority:
Sequence Number
    ↑
    │     ╱╱╱╱
    │   ╱╱    ╱╱╱╱
    │ ╱╱    ╱╱
    │╱    ╱╱
    └────────────────────────→ Time
    Stepped progression (retransmissions)
```

**Caption:**
"Figure X.5: TCP sequence graph comparison. HIGH priority shows smooth sequence number progression indicating no packet loss, while LOW priority exhibits stepped progression due to selective packet dropping."

---

## Analysis 6: Round Trip Time (RTT)

### Purpose
Measure latency differences

### Steps

1. **Statistics → TCP Stream Graphs → Round Trip Time**
2. **Select iPerf3 stream**
3. **Screenshot**

### What to Show in Thesis

**Figure X.6: Round Trip Time Comparison**

```
HIGH Priority RTT:
RTT (ms)
 100 ┤
  80 ┤
  60 ┤
  40 ┤████████████████████████  ← Stable ~40ms
  20 ┤
   0 └──────────────────────────→ Time

LOW Priority RTT:
RTT (ms)
 200 ┤    █
 160 ┤  █ █ █
 120 ┤ █ █ █ █
  80 ┤█ █ █ █ █  ← Variable 80-200ms
  40 ┤█ █ █ █ █
   0 └──────────────────────────→ Time
```

**Caption:**
"Figure X.6: Round Trip Time analysis. HIGH priority maintains stable RTT around 40ms, while LOW priority experiences variable RTT (80-200ms) due to queuing delays from rate limiting."

---

## Analysis 7: Packet Size Distribution

### Purpose
Show packet size patterns

### Steps

1. **Statistics → Packet Lengths**
2. **View distribution**
3. **Screenshot**

### What to Show in Thesis

**Figure X.7: Packet Length Distribution**

```
Packet Length Distribution:
Count
  ↑
  │         █
  │         █
  │         █
  │         █
  │         █
  │         █
  │ █       █       █
  └─┴───────┴───────┴──────→ Length (bytes)
    64    1500    1514
    
    64: TCP ACKs
    1500: Data packets (MTU)
    1514: Ethernet frames
```

**Caption:**
"Figure X.7: Packet length distribution showing typical TCP traffic pattern. Majority of packets are 1500 bytes (MTU-sized data packets) with smaller ACK packets."

---

## Analysis 8: Conversations

### Purpose
Show all active connections and their bandwidth usage

### Steps

1. **Statistics → Conversations**
2. **Select TCP tab**
3. **Sort by Bytes**
4. **Screenshot**

### What to Show in Thesis

**Figure X.8: TCP Conversations**

```
Address A          Port A  Address B          Port B  Packets  Bytes
192.168.43.2       54321   203.0.113.10       5201    45,231   67.8 MB
192.168.43.3       54322   203.0.113.10       5201    15,087   22.6 MB
```

**Caption:**
"Figure X.8: TCP conversations showing bandwidth distribution. Device 192.168.43.2 (HIGH priority) transferred 67.8 MB while device 192.168.43.3 (LOW priority) transferred 22.6 MB in the same time period, demonstrating 3:1 bandwidth ratio."

---

## Analysis 9: Expert Information

### Purpose
Identify network issues (retransmissions, errors)

### Steps

1. **Analyze → Expert Information**
2. **Review warnings and errors**
3. **Screenshot**

### What to Look For

**HIGH Priority:**
- Few or no warnings
- Minimal retransmissions
- Clean connection

**LOW Priority:**
- Many "Previous segment not captured" warnings
- Multiple retransmissions
- Connection issues

### What to Show in Thesis

**Figure X.9: Expert Information Comparison**

```
HIGH Priority:
Severity    Count   Summary
Info        1,234   TCP connection established
Warning     5       TCP retransmission
Error       0       No errors

LOW Priority:
Severity    Count   Summary
Info        1,234   TCP connection established
Warning     456     TCP retransmission
Warning     234     Previous segment not captured
Error       12      TCP connection reset
```

**Caption:**
"Figure X.9: Wireshark Expert Information showing network health. HIGH priority connection has minimal warnings (5 retransmissions) while LOW priority exhibits significant issues (456 retransmissions, 234 lost segments) due to intentional packet dropping for rate limiting."

---

## Analysis 10: Compare Two Captures Side-by-Side

### Purpose
Direct visual comparison

### Steps

1. **Open Wireshark twice** (two windows)
2. **Load HIGH priority capture in window 1**
3. **Load LOW priority capture in window 2**
4. **Apply same filter to both:** `tcp.port == 5201`
5. **Statistics → I/O Graph in both**
6. **Screenshot both windows**

### What to Show in Thesis

**Figure X.10: Side-by-Side Comparison**

Place two I/O graphs side-by-side showing clear throughput difference.

**Caption:**
"Figure X.10: Side-by-side Wireshark I/O Graph comparison. Left: HIGH priority device achieving 8 Mbps. Right: LOW priority device limited to 2 Mbps. Both captures taken simultaneously, demonstrating effective QoS differentiation."

---

## Thesis Integration

### Where to Add Wireshark Analysis

**Chapter 7: Validation Methodology**
- Section 7.3: Wireshark Analysis Procedure
- Describe capture setup
- Explain analysis techniques

**Chapter 8: Results**
- Section 8.4: Packet-Level Analysis
- Add Figures X.1 through X.10
- Discuss findings

**Appendix D: Wireshark Screenshots**
- Full-resolution screenshots
- Additional captures
- Raw data

### Recommended Figures for Thesis

**Must Have (Priority 1):**
1. ✅ I/O Graph comparison (Figure X.1)
2. ✅ Flow Graph comparison (Figure X.2)
3. ✅ Conversations table (Figure X.8)

**Should Have (Priority 2):**
4. ✅ Protocol Hierarchy (Figure X.3)
5. ✅ Packet List (Figure X.4)
6. ✅ TCP Sequence Graph (Figure X.5)

**Nice to Have (Priority 3):**
7. RTT comparison (Figure X.6)
8. Packet Length Distribution (Figure X.7)
9. Expert Information (Figure X.9)
10. Side-by-side comparison (Figure X.10)

---

## Screenshot Best Practices

### Quality
- **Resolution:** 1920x1080 or higher
- **Format:** PNG (lossless)
- **DPI:** 300 for print, 150 for digital

### Composition
- **Zoom:** Make text readable
- **Highlight:** Use Wireshark's marking feature
- **Annotate:** Add arrows/labels in thesis

### Clarity
- **Clean:** Close unnecessary windows
- **Focus:** Show only relevant information
- **Contrast:** Use light theme for printing

### How to Take Screenshots

**Windows:**
```
Win + Shift + S  (Snipping Tool)
Or: Wireshark → File → Export → Screenshot
```

**Mac:**
```
Cmd + Shift + 4  (Select area)
Or: Wireshark → File → Export → Screenshot
```

**Linux:**
```
gnome-screenshot -a  (Select area)
Or: Wireshark → File → Export → Screenshot
```

---

## Common Wireshark Filters Cheat Sheet

```
# Basic Filters
tcp                          # Show only TCP
udp                          # Show only UDP
ip.addr == 192.168.43.2      # Show traffic to/from IP
tcp.port == 5201             # Show traffic on port 5201

# Advanced Filters
tcp.len > 0                  # Data packets only (no ACKs)
tcp.analysis.retransmission  # Show retransmissions
tcp.analysis.lost_segment    # Show lost segments
tcp.flags.syn == 1           # Show SYN packets
tcp.flags.fin == 1           # Show FIN packets

# Combination Filters
tcp.port == 5201 && tcp.len > 0                    # iPerf3 data packets
ip.src == 192.168.43.2 && tcp.port == 5201         # Device 1 iPerf3 traffic
tcp.analysis.retransmission && ip.src == 192.168.43.3  # Device 2 retransmissions

# Time-based Filters
frame.time >= "2024-01-01 10:00:00"                # After specific time
frame.time_relative > 10 && frame.time_relative < 20  # Between 10-20 seconds

# Statistical Filters
tcp.analysis.ack_rtt > 0.1   # RTT > 100ms
tcp.window_size < 1000       # Small window size
```

---

## Troubleshooting

### No Packets Captured

**Problem:** Wireshark shows 0 packets

**Solutions:**
1. Check correct interface selected
2. Run Wireshark as administrator (Windows)
3. Check interface is connected to hotspot
4. Disable promiscuous mode if issues
5. Try different capture filter

### Too Many Packets

**Problem:** Capture file too large (>100 MB)

**Solutions:**
1. Use capture filter: `host YOUR_SERVER_IP`
2. Reduce test duration (30 seconds instead of 60)
3. Stop other applications using network
4. Use ring buffer: Capture Options → Output → Ring Buffer

### Can't See iPerf3 Traffic

**Problem:** Filter `tcp.port == 5201` shows nothing

**Solutions:**
1. Check iPerf3 is actually running
2. Verify server IP is correct
3. Try filter: `tcp` (show all TCP)
4. Check if using different port
5. Verify WiFi interface is correct

### Wireshark Crashes

**Problem:** Wireshark freezes or crashes

**Solutions:**
1. Update to latest version
2. Reduce capture file size
3. Close other applications
4. Increase RAM allocation
5. Use tshark (command-line) instead

---

## Summary Checklist

### Before Capture
- [ ] Wireshark installed and updated
- [ ] Correct WiFi interface identified
- [ ] QoS app running with priorities set
- [ ] iPerf3 test ready to run

### During Capture
- [ ] Start Wireshark capture
- [ ] Start iPerf3 test immediately
- [ ] Monitor packet count (should increase)
- [ ] Wait for test to complete
- [ ] Stop capture

### After Capture
- [ ] Save capture file (.pcapng)
- [ ] Verify file size (should be 10-100 MB)
- [ ] Apply filters to verify data
- [ ] Generate I/O Graph
- [ ] Take screenshots

### For Thesis
- [ ] I/O Graph screenshot (HIGH)
- [ ] I/O Graph screenshot (LOW)
- [ ] Flow Graph screenshot
- [ ] Protocol Hierarchy screenshot
- [ ] Conversations screenshot
- [ ] Packet List screenshot
- [ ] Add captions to all figures
- [ ] Reference figures in text

---

## Example Thesis Text

**Section 8.4: Packet-Level Analysis**

"To validate the QoS enforcement at the packet level, we utilized Wireshark network protocol analyzer to capture and analyze traffic from both HIGH and LOW priority devices.

Figure X.1 shows the I/O Graph comparison between the two priority classes. The HIGH priority device maintains a consistent throughput of approximately 8 Mbps throughout the 60-second test period, while the LOW priority device is limited to approximately 2 Mbps. This 4:1 ratio aligns with the configured WFQ weights (HIGH=4, LOW=1), demonstrating effective bandwidth allocation.

The Flow Graph analysis (Figure X.2) reveals distinct packet timing patterns. HIGH priority packets exhibit regular, evenly-spaced transmission, indicating smooth token bucket refill and minimal queuing delay. In contrast, LOW priority packets show irregular, bursty transmission patterns characteristic of rate-limited traffic, where packets are held until sufficient tokens accumulate.

TCP conversation statistics (Figure X.8) quantify the bandwidth distribution: the HIGH priority device transferred 67.8 MB while the LOW priority device transferred 22.6 MB during the same 60-second period, confirming the 3:1 throughput ratio observed in iPerf3 measurements.

Expert Information analysis (Figure X.9) shows that the LOW priority connection experienced 456 TCP retransmissions compared to only 5 for the HIGH priority connection. This 91× increase in retransmissions is expected behavior, as the token bucket intentionally drops packets exceeding the allocated rate, forcing TCP to retransmit. Despite the high retransmission rate, the LOW priority connection remained functional, demonstrating that the QoS system degrades performance gracefully rather than causing complete connection failure.

These packet-level observations corroborate the iPerf3 throughput measurements and provide visual evidence of the QoS system's effectiveness in differentiating traffic based on priority classes."

---

**Good luck with your Wireshark analysis!** 📊

