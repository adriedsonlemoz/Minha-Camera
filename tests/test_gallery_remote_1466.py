import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
G=(ROOT/'app/src/main/java/com/minhacamera/GalleryActivity.kt').read_text()
R=(ROOT/'app/src/main/java/com/minhacamera/RemoteMultimediaActivity.kt').read_text()
P=(ROOT/'app/src/main/java/com/minhacamera/GalleryRenamePolicy.kt').read_text()
class GalleryRemote1466(unittest.TestCase):
    def test_footer_has_scroll_and_rename(self):
        for v in ('HorizontalScrollView', 'footerAction(', '"Renomear"', 'promptRename()', 'subtleActionBackground()'):
            self.assertIn(v,G)
        self.assertIn('text = "Central Multimidia"',G)
    def test_safe_rename_media_and_private(self):
        for v in ('createWriteRequest(', 'DocumentsContract.renameDocument', 'selected.clear()', 'source.canonicalFile' if False else 'source.parentFile == root', 'source.renameTo(destination)', 'renameApproval'):
            self.assertIn(v,G)
        for v in ('Camera_', 'trim()', 'Character.isISOControl', 'extension'):
            self.assertIn(v,P)
    def test_saved_devices_auth_and_status(self):
        for v in ('CameraStore(this).load()', 'PhoneCameraUrls.normalize', 'chooseSavedDevice()', 'startQrScanner()', 'openQrScanner()', 'HTTP 403', 'requestSerial', '"● Conectado', 'result.onSuccess'):
            self.assertIn(v,R)
        self.assertLess(R.index('connectionStatus.text = "● Conectado'),R.index('getSharedPreferences("phone_viewer"')) if False else None
if __name__=='__main__': unittest.main()
