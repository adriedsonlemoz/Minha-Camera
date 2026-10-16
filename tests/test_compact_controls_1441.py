"""Testes de regressão da grade compacta do receptor v1.4.41."""
import re
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res/layout"
MAIN = (ROOT / "app/src/main/java/com/minhacamera/MainActivity.kt").read_text()
VIEWER = (ROOT / "app/src/main/java/com/minhacamera/PhoneCameraViewerActivity.kt").read_text()
NS = "{http://schemas.android.com/apk/res/android}"


def test_home_has_four_equal_shortcuts():
    root = ET.parse(RES / "activity_main.xml").getroot()
    ids = ("mainScan", "mainAdd", "phoneManual", "mainMedia")
    buttons = [next(e for e in root.iter() if e.attrib.get(NS+"id") == "@+id/"+name) for name in ids]
    assert len({b.attrib.get(NS+"layout_weight") for b in buttons}) == 1
    assert all(b.tag == "Button" and b.attrib.get(NS+"layout_width") == "0dp" for b in buttons)
    assert all(f"R.id.{name}" in MAIN for name in ids)


def test_device_has_four_actions_one_row():
    root = ET.parse(RES / "item_camera.xml").getroot()
    ids = ("btnOpen", "btnCopy", "btnEdit", "btnDelete")
    views = [next(e for e in root.iter() if e.attrib.get(NS+"id") == "@+id/"+name) for name in ids]
    # ElementTree não expõe pai; encontra o mesmo LinearLayout contendo as quatro ações.
    assert any([v.attrib.get(NS+"id") for v in row] == ["@+id/"+x for x in ids] for row in root.iter())
    assert all(v.attrib.get(NS+"layout_weight") == "1" for v in views)
    assert 'contentDescription = if (isPhoneCamera(camera))' in MAIN


def test_receiver_cards_and_all_click_handlers():
    root = ET.parse(RES / "activity_phone_viewer.xml").getroot()
    grid = next(e for e in root.iter() if e.attrib.get(NS+"id") == "@+id/phoneViewerControls")
    assert grid.tag == "GridLayout" and grid.attrib.get(NS+"columnCount") == "5"
    assert len(list(grid)) == 19
    assert all(tile.attrib.get(NS+"clickable") == "true" for tile in grid)
    for tile in grid:
        id_ = tile.attrib[NS+"id"].replace("@+id/", "")
        assert f"R.id.{id_}" in VIEWER
    for term in ('adaptControlColumns()', 'chooseRemoteQuality()', 'chooseRemoteFps()',
                 'chooseRemoteFacing()', 'setRemoteEco(', 'chooseViewerOrientation()',
                 'toggleVideoBatteryOverlay()', 'openTelemetryOptions()',
                 'toggleTechnicalDetails()', 'showRemoteDiagnostic()', 'chooseRemoteCodec()'):
        assert term in VIEWER
    assert 'widthDp < 318f -> 4' in VIEWER
    assert 'widthDp < 248f -> 3' in VIEWER
    assert 'private fun openMoreDialog()' not in VIEWER


def test_protocol_unchanged():
    assert 'PhoneCameraUrls.ecoCommandUrl' in VIEWER
    assert 'PhoneCameraUrls.recordingCommandUrl' in VIEWER
    assert 'RemoteMultimediaActivity::class.java' in VIEWER
    assert 'window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)' in VIEWER
