import urllib.request
import json
import sys

req = urllib.request.Request(
    'http://localhost:8091/api/chat',
    data=json.dumps({"message": "인턴 경험 있는 지원자 3명 합격 메일 보내줘"}).encode('utf-8'),
    headers={'Content-Type': 'application/json'}
)
try:
    with urllib.request.urlopen(req) as response:
        for line in response:
            s = line.decode('utf-8').strip()
            if s: print(s)
except Exception as e:
    print("Exception:", e)
