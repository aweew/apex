import urllib.request, json
url="http://127.0.0.1:8080/apex/api/portfolio/list"
with urllib.request.urlopen(url, timeout=30) as resp:
  body=json.loads(resp.read().decode("utf-8"))
for p in body.get("data") or []:
  print(p.get("id"), p.get("name"), p.get("positionCount"), p.get("ownerLabel"))
