# Converter contract tests for app/src/main/res/* @ 3b1794d.
# New tooling tests; the two upstream LocaleResourcesTest cases are translated separately.
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
def load(name, file):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts' / file)
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module); return module
strings = load('eta_strings', 'port-eta-strings.py')
vectors = load('eta_vectors', 'port-eta-vectors.py')
theme = load('eta_theme', 'port-eta-theme.py')

class ResourceConvertersTest(unittest.TestCase):
    def test_android_escapes_quotes_whitespace_and_unicode(self):
        self.assertEqual('你好\n"朋友"\t🙂', strings.android_text(r'你好\n\"朋友\"\t\uD83D\uDE42'))
        self.assertEqual('a b', strings.android_text('  a\n  b  '))
        self.assertEqual(' a  b ', strings.android_text('" a  b "'))
        self.assertEqual("It's a test", strings.android_text(r"It\'s a test"))
        self.assertEqual('@text ?thing \\', strings.android_text(r'\@text \?thing \\'))
    def test_xml_entities_and_indexed_arguments(self):
        n = ET.fromstring('<string name="greeting">Eta &amp; %2$s: %1$d\\n完成</string>')
        self.assertEqual('随身助手 & %2$s: %1$d\n完成', strings.convert_text(n))
        for text in ['%1$s', '%2$d / %1$d', '%f', '%%', '%s %d', '%1$ds']:
            self.assertEqual(text, strings.convert_text(ET.fromstring('<string>'+text+'</string>')))
    def test_literal_percentages_not_double_escaped(self):
        n = ET.fromstring('<string formatted="false">调整全局显示比例（80%–110%）</string>')
        self.assertEqual('调整全局显示比例（80%–110%）', strings.convert_text(n, False))
    def test_product_name_replacement_preserves_urls_identifiers_and_technical_terms(self):
        self.assertEqual('随身助手: https://github.com/Mangi-11/Eta io.github.Eta API Key Metadata',
                         strings.brand('Eta: https://github.com/Mangi-11/Eta io.github.Eta API Key Metadata'))
    def test_invalid_escapes_and_unsupported_format_fail(self):
        for text in ['a\\', r'\u12xx', r'\x', '"not closed']:
            with self.assertRaises(ValueError): strings.android_text(text)
        with self.assertRaises(ValueError): strings.convert_text(ET.fromstring('<string>%02x</string>'))
        with self.assertRaises(ValueError): strings.convert_text(ET.fromstring('<string><b>bold</b></string>'))
    def test_plural_quantities_preserved(self):
        with tempfile.TemporaryDirectory() as folder:
            p = Path(folder); (p/'x.xml').write_text('<resources><string name="x">x</string><plurals name="count"><item quantity="one">%d item</item><item quantity="other">%d items</item></plurals></resources>')
            _, plurals, _ = strings.read_locale(p)
            self.assertEqual({'one':'%d item', 'other':'%d items'}, plurals['count'])
    def test_duplicates_and_missing_plural_fallback_rejected(self):
        cases = ['<string name="x">a</string><string name="x">b</string>',
                 '<string name="x">a</string><plurals name="count"><item quantity="one">one</item></plurals>',
                 '<string name="x">a</string><plurals name="count"><item quantity="other">x</item><item quantity="other">y</item></plurals>']
        for text in cases:
            with tempfile.TemporaryDirectory() as folder:
                p=Path(folder); (p/'x.xml').write_text('<resources>'+text+'</resources>')
                with self.assertRaises(ValueError): strings.read_locale(p)
    def test_check_mode_detects_drift_without_rewriting(self):
        with tempfile.TemporaryDirectory() as folder:
            p=Path(folder); strings.generate(ROOT.parent,p)
            f=p/'entry/src/main/resources/base/element/string.json';f.write_text('tampered')
            with self.assertRaises(ValueError): strings.generate(ROOT.parent,p,True)
            self.assertEqual('tampered',f.read_text())
    def test_atom_outline_has_no_accidental_solid_fill(self):
        svg=ET.fromstring(vectors.vector_svg(ROOT.parent/'app/src/main/res/drawable/ic_atom.xml'))
        self.assertEqual(3,len(svg));self.assertEqual('0 0 24 24',svg.get('viewBox'))
        for p in svg:
            self.assertEqual('none',p.get('fill'));self.assertEqual('2.0',p.get('stroke-width'))
            self.assertEqual('round',p.get('stroke-linecap'))
    def test_android_argb_alpha_and_pivot_geometry(self):
        self.assertEqual(('#112233',128/255*0.5), vectors.color('#80112233',0.5))
        n=ET.fromstring('<group xmlns:android="http://schemas.android.com/apk/res/android" android:pivotX="5" android:pivotY="6" android:scaleX="2" android:scaleY="3"><path android:pathData="M0,0L1,1" android:fillColor="#FFFFFF"/></group>')
        g=vectors.convert(n);self.assertEqual('translate(5 6) rotate(0) scale(2 3) translate(-5 -6)',g.get('transform'))
        self.assertEqual('M0,0L1,1',g[0].get('d'))
    def test_unknown_vector_geometry_is_not_silently_dropped(self):
        with self.assertRaises(ValueError):vectors.convert(ET.fromstring('<clip-path/>'))
        with self.assertRaises(ValueError):vectors.color('@color/missing')
    def test_color_alpha_uses_compose_rounding_and_keeps_argb(self):
        self.assertEqual('#4D000000',theme.parse_color('Color.Black.copy(alpha = 0.3F)'))
        self.assertEqual('#99000000',theme.parse_color('Color.Black.copy(alpha = 0.6F)'))
        self.assertEqual('#80FFFFFF',theme.parse_color('Color(0x80FFFFFF)'))
        with self.assertRaises(ValueError):theme.parse_color('guessColor()')

if __name__ == '__main__':unittest.main()
