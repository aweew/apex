import unittest
from decimal import Decimal
import sys
import types
from unittest.mock import patch

pymysql_stub = types.ModuleType("pymysql")
pymysql_cursors_stub = types.ModuleType("pymysql.cursors")
pymysql_cursors_stub.DictCursor = object
pymysql_stub.cursors = pymysql_cursors_stub
sys.modules.setdefault("pymysql", pymysql_stub)
sys.modules.setdefault("pymysql.cursors", pymysql_cursors_stub)

import sync_sector


class SyncSectorConstituentTest(unittest.TestCase):

    def test_fetch_constituents_prefers_direct_eastmoney_client(self):
        expected = [{
            "stock_code": "300313",
            "stock_name": "天山生物",
            "pct_chg": Decimal("20.02"),
            "latest_price": Decimal("10.73"),
        }]

        with patch.object(sync_sector, "_fetch_em_constituents", return_value=expected) as direct_fetch:
            result = sync_sector.fetch_constituents("INDUSTRY", "BK1510", "其他养殖")

        self.assertEqual(expected, result)
        direct_fetch.assert_called_once_with("BK1510")

    def test_parse_direct_constituent_fields(self):
        result = sync_sector._constituent_row_from_em_item({
            "f2": 10.73,
            "f3": 20.02,
            "f12": "300313",
            "f14": "天山生物",
        })

        self.assertEqual("300313", result["stock_code"])
        self.assertEqual("天山生物", result["stock_name"])
        self.assertEqual(Decimal("10.73"), result["latest_price"])
        self.assertEqual(Decimal("20.02"), result["pct_chg"])

    def test_direct_constituent_parser_rejects_non_stock_codes(self):
        result = sync_sector._constituent_row_from_em_item({
            "f2": 36.0,
            "f3": 300.0,
            "f12": "MO2608-C-8100",
            "f14": "中证1000购26年8月8100",
        })

        self.assertIsNone(result)


if __name__ == "__main__":
    unittest.main()
