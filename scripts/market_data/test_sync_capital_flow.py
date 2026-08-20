import sys
import types
import unittest
from contextlib import redirect_stdout
from datetime import date
from decimal import Decimal
from io import StringIO
from unittest.mock import patch

pymysql_stub = types.ModuleType("pymysql")
pymysql_cursors_stub = types.ModuleType("pymysql.cursors")
pymysql_cursors_stub.DictCursor = object
pymysql_stub.cursors = pymysql_cursors_stub
sys.modules.setdefault("pymysql", pymysql_stub)
sys.modules.setdefault("pymysql.cursors", pymysql_cursors_stub)

import sync_capital_flow


class FakeFrame:

    def __init__(self, rows):
        self.rows = rows
        self.columns = list(rows[0].keys()) if rows else []
        self.empty = not rows

    def iterrows(self):
        for index, row in enumerate(self.rows):
            yield index, row


class FakeCursor:

    def __init__(self, fail=False):
        self.fail = fail
        self.executemany_calls = []

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        return False

    def executemany(self, sql, params):
        if self.fail:
            raise RuntimeError("database unavailable")
        self.executemany_calls.append((sql, params))


class FakeConnection:

    def __init__(self, fail=False):
        self.fake_cursor = FakeCursor(fail=fail)
        self.commit_count = 0
        self.rollback_count = 0

    def cursor(self):
        return self.fake_cursor

    def commit(self):
        self.commit_count += 1

    def rollback(self):
        self.rollback_count += 1


class FakeAkshare:

    def __init__(self, northbound=None, stock_flow=None, dragon_tiger=None):
        self.northbound = northbound or FakeFrame([])
        self.stock_flow = stock_flow or FakeFrame([])
        self.dragon_tiger = dragon_tiger or FakeFrame([])
        self.calls = []

    def stock_hsgt_hist_em(self, **kwargs):
        self.calls.append(("northbound", kwargs))
        return self.northbound

    def stock_individual_fund_flow_rank(self, **kwargs):
        self.calls.append(("stock_flow", kwargs))
        return self.stock_flow

    def stock_lhb_detail_em(self, **kwargs):
        self.calls.append(("dragon_tiger", kwargs))
        return self.dragon_tiger


class SyncCapitalFlowParseTest(unittest.TestCase):

    def test_parse_northbound_supports_column_aliases_and_converts_yi_to_yuan(self):
        frame = FakeFrame([
            {
                "交易日期": "2026-08-20",
                "当日净买额": "1.25",
                "买入额": 3,
                "卖出额": "1.75",
                "累计净买额": "200.5",
            },
            {
                "交易日期": "2026-08-21",
                "当日净买额": "--",
                "买入额": "--",
                "卖出额": None,
                "累计净买额": "201亿",
            },
        ])

        rows = sync_capital_flow.parse_northbound_rows(frame)

        self.assertEqual(date(2026, 8, 20), rows[0]["trade_date"])
        self.assertEqual(Decimal("125000000.00"), rows[0]["net_buy_amount"])
        self.assertEqual(Decimal("300000000.00"), rows[0]["buy_amount"])
        self.assertEqual(Decimal("175000000.00"), rows[0]["sell_amount"])
        self.assertEqual("PUBLISHED", rows[0]["data_status"])
        self.assertIsNone(rows[1]["net_buy_amount"])
        self.assertEqual(Decimal("20100000000.00"), rows[1]["cumulative_net_buy_amount"])
        self.assertEqual("NOT_DISCLOSED", rows[1]["data_status"])

    def test_parse_stock_flow_supports_prefixed_columns_and_keeps_yuan_amounts(self):
        frame = FakeFrame([{
            "股票代码": "600519",
            "股票简称": "贵州茅台",
            "今日涨跌幅": "1.23%",
            "今日主力净流入-净额": "1.2亿",
            "今日主力净流入-净占比": "3.5%",
            "今日超大单净流入-净额": 30000000,
            "今日大单净流入-净额": "2500万",
            "今日中单净流入-净额": -18000000,
            "今日小单净流入-净额": "-1700万",
        }])

        rows = sync_capital_flow.parse_stock_fund_flow_rows(frame, date(2026, 8, 21))

        self.assertEqual("600519", rows[0]["code"])
        self.assertEqual("贵州茅台", rows[0]["name"])
        self.assertEqual(Decimal("1.23"), rows[0]["pct_chg"])
        self.assertEqual(Decimal("120000000.00"), rows[0]["main_net_inflow"])
        self.assertEqual(Decimal("3.5"), rows[0]["main_net_inflow_pct"])
        self.assertEqual(Decimal("30000000.00"), rows[0]["super_large_net_inflow"])
        self.assertEqual(Decimal("25000000.00"), rows[0]["large_net_inflow"])

    def test_parse_dragon_tiger_supports_column_aliases_and_keeps_yuan_amounts(self):
        frame = FakeFrame([{
            "代码": "000001",
            "名称": "平安银行",
            "上榜日": "2026-08-21",
            "解读": "日涨幅偏离值达7%",
            "收盘价": "12.34",
            "涨跌幅": "8.2%",
            "换手率": "9.1%",
            "龙虎榜净买入额": "8000万",
            "买入额": 100000000,
            "卖出额": 20000000,
            "龙虎榜成交额": "1.2亿",
        }])

        rows = sync_capital_flow.parse_dragon_tiger_rows(frame)

        self.assertEqual("000001", rows[0]["code"])
        self.assertEqual(date(2026, 8, 21), rows[0]["trade_date"])
        self.assertEqual("日涨幅偏离值达7%", rows[0]["reason"])
        self.assertEqual(Decimal("80000000.00"), rows[0]["net_buy_amount"])
        self.assertEqual(Decimal("120000000.00"), rows[0]["amount"])


class SyncCapitalFlowTransactionTest(unittest.TestCase):

    def test_trade_date_rolls_back_across_market_holiday(self):
        self.assertEqual(
            date(2026, 9, 30),
            sync_capital_flow.recent_trade_date(date(2026, 10, 2)),
        )

    def test_empty_source_does_not_write_or_commit(self):
        connection = FakeConnection()
        akshare_client = FakeAkshare(northbound=FakeFrame([]))

        row_count = sync_capital_flow.sync_northbound_flow(connection, akshare_client)

        self.assertEqual(0, row_count)
        self.assertEqual([], connection.fake_cursor.executemany_calls)
        self.assertEqual(0, connection.commit_count)
        self.assertEqual(0, connection.rollback_count)

    def test_dataset_failure_rolls_back_its_transaction(self):
        frame = FakeFrame([{
            "日期": "2026-08-21",
            "当日成交净买额": 1,
            "买入成交额": 2,
            "卖出成交额": 1,
            "历史累计净买额": 10,
        }])
        connection = FakeConnection(fail=True)

        with self.assertRaisesRegex(RuntimeError, "database unavailable"):
            sync_capital_flow.sync_northbound_flow(connection, FakeAkshare(northbound=frame))

        self.assertEqual(0, connection.commit_count)
        self.assertEqual(1, connection.rollback_count)

    def test_not_disclosed_northbound_row_is_still_written(self):
        frame = FakeFrame([{
            "日期": "2026-08-21",
            "当日成交净买额": None,
            "买入成交额": None,
            "卖出成交额": None,
            "历史累计净买额": None,
        }])
        connection = FakeConnection()

        row_count = sync_capital_flow.sync_northbound_flow(
            connection,
            FakeAkshare(northbound=frame),
        )

        self.assertEqual(1, row_count)
        self.assertEqual(1, connection.commit_count)
        params = connection.fake_cursor.executemany_calls[0][1]
        self.assertEqual(
            (date(2026, 8, 21), None, None, None, None, "NOT_DISCLOSED"),
            params[0][:6],
        )

    def test_northbound_only_writes_latest_trade_date(self):
        frame = FakeFrame([
            {
                "日期": "2026-08-20",
                "当日成交净买额": 1,
            },
            {
                "日期": "2026-08-21",
                "当日成交净买额": None,
            },
        ])
        connection = FakeConnection()

        row_count = sync_capital_flow.sync_northbound_flow(
            connection,
            FakeAkshare(northbound=frame),
        )

        self.assertEqual(1, row_count)
        params = connection.fake_cursor.executemany_calls[0][1]
        self.assertEqual(1, len(params))
        self.assertEqual(date(2026, 8, 21), params[0][0])
        self.assertEqual("NOT_DISCLOSED", params[0][5])

    def test_successful_dataset_commits_once(self):
        frame = FakeFrame([{
            "股票代码": "600519",
            "股票简称": "贵州茅台",
            "今日主力净流入-净额": 100,
        }])
        connection = FakeConnection()

        row_count = sync_capital_flow.sync_stock_fund_flow(
            connection,
            FakeAkshare(stock_flow=frame),
            trade_date=date(2026, 8, 21),
        )

        self.assertEqual(1, row_count)
        self.assertEqual(1, connection.commit_count)
        self.assertEqual(0, connection.rollback_count)

    def test_all_mode_continues_after_one_dataset_fails(self):
        connection = FakeConnection()
        akshare_client = FakeAkshare()
        with patch.object(sync_capital_flow, "sync_northbound_flow", side_effect=RuntimeError("timeout")) as northbound, \
                patch.object(sync_capital_flow, "sync_stock_fund_flow", return_value=2) as stock_flow, \
                patch.object(sync_capital_flow, "sync_dragon_tiger", return_value=3) as dragon_tiger:
            result = sync_capital_flow.run_mode(connection, akshare_client, "all")

        self.assertEqual({"stock_fund_flow": 2, "dragon_tiger": 3}, result["counts"])
        self.assertEqual(["northbound_flow"], list(result["errors"]))
        northbound.assert_called_once()
        stock_flow.assert_called_once()
        dragon_tiger.assert_called_once()

    def test_run_mode_prints_partial_source_counts(self):
        connection = FakeConnection()
        output = StringIO()
        with patch.object(sync_capital_flow, "sync_northbound_flow", return_value=1), \
                patch.object(sync_capital_flow, "sync_stock_fund_flow", side_effect=RuntimeError("timeout")), \
                redirect_stdout(output):
            sync_capital_flow.run_mode(connection, FakeAkshare(), "flow")

        self.assertIn("成功数据源数=1，失败数据源数=1", output.getvalue())

    def test_mode_selects_expected_datasets(self):
        self.assertEqual(
            ("stock_fund_flow",),
            sync_capital_flow.dataset_names("stock"),
        )
        self.assertEqual(
            ("northbound_flow", "stock_fund_flow"),
            sync_capital_flow.dataset_names("flow"),
        )
        self.assertEqual(("dragon_tiger",), sync_capital_flow.dataset_names("lhb"))
        self.assertEqual(
            ("northbound_flow", "stock_fund_flow", "dragon_tiger"),
            sync_capital_flow.dataset_names("all"),
        )


if __name__ == "__main__":
    unittest.main()
