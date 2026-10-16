"""Regressões estruturais específicas da galeria 1.4.61, independentes do Android SDK."""
import unittest
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'app/src/main'

def kotlin(name):
    return (MAIN / 'java/com/minhacamera' / name).read_text()

class GallerySmoke(unittest.TestCase):
    def test_opens_gallery_without_three_cards(self):
        old = kotlin('MultimediaActivity.kt')
        self.assertIn('GalleryActivity::class.java', old)
        self.assertNotIn('addCard(', old)

    def test_source_uses_scoped_index(self):
        src = kotlin('GalleryLibrary.kt')
        self.assertIn('MediaStore.Images.Media.getContentUri', src)
        self.assertIn('RecordingFiles.clips(RecordingStore.root(context))', src)
        self.assertIn('MAX_PER_COLLECTION', src)
        self.assertIn('FileProvider.getUriForFile', src)
        self.assertNotIn('Environment.getExternalStorageDirectory', src)

    def test_viewer_internal_player(self):
        src = kotlin('GalleryViewerActivity.kt')
        self.assertIn('ExoPlayer.Builder(this).build()', src)
        self.assertIn('PlayerView(this)', src)
        self.assertIn('ScaleGestureDetector', src)
        self.assertIn('releasePlayer()', src)
        self.assertNotIn('Intent.ACTION_VIEW', src)

    def test_management_and_consent(self):
        src = kotlin('GalleryActivity.kt')
        for term in ('createDeleteRequest', 'OpenDocumentTree', 'OpenMultipleDocuments',
                     'Intent.ACTION_SEND_MULTIPLE', 'Selecionar', 'Compartilhar', 'deletePrivate'):
            if term == 'Selecionar': continue
            self.assertIn(term, src)
        self.assertIn('private.name.startsWith("Camera_")', kotlin('GalleryViewerActivity.kt'))
        self.assertIn('file.name.startsWith("Camera_")', src)
        self.assertIn('android.permission.READ_MEDIA_VISUAL_USER_SELECTED', (MAIN / 'AndroidManifest.xml').read_text())

    def test_gallery_ui(self):
        src = kotlin('GalleryActivity.kt')
        self.assertIn('RecyclerView', src)
        self.assertIn('GalleryThumbnails.load', src)
        self.assertIn('"Fotos", "Vídeos", "Gravações"', src)
        self.assertIn('RemoteMultimediaActivity::class.java', src)

if __name__ == '__main__': unittest.main()
