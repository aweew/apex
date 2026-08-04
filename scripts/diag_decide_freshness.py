# -*- coding: utf-8 -*-
import re
from datetime import datetime
import pymysql

text = open("apex-be/src/main/resources/application-local.yml", encoding="utf-8").read()
user_m = re.search(r"username:\s*(\S+)", text)
pwd_m = re.search(r"password:\s*(\S+)", text)
user = user_m.group(1) if user_m else "root"
pwd = (pwd_m.group(1) if pwd_m else "").strip("\"'")

conn = pymysql.connect(
    host="127.0.0.1",
    port=3306,
    user=user,
    password=pwd,
    database="apex",
    charset="utf8mb4",
)
cur = conn.cursor()

queries = [
    ("bar_daily max trade_date", "SELECT MAX(trade_date) FROM bar_daily WHERE deleted=0"),
    ("codes with >=60 bars", "SELECT COUNT(1) FROM (SELECT code FROM bar_daily WHERE deleted=0 GROUP BY code HAVING COUNT(1)>=60) t"),
    (
        "stock_basic quote",
        "SELECT MAX(quote_time), COUNT(1), SUM(latest_price IS NOT NULL AND latest_price>0), SUM(quote_time>=CURDATE()) FROM stock_basic WHERE deleted=0",
    ),
    (
        "signal max",
        "SELECT MAX(signal_date), MAX(create_time), COUNT(1) FROM strategy_signal WHERE deleted=0",
    ),
    (
        "signal by date/side last 7d",
        """
        SELECT signal_date, side, strategy_id, COUNT(1)
        FROM strategy_signal
        WHERE deleted=0 AND signal_date>=DATE_SUB(CURDATE(), INTERVAL 7 DAY)
        GROUP BY signal_date, side, strategy_id
        ORDER BY signal_date DESC, side, strategy_id
        """,
    ),
    (
        "signal distinct codes last 2 batches by date",
        """
        SELECT signal_date, COUNT(DISTINCT code), COUNT(1)
        FROM strategy_signal
        WHERE deleted=0 AND signal_date>=DATE_SUB(CURDATE(), INTERVAL 5 DAY)
        GROUP BY signal_date
        ORDER BY signal_date DESC
        """,
    ),
    (
        "observe active mix",
        "SELECT side, status, COUNT(1) FROM observe_pool WHERE deleted=0 AND status<>'ARCHIVED' GROUP BY side, status",
    ),
    (
        "observe update freshness",
        """
        SELECT COUNT(1) total,
               SUM(tags LIKE '%决策%') auto_tagged,
               SUM(DATE(update_time)=CURDATE()) updated_today,
               SUM(DATE(create_time)=CURDATE()) created_today,
               MAX(update_time), MAX(create_time)
        FROM observe_pool WHERE deleted=0 AND status<>'ARCHIVED'
        """,
    ),
    (
        "observe top codes by update",
        """
        SELECT code, name, side, status, DATE(create_time), DATE(update_time), LEFT(reason,40)
        FROM observe_pool
        WHERE deleted=0 AND status<>'ARCHIVED'
        ORDER BY update_time DESC
        LIMIT 15
        """,
    ),
    (
        "universe latest",
        """
        SELECT batch_no, COUNT(1), MIN(create_time), MAX(create_time)
        FROM universe_snapshot
        WHERE deleted=0 AND batch_no=(
          SELECT batch_no FROM universe_snapshot WHERE deleted=0 ORDER BY id DESC LIMIT 1
        )
        """,
    ),
    (
        "daily_action recent",
        """
        SELECT action_date, COUNT(1), MAX(create_time)
        FROM daily_action WHERE deleted=0
        GROUP BY action_date
        ORDER BY action_date DESC
        LIMIT 5
        """,
    ),
    (
        "sync jobs recent bars",
        """
        SELECT task_key, status, message, start_time, end_time
        FROM sync_job
        WHERE deleted=0
        ORDER BY id DESC
        LIMIT 8
        """,
    ),
]

for title, sql in queries:
    print("==", title)
    try:
        cur.execute(sql)
        rows = cur.fetchall()
        if not rows:
            print("  (empty)")
        for row in rows:
            print(" ", row)
    except Exception as e:
        print("  ERR", e)

# overlap of buy signal codes between latest 2 signal dates
print("== buy signal code overlap latest 2 dates")
try:
    cur.execute(
        """
        SELECT DISTINCT signal_date FROM strategy_signal
        WHERE deleted=0 AND side='BUY'
        ORDER BY signal_date DESC LIMIT 2
        """
    )
    dates = [r[0] for r in cur.fetchall()]
    print("  dates", dates)
    if len(dates) >= 2:
        cur.execute(
            "SELECT DISTINCT code FROM strategy_signal WHERE deleted=0 AND side='BUY' AND signal_date=%s",
            (dates[0],),
        )
        a = {r[0] for r in cur.fetchall()}
        cur.execute(
            "SELECT DISTINCT code FROM strategy_signal WHERE deleted=0 AND side='BUY' AND signal_date=%s",
            (dates[1],),
        )
        b = {r[0] for r in cur.fetchall()}
        inter = a & b
        print(
            f"  {dates[0]}={len(a)} {dates[1]}={len(b)} overlap={len(inter)} "
            f"new={len(a-b)} dropped={len(b-a)} overlap_ratio={len(inter)/max(len(a),1):.2f}"
        )
        print("  sample new", list(a - b)[:10])
        print("  sample same", list(inter)[:10])
except Exception as e:
    print("  ERR", e)

print("now", datetime.now())
conn.close()
