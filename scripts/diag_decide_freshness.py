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
    ("日线最大交易日期", "SELECT MAX(trade_date) FROM bar_daily WHERE deleted=0"),
    ("日线不少于60条的证券数", "SELECT COUNT(1) FROM (SELECT code FROM bar_daily WHERE deleted=0 GROUP BY code HAVING COUNT(1)>=60) t"),
    (
        "股票基础行情",
        "SELECT MAX(quote_time), COUNT(1), SUM(latest_price IS NOT NULL AND latest_price>0), SUM(quote_time>=CURDATE()) FROM stock_basic WHERE deleted=0",
    ),
    (
        "信号最新时间",
        "SELECT MAX(signal_date), MAX(create_time), COUNT(1) FROM strategy_signal WHERE deleted=0",
    ),
    (
        "最近7日按日期和方向统计信号",
        """
        SELECT signal_date, side, strategy_id, COUNT(1)
        FROM strategy_signal
        WHERE deleted=0 AND signal_date>=DATE_SUB(CURDATE(), INTERVAL 7 DAY)
        GROUP BY signal_date, side, strategy_id
        ORDER BY signal_date DESC, side, strategy_id
        """,
    ),
    (
        "最近两批信号证券去重数",
        """
        SELECT signal_date, COUNT(DISTINCT code), COUNT(1)
        FROM strategy_signal
        WHERE deleted=0 AND signal_date>=DATE_SUB(CURDATE(), INTERVAL 5 DAY)
        GROUP BY signal_date
        ORDER BY signal_date DESC
        """,
    ),
    (
        "观察池活跃状态分布",
        "SELECT side, status, COUNT(1) FROM observe_pool WHERE deleted=0 AND status<>'ARCHIVED' GROUP BY side, status",
    ),
    (
        "观察池更新时效",
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
        "观察池最近更新证券",
        """
        SELECT code, name, side, status, DATE(create_time), DATE(update_time), LEFT(reason,40)
        FROM observe_pool
        WHERE deleted=0 AND status<>'ARCHIVED'
        ORDER BY update_time DESC
        LIMIT 15
        """,
    ),
    (
        "最新股票池批次",
        """
        SELECT batch_no, COUNT(1), MIN(create_time), MAX(create_time)
        FROM universe_snapshot
        WHERE deleted=0 AND batch_no=(
          SELECT batch_no FROM universe_snapshot WHERE deleted=0 ORDER BY id DESC LIMIT 1
        )
        """,
    ),
    (
        "最近每日行动",
        """
        SELECT action_date, COUNT(1), MAX(create_time)
        FROM daily_action WHERE deleted=0
        GROUP BY action_date
        ORDER BY action_date DESC
        LIMIT 5
        """,
    ),
    (
        "最近日线同步任务",
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
    print("== 检查项：", title)
    try:
        cur.execute(sql)
        rows = cur.fetchall()
        if not rows:
            print("  （空）")
        for row in rows:
            print("  查询结果：", row)
    except Exception as e:
        print("  查询异常：", e)

# overlap of buy signal codes between latest 2 signal dates
print("== 最近两个日期的买入信号证券重合情况")
try:
    cur.execute(
        """
        SELECT DISTINCT signal_date FROM strategy_signal
        WHERE deleted=0 AND side='BUY'
        ORDER BY signal_date DESC LIMIT 2
        """
    )
    dates = [r[0] for r in cur.fetchall()]
    print("  日期=", dates)
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
            f"  {dates[0]}={len(a)}，{dates[1]}={len(b)}，重合数={len(inter)}，"
            f"新增数={len(a-b)}，移除数={len(b-a)}，重合比例={len(inter)/max(len(a),1):.2f}"
        )
        print("  新增样本=", list(a - b)[:10])
        print("  重合样本=", list(inter)[:10])
except Exception as e:
    print("  查询异常：", e)

print("当前时间=", datetime.now())
conn.close()
