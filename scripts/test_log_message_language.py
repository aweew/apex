import ast
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCAN_ROOTS = (ROOT / "scripts", ROOT / "integrations")


def call_name(call: ast.Call) -> str:
    if isinstance(call.func, ast.Name):
        return call.func.id
    if isinstance(call.func, ast.Attribute):
        owner = call.func.value
        if isinstance(owner, ast.Name):
            return f"{owner.id}.{call.func.attr}"
    return ""


def literal_text(node: ast.AST) -> str:
    return "".join(
        child.value
        for child in ast.walk(node)
        if isinstance(child, ast.Constant) and isinstance(child.value, str)
    )


class LogMessageLanguageTest(unittest.TestCase):

    def test_python_logs_include_chinese_context(self):
        failures = []
        for scan_root in SCAN_ROOTS:
            for source_file in scan_root.rglob("*.py"):
                if source_file.name.startswith("test_"):
                    continue
                tree = ast.parse(source_file.read_text(encoding="utf-8"), filename=str(source_file))
                for node in ast.walk(tree):
                    if not isinstance(node, ast.Call):
                        continue
                    name = call_name(node)
                    if name != "print" and name not in {
                        "logging.debug",
                        "logging.info",
                        "logging.warning",
                        "logging.error",
                        "logging.exception",
                        "logging.critical",
                        "logger.debug",
                        "logger.info",
                        "logger.warning",
                        "logger.error",
                        "logger.exception",
                        "logger.critical",
                    }:
                        continue
                    text = "".join(literal_text(argument) for argument in node.args)
                    if not any("\u4e00" <= char <= "\u9fff" for char in text):
                        relative_path = source_file.relative_to(ROOT)
                        failures.append(f"{relative_path}:{node.lineno} -> {text or '动态日志'}")
        self.assertEqual([], failures, "发现没有中文上下文的 Python 日志")

    def test_shell_logs_include_chinese_context(self):
        failures = []
        for scan_root in SCAN_ROOTS:
            for source_file in scan_root.rglob("*.sh"):
                if source_file.name.endswith(".test.sh"):
                    continue
                for line_number, line in enumerate(source_file.read_text(encoding="utf-8").splitlines(), 1):
                    stripped = line.strip()
                    if not re.match(r"^(?:echo\b|print\()", stripped):
                        continue
                    if not any("\u4e00" <= char <= "\u9fff" for char in stripped):
                        relative_path = source_file.relative_to(ROOT)
                        failures.append(f"{relative_path}:{line_number} -> {stripped}")
        self.assertEqual([], failures, "发现没有中文上下文的 Shell 日志")


if __name__ == "__main__":
    unittest.main()
