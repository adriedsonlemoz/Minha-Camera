#!/usr/bin/env python3
"""Preflight: use setFillViewport or isFillViewport, not fillViewport in Kotlin Android.

The Kotlin synthetic property for Java isFillViewport/setFillViewport is named
isFillViewport, not fillViewport. The Java setter is unambiguous for both axes.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
BAD = re.compile(r"(?m)^\s*(?:this\.)?fillViewport\s*=")

def find_invalid(text: str):
    return [text.count("\n", 0, m.start()) + 1 for m in BAD.finditer(text)]

def main():
    failures = []
    for path in (ROOT / "app/src/main/java").rglob("*.kt"):
        for number in find_invalid(path.read_text(encoding="utf-8")):
            failures.append(f"{path.relative_to(ROOT)}:{number}: fillViewport: use setFillViewport(...) or isFillViewport")
    if failures:
        print("\n".join(failures), file=sys.stderr)
        return 1
    print("Propriedades de preenchimento dos ScrollViews validadas.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
