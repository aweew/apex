import os
import sys
import tempfile
import types
import unittest
from datetime import date
from pathlib import Path
from unittest.mock import call, patch

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

    def fetchone(self):
        return self.rows[0] if self.rows else None


class FakeConnection:

    def __init__(self, rows):
        self.cursor_instance = FakeCursor(rows)
        self.rollback_count = 0

    def cursor(self):
        return self.cursor_instance

    def rollback(self):
        self.rollback_count += 1


class MissingBarSelectionTest(unittest.TestCase):

    def test_resolves_beijing_exchange_codes_before_shanghai_codes(self):
        self.assertEqual("BJ", sync_a_share.resolve_market("920176"))
        self.assertEqual("BJ", sync_a_share.resolve_market("830001"))
        self.assertEqual("SH", sync_a_share.resolve_market("900901"))

    def test_selects_stocks_with_too_few_or_stale_daily_bars(self):
        connection = FakeConnection([{"code": "000001"}, {"code": "600519"}])
        expected_date = date(2026, 8, 17)

        codes = sync_missing_bars.fetch_missing(connection, 80, 30, expected_date)

        self.assertEqual(["000001", "600519"], codes)
        self.assertIn("MAX(trade_date)", connection.cursor_instance.sql)
        self.assertIn("latest_trade_date < %s", connection.cursor_instance.sql)
        self.assertEqual((30, expected_date, 80), connection.cursor_instance.params)

    def test_selects_missing_stocks_after_persisted_cursor(self):
        connection = FakeConnection([{"code": "600519"}])
        expected_date = date(2026, 8, 17)

        codes = sync_missing_bars.fetch_missing(
            connection, 80, 30, expected_date, after_code="300750"
        )

        self.assertEqual(["600519"], codes)
        self.assertIn("t1.code > %s", connection.cursor_instance.sql)
        self.assertEqual((30, expected_date, "300750", 80), connection.cursor_instance.params)

    def test_counts_all_remaining_missing_stocks(self):
        connection = FakeConnection([{"missing_count": 17}])

        missing_count = sync_missing_bars.count_missing(
            connection, 30, date(2026, 8, 17)
        )

        self.assertEqual(17, missing_count)
        self.assertIn("COUNT(*) AS missing_count", connection.cursor_instance.sql)

    def test_progress_cursor_is_saved_atomically(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            progress_path = Path(temp_dir) / ".progress" / "missing_bars.json"
            with patch.object(sync_missing_bars, "PROGRESS_PATH", progress_path):
                sync_missing_bars.save_cursor("600519")

                self.assertEqual("600519", sync_missing_bars.load_cursor())
                self.assertFalse(progress_path.with_suffix(".tmp").exists())

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

    def test_daily_bar_sync_reports_failed_stock_details_at_end(self):
        connection = FakeConnection([])

        with patch.object(sync_a_share, "list_codes", return_value=["000001"]), \
                patch.object(sync_a_share, "load_progress", return_value={}), \
                patch.object(sync_a_share, "max_bar_date", return_value=None), \
                patch.object(sync_a_share, "fetch_hist_bars", side_effect=RuntimeError("数据源超时")), \
                patch.object(sync_a_share, "save_progress"), \
                patch.object(sync_a_share.time, "sleep"), \
                patch("builtins.print") as print_mock:
            exit_code = sync_a_share.sync_bars(
                connection, "20240101", "20260821", 0, None, False, False
            )

        self.assertEqual(1, exit_code)
        self.assertTrue(any(
            "失败详情：000001（数据源超时）" in str(call)
            for call in print_mock.call_args_list
        ))

    def test_beijing_exchange_does_not_fallback_to_sina_daily_bars(self):
        akshare_stub = types.SimpleNamespace(
            stock_zh_a_hist=lambda **kwargs: (_ for _ in ()).throw(RuntimeError("连接中断")),
            stock_zh_a_daily=lambda **kwargs: self.fail("北交所不应调用新浪日线"),
        )

        with patch.dict(sys.modules, {"akshare": akshare_stub}), \
                patch.object(sync_a_share.time, "sleep") as sleep_mock:
            with self.assertRaisesRegex(RuntimeError, "em:连接中断"):
                sync_a_share.fetch_hist_bars("920176", "20240101", "20260814")

        self.assertEqual(5, sleep_mock.call_count)

    def test_beijing_exchange_retries_eastmoney_without_proxy_after_proxy_failure(self):
        proxy_states = []

        def fetch_hist(**kwargs):
            proxy_states.append(all(
                key not in os.environ
                for key in ("HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "http_proxy", "https_proxy", "all_proxy")
            ))
            if len(proxy_states) == 1:
                raise RuntimeError("ProxyError: Remote end closed connection")
            return types.SimpleNamespace(empty=False)

        akshare_stub = types.SimpleNamespace(
            stock_zh_a_hist=fetch_hist,
            stock_zh_a_daily=lambda **kwargs: self.fail("北交所不应调用新浪日线"),
        )

        with patch.dict(os.environ, {
            "HTTPS_PROXY": "http://127.0.0.1:7890",
            "ALL_PROXY": "http://127.0.0.1:7890",
        }), \
                patch.dict(sys.modules, {"akshare": akshare_stub}), \
                patch.object(sync_a_share.time, "sleep"):
            _, source = sync_a_share.fetch_hist_bars("920176", "20240101", "20260814")
            self.assertEqual("http://127.0.0.1:7890", os.environ.get("HTTPS_PROXY"))

        self.assertEqual("akshare-em-direct", source)
        self.assertEqual([False, True], proxy_states)

    def test_beijing_exchange_retries_without_proxy_after_remote_disconnect(self):
        proxy_states = []

        def fetch_hist(**kwargs):
            proxy_states.append(all(
                key not in os.environ
                for key in ("HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "http_proxy", "https_proxy", "all_proxy")
            ))
            if len(proxy_states) == 1:
                raise RuntimeError(
                    "('Connection aborted.', RemoteDisconnected('Remote end closed connection without response'))"
                )
            return types.SimpleNamespace(empty=False)

        akshare_stub = types.SimpleNamespace(
            stock_zh_a_hist=fetch_hist,
            stock_zh_a_daily=lambda **kwargs: self.fail("北交所不应调用新浪日线"),
        )

        with patch.dict(os.environ, {
            "HTTPS_PROXY": "http://127.0.0.1:7890",
            "ALL_PROXY": "http://127.0.0.1:7890",
        }), \
                patch.dict(sys.modules, {"akshare": akshare_stub}), \
                patch.object(sync_a_share.time, "sleep"):
            _, source = sync_a_share.fetch_hist_bars("920176", "20240101", "20260825")

        self.assertEqual("akshare-em-direct", source)
        self.assertEqual([False, True], proxy_states)

    def test_missing_bar_round_failure_does_not_block_later_rounds(self):
        args = types.SimpleNamespace(
            batch=80,
            rounds=2,
            max_minutes=0,
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
                patch.object(sync_missing_bars, "count_missing", return_value=17), \
                patch.object(sync_missing_bars, "load_cursor", return_value=""), \
                patch.object(sync_missing_bars, "save_cursor") as save_cursor, \
                patch.object(sync_missing_bars.subprocess, "call", side_effect=[1, 0]) as subprocess_call:
            exit_code = sync_missing_bars.main()

        self.assertEqual(1, exit_code)
        self.assertEqual(2, subprocess_call.call_count)
        self.assertEqual([call("000001"), call("600519")], save_cursor.call_args_list)

    def test_missing_bar_cursor_wraps_and_marks_remaining_gap_incomplete(self):
        args = types.SimpleNamespace(
            batch=80,
            rounds=1,
            max_minutes=0,
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
                patch.object(sync_missing_bars, "load_cursor", return_value="600519"), \
                patch.object(sync_missing_bars, "fetch_missing", side_effect=[[], ["000001"]]) as fetch_missing, \
                patch.object(sync_missing_bars, "count_missing", return_value=16), \
                patch.object(sync_missing_bars, "save_cursor") as save_cursor, \
                patch.object(sync_missing_bars.subprocess, "call", return_value=0), \
                patch("builtins.print") as print_mock:
            exit_code = sync_missing_bars.main()

        self.assertEqual(1, exit_code)
        self.assertEqual("600519", fetch_missing.call_args_list[0].kwargs["after_code"])
        self.assertIsNone(fetch_missing.call_args_list[1].kwargs["after_code"])
        save_cursor.assert_called_once_with("000001")
        self.assertTrue(any("剩余缺口=16" in str(call) for call in print_mock.call_args_list))

    def test_time_budget_stops_after_current_round_and_preserves_cursor(self):
        args = types.SimpleNamespace(
            batch=80,
            rounds=0,
            max_minutes=1,
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
                patch.object(sync_missing_bars, "load_cursor", return_value=""), \
                patch.object(sync_missing_bars, "fetch_missing", return_value=["300750"]), \
                patch.object(sync_missing_bars, "count_missing", return_value=15), \
                patch.object(sync_missing_bars, "save_cursor") as save_cursor, \
                patch.object(sync_missing_bars.subprocess, "call", return_value=0) as subprocess_call, \
                patch.object(sync_missing_bars.time, "monotonic", side_effect=[0, 0, 0, 61]), \
                patch("builtins.print") as print_mock:
            exit_code = sync_missing_bars.main()

        self.assertEqual(1, exit_code)
        subprocess_call.assert_called_once()
        save_cursor.assert_called_once_with("300750")
        self.assertTrue(any("时间预算" in str(call) for call in print_mock.call_args_list))


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
