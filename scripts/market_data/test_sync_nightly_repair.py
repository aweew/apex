import unittest
from unittest.mock import patch

import sync_nightly_repair


class NightlyRepairTest(unittest.TestCase):

    def test_builds_daily_bar_profile_and_fundamental_steps(self):
        steps = sync_nightly_repair.build_steps(
            expected_date="2026-08-17",
            start="20240101",
            bars_batch=80,
            bars_rounds=0,
            bars_max_minutes=150,
            profile_limit=300,
            fundamental_limit=60,
            stale_days=90,
        )

        self.assertEqual(["daily_bars", "company_profile", "fundamentals"], [step[0] for step in steps])
        self.assertIn("--expected-date", steps[0][2])
        self.assertIn("2026-08-17", steps[0][2])
        self.assertIn("--max-minutes", steps[0][2])
        self.assertIn("150", steps[0][2])
        self.assertIn("0", steps[0][2])
        self.assertIn("--missing", steps[1][2])
        self.assertIn("--missing", steps[2][2])
        self.assertIn("--no-resume", steps[2][2])

    def test_continues_remaining_steps_and_fails_at_end(self):
        steps = [
            ("daily_bars", "bars.py", []),
            ("company_profile", "profile.py", []),
            ("fundamentals", "fundamentals.py", []),
        ]

        with patch.object(
                sync_nightly_repair,
                "run_one",
                side_effect=[(1, "轮次 1 最终失败，退出码=1"), (0, ""), (2, "脚本不存在")],
        ) as run_one:
            exit_code = sync_nightly_repair.run_steps(steps)

        self.assertEqual(1, exit_code)
        self.assertEqual(3, run_one.call_count)

    def test_summarizes_successful_and_failed_steps(self):
        steps = [
            ("daily_bars", "bars.py", []),
            ("company_profile", "profile.py", []),
            ("fundamentals", "fundamentals.py", []),
        ]

        with patch.object(
                sync_nightly_repair,
                "run_one",
                side_effect=[(1, "完成，原因=已达时间预算，失败轮次=[1]，剩余缺口=80"), (0, ""), (0, "")],
        ), \
                patch("builtins.print") as print_mock:
            exit_code = sync_nightly_repair.run_steps(steps)

        self.assertEqual(1, exit_code)
        self.assertTrue(any(
            "成功步骤=company_profile,fundamentals，失败步骤=daily_bars，"
            "失败详情=daily_bars（完成，原因=已达时间预算，失败轮次=[1]，剩余缺口=80）" in str(call)
            for call in print_mock.call_args_list
        ))


if __name__ == "__main__":
    unittest.main()
