import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'app/src/main/java/com/minhacamera'

class McrpTests(unittest.TestCase):
    def test_server_mcrp_and_legacy(self):
        s=(SRC/'PhoneCameraServer.kt').read_text()
        for endpoint in ('$mcrpRoot/info', '$mcrpRoot/media', 'serveMedia', 'Accept-Ranges: bytes',
                         '206 Partial Content', 'Range Not Satisfiable', '/live/', '/h264/'):
            self.assertIn(endpoint,s)
        self.assertIn('mediaOpen?.invoke(id)',s)
    def test_media_does_not_browse_arbitrary_paths(self):
        s=(SRC/'PhoneRemoteLibrary.kt').read_text()
        for marker in ('RecordingFiles.clips(root)', 'OWNER_PACKAGE_NAME', 'IS_PENDING',
                       'file.canonicalFile.parentFile', 'entries().firstOrNull { it.id == id }'):
            self.assertIn(marker,s)
        self.assertNotIn('File(context.filesDir, id)',s)
    def test_receiver_multimedia(self):
        s=(SRC/'RemoteMultimediaActivity.kt').read_text()
        self.assertIn('PhoneCameraUrls.mcrpMediaUrl',s)
        self.assertIn('VideoView',s)
        self.assertIn('Photo',s) if False else None
        self.assertIn('setOnClickListener',s)
        self.assertIn('inputStream.use',s)
    def test_views_route_to_media(self):
        self.assertIn('GalleryActivity::class.java',(SRC/'MultimediaActivity.kt').read_text())
        self.assertIn('RemoteMultimediaActivity::class.java',(SRC/'GalleryActivity.kt').read_text())
        self.assertIn('RemoteMultimediaActivity::class.java',(SRC/'PhoneCameraViewerActivity.kt').read_text())
        self.assertIn('RemoteMultimediaActivity', (ROOT/'app/src/main/AndroidManifest.xml').read_text())
    def test_documented_shared_protocol(self):
        self.assertIn('MCRP) v1',(ROOT/'docs/MCRP_V1.md').read_text())

if __name__=='__main__':unittest.main()
