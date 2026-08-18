import sys
import types
import unittest
from datetime import date
from unittest.mock import patch

try:
    import pymysql  # noqa: F401
except ImportError:
    pymysql_stub = types.ModuleType("pymysql")
    pymysql_cursors_stub = types.ModuleType("pymysql.cursors")
    pymysql_cursors_stub.DictCursor = object
    pymysql_stub.cursors = pymysql_cursors_stub
    sys.modules["pymysql"] = pymysql_stub
    sys.modules["pymysql.cursors"] = pymysql_cursors_stub

import sync_company_profile
import sync_a_share
import sync_fundamentals
import sync_missing_bars


class FakeCursor:

    def __init__(self, rows):
        self.rows = rows
        self.sql = ""
        self.params = ()

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        return False

    def execute(self, sql, params=()):
        self.sql = sql
        self.params = params

    def fetchall(self):
        return self.rows


class FakeConnection:

    def __init__(self, rows):
        self.cursor_instance = FakeCursor(rows)
        self.rollback_count = 0

    def cursor(self):
        return self.cursor_instance

    def rollback(self):
        self.rollback_count += 1


class MissingBarSelectionTest(unittest.TestCase):

    def test_selects_stocks_with_too_few_or_stale_daily_bars(self):
        connection = FakeConnection([{"code": "000001"}, {"code": "600519"}])
        expected_date = date(2026, 8, 17)

        codes = sync_missing_bars.fetch_missing(connection, 80, 30, expected_date)

        self.assertEqual(["000001", "600519"], codes)
        self.assertIn("MAX(trade_date)", connection.cursor_instance.sql)
        self.assertIn("latest_trade_date < %s", connection.cursor_instance.sql)
        self.assertEqual((30, expected_date, 80), connection.cursor_instance.params)

    def test_daily_bar_process_returns_failure_when_any_stock_fails(self):
        args = types.SimpleNamespace(
            codes="000001",
            mode="bars",
            limit=None,
            start="20240101",
            end="20260817",
            sleep=0,
            no_resume=True,
            full_refresh=False,
        )
        connection = FakeConnection([])
        connection.close = lambda: None

        with patch.object(sync_a_share, "load_env"), \
                patch.object(sync_a_share, "parse_args", return_value=args), \
                patch.object(sync_a_share, "db_conn", return_value=connection), \
                patch.object(sync_a_share, "sync_bars", return_value=1):
            exit_code = sync_a_share.main()

        self.assertEqual(1, exit_code)

    def test_missing_bar_round_failure_does_not_block_later_rounds(self):
        args = types.SimpleNamespace(
            batch=80,
            rounds=2,
            start="20240101",
            sleep=0,
            min_bars=30,
            expected_date="2026-08-17",
        )
        connection = FakeConnection([])
        connection.close = lambda: None

        with patch.object(sync_missing_bars.argparse.ArgumentParser, "parse_args", return_value=args), \
                patch.object(sync_missing_bars, "load_env"), \
                patch.object(sync_missing_bars, "db_conn", return_value=connection), \
                patch.object(sync_missing_bars, "fetch_missing", side_effect=[["000001"], ["600519"]]), \
                patch.object(sync_missing_bars.subprocess, "call", side_effect=[1, 0]) as subprocess_call:
            exit_code = sync_missing_bars.main()

        self.assertEqual(1, exit_code)
        self.assertEqual(2, subprocess_call.call_count)


class MissingCompanyProfileSelectionTest(unittest.TestCase):

    def test_selects_absent_incomplete_or_stale_company_profiles(self):
        connection = FakeConnection([{"code": "300750"}])

        codes = sync_company_profile.list_missing_codes(connection, limit=100, stale_days=90)

        self.assertEqual(["300750"], codes)
        self.assertIn("LEFT JOIN stock_company_profile", connection.cursor_instance.sql)
        self.assertIn("main_business IS NULL", connection.cursor_instance.sql)
        self.assertIn("DATE_SUB(NOW(), INTERVAL %s DAY)", connection.cursor_instance.sql)
        self.assertEqual((90, 100), connection.cursor_instance.params)


class MissingFundamentalSelectionTest(unittest.TestCase):

    def test_selects_stocks_missing_any_fundamental_dataset(self):
        connection = FakeConnection([{"code": "601318"}])

        codes = sync_fundamentals.list_missing_codes(connection, limit=50)

        self.assertEqual(["601318"], codes)
        sql = connection.cursor_instance.sql
        self.assertIn("stock_fin_indicator", sql)
        self.assertIn("stock_fin_abstract", sql)
        self.assertIn("statement_type = 'profit'", sql)
        self.assertIn("statement_type = 'balance'", sql)
        self.assertIn("statement_type = 'cashflow'", sql)
        self.assertEqual((50,), connection.cursor_instance.params)

    def test_missing_mode_succeeds_when_no_gap_remains(self):
        args = types.SimpleNamespace(
            codes="",
            limit=0,
            mode="all",
            sleep=0,
            missing=True,
            no_resume=True,
        )
        connection = FakeConnection([])
        connection.close = lambda: None

        with patch.object(sync_fundamentals, "load_env"), \
                patch.object(sync_fundamentals.argparse.ArgumentParser, "parse_args", return_value=args), \
                patch.object(sync_fundamentals, "load_progress", return_value={}), \
                patch.object(sync_fundamentals, "db_conn", return_value=connection), \
                patch.object(sync_fundamentals, "list_missing_codes", return_value=[]):
            exit_code = sync_fundamentals.main()

        self.assertEqual(0, exit_code)

    def test_run_mode_reports_failed_stock_count(self):
        connection = FakeConnection([])

        with patch.object(sync_fundamentals, "sync_indicator", side_effect=RuntimeError("timeout")), \
                patch.object(sync_fundamentals, "mark_done"), \
                patch.object(sync_fundamentals.time, "sleep"):
            failed = sync_fundamentals.run_mode(
                connection,
                "indicator",
                ["000001"],
                0,
                False,
                {},
            )

        self.assertEqual(1, failed)
        self.assertEqual(1, connection.rollback_count)


if __name__ == "__main__":
    unittest.main()
