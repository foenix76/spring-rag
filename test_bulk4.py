import urllib.request
import json

req = urllib.request.Request(
    'http://localhost:8091/api/chat',
    data=json.dumps({"message": "인턴 경험 있는 지원자 3명 합격 메일 보내줘"}).encode('utf-8'),
    headers={'Content-Type': 'application/json'}
)
with urllib.request.urlopen(req) as response:
    for line in response:
        if line.strip():
            print(line.decode('utf-8').strip())
