import urllib.request
import json
import sys

req = urllib.request.Request(
    'http://localhost:8091/api/chat',
    data=json.dumps({"message": "지원자들한테 일괄 발송해줘"}).encode('utf-8'),
    headers={'Content-Type': 'application/json'}
)
with urllib.request.urlopen(req) as response:
    for line in response:
        if line.strip():
            print(line.decode('utf-8').strip())
