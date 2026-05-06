# QoS Scheduler: Technical Project Overview

## 1) Project Goal

QoS Scheduler is an Android application that uses `VpnService` to capture IP packets on the host device, classify traffic, and apply policy-based scheduling decisions (priority + token bucket logic).  
The objective is to provide a software-defined QoS layer in userspace without root access.

## 2) Core Architecture

The project is split into four main layers:

- **UI Layer** (`MainActivity`, Compose screens)
  - Dashboard and detail views for apps/devices.
  - Priority controls and runtime statistics.
- **Service Layer** (`QosVpnService`)
  - Foreground VPN service lifecycle.
  - TUN I/O orchestration and background jobs.
- **Data Plane Layer** (`DataPlaneProcessor`)
  - Packet-level processing path:
    - parse -> classify -> app resolution -> flow accounting -> scheduling decision.
- **Policy/Model Layer**
  - `DpiClassifier` for category mapping.
  - `BandwidthScheduler` + `TokenBucket` for rate enforcement.
  - Data models (`RawPacket`, `AppTraffic`, `PacketFlow`, etc.).

## 3) Packet Processing Pipeline

Current packet pipeline is implemented with two asynchronous loops:

1. **Ingress loop** (`runTunReadLoop`)
   - Reads raw packets from TUN.
   - Pushes packets into a bounded channel queue (`Channel`).
2. **Processing/Egress loop** (`runPacketProcessLoop`)
   - Pulls packets from channel.
   - Calls `DataPlaneProcessor` to:
     - parse packet metadata (`RawPacket`)
     - classify traffic (`DpiClassifier`)
     - resolve app UID (`AppResolver`)
     - update per-app flow state (`AppTraffic.activeFlows`)
     - evaluate scheduler decision (`BandwidthScheduler`)
   - Forwards packet if allowed.

This split reduces head-of-line blocking versus a single-loop design and provides clearer data-plane mechanics.

## 4) Traffic Classification

`DpiClassifier` uses protocol/port heuristics to map flows into categories:

- Video Conferencing
- Online Gaming
- VoIP
- Web Browsing
- Streaming
- File Transfer
- Unknown

Classification results are attached to `PacketFlow` entries and exposed in UI (app detail screen).

## 5) Scheduling and Prioritization

`BandwidthScheduler` holds token buckets keyed by endpoint identity and decides whether a packet should be forwarded or dropped.

- Token bucket refill is time-based.
- Decision is O(1) per packet after lookup.
- Priority class changes update scheduling behavior at runtime.

## 6) Runtime State and Telemetry

The system keeps lightweight runtime state:

- Per-app totals (`bytesIn`, `bytesOut`, `currentThroughputBps`)
- Per-app active flows (`FlowKey` -> `PacketFlow`)
- Last-seen timestamps for stale-flow cleanup

Background jobs refresh app/socket mapping and perform periodic cleanup to keep data-plane hot path minimal.

## 7) Current Technical Status

Implemented:

- TUN capture path
- Data-plane module extraction (`DataPlaneProcessor`)
- Two-stage packet pipeline (ingress queue + process/egress)
- Real-time app usage + flow category UI
- Scheduler decision integration

Known limitation:

- Full production-grade forwarding mechanics still require deeper protocol handling (especially robust end-to-end relay behavior under high load and complex traffic patterns).

## 8) Why This Design

This architecture emphasizes:

- **Separation of concerns** (service orchestration vs packet mechanics)
- **Performance-aware pipeline** (queue-based decoupling)
- **Observable behavior** (flow/category visibility in UI)
- **Extensibility** (future UDP/TCP relay specialization in data plane)

It provides a practical foundation for iterative QoS experimentation and thesis-grade validation.
