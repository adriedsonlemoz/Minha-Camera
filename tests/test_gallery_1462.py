"""Contratos estruturais para a central multimídia compacta (v1.4.62)."""
from pathlib import Path
import unittest
BASE = Path(__file__).resolve().parents[1]
JAVA = BASE/'app/src/main/java/com/minhacamera'

class GalleryUI(unittest.TestCase):
    def test_header_and_remote(self):
        src = (JAVA/'GalleryActivity.kt').read_text()
        self.assertIn('supportActionBar?.hide()', src)
        self.assertIn('"Remoto"', src)
        self.assertIn('R.drawable.ic_ui_smartphone', src)
        self.assertIn('"Pesquisar na galeria"', src)
        self.assertIn('toggleSearch()', src)
        self.assertNotIn('▦  Neste aparelho', src)
        self.assertNotIn('⌁  Outro celular', src)
    def test_filters_have_vectors(self):
        src = (JAVA/'GalleryActivity.kt').read_text()
        for name in ('ic_ui_gallery','ic_ui_camera','ic_ui_video','ic_ui_record'):
            self.assertIn('R.drawable.'+name, src)
        self.assertIn('bg_gallery_filter_selected', src)
    def test_metadata_controls_are_persistent_and_async(self):
        src = (JAVA/'GalleryActivity.kt').read_text()
        for name in ('Tamanho do arquivo', 'Resolução da imagem', 'Duração dos vídeos', 'Data na miniatura'):
            self.assertIn(name, src)
        self.assertIn('getSharedPreferences("gallery_presentation"',src)
        self.assertIn('GalleryMetadata.load',src)
        self.assertIn('R.id.gallery_media_tag',src)
        meta = (JAVA/'GalleryMetadata.kt').read_text()
        self.assertIn('MediaMetadataRetriever', meta)
        self.assertIn('inJustDecodeBounds = true', meta)
        self.assertIn('Executors.newFixedThreadPool(2)', meta)
    def test_thumbnail_quality(self):
        src = (JAVA/'GalleryThumbnails.kt').read_text()
        self.assertNotIn('contentResolver.loadThumbnail',src)
        self.assertIn('ImageDecoder.decodeBitmap',src)
        ui = (JAVA/'GalleryActivity.kt').read_text()
        self.assertIn('widthPixels * 1.4f / columns',ui)
    def test_management_not_removed(self):
        src = (JAVA/'GalleryActivity.kt').read_text()
        for name in ('deletePrivate(', 'createDeleteRequest', 'OpenMultipleDocuments', 'Ordenar arquivos', 'Selecionar vários', 'Atualizar galeria'):
            self.assertIn(name,src)
if __name__ == '__main__': unittest.main()
