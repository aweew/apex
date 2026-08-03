# -*- coding: utf-8 -*-
"""看板大盘验收：对照本地 API 与东财 fenbu / 成交额口径。"""
import json
import sys
import urllib.request

BASE = "http://127.0.0.1:8080/apex"


def get_json(url):
    req = urllib.request.Request(
        url,
        headers={"User-Agent": "Mozilla/5.0", "Referer": "https://quote.eastmoney.com/"},
    )
    with urllib.request.urlopen(req, timeout=90) as r:
        return json.loads(r.read().decode("utf-8"))


def fenbu_stats():
    data = get_json(
        "https://push2ex.eastmoney.com/getTopicZDFenBu"
        "?ut=7eea3edcaed734bea9cbfc24409ed989&dpt=wz.ztzt"
    )
    levels = {}
    for item in (data.get("data") or {}).get("fenbu") or []:
        for k, v in item.items():
            levels[int(k)] = int(v)
    up = sum(v for k, v in levels.items() if k > 0)
    down = sum(v for k, v in levels.items() if k < 0)
    flat = levels.get(0, 0)
    return up, flat, down, levels.get(11, 0), levels.get(-11, 0)


def main():
    errors = []
    home = get_json(f"{BASE}/api/dashboard/home?forceRefresh=true")
    if home.get("code") != 0:
        print("FAIL home code", home.get("code"), home.get("msg"))
        return 1
    m = (home.get("data") or {}).get("market") or {}
    print("stance", m.get("stance"), "score", m.get("stanceScore"), "level", m.get("dataLevel"))
    print("volume", m.get("indexVolumeText"), m.get("volumeLabel"), m.get("volumeTrend"), m.get("volumeVsMa5Pct"))
    print("breadth", m.get("breadthUp"), m.get("breadthFlat"), m.get("breadthDown"))
    print("limit", m.get("limitUpCount"), m.get("limitDownCount"))
    for row in m.get("indexes") or []:
        print("index", row.get("name"), row.get("close"), row.get("pctChg"))

    if not m.get("indexVolume") or float(m["indexVolume"]) <= 0:
        errors.append("成交额为空")
    if not m.get("indexes"):
        errors.append("指数为空")
    up = m.get("breadthUp") or 0
    down = m.get("breadthDown") or 0
    if up <= 0 and down <= 0:
        errors.append("涨跌家数为空")
    if m.get("limitDownCount") is None:
        errors.append("跌停为空")
    label = m.get("volumeLabel") or ""
    if "放量" not in label and "缩量" not in label and "实时" not in label and "今日" not in label:
        errors.append(f"量能标签异常 label={label}")

    em_up, em_flat, em_down, em_lu, em_ld = fenbu_stats()
    print("fenbu", em_up, em_flat, em_down, "limit", em_lu, em_ld)
    if abs(em_up - up) > 80 or abs(em_down - down) > 80:
        errors.append(f"广度与东财fenbu偏差过大 board={up}/{down} fenbu={em_up}/{em_down}")
    lu = m.get("limitUpCount")
    ld = m.get("limitDownCount")
    if lu is not None and abs(int(lu) - em_lu) > 15:
        errors.append(f"涨停偏差 board={lu} fenbu={em_lu}")
    if ld is not None and abs(int(ld) - em_ld) > 10:
        errors.append(f"跌停偏差 board={ld} fenbu={em_ld}")

    if errors:
        print("FAIL", errors)
        return 1
    print("PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
