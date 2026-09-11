import json
import os
import unittest
from datetime import datetime
from http.server import BaseHTTPRequestHandler, HTTPServer
from threading import Thread
from unittest.mock import patch

from hithink_api import (
    HithinkApiClient,
    HithinkApiError,
    hithink_report_rows,
)


class HithinkApiTest(unittest.TestCase):

    def test_disabled_without_switch_or_key(self):
        old_enabled = os.environ.get("APEX_HITHINK_ENABLED")
        try:
            os.environ["APEX_HITHINK_ENABLED"] = "false"
            client = HithinkApiClient(api_key="test-key")
            self.assertFalse(client.enabled)
        finally:
            if old_enabled is None:
                os.environ.pop("APEX_HITHINK_ENABLED", None)
            else:
                os.environ["APEX_HITHINK_ENABLED"] = old_enabled

    def test_get_sends_api_key_and_checks_business_code(self):
        requests = []

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                requests.append((self.path, self.headers.get("X-api-key")))
                body = json.dumps({
                    "code": 0,
                    "request_id": "req-1",
                    "data": {"item": [{"thscode": "600519.SH"}]},
                }).encode()
                self.send_response(200)
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, *_):
                pass

        server = HTTPServer(("127.0.0.1", 0), Handler)
        thread = Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            client = HithinkApiClient(
                base_url=f"http://127.0.0.1:{server.server_port}",
                api_key="test-key",
            )
            data = client.get("/test", {"q": "600519"})
            self.assertEqual("600519.SH", data["item"][0]["thscode"])
            self.assertEqual("test-key", requests[0][1])
            self.assertIn("q=600519", requests[0][0])
        finally:
            server.shutdown()
            thread.join(timeout=2)
            server.server_close()

    def test_business_error_contains_request_id(self):
        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                body = b'{"code":2003,"request_id":"req-invalid","data":null}'
                self.send_response(200)
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, *_):
                pass

        server = HTTPServer(("127.0.0.1", 0), Handler)
        thread = Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            client = HithinkApiClient(
                base_url=f"http://127.0.0.1:{server.server_port}",
                api_key="test-key",
            )
            with self.assertRaisesRegex(HithinkApiError, "req-invalid"):
                client.get("/test", {})
        finally:
            server.shutdown()
            thread.join(timeout=2)
            server.server_close()

    def test_financial_rows_are_mapped_to_report_dates(self):
        rows = hithink_report_rows([{
            "thscode": "600519.SH",
            "period_end_ms": 1785369600000,
            "operating_income": 100,
        }])

        self.assertEqual("2026-07-30", rows[0]["报告期"])
        self.assertEqual(100, rows[0]["operating_income"])

    def test_list_a_share_stops_at_max_items(self):
        requests = []

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                requests.append(self.path)
                body = json.dumps({
                    "code": 0,
                    "data": {
                        "item": [
                            {"ticker": "600000", "exchange": "SH"},
                            {"ticker": "000001", "exchange": "SZ"},
                        ],
                    },
                }).encode()
                self.send_response(200)
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, *_):
                pass

        server = HTTPServer(("127.0.0.1", 0), Handler)
        thread = Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            client = HithinkApiClient(
                base_url=f"http://127.0.0.1:{server.server_port}",
                api_key="test-key",
            )
            rows = client.list_a_share(max_items=1)
            self.assertEqual(1, len(rows))
            self.assertEqual("600000", rows[0]["ticker"])
            self.assertEqual(1, len(requests))
        finally:
            server.shutdown()
            thread.join(timeout=2)
            server.server_close()

    def test_retries_rate_limit_but_not_invalid_key(self):
        requests = []

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                requests.append(self.path)
                if len(requests) < 3:
                    body = b'{"code":4001,"request_id":"req-rate","data":null}'
                else:
                    body = b'{"code":0,"data":{"item":[]}}'
                self.send_response(200)
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, *_):
                pass

        server = HTTPServer(("127.0.0.1", 0), Handler)
        thread = Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            client = HithinkApiClient(
                base_url=f"http://127.0.0.1:{server.server_port}",
                api_key="test-key",
            )
            with patch("hithink_api.time.sleep"):
                client.get("/test", {})
            self.assertEqual(3, len(requests))
        finally:
            server.shutdown()
            thread.join(timeout=2)
            server.server_close()

        requests.clear()

        class InvalidKeyHandler(BaseHTTPRequestHandler):
            def do_GET(self):
                requests.append(self.path)
                body = b'{"code":2003,"request_id":"req-invalid","data":null}'
                self.send_response(200)
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, *_):
                pass

        server = HTTPServer(("127.0.0.1", 0), InvalidKeyHandler)
        thread = Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            client = HithinkApiClient(
                base_url=f"http://127.0.0.1:{server.server_port}",
                api_key="test-key",
            )
            with patch("hithink_api.time.sleep"):
                with self.assertRaises(HithinkApiError):
                    client.get("/test", {})
            self.assertEqual(1, len(requests))
        finally:
            server.shutdown()
            thread.join(timeout=2)
            server.server_close()

    def test_historical_splits_windows_longer_than_ten_years(self):
        requests = []

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                requests.append(self.path)
                body = b'{"code":0,"data":{"item":[]}}'
                self.send_response(200)
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, *_):
                pass

        server = HTTPServer(("127.0.0.1", 0), Handler)
        thread = Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            client = HithinkApiClient(
                base_url=f"http://127.0.0.1:{server.server_port}",
                api_key="test-key",
            )
            client.historical("600519.SH", "20100101", "20260911")
            self.assertEqual(2, len(requests))
        finally:
            server.shutdown()
            thread.join(timeout=2)
            server.server_close()


if __name__ == "__main__":
    unittest.main()
