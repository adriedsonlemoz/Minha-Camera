"""Regressões da galeria em lista padrão e opções de visualização v1.4.64."""
import re
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[1]
SRC = (ROOT / 'app/src/main/java/com/minhacamera/GalleryActivity.kt').read_text()

class GalleryLayoutTests(unittest.TestCase):
    def test_first_launch_is_list_and_mode_persists(self):
        self.assertIn('galleryLayoutMode = "list"', SRC)
        self.assertIn('preferences.getString("layout_mode", "list")', SRC)
        self.assertIn('preferences.edit().putString("layout_mode", normalized).apply()', SRC)
        self.assertIn('galleryLayoutMode == "list") LinearLayoutManager', SRC)
        self.assertIn('else GridLayoutManager', SRC)
        self.assertIn('grid.scrollToPosition(0)', SRC)
    def test_list_details_next_to_thumbnail(self):
        self.assertIn('override fun getItemViewType(position: Int)', SRC)
        self.assertIn('LinearLayout.LayoutParams(dp(76), dp(76))', SRC)
        self.assertIn('row.addView(words, LinearLayout.LayoutParams(0, -1, 1f))', SRC)
        self.assertIn('holder.filename?.text = item.name', SRC)
        self.assertIn('GalleryMetadata.load', SRC)
        self.assertIn('GalleryMetadata.formatDuration(duration)', SRC)
        self.assertIn('holder.root.setOnLongClickListener', SRC)
        self.assertIn('if (selectMode) toggle(item) else open(item)', SRC)
    def test_settings_contain_modes_and_controls_without_old_second_dialog(self):
        self.assertIn('"Lista", "Nome e informações"', SRC)
        self.assertIn('"Miniaturas", "Grade de imagens"', SRC)
        self.assertIn('"Informações exibidas"', SRC)
        self.assertIn('"Gerenciar"', SRC)
        self.assertIn('"Tamanho do arquivo"', SRC)
        self.assertIn('"Resolução da imagem"', SRC)
        self.assertIn('"Duração dos vídeos"', SRC)
        self.assertIn('"Data do arquivo"', SRC)
        self.assertNotIn('private fun showGalleryActions()', SRC)
        self.assertIn('actionRow("Atualizar galeria"', SRC)
        self.assertIn('actionRow("Ordenar arquivos"', SRC)
    def test_new_icons_exist_and_xml_valid(self):
        for name in ('ic_ui_list', 'ic_ui_sort', 'ic_ui_refresh'):
            p = ROOT / f'app/src/main/res/drawable/{name}.xml'
            self.assertTrue(p.exists(), name)
            ET.parse(p)
            self.assertIn(f'R.drawable.{name}', SRC)
    def test_selection_and_share_preserved(self):
        for code in ('createDeleteRequest', 'deletePrivate(', 'OpenMultipleDocuments', 'selection()', 'exportCopies(', 'share()', 'confirmDelete()', 'RemoteMultimediaActivity'):
            self.assertIn(code, SRC)

if __name__ == '__main__': unittest.main()
