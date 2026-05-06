# UML Diagrams
## Dynamic QoS Scheduler for Mobile Hotspots

**Version:** 1.0  
**Author:** Phạm Hiếu Minh — 23BI14295  

---

## Table of Contents

1. [Use Case Diagram](#1-use-case-diagram)
2. [Class Diagram](#2-class-diagram)
3. [Sequence Diagram — Packet Processing Flow](#3-sequence-diagram--packet-processing-flow)
4. [Sequence Diagram — User Assigns Device Priority](#4-sequence-diagram--user-assigns-device-priority)
5. [State Diagram — Token Bucket](#5-state-diagram--token-bucket)
6. [State Diagram — QoS Service Lifecycle](#6-state-diagram--qos-service-lifecycle)
7. [Component Diagram](#7-component-diagram)

---

## 1. Use Case Diagram

```mermaid
graph TD
    HO((Hotspot Owner))

    HO --> UC01[Activate QoS Scheduler]
    HO --> UC02[Deactivate QoS Scheduler]
    HO --> UC03[Assign Device Priority]
    HO --> UC04[Set Manual Bandwidth Cap]
    HO --> UC05[Monitor Traffic Dashboard]
    HO --> UC06[View Per-Device Flow Detail]
    HO --> UC07[Reset All Priorities]
    HO --> UC08[Configure Uplink Bandwidth]

    UC01 --> UC09[Request VPN Permission]
    UC01 --> UC10[Establish TUN Tunnel]
    UC03 --> UC11[Update Token Bucket Params]
    UC04 --> UC11
    UC10 --> UC12[Discover Connected Devices]
    UC10 --> UC13[Classify Traffic Flows]
    UC13 --> UC11

    CC((Connected Client))
    CC --> UC14[Send / Receive Traffic]
    UC14 --> UC13
```

---

## 2. Class Diagram

```mermaid
classDiagram

    class QosVpnService {
        -tunInterface: ParcelFileDescriptor
        -inputStream: FileInputStream
        -outputStream: FileOutputStream
        -scheduler: BandwidthScheduler
        -registry: DeviceRegistry
        -classifier: DpiClassifier
        -serviceScope: CoroutineScope
        +onStartCommand(intent, flags, startId): Int
        +onRevoke()
        -startTunnel()
        -stopTunnel()
        -runPacketLoop()
        -buildNotification(): Notification
    }

    class BandwidthScheduler {
        -buckets: Map~String, TokenBucket~
        -uplinkBps: Long
        +processPacket(packet: RawPacket): Boolean
        +addDevice(device: ConnectedDevice)
        +removeDevice(ipAddress: String)
        +updatePriority(ipAddress: String, priority: TrafficClass)
        +setManualCap(ipAddress: String, rateBps: Long)
        +rebalance()
        -allocateRates()
    }

    class TokenBucket {
        -rateBps: Long
        -burstBytes: Long
        -tokens: Double
        -lastRefillTime: Long
        +consume(bytes: Int): Boolean
        +refill()
        +setRate(rateBps: Long)
        +setBurst(burstBytes: Long)
    }

    class DpiClassifier {
        -rules: List~ClassificationRule~
        -flowCache: Map~FlowKey, TrafficCategory~
        +classify(packet: RawPacket): TrafficCategory
        +reclassifyAll()
        -matchPort(port: Int, protocol: Protocol): TrafficCategory
        -matchHeuristic(flow: PacketFlow): TrafficCategory
    }

    class ClassificationRule {
        +category: TrafficCategory
        +protocol: Protocol
        +portRanges: List~IntRange~
        +matches(port: Int, protocol: Protocol): Boolean
    }

    class DeviceRegistry {
        -devices: MutableMap~String, ConnectedDevice~
        -persistenceStore: DataStore
        +getOrCreate(ipAddress: String): ConnectedDevice
        +updateStats(ipAddress: String, bytes: Long)
        +remove(ipAddress: String)
        +getAll(): List~ConnectedDevice~
        +persistPriority(mac: String, priority: TrafficClass)
        +loadPersistedPriorities()
        -scheduleTimeoutCheck()
    }

    class ConnectedDevice {
        +ipAddress: String
        +macAddress: String?
        +hostname: String?
        +priorityClass: TrafficClass
        +bytesIn: Long
        +bytesOut: Long
        +currentThroughputBps: Long
        +lastSeenTimestamp: Long
        +activeFlows: List~PacketFlow~
    }

    class PacketFlow {
        +srcIp: String
        +dstIp: String
        +srcPort: Int
        +dstPort: Int
        +protocol: Protocol
        +trafficCategory: TrafficCategory
        +byteCount: Long
        +lastSeen: Long
    }

    class RawPacket {
        +buffer: ByteBuffer
        +length: Int
        +srcIp: String
        +dstIp: String
        +srcPort: Int
        +dstPort: Int
        +protocol: Protocol
        +payload: ByteArray
        +parse(buffer: ByteBuffer): RawPacket$
    }

    class TrafficClass {
        <<enumeration>>
        HIGH
        MEDIUM
        LOW
    }

    class TrafficCategory {
        <<enumeration>>
        VIDEO_CONFERENCING
        ONLINE_GAMING
        VOIP
        WEB_BROWSING
        STREAMING
        FILE_TRANSFER
        OS_UPDATE
        UNKNOWN
    }

    class Protocol {
        <<enumeration>>
        TCP
        UDP
        OTHER
    }

    class FlowKey {
        +srcIp: String
        +dstIp: String
        +srcPort: Int
        +dstPort: Int
        +protocol: Protocol
        +equals(): Boolean
        +hashCode(): Int
    }

    QosVpnService --> BandwidthScheduler
    QosVpnService --> DeviceRegistry
    QosVpnService --> DpiClassifier
    BandwidthScheduler --> TokenBucket
    BandwidthScheduler --> DeviceRegistry
    DpiClassifier --> ClassificationRule
    DpiClassifier --> FlowKey
    DeviceRegistry --> ConnectedDevice
    ConnectedDevice --> PacketFlow
    ConnectedDevice --> TrafficClass
    PacketFlow --> TrafficCategory
    PacketFlow --> Protocol
    RawPacket --> Protocol
    ClassificationRule --> TrafficCategory
    ClassificationRule --> Protocol
```

---

## 3. Sequence Diagram — Packet Processing Flow

Shows the lifecycle of a single packet from TUN ingress to uplink egress.

```mermaid
sequenceDiagram
    participant TUN as TUN Interface
    participant VPN as QosVpnService
    participant Parser as RawPacket.parse()
    participant Registry as DeviceRegistry
    participant Classifier as DpiClassifier
    participant Scheduler as BandwidthScheduler
    participant Bucket as TokenBucket
    participant Uplink as Internet Uplink

    TUN->>VPN: raw bytes (FileInputStream.read)
    VPN->>Parser: parse(ByteBuffer)
    Parser-->>VPN: RawPacket (srcIp, dstIp, ports, protocol)

    VPN->>Registry: getOrCreate(srcIp)
    Registry-->>VPN: ConnectedDevice

    VPN->>Classifier: classify(RawPacket)
    Classifier->>Classifier: matchPort(dstPort, protocol)
    Classifier-->>VPN: TrafficCategory

    VPN->>Registry: updateStats(srcIp, bytes)

    VPN->>Scheduler: processPacket(RawPacket)
    Scheduler->>Bucket: consume(packetBytes)

    alt Tokens available
        Bucket-->>Scheduler: true
        Scheduler-->>VPN: allowed
        VPN->>Uplink: write(rawBytes) via FileOutputStream
    else Tokens exhausted
        Bucket-->>Scheduler: false
        Scheduler-->>VPN: dropped
        Note over VPN: Packet dropped (token bucket policy)
    end
```

---

## 4. Sequence Diagram — User Assigns Device Priority

Shows the interaction chain when the user changes a device's priority class from the UI.

```mermaid
sequenceDiagram
    participant User
    participant UI as DashboardScreen (Compose)
    participant VM as MainViewModel
    participant Registry as DeviceRegistry
    participant Scheduler as BandwidthScheduler
    participant Bucket as TokenBucket
    participant Store as DataStore

    User->>UI: Selects device → taps priority selector → chooses HIGH
    UI->>VM: onPriorityChanged(ipAddress, HIGH)
    VM->>Registry: updatePriority(ipAddress, HIGH)
    Registry->>Registry: device.priorityClass = HIGH

    Registry->>Store: persistPriority(mac, HIGH)
    Store-->>Registry: ack

    VM->>Scheduler: updatePriority(ipAddress, HIGH)
    Scheduler->>Scheduler: rebalance()
    Scheduler->>Bucket: setRate(newRateBps)
    Scheduler->>Bucket: setBurst(newBurstBytes)
    Bucket-->>Scheduler: updated

    Scheduler-->>VM: ack
    VM->>VM: emit updated device list (StateFlow)
    VM-->>UI: recompose with new priority label
    UI-->>User: Device shows HIGH priority badge
```

---

## 5. State Diagram — Token Bucket

Shows the internal states of a single token bucket instance during packet processing.

```mermaid
stateDiagram-v2
    [*] --> Idle : instantiated

    Idle --> Refilling : refill() called (periodic timer)
    Refilling --> Available : tokens += rate * deltaTime
    Available --> Idle : waiting for next packet

    Available --> Consuming : consume(bytes) called
    Consuming --> Available : tokens >= bytes\n(packet allowed)
    Consuming --> Dropping : tokens < bytes\n(packet dropped)
    Dropping --> Available : drop recorded, await refill

    Available --> Reconfiguring : setRate() or setBurst() called
    Reconfiguring --> Available : new rate/burst applied

    Available --> [*] : device removed from registry
```

---

## 6. State Diagram — QoS Service Lifecycle

Shows the full lifecycle of the QosVpnService from creation to destruction.

```mermaid
stateDiagram-v2
    [*] --> Created : onStartCommand()

    Created --> RequestingPermission : VPN permission not yet granted
    RequestingPermission --> Created : permission denied
    RequestingPermission --> BuildingTunnel : permission granted

    Created --> BuildingTunnel : permission already granted

    BuildingTunnel --> Active : TUN interface established\nforeground notification shown

    Active --> ProcessingPackets : packet received on TUN
    ProcessingPackets --> Active : packet forwarded or dropped

    Active --> Recovering : tunnel dropped unexpectedly
    Recovering --> BuildingTunnel : retry within 3 seconds
    Recovering --> Stopped : max retries exceeded

    Active --> Stopped : user taps Stop QoS\nor hotspot disabled

    Stopped --> [*] : onDestroy()\ntunnel torn down\nnotification dismissed
```

---

## 7. Component Diagram

Shows the major architectural components, their responsibilities, and dependencies.

```mermaid
graph TB
    subgraph Android_App ["Android Application"]

        subgraph UI_Layer ["UI Layer (Jetpack Compose)"]
            Dashboard["DashboardScreen\n- Device list\n- Aggregate stats\n- Start/Stop toggle"]
            DeviceDetail["DeviceDetailScreen\n- Per-flow breakdown\n- Priority selector\n- Bandwidth cap input"]
            Settings["SettingsScreen\n- Uplink config\n- Reset priorities"]
            VM["MainViewModel\n- StateFlow<UiState>\n- Event handlers"]
        end

        subgraph Service_Layer ["Service Layer"]
            VPN["QosVpnService\n(Foreground Service)\n- TUN I/O loop\n- Coroutine scope"]
        end

        subgraph Core_Layer ["Core Layer"]
            Classifier["DpiClassifier\n- Port rule matching\n- Flow cache\n- Heuristic analysis"]
            Scheduler["BandwidthScheduler\n- Per-device queues\n- Rate rebalancing"]
            Bucket["TokenBucket\n- Token refill\n- Consume / drop"]
            Registry["DeviceRegistry\n- Device tracking\n- Timeout eviction\n- Stats aggregation"]
            Parser["RawPacket\n- ByteBuffer parsing\n- IPv4 / IPv6 headers"]
        end

        subgraph Persistence_Layer ["Persistence Layer"]
            Store["DataStore\n- Priority overrides\n- Bandwidth caps"]
        end

    end

    subgraph Android_OS ["Android OS"]
        VpnAPI["VpnService API\n(TUN Interface)"]
        WifiMgr["WifiManager\n(Hotspot State)"]
        NotifMgr["NotificationManager\n(Foreground Notification)"]
    end

    subgraph External ["External / Network"]
        Hotspot["Connected Client Devices"]
        Uplink["Internet Uplink"]
    end

    Dashboard --> VM
    DeviceDetail --> VM
    Settings --> VM
    VM --> Registry
    VM --> Scheduler

    VPN --> Parser
    VPN --> Classifier
    VPN --> Scheduler
    VPN --> Registry
    VPN --> VpnAPI
    VPN --> NotifMgr

    Scheduler --> Bucket
    Scheduler --> Registry
    Registry --> Store
    Registry --> VM

    VpnAPI --> Hotspot
    VpnAPI --> Uplink
    WifiMgr --> VPN
```
