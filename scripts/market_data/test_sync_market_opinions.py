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

    def test_enrich_active_seats_only_marks_audited_seat_mapping(self):
        seat_rows = [{
            "subject_name": "国盛证券宁波桑田路证券营业部",
            "title": "龙虎榜活跃席位",
            "summary": "涉及：示例股份",
            "direction": "活跃席位",
            "url": "https://data.eastmoney.com/stock/hyyyb.html",
        }, {
            "subject_name": "中信证券上海分公司",
            "title": "龙虎榜活跃席位",
            "summary": "涉及：另一只股票",
            "direction": "活跃席位",
            "url": "https://data.eastmoney.com/stock/hyyyb.html",
        }]
        seat_mappings = [{
            "actor_code": "NINGBO_SANGTIANLU",
            "actor_name": "宁波桑田路",
            "seat_keyword": "宁波桑田路",
            "confidence": "SEAT_LABEL",
            "evidence_url": "https://example.test/evidence/ningbo-sangtianlu",
        }]

        enriched_rows = sync_market_opinions.enrich_active_seat_rows(seat_rows, seat_mappings)

        self.assertEqual("宁波桑田路", enriched_rows[0]["actor_name"])
        self.assertEqual("SEAT_LABEL", enriched_rows[0]["actor_confidence"])
        self.assertEqual("国盛证券宁波桑田路证券营业部", enriched_rows[0]["subject_name"])
        self.assertIsNone(enriched_rows[1]["actor_name"])

    def test_parse_public_account_feed_keeps_original_post_link_and_time(self):
        feed_xml = """
        <rss version="2.0"><channel><item>
          <title>市场复盘</title>
          <link>https://example.test/posts/1</link>
          <description>原帖摘要</description>
          <pubDate>Mon, 25 Aug 2026 08:00:00 +0800</pubDate>
        </item></channel></rss>
        """
        account = {
            "actor_code": "DEMO_KOL",
            "actor_name": "示例账号",
            "platform": "RSS",
            "account_url": "https://example.test/account",
            "feed_url": "https://example.test/feed.xml",
            "source_status": "READY",
        }

        rows = sync_market_opinions.parse_public_account_feed(account, feed_xml, datetime(2026, 8, 25, 9, 0))

        self.assertEqual(1, len(rows))
        self.assertEqual("KOL", rows[0]["opinion_type"])
        self.assertEqual("示例账号", rows[0]["actor_name"])
        self.assertEqual("https://example.test/posts/1", rows[0]["url"])
        self.assertEqual("https://example.test/account", rows[0]["actor_evidence_url"])

    def test_parse_public_account_feed_prefers_atom_alternate_post_link(self):
        feed_xml = """
        <feed xmlns="http://www.w3.org/2005/Atom"><entry>
          <title>盘面观察</title>
          <link rel="self" href="https://example.test/feed/entry-1" />
          <link rel="alternate" href="https://example.test/posts/2" />
          <updated>2026-08-25T08:00:00+08:00</updated>
        </entry></feed>
        """
        account = {
            "actor_code": "DEMO_KOL",
            "actor_name": "示例账号",
            "platform": "RSS",
            "account_url": "https://example.test/account",
            "feed_url": "https://example.test/feed.xml",
            "source_status": "READY",
        }

        rows = sync_market_opinions.parse_public_account_feed(account, feed_xml, datetime(2026, 8, 25, 9, 0))

        self.assertEqual("https://example.test/posts/2", rows[0]["url"])


if __name__ == "__main__":
    unittest.main()
