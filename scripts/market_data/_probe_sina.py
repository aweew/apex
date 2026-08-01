import urllib.request

for s in ["sh688525", "sz688525", "sh688308", "hk01810"]:
    req = urllib.request.Request(
        "https://hq.sinajs.cn/list=" + s,
        headers={"Referer": "https://finance.sina.com.cn", "User-Agent": "Mozilla/5.0"},
    )
    with urllib.request.urlopen(req, timeout=10) as resp:
        body = resp.read().decode("gbk", "ignore")
    empty = '=""' in body
    print(s, "EMPTY" if empty else body[20:140].replace("\n", " "))
