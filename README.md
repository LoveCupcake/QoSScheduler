# QoS Scheduler for Mobile Hotspots

Dynamic Quality of Service (QoS) scheduling framework for Android hotspot hosts.

## Overview

This Android application enforces priority-driven bandwidth allocation for applications running on the hotspot host phone. It uses a software-defined QoS layer in userspace via the Android `VpnService` API.

Current scope: host-only per-app QoS (not per-client-device hotspot shaping).

## Key Features

- No root required (userspace `VpnService` implementation)
- Per-app QoS buckets on host with weighted scheduling
- DPI-lite traffic classification (port/protocol heuristics)
- Token bucket enforcement per scheduler bucket
- Real-time app throughput and flow monitoring
- Runtime app traffic tracking with QoS counters
- IPv4 and IPv6 packet parsing support

## Architecture

- `ui/`: Jetpack Compose screens + `MainViewModel`
- `service/`: `QosVpnService` packet loop + dataplane
- `service/dataplane/`: parse -> classify -> resolve UID -> scheduler decision
- `scheduler/`: token bucket + weighted rebalance
- `classifier/`: DPI-lite rules
- `registry/`: runtime host/hotspot state + persistence helpers
- `model/`: app, flow, packet, and enum models

## Traffic Classification

Default categories are mapped by protocol/port heuristics:

- Video Conferencing
- Online Gaming
- VoIP
- Web Browsing
- Streaming
- File Transfer
- OS Updates
- Unknown

## Token Bucket Parameters

Default class profiles:

- `HIGH`: 80% target rate, 2x burst
- `MEDIUM`: 50% target rate, 1x burst
- `LOW`: 20% target rate, 0.5x burst

Rates are dynamically rebalanced with weighted fair sharing (`HIGH=4`, `MEDIUM=2`, `LOW=1`) across active host applications.

## Requirements

- Android 10+ (API 29+)
- VPN permission (first launch)
- Active mobile hotspot

## Installation

1. Clone repository
2. Open in Android Studio
3. Build and install on Android device
4. Grant VPN permission when prompted

## Usage

1. Enable hotspot on the host phone
2. Start QoS in app
3. Open apps on host phone to generate traffic
4. Tap app entries to inspect traffic details
5. Monitor throughput and active flows in real time

## Configuration

- `Settings -> Uplink Bandwidth`: set uplink Mbps
- `Settings -> Reset All Priorities`: clear saved runtime overrides

## Performance Targets

- Packet processing latency: < 5 ms per packet
- Throughput capacity: 10,000 packets/second
- CPU utilization: < 15% under normal load
- Memory footprint: < 80 MB RAM

## Security & Privacy

- No payload content logging/persistence
- Header metadata only (IP/port/protocol/bytes)
- Local VPN tunnel usage (no external VPN endpoint)
- Runtime state and preferences stored in app private storage

## Limitations

- Classification is heuristic (no deep payload inspection)
- Uplink value should be configured accurately by user
- IPv6 behavior depends on carrier/device support

## Documentation

- [Software Requirements Specification](docs/SRS.md)
- [UML Diagrams](docs/UML.md)

## License

Academic project - University of Science & Technology of Hanoi, 2024-2025

## Author

Pham Hieu Minh - Student ID: 23BI14295
