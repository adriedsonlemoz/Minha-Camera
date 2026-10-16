from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def test_dialog_layout_exists_with_switches_and_rows():
    layout = ROOT / 'app/src/main/res/layout/dialog_phone_more.xml'
    text = layout.read_text(encoding='utf-8')
    assert '@+id/moreLinkRow' in text
    assert '@+id/moreCodeRow' in text
    assert '@+id/moreOverlaySwitch' in text
    assert '@+id/moreEcoSwitch' in text
    assert '@+id/moreBackgroundSwitch' in text
    assert 'androidx.appcompat.widget.SwitchCompat' in text
    ET.fromstring(text)


def test_activity_uses_custom_more_dialog():
    kt = (ROOT / 'app/src/main/java/com/minhacamera/PhoneCameraActivity.kt').read_text(encoding='utf-8')
    assert 'R.layout.dialog_phone_more' in kt
    assert 'moreOverlayRow' in kt
    assert 'moreEcoRow' in kt
    assert 'moreBackgroundRow' in kt
    assert 'ColorDrawable(Color.TRANSPARENT)' in kt


def test_strings_include_new_dialog_copy_and_novidades():
    xml = (ROOT / 'app/src/main/res/values/strings.xml').read_text(encoding='utf-8')
    assert 'phone_more_dialog_subtitle' in xml
    assert 'phone_more_link_subtitle' in xml
    assert 'Versão 1.4.41 (código 51)' in xml
