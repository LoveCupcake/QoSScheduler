import base64
import urllib.request
import json
import os

with open('thesis/figures/ch3_architecture.mmd', 'r') as f:
    mermaid_code = f.read()

state = {
    'code': mermaid_code,
    'mermaid': {'theme': 'dark'}
}
state_json = json.dumps(state).encode('utf-8')
b64 = base64.urlsafe_b64encode(state_json).decode('utf-8')
url = 'https://mermaid.ink/img/' + b64

output_path = 'thesis/figures/ch3_three_plane_arch_1780555671580.png'
try:
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    with urllib.request.urlopen(req) as response, open(output_path, 'wb') as out_file:
        data = response.read()
        out_file.write(data)
    print('Downloaded successfully to', output_path)
except Exception as e:
    print('Failed to download: ', e)
