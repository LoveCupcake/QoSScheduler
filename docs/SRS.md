# Software Requirements Specification
## Dynamic QoS Scheduler for Mobile Hotspots

**Version:** 1.0  
**Author:** Phạm Hiếu Minh — 23BI14295  
**Institution:** University of Science & Technology of Hanoi  
**Academic Year:** 2023–2026  

---

## Table of Contents

1. [Introduction](#1-introduction)
2. [Overall Description](#2-overall-description)
3. [Functional Requirements](#3-functional-requirements)
4. [Non-Functional Requirements](#4-non-functional-requirements)
5. [System Constraints](#5-system-constraints)
6. [External Interface Requirements](#6-external-interface-requirements)
7. [Use Cases](#7-use-cases)
8. [Data Requirements](#8-data-requirements)
9. [Assumptions and Dependencies](#9-assumptions-and-dependencies)
10. [Acceptance Criteria](#10-acceptance-criteria)

---

## 1. Introduction

### 1.1 Purpose

This document specifies the software requirements for the **Dynamic QoS Scheduler for Mobile Hotspots** — an Android application that enforces priority-driven bandwidth allocation across devices connected to a personal mobile hotspot. It serves as the authoritative reference for design, implementation, and validation activities.

### 1.2 Scope

The system intercepts all hotspot traffic through an Android VpnService tunnel, classifies flows using port-based DPI-lite, and enforces per-device bandwidth policies via a software token bucket algorithm. The application targets Android devices acting as hotspot hosts and requires no rooting, no dedicated hardware, and no modifications to connected client devices.

### 1.3 Definitions and Acronyms

| Term | Definition |
|------|-----------|
| QoS | Quality of Service — measurable network performance guarantees |
| VpnService | Android API that creates a virtual TUN network interface |
| DPI-lite | Lightweight Deep Packet Inspection using port/protocol heuristics |
| Token Bucket | Rate-limiting algorithm that controls burst and sustained throughput |
| TUN | Virtual network interface operating at Layer 3 (IP) |
| Flow | A unidirectional stream of packets sharing the same 5-tuple |
| Priority Class | One of three tiers: HIGH, MEDIUM, LOW |
| iPerf3 | Network throughput benchmarking tool |
| Jitter | Variation in packet inter-arrival delay |

### 1.4 References

- Android VpnService API Documentation (developer.android.com)
- RFC 2697 — A Single Rate Three Color Marker (token bucket basis)
- iPerf3 documentation (software.es.net/iperf)
- Proposal: *Dynamic QoS Scheduler for Mobile Hotspots*, USTH 2024–2025

---

## 2. Overall Description

### 2.1 Product Perspective

Personal mobile hotspots provide no traffic arbitration. All connected devices share bandwidth equally regardless of application sensitivity, causing latency-sensitive flows (video calls, gaming) to degrade under competition from bulk transfers (cloud sync, OS updates). This system inserts a software-defined QoS layer between the hotspot interface and the internet uplink, operating entirely in userspace via the Android VpnService API.

```
[Connected Devices]
       |
       v
[Android Hotspot Interface]
       |
       v
[QoS VPN Tunnel — TUN Interface]
       |
   +---+------------------------+
   |  DPI-Lite Classifier       |
   |  Token Bucket Enforcer     |
   |  Priority Scheduler        |
   +---+------------------------+
       |
       v
[Internet Uplink]
```

### 2.2 Product Functions (Summary)

- Intercept all hotspot traffic via a local VpnService tunnel
- Discover and track devices connected to the hotspot
- Classify traffic flows by application type using port-based DPI-lite
- Assign per-device priority classes (HIGH / MEDIUM / LOW)
- Enforce bandwidth limits using per-device token bucket queues
- Display real-time traffic statistics per device
- Allow the user to manually override priority assignments

### 2.3 User Classes

| User | Description |
|------|-------------|
| Hotspot Owner | The Android device owner who manages QoS policies |
| Connected Client | Any device tethered to the hotspot (passive, unaware of QoS) |

### 2.4 Operating Environment

- Host device: Android 8+ (API level 26+)
- Architecture: ARM64 / x86_64
- Language: Kotlin (JVM), Java NIO for packet parsing
- UI framework: Jetpack Compose
- No root access required
- No modifications to connected client devices

---

## 3. Functional Requirements

### 3.1 VPN Tunnel Management

**FR-01** The system shall establish a local VpnService tunnel on the host device when the user activates QoS scheduling.

**FR-02** The system shall route all hotspot traffic through the TUN interface created by the VpnService.

**FR-03** The system shall forward processed packets to the internet uplink after classification and scheduling.

**FR-04** The system shall gracefully tear down the VPN tunnel when the user deactivates QoS scheduling or the hotspot is disabled.

**FR-05** The system shall automatically restart the tunnel if it drops unexpectedly while the hotspot remains active.

### 3.2 Device Discovery and Registry

**FR-06** The system shall detect all devices connected to the hotspot by inspecting source IP addresses from intercepted packets.

**FR-07** The system shall maintain a registry of connected devices including: IP address, MAC address (where resolvable), hostname (where resolvable), assigned priority class, and per-session traffic statistics.

**FR-08** The system shall remove a device from the active registry after a configurable inactivity timeout (default: 60 seconds).

**FR-09** The system shall persist user-assigned priority overrides across sessions using device MAC address as the key.

### 3.3 Traffic Classification (DPI-Lite)

**FR-10** The system shall parse raw IPv4 and IPv6 packet headers from the TUN interface using Java NIO ByteBuffer.

**FR-11** The system shall extract the 5-tuple from each packet: source IP, destination IP, source port, destination port, protocol (TCP/UDP).

**FR-12** The system shall classify each flow into one of the following application categories based on destination port and protocol:

| Category | Default Priority | Ports / Protocol |
|----------|-----------------|-----------------|
| Video Conferencing | HIGH | UDP 3478, 19302-19309, TCP 443 (STUN/TURN/SRTP) |
| Online Gaming | HIGH | UDP 27015-27030, 3074, 25565 |
| VoIP | HIGH | UDP 5060, 5061, RTP 16384-32767 |
| Web Browsing | MEDIUM | TCP 80, 443 |
| Streaming | MEDIUM | TCP 1935, 443 (heuristic: sustained high-throughput) |
| File Transfer / Cloud Sync | LOW | TCP 20, 21, 22, 445, 2049 |
| OS Updates | LOW | TCP 80, 443 (heuristic: large payload, background) |
| Unknown / Default | MEDIUM | All other flows |

**FR-13** The system shall allow the user to manually override the traffic class of any active flow or device.

**FR-14** The system shall re-classify flows periodically (every 5 seconds) to adapt to changing traffic patterns.

### 3.4 Bandwidth Scheduling (Token Bucket)

**FR-15** The system shall instantiate one token bucket per connected device.

**FR-16** Each token bucket shall be configurable with:
- `rate` — sustained token refill rate (bytes/second)
- `burst` — maximum burst capacity (bytes)

**FR-17** The system shall map priority classes to default token bucket parameters:

| Priority | Default Rate | Default Burst |
|----------|-------------|--------------|
| HIGH | 80% of available uplink | 2x rate |
| MEDIUM | 50% of available uplink | 1x rate |
| LOW | 20% of available uplink | 0.5x rate |

**FR-18** The system shall drop or delay packets that exceed the token bucket allowance for their device.

**FR-19** The system shall dynamically adjust token bucket rates when devices join or leave the hotspot to maintain fair allocation within each priority tier.

**FR-20** The system shall allow the user to set a manual bandwidth cap (in Mbps) per device, overriding the priority-derived default.

### 3.5 Real-Time Monitoring

**FR-21** The system shall display a live dashboard showing all connected devices with: current throughput (Mbps), total bytes transferred, assigned priority class, and active traffic category.

**FR-22** The system shall update dashboard statistics at a minimum refresh rate of 1 second.

**FR-23** The system shall display a per-device detail view showing per-flow breakdown of active connections.

**FR-24** The system shall expose aggregate statistics: total uplink throughput, number of active devices, number of active flows.

### 3.6 User Controls

**FR-25** The system shall provide a toggle to start and stop the QoS scheduler.

**FR-26** The system shall allow the user to assign a priority class (HIGH / MEDIUM / LOW) to any connected device.

**FR-27** The system shall allow the user to set a custom bandwidth cap per device.

**FR-28** The system shall allow the user to reset all priority assignments to defaults.

**FR-29** The system shall display a notification in the Android status bar while the QoS scheduler is active, as required by Android foreground service policy.

---

## 4. Non-Functional Requirements

### 4.1 Performance

**NFR-01** Packet processing latency introduced by the QoS layer shall not exceed **5 ms** per packet under normal load (5 or fewer connected devices, 100 Mbps aggregate throughput).

**NFR-02** The scheduler shall sustain processing of at least **10,000 packets/second** without packet loss due to processing backlog.

**NFR-03** CPU utilization of the QoS service shall remain below **15%** on a mid-range Android device (Snapdragon 700-series equivalent) under normal load.

**NFR-04** Memory footprint of the QoS service shall remain below **80 MB** RAM.

### 4.2 Reliability

**NFR-05** The VPN tunnel shall recover automatically within **3 seconds** of an unexpected disconnection.

**NFR-06** The system shall not cause packet loss beyond the configured token bucket drop policy (i.e., no unintended drops).

**NFR-07** Priority assignments persisted to storage shall survive application restarts and device reboots.

### 4.3 Usability

**NFR-08** A new user shall be able to activate QoS scheduling and assign device priorities within **3 interactions** from the main screen.

**NFR-09** The UI shall display device and flow information in a human-readable format (hostnames where available, IP addresses as fallback).

### 4.4 Security

**NFR-10** The system shall not log or persist packet payload content — only header metadata (IP, port, protocol, byte counts).

**NFR-11** The VPN tunnel shall be local-only (no traffic routed to external VPN servers).

**NFR-12** Persisted device priority data shall be stored in Android internal storage, inaccessible to other applications.

### 4.5 Compatibility

**NFR-13** The application shall support Android API level 29 (Android 10) and above.

**NFR-14** The application shall function correctly on both IPv4 and IPv6 hotspot configurations, including proper parsing of IPv6 extension headers (Hop-by-Hop, Routing, Fragment, Destination Options) and compressed address formatting.

**NFR-15** The application shall not require root access, ADB access, or kernel modifications.

---

## 5. System Constraints

**CON-01** The system operates entirely within Android userspace; kernel-level traffic shaping (tc/netem) is unavailable without root.

**CON-02** The VpnService API processes packets at Layer 3 (IP layer); Layer 2 MAC information is not directly available from the TUN interface and must be resolved via ARP cache inspection.

**CON-03** Android battery optimization may suspend background services; the QoS service must run as a foreground service with a persistent notification.

**CON-04** Actual uplink bandwidth is not directly measurable from within the VpnService; it must be estimated via throughput sampling or user-configured input.

**CON-05** The system cannot enforce QoS on encrypted payload content — classification relies solely on port/protocol heuristics.

---

## 6. External Interface Requirements

### 6.1 User Interface

- Built with Jetpack Compose
- Screens: Dashboard, Device Detail, Settings
- Persistent foreground notification with scheduler status

### 6.2 Android System Interfaces

| Interface | Usage |
|-----------|-------|
| `android.net.VpnService` | TUN interface creation and packet I/O |
| `java.nio.ByteBuffer` | Raw packet header parsing |
| `android.net.wifi.WifiManager` | Hotspot state detection |
| `SharedPreferences / DataStore` | Persisting priority assignments |
| `NotificationManager` | Foreground service notification |

### 6.3 Validation Tools (External)

| Tool | Role |
|------|------|
| iPerf3 | Deterministic throughput measurement for controlled experiments |
| Wireshark | Packet-level flow analysis and ground-truth classification verification |
| Python (pandas, matplotlib) | Experimental result analysis and graph generation |

---

## 7. Use Cases

### UC-01: Activate QoS Scheduler
- **Actor:** Hotspot Owner
- **Precondition:** Android hotspot is active
- **Flow:** User opens app → taps "Start QoS" → system requests VPN permission → tunnel established → dashboard shows connected devices
- **Postcondition:** All hotspot traffic is routed through the QoS tunnel

### UC-02: Assign Device Priority
- **Actor:** Hotspot Owner
- **Precondition:** QoS scheduler is active; at least one device is connected
- **Flow:** User selects a device from dashboard → taps priority selector → chooses HIGH / MEDIUM / LOW → token bucket parameters update immediately
- **Postcondition:** Device traffic is shaped according to new priority class

### UC-03: Set Manual Bandwidth Cap
- **Actor:** Hotspot Owner
- **Precondition:** QoS scheduler is active; device is in registry
- **Flow:** User opens device detail → taps "Set Bandwidth Cap" → enters value in Mbps → confirms → token bucket rate updated
- **Postcondition:** Device throughput is capped at the specified rate

### UC-04: Monitor Traffic in Real Time
- **Actor:** Hotspot Owner
- **Precondition:** QoS scheduler is active
- **Flow:** User views dashboard → sees live throughput per device → taps a device → sees per-flow breakdown
- **Postcondition:** No state change; informational only

### UC-05: Deactivate QoS Scheduler
- **Actor:** Hotspot Owner
- **Precondition:** QoS scheduler is active
- **Flow:** User taps "Stop QoS" → tunnel torn down → traffic flows unmanaged → notification dismissed
- **Postcondition:** Hotspot operates without QoS enforcement

### UC-06: Reset All Priorities
- **Actor:** Hotspot Owner
- **Precondition:** At least one device has a non-default priority
- **Flow:** User opens Settings → taps "Reset All Priorities" → confirms → all devices revert to MEDIUM
- **Postcondition:** All token buckets reset to MEDIUM-tier parameters

---

## 8. Data Requirements

### 8.1 ConnectedDevice

| Field | Type | Description |
|-------|------|-------------|
| ipAddress | String | IPv4 or IPv6 address |
| macAddress | String? | Resolved from ARP cache; nullable |
| hostname | String? | Reverse DNS or mDNS; nullable |
| priorityClass | Enum (HIGH/MEDIUM/LOW) | Current priority assignment |
| bytesIn | Long | Total bytes received this session |
| bytesOut | Long | Total bytes sent this session |
| currentThroughputBps | Long | Rolling 1-second average |
| lastSeenTimestamp | Long | Epoch ms of last packet |

### 8.2 PacketFlow (in-memory only)

| Field | Type | Description |
|-------|------|-------------|
| srcIp | String | Source IP address |
| dstIp | String | Destination IP address |
| srcPort | Int | Source port |
| dstPort | Int | Destination port |
| protocol | Enum (TCP/UDP/OTHER) | Transport protocol |
| trafficCategory | Enum | Classified application type |
| byteCount | Long | Bytes observed in this flow |

### 8.3 TokenBucketConfig (persisted per device)

| Field | Type | Description |
|-------|------|-------------|
| deviceMac | String | Key for persistence |
| priorityClass | Enum | Assigned priority |
| customRateBps | Long? | Manual cap; null = use priority default |
| burstBytes | Long | Burst capacity |

---

## 9. Assumptions and Dependencies

**A-01** The host Android device supports the VpnService API (all Android 4.0+ devices do; this project targets Android 10+).

**A-02** The hotspot is active and managed by the Android OS before the QoS scheduler is started.

**A-03** Connected client devices use standard TCP/UDP transport; exotic protocols are classified as MEDIUM by default.

**A-04** The user has granted VPN permission via the Android system dialog before the first tunnel establishment.

**A-05** Uplink bandwidth is either user-configured or estimated from observed throughput; the system does not have direct access to carrier-reported speeds.

**D-01** Depends on Android VpnService API (API 8+, used at API 26+).

**D-02** Depends on Jetpack Compose (UI rendering).

**D-03** Depends on Kotlin Coroutines (asynchronous packet I/O and scheduling loops).

**D-04** Depends on Android DataStore (priority persistence).

**D-05** Validation depends on iPerf3 and Wireshark being available on test devices/machines.

---

## 10. Acceptance Criteria

Acceptance criteria are written in **Given / When / Then** format and are grouped by functional area. Each criterion is traceable to one or more requirements from Section 3 and Section 4.

---

### 10.1 VPN Tunnel Management

**AC-01** — Tunnel Establishment *(FR-01, FR-02)*
- **Given** the Android hotspot is active and the user has granted VPN permission
- **When** the user taps "Start QoS" in the app
- **Then** a VpnService tunnel is established within 2 seconds, a foreground notification appears, and the dashboard transitions to the active state showing connected devices

**AC-02** — Traffic Routing Through Tunnel *(FR-02, FR-03)*
- **Given** the QoS tunnel is active
- **When** a connected client device sends any TCP or UDP packet
- **Then** the packet is intercepted by the TUN interface, processed by the classifier and scheduler, and forwarded to the internet uplink — verified by Wireshark showing all flows passing through the tunnel

**AC-03** — Graceful Tunnel Teardown *(FR-04)*
- **Given** the QoS scheduler is active
- **When** the user taps "Stop QoS"
- **Then** the VPN tunnel is torn down within 1 second, the foreground notification is dismissed, and connected devices continue to access the internet via the unmanaged hotspot

**AC-04** — Automatic Tunnel Recovery *(FR-05)*
- **Given** the QoS tunnel is active
- **When** the tunnel drops unexpectedly (simulated by killing the VpnService process)
- **Then** the system automatically re-establishes the tunnel within 3 seconds without user intervention

---

### 10.2 Device Discovery and Registry

**AC-05** — Device Detection *(FR-06, FR-07)*
- **Given** the QoS tunnel is active
- **When** a new device connects to the hotspot and sends at least one packet
- **Then** the device appears in the dashboard registry within 2 seconds, showing its IP address and a default priority class of MEDIUM

**AC-06** — Device Inactivity Timeout *(FR-08)*
- **Given** a device is in the registry and has sent no packets for 60 seconds
- **When** the inactivity timeout elapses
- **Then** the device is removed from the active registry and no longer appears on the dashboard

**AC-07** — Priority Persistence Across Sessions *(FR-09)*
- **Given** the user has assigned HIGH priority to a device identified by MAC address X
- **When** the app is closed and reopened, and device X reconnects
- **Then** device X is automatically assigned HIGH priority without any user action

---

### 10.3 Traffic Classification (DPI-Lite)

**AC-08** — IPv4 Header Parsing *(FR-10, FR-11)*
- **Given** the QoS tunnel is active
- **When** an IPv4 TCP packet is received on the TUN interface
- **Then** the classifier correctly extracts source IP, destination IP, source port, destination port, and protocol — verified against Wireshark ground truth with 100% accuracy on a 1,000-packet sample

**AC-09** — Known Port Classification *(FR-12)*
- **Given** a device sends UDP traffic to destination port 3478
- **When** the DPI-lite classifier processes the flow
- **Then** the flow is classified as Video Conferencing with priority HIGH

**AC-10** — Gaming Traffic Classification *(FR-12)*
- **Given** a device sends UDP traffic to destination port 27015
- **When** the DPI-lite classifier processes the flow
- **Then** the flow is classified as Online Gaming with priority HIGH

**AC-11** — Default Classification for Unknown Ports *(FR-12)*
- **Given** a device sends traffic to a port not in any classification rule
- **When** the DPI-lite classifier processes the flow
- **Then** the flow is classified as Unknown with priority MEDIUM

**AC-12** — Manual Classification Override *(FR-13)*
- **Given** a flow is currently classified as MEDIUM
- **When** the user manually overrides it to HIGH via the device detail screen
- **Then** the flow's token bucket is updated to HIGH-tier parameters within 500 ms and the UI reflects the change immediately

**AC-13** — Periodic Re-classification *(FR-14)*
- **Given** a flow's traffic pattern changes (e.g., throughput drops significantly)
- **When** 5 seconds elapse since the last classification
- **Then** the classifier re-evaluates the flow and updates its category if the pattern matches a different rule

---

### 10.4 Bandwidth Scheduling (Token Bucket)

**AC-14** — Per-Device Token Bucket Instantiation *(FR-15)*
- **Given** the QoS tunnel is active
- **When** a new device is added to the registry
- **Then** a dedicated token bucket is instantiated for that device with parameters matching its assigned priority class

**AC-15** — HIGH Priority Throughput Advantage *(FR-17, FR-18)*
- **Given** two devices are connected — Device A assigned HIGH, Device B assigned LOW — and total uplink is 20 Mbps
- **When** both devices simultaneously run iPerf3 to saturate the uplink
- **Then** Device A achieves at least 3x the throughput of Device B, measured over a 30-second iPerf3 run

**AC-16** — Token Bucket Rate Enforcement *(FR-18)*
- **Given** a device is assigned a manual bandwidth cap of 5 Mbps
- **When** the device runs iPerf3 attempting to exceed 5 Mbps
- **Then** the measured throughput does not exceed 5.5 Mbps (10% tolerance) over a 30-second test window

**AC-17** — Dynamic Rate Rebalancing *(FR-19)*
- **Given** three HIGH-priority devices are sharing the uplink
- **When** one HIGH-priority device disconnects
- **Then** the token bucket rates of the remaining two HIGH-priority devices are recalculated and updated within 2 seconds to reflect the new allocation

**AC-18** — Manual Bandwidth Cap Override *(FR-20)*
- **Given** a device has a priority-derived rate
- **When** the user sets a manual cap of 2 Mbps for that device
- **Then** the token bucket rate is immediately updated to 2 Mbps and the priority-derived rate is no longer applied until the cap is removed

---

### 10.5 Real-Time Monitoring

**AC-19** — Dashboard Live Throughput *(FR-21, FR-22)*
- **Given** the QoS scheduler is active and a device is transferring data
- **When** the user views the dashboard
- **Then** the device's throughput value updates at least once per second and reflects the actual transfer rate within ±10% of the iPerf3-measured value

**AC-20** — Per-Flow Detail View *(FR-23)*
- **Given** a device has two or more active flows
- **When** the user taps the device on the dashboard
- **Then** the detail screen shows each flow's destination IP, destination port, protocol, traffic category, and byte count

**AC-21** — Aggregate Statistics *(FR-24)*
- **Given** three devices are connected and active
- **When** the user views the dashboard header
- **Then** the aggregate view correctly shows total uplink throughput (sum of all device throughputs ±10%), device count = 3, and the correct number of active flows

---

### 10.6 User Controls

**AC-22** — Start/Stop Toggle *(FR-25)*
- **Given** the app is open and the hotspot is active
- **When** the user taps the QoS toggle
- **Then** the scheduler starts (or stops) and the UI toggle state, foreground notification, and dashboard state all update consistently within 2 seconds

**AC-23** — Priority Assignment *(FR-26)*
- **Given** a device is shown on the dashboard with MEDIUM priority
- **When** the user selects the device and changes its priority to HIGH
- **Then** the priority label updates immediately in the UI and the device's token bucket parameters are recalculated within 500 ms

**AC-24** — Reset All Priorities *(FR-28)*
- **Given** two or more devices have non-default priority assignments
- **When** the user taps "Reset All Priorities" and confirms
- **Then** all devices revert to MEDIUM priority, all token buckets are recalculated, and the UI reflects the reset within 1 second

**AC-25** — Foreground Notification *(FR-29)*
- **Given** the QoS scheduler is started
- **When** the user navigates away from the app or locks the screen
- **Then** a persistent notification remains visible in the Android status bar showing "QoS Scheduler Active" and the scheduler continues to enforce policies

---

### 10.7 Non-Functional Acceptance Criteria

**AC-26** — Packet Processing Latency *(NFR-01)*
- **Given** 5 devices are connected with 100 Mbps aggregate throughput
- **When** per-packet processing time is measured via timestamped logging at tunnel ingress and egress
- **Then** the 99th-percentile processing latency is at or below 5 ms

**AC-27** — Packet Throughput Capacity *(NFR-02)*
- **Given** a synthetic packet injection test generating 10,000 packets/second into the TUN interface
- **When** the scheduler processes the load for 60 seconds
- **Then** zero packets are dropped due to processing backlog (drops due to token bucket policy are excluded)

**AC-28** — CPU Utilization *(NFR-03)*
- **Given** the QoS service is running under normal load (5 devices, mixed traffic)
- **When** CPU usage is sampled via Android Profiler over a 60-second window
- **Then** the QoS service process does not exceed 15% CPU utilization on average

**AC-29** — Memory Footprint *(NFR-04)*
- **Given** the QoS service is running with 5 connected devices and 20 active flows
- **When** heap memory is measured via Android Profiler
- **Then** the service heap does not exceed 80 MB

**AC-30** — No Payload Logging *(NFR-10)*
- **Given** the QoS service is running and processing HTTPS traffic
- **When** the application's log output and DataStore contents are inspected
- **Then** no packet payload bytes, URL paths, or application-layer content are present in any log or persisted storage

**AC-31** — Local-Only Tunnel *(NFR-11)*
- **Given** the QoS tunnel is active
- **When** a network capture is taken on an external network monitor
- **Then** no traffic from the VpnService is routed to any external IP address other than the original packet's intended destination

**AC-32** — No Root Required *(NFR-15)*
- **Given** a stock, unrooted Android 10+ device
- **When** the application is installed and the QoS scheduler is started
- **Then** all features function correctly without any root permission prompt or ADB command

---

### 10.8 Acceptance Criteria Traceability Matrix

| AC ID | Requirement(s) | Test Method | Priority |
|-------|---------------|-------------|----------|
| AC-01 | FR-01, FR-02 | Manual + timing | High |
| AC-02 | FR-02, FR-03 | Wireshark capture | High |
| AC-03 | FR-04 | Manual | High |
| AC-04 | FR-05 | Process kill simulation | High |
| AC-05 | FR-06, FR-07 | Manual + UI inspection | High |
| AC-06 | FR-08 | Timed observation | Medium |
| AC-07 | FR-09 | App restart test | Medium |
| AC-08 | FR-10, FR-11 | Wireshark comparison | High |
| AC-09 | FR-12 | iPerf3 + Wireshark | High |
| AC-10 | FR-12 | iPerf3 + Wireshark | High |
| AC-11 | FR-12 | Wireshark | Medium |
| AC-12 | FR-13 | Manual + timing | Medium |
| AC-13 | FR-14 | Timed observation | Low |
| AC-14 | FR-15 | Code inspection + log | High |
| AC-15 | FR-17, FR-18 | iPerf3 dual-stream | High |
| AC-16 | FR-18 | iPerf3 single-stream | High |
| AC-17 | FR-19 | Device disconnect test | Medium |
| AC-18 | FR-20 | iPerf3 + UI inspection | Medium |
| AC-19 | FR-21, FR-22 | iPerf3 + UI comparison | High |
| AC-20 | FR-23 | Manual + UI inspection | Medium |
| AC-21 | FR-24 | Manual + UI inspection | Medium |
| AC-22 | FR-25 | Manual | High |
| AC-23 | FR-26 | Manual + timing | High |
| AC-24 | FR-28 | Manual | Low |
| AC-25 | FR-29 | Background navigation | High |
| AC-26 | NFR-01 | Timestamped logging | High |
| AC-27 | NFR-02 | Synthetic injection test | High |
| AC-28 | NFR-03 | Android Profiler | Medium |
| AC-29 | NFR-04 | Android Profiler | Medium |
| AC-30 | NFR-10 | Log + storage inspection | High |
| AC-31 | NFR-11 | Network capture | High |
| AC-32 | NFR-15 | Unrooted device test | High |
