import unittest
import sys
import types
from datetime import datetime
from unittest.mock import patch

if "pymysql" not in sys.modules:
    pymysql_module = types.ModuleType("pymysql")
    cursors_module = types.ModuleType("pymysql.cursors")
    cursors_module.DictCursor = object
    pymysql_module.cursors = cursors_module
    sys.modules["pymysql"] = pymysql_module
    sys.modules["pymysql.cursors"] = cursors_module

import sync_market_opinions


class MarketOpinionSyncTest(unittest.TestCase):

    @patch("sync_market_opinions.request_json")
    def test_fetch_institution_reports_keeps_rating_and_pdf_source(self, request_json):
        request_json.return_value = {
            "data": [{
                "title": "半年度业绩点评",
                "orgSName": "中信证券",
                "infoCode": "AP202608250001",
                "publishDate": "2026-08-25 00:00:00.000",
                "sRatingName": "买入",
                "stockCode": "000001",
                "stockName": "平安银行",
                "indvInduName": "银行",
            }]
        }

        rows = sync_market_opinions.fetch_institution_reports(80, datetime(2026, 8, 25, 7, 0))

        self.assertEqual(1, len(rows))
        self.assertEqual("INSTITUTION", rows[0]["opinion_type"])
        self.assertEqual("中信证券", rows[0]["subject_name"])
        self.assertEqual("买入", rows[0]["direction"])
        self.assertEqual("https://pdf.dfcfw.com/pdf/H3_AP202608250001_1.pdf", rows[0]["url"])

    @patch("sync_market_opinions.request_json")
    def test_fetch_active_seats_keeps_public_behavior_without_person_mapping(self, request_json):
        request_json.return_value = {
            "result": {"data": [{
                "OPERATEDEPT_NAME": "中信证券上海分公司",
                "OPERATEDEPT_CODE": "10001",
                "ONLIST_DATE": "2026-08-25 00:00:00",
                "TOTAL_NETAMT": 201000000,
                "SECURITY_NAME_ABBR": "示例股份",
            }]}
        }

        rows = sync_market_opinions.fetch_active_seats(80, datetime(2026, 8, 25, 18, 0))

        self.assertEqual(1, len(rows))
        self.assertEqual("ACTIVE_SEAT", rows[0]["opinion_type"])
        self.assertEqual("活跃席位", rows[0]["direction"])
        self.assertEqual("涉及：示例股份", rows[0]["summary"])
        _, request_params = request_json.call_args.args
        self.assertEqual("(ONLIST_DATE>='2026-08-20')(ONLIST_DATE<='2026-08-25')", request_params["filter"])


if __name__ == "__main__":
    unittest.main()
