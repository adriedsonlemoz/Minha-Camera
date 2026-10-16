#!/usr/bin/env python3
"""Prevent broken transmitter source IDs, analogous to the viewer preflight."""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'app/src/main/java/com/minhacamera/PhoneCameraActivity.kt'
RES=ROOT/'app/src/main/res'
ANDROID_ID='{http://schemas.android.com/apk/res/android}id'

def missing_view_ids():
    used=set(re.findall(r'\bR\.id\.([A-Za-z_][A-Za-z_0-9]*)',SOURCE.read_text()))
    declared=set()
    for path in RES.rglob('*.xml'):
        for element in ET.parse(path).iter():
            raw=element.attrib.get(ANDROID_ID,'')
            if raw.startswith('@+id/'): declared.add(raw[5:])
            if element.tag=='item' and element.get('type')=='id':
                declared.add(element.get('name',''))
    return sorted(used-declared)

if __name__=='__main__':
    missing=missing_view_ids()
    if missing:
        print('IDs ausentes no transmissor: '+', '.join(missing),file=sys.stderr)
        sys.exit(1)
    print('IDs do transmissor: todos os R.id declarados')
