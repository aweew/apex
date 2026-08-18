import sys
import types
import unittest

try:
    import pymysql  # noqa: F401
except ImportError:
    pymysql_stub = types.ModuleType("pymysql")
    pymysql_cursors_stub = types.ModuleType("pymysql.cursors")
    pymysql_cursors_stub.DictCursor = object
    pymysql_stub.cursors = pymysql_cursors_stub
    sys.modules["pymysql"] = pymysql_stub
    sys.modules["pymysql.cursors"] = pymysql_cursors_stub

import backfill_turnover


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

    def cursor(self):
        return self.cursor_instance


class TurnoverCodeSelectionTest(unittest.TestCase):

    def test_selects_only_registered_stocks_with_missing_turnover(self):
        connection = FakeConnection([{"code": "000032"}, {"code": "600519"}])

        codes = backfill_turnover.list_codes(connection, None, 50, only_missing=True)

        self.assertEqual(["000032", "600519"], codes)
        self.assertIn("FROM stock_basic t1", connection.cursor_instance.sql)
        self.assertIn("INNER JOIN bar_daily t2", connection.cursor_instance.sql)
        self.assertIn("t2.turnover_rate IS NULL", connection.cursor_instance.sql)
        self.assertEqual((50,), connection.cursor_instance.params)

    def test_excludes_benchmark_from_explicit_codes(self):
        connection = FakeConnection([{"code": "600519"}])

        codes = backfill_turnover.list_codes(
            connection,
            ["000300", "600519", "600519"],
            0,
            only_missing=True,
        )

        self.assertEqual(["600519"], codes)
        self.assertIn("t1.code IN (%s,%s)", connection.cursor_instance.sql)
        self.assertEqual(("000300", "600519"), connection.cursor_instance.params)

    def test_include_filled_still_requires_registered_stock(self):
        connection = FakeConnection([{"code": "300750"}])

        codes = backfill_turnover.list_codes(connection, None, 0, only_missing=False)

        self.assertEqual(["300750"], codes)
        self.assertNotIn("turnover_rate IS NULL", connection.cursor_instance.sql)
        self.assertEqual((), connection.cursor_instance.params)


if __name__ == "__main__":
    unittest.main()
