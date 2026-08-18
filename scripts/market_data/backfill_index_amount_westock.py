# -*- coding: utf-8 -*-
"""Backfill index_bar.amount by code CN_SH/CN_SZ/CN_BJ50 from WeStock."""
from datetime import date

import pymysql

ROWS = {
    "CN_SH": {
        date(2026, 8, 3): 952256890000,
        date(2026, 7, 31): 1187681550000,
        date(2026, 7, 30): 1106477270000,
        date(2026, 7, 29): 1087407760000,
        date(2026, 7, 28): 949683080000,
        date(2026, 7, 27): 1031312140000,
        date(2026, 7, 24): 915366400000,
        date(2026, 7, 23): 1025875520000,
        date(2026, 7, 22): 1258148130000,
        date(2026, 7, 21): 1396517620000,
    },
    "CN_SZ": {
        date(2026, 8, 3): 1045128970000,
        date(2026, 7, 31): 1354266630000,
        date(2026, 7, 30): 1236331990000,
        date(2026, 7, 29): 1209171340000,
        date(2026, 7, 28): 1076097520000,
        date(2026, 7, 27): 1045308330000,
        date(2026, 7, 24): 1015773620000,
        date(2026, 7, 23): 1169425770000,
        date(2026, 7, 22): 1395196960000,
        date(2026, 7, 21): 1560575060000,
    },
    "CN_BJ50": {
        date(2026, 8, 3): 13894870500,
        date(2026, 7, 31): 18142358100,
        date(2026, 7, 30): 15846647400,
        date(2026, 7, 29): 15388501300,
        date(2026, 7, 28): 13524892300,
        date(2026, 7, 27): 12222975500,
        date(2026, 7, 24): 13538334300,
        date(2026, 7, 23): 14480815300,
        date(2026, 7, 22): 15474296200,
        date(2026, 7, 21): 17298954100,
    },
}


def main():
    conn = pymysql.connect(
        host="127.0.0.1",
        user="root",
        password="apex123",
        database="apex",
        charset="utf8mb4",
    )
    cur = conn.cursor()
    updated = 0
    for code, day_map in ROWS.items():
        for trade_date, amount in day_map.items():
            n = cur.execute(
                "UPDATE index_bar SET amount=%s WHERE code=%s AND trade_date=%s",
                (amount, code, trade_date),
            )
            updated += n
    conn.commit()
    cur.execute(
        "SELECT code, trade_date, amount FROM index_bar "
        "WHERE code IN ('CN_SH','CN_SZ','CN_BJ50') AND trade_date>='2026-07-21' "
        "ORDER BY code, trade_date DESC LIMIT 18"
    )
    for row in cur.fetchall():
        print("指数成交额记录：", row[0], row[1], row[2])
    print("更新行数=", updated)
    conn.close()


if __name__ == "__main__":
    main()
