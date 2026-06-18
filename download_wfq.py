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

wfq_code = """graph LR
    classDef pktH fill:#f38ba8,stroke:#1e1e2e,color:#1e1e2e,stroke-width:2px;
    classDef pktM fill:#f9e2af,stroke:#1e1e2e,color:#1e1e2e,stroke-width:2px;
    classDef pktL fill:#89b4fa,stroke:#1e1e2e,color:#1e1e2e,stroke-width:2px;
    classDef qH fill:#1e1e2e,stroke:#f38ba8,stroke-width:3px,color:#cdd6f4;
    classDef qM fill:#1e1e2e,stroke:#f9e2af,stroke-width:3px,color:#cdd6f4;
    classDef qL fill:#1e1e2e,stroke:#89b4fa,stroke-width:3px,color:#cdd6f4;
    classDef sched fill:#cba6f7,stroke:#1e1e2e,color:#1e1e2e,stroke-width:3px;
    classDef out fill:#a6e3a1,stroke:#1e1e2e,color:#1e1e2e,stroke-width:3px;

    IN(("Incoming<br>Traffic")) --> CLASSIFIER{"Traffic<br>Classifier"}

    subgraph Queues["Weighted Fair Queuing (WFQ) Buffers"]
        direction LR
        CLASSIFIER -->|"Game/VoIP"| QH["[High Priority Queue]<br>Weight: 4"]
        CLASSIFIER -->|"Web/App"| QM["[Normal Priority Queue]<br>Weight: 2"]
        CLASSIFIER -->|"Background"| QL["[Low Priority Queue]<br>Weight: 1"]
        
        class QH qH;
        class QM qM;
        class QL qL;
    end

    QH -->|"(Extract 4 Packets)"| SCHED(("WFQ<br>Scheduler"))
    QM -->|"(Extract 2 Packets)"| SCHED
    QL -->|"(Extract 1 Packet)"| SCHED
    
    class SCHED sched;

    SCHED --> OUT[["Outgoing Interface<br>(To Internet)"]]
    class OUT out;
"""

download_mermaid(wfq_code, 'wfq_algorithm.png')
