import unittest

from sync_news import source_result_exit_code


class SourceResultExitCodeTest(unittest.TestCase):

    def test_success_when_all_sources_succeed(self):
        self.assertEqual(0, source_result_exit_code(success_count=4))

    def test_success_when_at_least_one_source_succeeds(self):
        self.assertEqual(0, source_result_exit_code(success_count=1))

    def test_failure_when_all_sources_fail(self):
        self.assertEqual(1, source_result_exit_code(success_count=0))


if __name__ == "__main__":
    unittest.main()
