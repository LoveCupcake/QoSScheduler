# QoS Scheduler Project Walkthrough

Welcome to the QoS Scheduler repository. This document serves as a guided tour through the codebase, explaining how the different components fit together to form a complete userspace QoS system.

---

## 1. Project Structure

The repository is divided into three main pillars:
1.  **`app/` (The Data & Control Plane):** The native Android application written in Kotlin. This is where the core packet interception, parsing, and scheduling occur.
2.  **`server/` (The Telemetry Plane):** The Node.js Web Admin dashboard. It ingests data from the Android client and provides real-time visualization.
3.  **`thesis/` (Documentation):** The LaTeX source files for the final academic thesis, detailing the architecture, implementation, and experimental results.

---

## 2. Walkthrough of the Android Client (`app/`)

### A. The Interception Engine (VpnService)
*   **File:** `QosVpnService.kt`
*   **Role:** Acts as the entry point for the network pipeline. It configures the virtual TUN interface (`Builder().addRoute("0.0.0.0", 0)`), capturing all outgoing IPv4 and IPv6 traffic. It runs a continuous coroutine loop (`Dispatchers.IO`) reading raw bytes into a reused `ByteBuffer`.

### B. Packet Parsing and DPI
*   **File:** `RawPacket.kt` & `DpiClassifier.kt`
*   **Role:** Extracts source/destination IPs, ports, and protocol numbers using bitwise operations (`shr`, `and`) without allocating new memory, mitigating Garbage Collection (GC) pauses. It identifies traffic types (e.g., DNS, HTTP, Gaming) using port heuristics.

### C. The Brains: Token Bucket & WFQ
*   **File:** `BandwidthScheduler.kt` & `TokenBucket.kt`
*   **Role:** The heart of the QoS system.
    *   `TokenBucket` enforces rate limits using lazy nanosecond refill evaluation.
    *   `BandwidthScheduler` distributes available bandwidth dynamically using a Weighted Fair Queuing (WFQ) algorithm with a 4:2:1 ratio for HIGH, MEDIUM, and LOW priority classes.

### D. The Relay Proxy
*   **File:** `TcpRelayManager.kt` & `PacketComposer.kt`
*   **Role:** Since the app intercepts Layer 3 packets, it must proxy them. It creates a protected socket (`vpnService.protect()`) to bypass the TUN interface. `PacketComposer` is used to manually craft synthetic `SYN-ACK` IP headers (with RFC 1071 checksums) to fool the local app into thinking it has a direct connection.

---

## 3. Walkthrough of the Web Admin (`server/`)

### A. The Backend API
*   **File:** `server/index.js`
*   **Role:** An Express.js REST API that receives POST requests from the Android app containing telemetry data (app names, requested BPS, allowed BPS, dropped packets). It stores this data in an SQLite database.

### B. The Real-time Dashboard
*   **File:** `server/public/index.html` & `app.js`
*   **Role:** A responsive, cyberpunk-styled dashboard. It uses `Chart.js` with a custom Neon Glow plugin to render real-time graphs of the Token Bucket behavior and WFQ fair-share pie charts.

---

## 4. How Data Flows

1.  A local app (e.g., Chrome) tries to open a webpage.
2.  Android routes the TCP `SYN` packet to the `QosVpnService` TUN interface.
3.  `RawPacket` parses the headers. `DpiClassifier` tags it as MEDIUM priority.
4.  `BandwidthScheduler` checks the `TokenBucket` for Chrome. If there are enough tokens, the packet is processed.
5.  `TcpRelayManager` protects a socket, connects to the real web server, and relays the data.
6.  Every 5 seconds, the Android app pushes telemetry to the Node.js `server/`.
7.  The Web Admin UI polls the server and updates the live charts.

---

## 5. Recent Bug Fixes and Updates

### QoS Bypass Bug (Facebook / QUIC)
*   **Symptom:** Facebook and other heavy UDP/QUIC apps bypassed QoS limits entirely, causing packet loss charts to flatline and policies to seemingly not apply.
*   **Root Cause:** The `AppResolver` had a strict negative cache (3 failures) that permanently cached unresolvable flows to the `__host__` (UID -1) bucket. QUIC's aggressive handshakes easily triggered this before the OS kernel fully established the connection.
*   **Fix:** Increased the failure threshold to 20, and modified the negative cache to expire after 10 seconds rather than permanently banning the flow.

### Web Admin Policy Overwrite Bug
*   **Symptom:** Changing a policy multiple times on the Web Admin would sometimes result in the older policy applying instead of the newer one.
*   **Root Cause:** The Node.js server returned policies ordered by `created_at DESC` (newest first). The Android `MainViewModel` processed this list sequentially (`forEach`), meaning older duplicates at the end of the list would overwrite the newer settings applied earlier in the loop.
*   **Fix:** Added `.distinctBy { it.packageName }` to the sync loop to ensure only the most recent policy for each app is processed.
