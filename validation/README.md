# Experimental Validation - Complete Guide

**Everything you need to validate your QoS Scheduler thesis**

---

## 📁 Files in This Directory

| File | Purpose | When to Use |
|------|---------|-------------|
| `QUICKSTART.md` | **Start here!** Step-by-step guide | Before running experiments |
| `EXPERIMENTAL_VALIDATION.md` | Detailed methodology | Reference during testing |
| `WIRESHARK_ANALYSIS.md` | Wireshark-specific guide | During packet analysis |
| `analyze_results.py` | Python script to generate graphs | After collecting data |
| `README.md` | This file | Overview |

---

## 🚀 Quick Start (TL;DR)

**Goal:** Get thesis-ready results in 1-2 days

**Steps:**
1. Read `QUICKSTART.md` (15 minutes)
2. Set up environment (30 minutes)
3. Run tests (2 hours)
4. Analyze results (2 hours)
5. Add to thesis (2 hours)

**Total time:** 6-7 hours over 1-2 days

---

## 📊 What You'll Get

### Graphs (5 publication-quality figures)

1. **Throughput Comparison** - Bar chart showing 3.2× improvement
2. **Time Series** - Real-time throughput over 60 seconds
3. **Latency & Jitter** - 47% and 62% reductions
4. **WFQ Allocation** - Pie chart showing bandwidth distribution
5. **Packet Loss** - Bar chart showing selective dropping

### Data Files

- iPerf3 JSON outputs (9 files)
- Wireshark captures (2-5 files)
- App screenshots (5 images)
- Summary report (text file)

### Thesis Content

- Chapter 7: Validation methodology
- Chapter 8: Results with graphs
- Appendix D: Raw data and screenshots

---

## 🎯 Expected Results

| Metric | Target | Typical Range |
|--------|--------|---------------|
| Throughput Improvement | 3.2× | 2.5× - 4.0× |
| Latency Reduction | 47% | 40% - 55% |
| Jitter Reduction | 62% | 55% - 70% |
| Packet Loss (HIGH) | <1% | 0.1% - 1.5% |
| Packet Loss (LOW) | >50% | 50% - 80% |

**Note:** Your actual results may vary based on network conditions. Any measurable improvement is good for a bachelor's thesis!

---

## 📋 Prerequisites

### Hardware

- ✅ Android phone (Android 10+)
- ✅ 2-3 laptops/PCs with WiFi
- ✅ Mobile data or WiFi connection
- ✅ iPerf3 server (cloud or remote machine)

### Software

- ✅ QoS Scheduler app (installed on phone)
- ✅ iPerf3 (on all test devices)
- ✅ Wireshark (on at least one device)
- ✅ Python 3.7+ with pandas, matplotlib

### Knowledge

- ✅ Basic command line usage
- ✅ Basic networking concepts
- ✅ How to take screenshots

---

## 🗺️ Workflow

```
┌─────────────────────────────────────────────────────────┐
│ 1. SETUP (30 min)                                       │
│    - Install iPerf3                                     │
│    - Set up server                                      │
│    - Configure hotspot                                  │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│ 2. BASELINE TESTS (30 min)                              │
│    - Turn OFF QoS                                       │
│    - Run simultaneous iPerf3 tests                      │
│    - Save JSON outputs                                  │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│ 3. QoS TESTS (1 hour)                                   │
│    - Turn ON QoS                                        │
│    - Set priorities (HIGH/LOW)                          │
│    - Run TCP tests                                      │
│    - Run UDP tests                                      │
│    - Run 3-device tests (optional)                      │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│ 4. WIRESHARK CAPTURE (30 min)                           │
│    - Capture HIGH priority traffic                      │
│    - Capture LOW priority traffic                       │
│    - Save .pcapng files                                 │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│ 5. SCREENSHOTS (15 min)                                 │
│    - Dashboard with devices                             │
│    - Device details                                     │
│    - Settings screen                                    │
│    - Active flows                                       │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│ 6. ANALYSIS (2 hours)                                   │
│    - Run analyze_results.py                             │
│    - Analyze Wireshark captures                         │
│    - Generate all graphs                                │
│    - Review summary report                              │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│ 7. ADD TO THESIS (2 hours)                              │
│    - Update Chapter 7 (Methodology)                     │
│    - Update Chapter 8 (Results)                         │
│    - Add graphs and screenshots                         │
│    - Write analysis text                                │
└─────────────────────────────────────────────────────────┘
```

---

## 📂 Directory Structure

```
validation/
├── README.md                    # This file
├── QUICKSTART.md                # Quick start guide
├── EXPERIMENTAL_VALIDATION.md   # Detailed methodology
├── WIRESHARK_ANALYSIS.md        # Wireshark guide
├── analyze_results.py           # Analysis script
│
├── results/                     # iPerf3 JSON outputs
│   ├── baseline_device1.json
│   ├── baseline_device2.json
│   ├── qos_high_priority.json
│   ├── qos_low_priority.json
│   ├── qos_high_udp.json
│   ├── qos_low_udp.json
│   ├── qos_3dev_high.json
│   ├── qos_3dev_medium.json
│   └── qos_3dev_low.json
│
├── captures/                    # Wireshark .pcapng files
│   ├── qos_high_priority.pcapng
│   └── qos_low_priority.pcapng
│
├── graphs/                      # Generated graphs (auto-created)
│   ├── graph1_throughput_comparison.png
│   ├── graph2_throughput_timeseries.png
│   ├── graph3_latency_jitter.png
│   ├── graph4_wfq_allocation.png
│   ├── graph5_packet_loss.png
│   └── summary_report.txt
│
└── screenshots/                 # App screenshots
    ├── dashboard.png
    ├── device_high.png
    ├── device_low.png
    ├── settings.png
    └── flows.png
```

---

## 🔧 Installation

### 1. Install iPerf3

**Windows:**
```powershell
# Download from: https://iperf.fr/iperf-download.php
# Or use Chocolatey:
choco install iperf3
```

**Linux:**
```bash
sudo apt-get install iperf3
```

**Mac:**
```bash
brew install iperf3
```

### 2. Install Wireshark

**Download from:** https://www.wireshark.org/download.html

### 3. Install Python Dependencies

```bash
pip install pandas matplotlib numpy
```

### 4. Verify Installation

```bash
iperf3 --version
wireshark --version
python --version
```

---

## 🧪 Running Experiments

### Option 1: Follow Quick Start (Recommended)

```bash
# Read the guide:
cat QUICKSTART.md

# Follow step-by-step instructions
```

### Option 2: Follow Detailed Guide

```bash
# Read the detailed methodology:
cat EXPERIMENTAL_VALIDATION.md

# Follow comprehensive instructions
```

### Option 3: Automated Script (Advanced)

```bash
# Coming soon: run_all_tests.sh
# Will automate the entire testing process
```

---

## 📈 Analyzing Results

### Step 1: Run Analysis Script

```bash
cd validation
python analyze_results.py
```

**Output:**
- 5 graphs in `graphs/` directory
- Summary report in `graphs/summary_report.txt`

### Step 2: Analyze Wireshark Captures

```bash
# Read the Wireshark guide:
cat WIRESHARK_ANALYSIS.md

# Open captures in Wireshark
# Follow analysis instructions
# Take screenshots
```

### Step 3: Review Results

```bash
# Check summary report:
cat graphs/summary_report.txt

# View graphs:
ls graphs/*.png
```

---

## 📝 Adding to Thesis

### Chapter 7: Validation Methodology

**Add sections:**

1. **7.1 Experimental Setup**
   - Hardware configuration
   - Network topology
   - Software tools

2. **7.2 iPerf3 Testing Procedure**
   - Baseline tests
   - QoS tests
   - UDP tests

3. **7.3 Wireshark Analysis Procedure**
   - Capture methodology
   - Analysis techniques
   - Metrics extracted

### Chapter 8: Results

**Add sections:**

1. **8.1 Baseline Performance**
   - Graph 1: Throughput comparison
   - Fair sharing results

2. **8.2 QoS Performance**
   - Graph 2: Time series
   - Throughput improvement

3. **8.3 Latency and Jitter**
   - Graph 3: Latency/jitter comparison
   - Reductions achieved

4. **8.4 WFQ Validation**
   - Graph 4: Bandwidth allocation
   - 3-device scenario

5. **8.5 Packet Loss Analysis**
   - Graph 5: Packet loss by priority
   - Selective dropping

6. **8.6 Packet-Level Analysis**
   - Wireshark screenshots
   - Flow analysis
   - Expert information

### Appendix D: Experimental Data

**Include:**
- Raw iPerf3 JSON files
- Wireshark screenshots
- App screenshots
- Summary report

---

## ❓ Troubleshooting

### Common Issues

**1. iPerf3 connection failed**
```bash
# Check server is running:
ssh user@server
ps aux | grep iperf3

# Restart server:
iperf3 -s
```

**2. Devices not showing in app**
- Make sure QoS app is running
- Send traffic from device (ping 8.8.8.8)
- Wait 5-10 seconds

**3. Wireshark not capturing**
- Run as administrator (Windows)
- Select correct WiFi interface
- Check interface is connected

**4. Python script errors**
```bash
# Install dependencies:
pip install pandas matplotlib numpy

# Check Python version:
python --version  # Should be 3.7+
```

### Getting Help

**Check documentation:**
1. Read QUICKSTART.md
2. Read EXPERIMENTAL_VALIDATION.md
3. Read WIRESHARK_ANALYSIS.md

**Common questions:**
- Q: What if I don't have a server?
  - A: Use a friend's computer or ngrok

- Q: What if I only have 2 devices?
  - A: Skip 3-device test, focus on HIGH vs LOW

- Q: What if results don't match expected?
  - A: That's okay! Document actual results

---

## ✅ Validation Checklist

### Before Defense

- [ ] All iPerf3 tests completed
- [ ] All graphs generated
- [ ] Wireshark screenshots taken
- [ ] App screenshots taken
- [ ] Results added to Chapter 8
- [ ] Methodology added to Chapter 7
- [ ] Summary report reviewed
- [ ] Practice explaining results

### Files to Submit

- [ ] Thesis PDF with graphs
- [ ] results/ directory (JSON files)
- [ ] graphs/ directory (PNG files)
- [ ] captures/ directory (optional)
- [ ] screenshots/ directory

---

## 🎓 Expected Grade Impact

**Without experimental validation:** 15-16/20 (Good)

**With experimental validation:** 17-18/20 (Very Good to Excellent)

**Improvement:** +1 to +2 points

**Why it matters:**
- Proves your system works
- Shows scientific rigor
- Provides visual evidence
- Demonstrates practical validation

---

## 📚 References

### Tools Used

- **iPerf3:** https://iperf.fr/
- **Wireshark:** https://www.wireshark.org/
- **Python:** https://www.python.org/
- **Matplotlib:** https://matplotlib.org/

### Relevant Papers

- RFC 2697: A Single Rate Three Color Marker
- RFC 2698: A Two Rate Three Color Marker
- iPerf3 Documentation: https://iperf.fr/iperf-doc.php
- Wireshark User Guide: https://www.wireshark.org/docs/

---

## 🚀 Next Steps

1. **Read QUICKSTART.md** (15 minutes)
2. **Set up environment** (30 minutes)
3. **Run baseline tests** (30 minutes)
4. **Run QoS tests** (1 hour)
5. **Analyze results** (2 hours)
6. **Add to thesis** (2 hours)

**Total:** 6-7 hours over 1-2 days

---

## 📞 Support

**If you get stuck:**

1. Check troubleshooting sections in guides
2. Review error messages carefully
3. Try alternative approaches
4. Document what you tried

**Remember:** Any measurable improvement is good for a bachelor's thesis. Don't stress about perfect results!

---

## 🎉 Success Criteria

**Minimum (Pass):**
- ✅ Baseline tests completed
- ✅ QoS tests completed
- ✅ At least 2× throughput improvement
- ✅ Graphs generated

**Good (16-17/20):**
- ✅ All of the above
- ✅ UDP tests completed
- ✅ Wireshark analysis
- ✅ App screenshots

**Excellent (18-19/20):**
- ✅ All of the above
- ✅ 3-device tests
- ✅ Comprehensive Wireshark analysis
- ✅ Detailed thesis integration

---

**Good luck with your experiments!** 🚀

**You've got this!** 💪

