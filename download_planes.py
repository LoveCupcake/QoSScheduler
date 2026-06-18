import base64
import urllib.request
import json
import os

def download_mermaid(mermaid_code, output_filename):
    state = {
        'code': mermaid_code,
        'mermaid': {'theme': 'dark'}
    }
    state_json = json.dumps(state).encode('utf-8')
    b64 = base64.urlsafe_b64encode(state_json).decode('utf-8')
    url = 'https://mermaid.ink/img/' + b64
    
    output_path = os.path.join(r'C:\Users\hieum\.gemini\antigravity-ide\brain\551268f7-5916-4b75-bcfe-0fcee55e370a', output_filename)
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req) as response, open(output_path, 'wb') as out_file:
            data = response.read()
            out_file.write(data)
        print(f'Downloaded {output_filename}')
    except Exception as e:
        print(f'Failed to download {output_filename}: {e}')

dp_code = """graph LR
    classDef dp fill:#1E1E2E,stroke:#89B4FA,stroke-width:2px,color:#CDD6F4;
    TUN["TUN Interface"] --> PARSE["IP Parser"]
    PARSE --> UIDR["UID Resolver"]
    UIDR --> DPIC["DPI Classifier (DPI)"]
    DPIC --> RELAY["TCP/UDP Relay"]
    class TUN,PARSE,UIDR,DPIC,RELAY dp;
"""

cp_code = """graph LR
    classDef cp fill:#1E1E2E,stroke:#F38BA8,stroke-width:2px,color:#CDD6F4;
    TBK["Token Bucket (CAS Lock-Free)"] --> BS["BandwidthScheduler"]
    WFQ["WFQ Queueing (Weights 4:2:1)"] --> BS
    class TBK,WFQ,BS cp;
"""

mp_code = """graph LR
    classDef mp fill:#1E1E2E,stroke:#A6E3A1,stroke-width:2px,color:#CDD6F4;
    APP["Android Client"] -->|"HTTP Telemetry Push"| RAPI["Node.js REST API"]
    APP -->|"HTTP Polling"| RAPI
    RAPI --> WADM["Web Admin Dashboard"]
    RAPI --> SQDB[("SQLite Database")]
    class APP,RAPI,WADM,SQDB mp;
"""

download_mermaid(dp_code, 'plane_data.png')
download_mermaid(cp_code, 'plane_control.png')
download_mermaid(mp_code, 'plane_management.png')
