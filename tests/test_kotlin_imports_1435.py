"""Guarda pré-build de imports duplicados e nomes ambíguos no Kotlin."""
import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from scripts.check_kotlin_imports import import_problems  # noqa: E402


class KotlinImportTests(unittest.TestCase):
    def test_duplicate_android_systemclock(self):
        code = "import android.os.SystemClock\nimport android.os.Handler\nimport android.os.SystemClock\n"
        self.assertTrue(any("SystemClock" in p for p in import_problems(code)))

    def test_two_imports_with_same_simple_name(self):
        code = "import com.one.Handler\nimport android.os.Handler\n"
        self.assertTrue(any("ambíguo" in p for p in import_problems(code)))

    def test_aliased_import_is_allowed(self):
        code = "import com.one.Handler as OtherHandler\nimport android.os.Handler\n"
        self.assertEqual([], import_problems(code))

    def test_current_kotlin_sources_clean(self):
        subprocess.run([sys.executable, str(ROOT / "scripts/check_kotlin_imports.py")],
                       check=True, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)


if __name__ == "__main__":
    unittest.main()
