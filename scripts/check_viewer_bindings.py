#!/usr/bin/env python3
"""Confere referências R.id do receptor contra IDs declarados no projeto.

Previne compilação quebrada por controles removidos do XML mas ainda ligados
em Kotlin (como viewerFullPip na 1.4.55). Usado no preflight do Actions.
"""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/minhacamera/PhoneCameraViewerActivity.kt"
RES = ROOT / "app/src/main/res"
ANDROID_ID = "{http://schemas.android.com/apk/res/android}id"


def missing_view_ids():
    referenced = set(re.findall(r"\bR\.id\.([A-Za-z_][A-Za-z_0-9]*)", SOURCE.read_text()))
    declared = set()
    for path in RES.rglob("*.xml"):
        tree = ET.parse(path)
        for element in tree.iter():
            raw = element.attrib.get(ANDROID_ID, "")
            if raw.startswith("@+id/"):
                declared.add(raw[5:])
            if element.tag == "item" and element.attrib.get("type") == "id":
                name = element.attrib.get("name")
                if name:
                    declared.add(name)
    return sorted(referenced - declared)


if __name__ == "__main__":
    missing = missing_view_ids()
    if missing:
        print("IDs referenciados no receptor e ausentes nos recursos Android: " + ", ".join(missing), file=sys.stderr)
        sys.exit(1)
    print("IDs do receptor: todos os R.id possuem definição em recursos Android.")
