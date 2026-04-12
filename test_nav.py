import urllib.request
import json
import sys

req = urllib.request.Request(
    'http://localhost:8091/api/chat',
    data=json.dumps({"message": "대시보드로 이동해줄래?"}).encode('utf-8'),
    headers={'Content-Type': 'application/json'}
)
try:
    with urllib.request.urlopen(req) as response:
        for line in response:
            s = line.decode('utf-8').strip()
            if s: print(s)
except Exception as e:
    print("Exception:", e)
