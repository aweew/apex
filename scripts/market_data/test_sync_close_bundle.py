import argparse
import unittest
from pathlib import Path
from unittest.mock import patch

import sync_close_bundle


class CloseBundleStepsTest(unittest.TestCase):

    def test_index_step_syncs_only_a_share_indices(self):
        args = argparse.Namespace(
            types="INDUSTRY,CONCEPT,THEME",
            date="",
            hot_sources="eastmoney,baidu",
            news_sources="eastmoney,cls,ths,sina",
            hot_limit=50,
            news_limit=80,
        )

        steps = sync_close_bundle.build_steps(Path("/tmp/scripts"), args, "20260820")
        index_step = next(step for step in steps if step[0] == "index")

        self.assertEqual(
            ["--start", "20260820", "--regions", "CN", "--sleep", "0.25"],
            index_step[2],
        )

    def test_reports_partial_result_when_a_step_fails(self):
        args = argparse.Namespace(
            start="20260820",
            types="INDUSTRY",
            date="",
            hot_sources="eastmoney,baidu",
            news_sources="eastmoney,cls,ths,sina",
            hot_limit=50,
            news_limit=80,
            step_retries=1,
            continue_on_error=True,
            strict=False,
            skip="",
        )
        steps = [("index", Path("/tmp/index.py"), []), ("news", Path("/tmp/news.py"), [])]

        with patch.object(sync_close_bundle.argparse.ArgumentParser, "parse_args", return_value=args), \
                patch.object(sync_close_bundle, "build_steps", return_value=steps), \
                patch.object(Path, "is_file", return_value=True), \
                patch.object(sync_close_bundle, "run_with_retries", side_effect=[0, 1]), \
                patch("builtins.print") as print_mock:
            exit_code = sync_close_bundle.main()

        self.assertEqual(1, exit_code)
        self.assertTrue(any(
            "成功步骤=index，失败步骤=news" in str(call)
            for call in print_mock.call_args_list
        ))


if __name__ == "__main__":
    unittest.main()
