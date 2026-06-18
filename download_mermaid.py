import base64
import urllib.request
import json
import os

mermaid_code = """graph TB
    DEV["Android Device"] --> TUN

    subgraph DP["Data Plane"]
        direction LR
        TUN["TUN Interface"] --> PARSE["IP Parser"]
        PARSE --> UIDR["UID Resolver"]
        UIDR --> DPIC["DPI Classifier"]
        DPIC --> RELAY["TCP/UDP Relay"]
    end

    subgraph CP["Control Plane"]
        direction LR
        TBK["Token Bucket - CAS Lock-Free"]
        WFQ["WFQ Scheduler - Weights 4:2:1"]
        BS["BandwidthScheduler"]
    end

    subgraph TP["Management Plane"]
        direction LR
        PUSH["HTTP Telemetry Push"]
        POLL["HTTP Polling"]
        PSYNC["Policy Sync"]
    end

    DP -->|"packet metadata"| CP
    CP -->|"allow/drop verdict"| DP
    CP -->|"statistics"| TP
    TP -->|"policy updates"| CP

    RELAY --> INT(("Internet"))

    TP <-.->|"REST API"| NS

    subgraph NS["Node.js Server"]
        direction TB
        RAPI["REST API"]
        SQDB["SQLite DB"]
        WADM["Web Admin"]
    end"""

# Create state object for mermaid.ink
state = {
    'code': mermaid_code,
    'mermaid': {'theme': 'dark'}
}
state_json = json.dumps(state).encode('utf-8')
b64 = base64.urlsafe_b64encode(state_json).decode('utf-8')
url = 'https://mermaid.ink/img/' + b64

output_path = r'C:\Users\hieum\.gemini\antigravity-ide\brain\551268f7-5916-4b75-bcfe-0fcee55e370a\ch3_three_plane_arch_updated.png'
try:
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    with urllib.request.urlopen(req) as response, open(output_path, 'wb') as out_file:
        data = response.read()
        out_file.write(data)
    print('Downloaded successfully.')
except Exception as e:
    print('Failed to download: ', e)
