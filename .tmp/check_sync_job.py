# -*- coding: utf-8 -*-
import os
import sys
from pathlib import Path

import pymysql

sys.stdout.reconfigure(encoding="utf-8")
try:
    from dotenv import load_dotenv

    load_dotenv(Path(r"D:/code/apex/scripts/market_data") / ".env")
except Exception:
    pass

conn = pymysql.connect(
    host=os.getenv("MYSQL_HOST", "127.0.0.1"),
    port=int(os.getenv("MYSQL_PORT", "3306")),
    user=os.getenv("MYSQL_USER", "root"),
    password=os.getenv("MYSQL_PASSWORD", "apex123"),
    database=os.getenv("MYSQL_DB", "apex"),
    charset="utf8mb4",
)
cur = conn.cursor()
cur.execute("SHOW COLUMNS FROM sync_job")
cols = [r[0] for r in cur.fetchall()]
print("cols", cols)
cur.execute(
    "SELECT id, task_type, status, exit_code, message, left(ifnull(log_tail,''), 1200), "
    "started_at, finished_at FROM sync_job ORDER BY id DESC LIMIT 8"
)
for r in cur.fetchall():
    print("---")
    print("id", r[0], "type", r[1], "status", r[2], "exit", r[3])
    print("msg", r[4])
    print("log", r[5])
    print("time", r[6], r[7])
conn.close()
