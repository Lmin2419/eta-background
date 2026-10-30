#!/usr/bin/env python3
# Ported from app/src/main/res/drawable/*.xml @ 3b1794d.
# Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
"""Translate Android path/group/solid-shape geometry to SVG without redrawing it."""
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID = '{http://schemas.android.com/apk/res/android}'


def attrs(node):
    return {key.removeprefix(ANDROID): value for key, value in node.attrib.items()}


def color(value, alpha=1.0):
    if value is None: return 'none', 1.0
    if not value.startswith('#'): raise ValueError('Unresolved vector color: ' + value)
    h = value[1:]
    if len(h) in (3, 4): h = ''.join(c * 2 for c in h)
    if len(h) == 8: return '#' + h[2:], int(h[:2], 16) / 255 * alpha
    if len(h) == 6: return '#' + h, alpha
    raise ValueError('Invalid vector color: ' + value)


def convert(node):
    a = attrs(node)
    if node.tag == 'path':
        allowed = {'name', 'pathData', 'fillColor', 'fillAlpha', 'fillType', 'strokeColor', 'strokeAlpha',
                   'strokeWidth', 'strokeLineCap', 'strokeLineJoin', 'strokeMiterLimit'}
        if set(a) - allowed or len(node): raise ValueError('Unsupported path geometry: ' + str(set(a) - allowed))
        f, fa = color(a.get('fillColor'), float(a.get('fillAlpha', 1)))
        s, sa = color(a.get('strokeColor'), float(a.get('strokeAlpha', 1)))
        result = ET.Element('path', {'d': a['pathData'], 'fill': f})
        if a.get('fillType'): result.set('fill-rule', {'evenOdd': 'evenodd', 'nonZero': 'nonzero'}[a['fillType']])
        if fa != 1: result.set('fill-opacity', format(fa, '.12g'))
        if s != 'none':
            result.set('stroke', s)
            if sa != 1: result.set('stroke-opacity', format(sa, '.12g'))
            for old, new in [('strokeWidth', 'stroke-width'), ('strokeLineCap', 'stroke-linecap'),
                             ('strokeLineJoin', 'stroke-linejoin'), ('strokeMiterLimit', 'stroke-miterlimit')]:
                if old in a: result.set(new, a[old])
        return result
    if node.tag == 'group':
        allowed = {'name', 'pivotX', 'pivotY', 'rotation', 'scaleX', 'scaleY', 'translateX', 'translateY'}
        if set(a) - allowed: raise ValueError('Unsupported vector group')
        px, py = float(a.get('pivotX', 0)), float(a.get('pivotY', 0))
        tx, ty = float(a.get('translateX', 0)), float(a.get('translateY', 0))
        result = ET.Element('g', {'transform': f'translate({tx+px:g} {ty+py:g}) rotate({a.get("rotation", "0")}) scale({a.get("scaleX", "1")} {a.get("scaleY", "1")}) translate({-px:g} {-py:g})'})
        for child in node: result.append(convert(child))
        return result
    raise ValueError('Unsupported vector node: ' + node.tag)


def vector_svg(path):
    root = ET.parse(path).getroot(); a = attrs(root)
    if root.tag == 'shape':
        if a.get('shape') != 'rectangle' or len(root) != 1 or root[0].tag != 'solid':
            raise ValueError('Unsupported shape: ' + str(path))
        f, alpha = color(attrs(root[0])['color'])
        svg = ET.Element('svg', {'xmlns': 'http://www.w3.org/2000/svg', 'viewBox': '0 0 1 1'})
        svg.append(ET.Element('rect', {'width': '1', 'height': '1', 'fill': f, 'fill-opacity': str(alpha)}))
    elif root.tag == 'vector':
        allowed = {'width', 'height', 'viewportWidth', 'viewportHeight', 'alpha', 'autoMirrored'}
        if set(a) - allowed: raise ValueError('Unsupported vector attribute: ' + str(set(a) - allowed))
        svg = ET.Element('svg', {'xmlns': 'http://www.w3.org/2000/svg',
            'width': a['width'].removesuffix('dp'), 'height': a['height'].removesuffix('dp'),
            'viewBox': f'0 0 {a["viewportWidth"]} {a["viewportHeight"]}'})
        if 'alpha' in a: svg.set('opacity', a['alpha'])
        for child in root: svg.append(convert(child))
    else: raise ValueError('Unsupported drawable: ' + root.tag)
    comment = f'<!-- Ported from app/src/main/res/drawable/{path.name} @ 3b1794d; Required Notice: Copyright © 2026 蛮吉 (Mangi-11). -->\n'
    return comment + ET.tostring(svg, encoding='unicode') + '\n'


if __name__ == '__main__':
    print(json.dumps({p.stem: vector_svg(p) for p in sorted(Path(sys.argv[1]).glob('*.xml'))}, ensure_ascii=False))
