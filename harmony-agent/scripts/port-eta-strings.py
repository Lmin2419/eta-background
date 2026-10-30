#!/usr/bin/env python3
# Ported from app/src/main/res/values*/ @ 3b1794d.
# Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
"""Convert Android string/plural resources; preserve keys, text and format arguments."""
import re
import xml.etree.ElementTree as ET
from eta_port_common import PROJECT, ETA_COMMIT, arguments, emit_json, origin

# API 26 ResourceManager supports %d/%f/%s, indexed variants and %%.
FORMAT = re.compile(r'%(?:(\d+)\$)?([sdf%])')
QUANTITIES = ('zero', 'one', 'two', 'few', 'many', 'other')


def brand(text):
    # Change product self-reference, not URLs, package names or resource identifiers.
    parts = re.split(r'(https?://[^\s<>]+|\b[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+){2,}\b)', text)
    return ''.join(part if i % 2 else re.sub(r'(?<![A-Za-z0-9_])Eta(?![A-Za-z0-9_])', '随身助手', part)
                   for i, part in enumerate(parts))


def android_text(text):
    """Decode Android quoting/escaping without corrupting Unicode or escaped whitespace."""
    out = []; quoted = False; pending_space = False; i = 0
    escapes = {'n': '\n', 't': '\t', 'r': '\r', '\\': '\\', "'": "'", '"': '"', '@': '@', '?': '?'}
    while i < len(text):
        char = text[i]
        if char == '"':
            if pending_space and out: out.append(' ')
            pending_space = False; quoted = not quoted; i += 1; continue
        if char == '\\':
            i += 1
            if i >= len(text): raise ValueError('Trailing Android escape')
            if pending_space and out: out.append(' ')
            pending_space = False
            if text[i] == 'u':
                code = text[i+1:i+5]
                if not re.fullmatch('[0-9a-fA-F]{4}', code): raise ValueError('Invalid Unicode escape')
                out.append(chr(int(code, 16))); i += 5; continue
            if text[i] not in escapes: raise ValueError('Unsupported Android escape: ' + text[i])
            out.append(escapes[text[i]]); i += 1; continue
        if char.isspace() and not quoted:
            pending_space = True
        else:
            if pending_space and out: out.append(' ')
            pending_space = False; out.append(char)
        i += 1
    if quoted: raise ValueError('Unclosed Android quoted string')
    # JSON must contain actual Unicode scalar values, including paired UTF-16 escapes.
    return ''.join(out).encode('utf-16', 'surrogatepass').decode('utf-16')


def convert_text(node, formatted=True):
    if len(node): raise ValueError('Styled XML requires explicit port: ' + str(node.get('name')))
    value = brand(android_text(node.text or ''))
    if not formatted:
        # The corresponding UI reads these resources without formatting arguments.
        # Keep literal percentages (80%–110%) byte-for-byte, rather than displaying %%.
        return value
    for match in re.finditer('%', value):
        previous = value[match.start()-1:match.start()]
        if previous == '%': continue
        token = FORMAT.match(value, match.start())
        # Literal percentages are not placeholders. Unsupported format directives fail closed.
        if token is None and re.match(r'%[0-9$+.#-]*[A-Za-z]', value[match.start():]):
            raise ValueError('Unsupported format directive: ' + value[match.start():])
    return value


def read_locale(directory):
    strings = {}; plurals = {}; inputs = []
    for path in sorted(directory.glob('*.xml')):
        inputs.append(path)
        for node in ET.parse(path).getroot():
            if node.tag not in ('string', 'plurals'): continue
            key = node.attrib['name']; target = strings if node.tag == 'string' else plurals
            if key in target: raise ValueError('Duplicate resource: ' + key)
            formatted = node.get('formatted', 'true') != 'false'
            if node.tag == 'string': target[key] = convert_text(node, formatted)
            else:
                quantities = {}
                for item in node:
                    quantity = item.attrib['quantity']
                    if item.tag != 'item' or quantity not in QUANTITIES or quantity in quantities:
                        raise ValueError('Invalid or duplicate plural quantity: ' + key)
                    quantities[quantity] = convert_text(item, formatted)
                if 'other' not in quantities: raise ValueError('Plural has no other: ' + key)
                target[key] = quantities
    if not strings: raise ValueError('No strings in ' + str(directory))
    return strings, plurals, inputs


def generate(eta_root, output, check=False):
    import json
    res = eta_root / 'app/src/main/res'
    english, en_plurals, en_inputs = read_locale(res / 'values')
    legacy = json.loads((PROJECT / 'scripts/eta-port/legacy-strings.json').read_text())['string']
    extras = json.loads((PROJECT / 'scripts/eta-port/harmony-strings.json').read_text())
    report = {'etaCommit': ETA_COMMIT, 'defaultLocale': 'zh-Hans',
              'placeholderSpec': 'API 26 ResourceManager: %d, %f, %s, %% and %number$[dfs]',
              'locales': {}}
    for locale, source in [('base', 'values-b+zh+Hans'), ('en_US', 'values'), ('zh_Hant', 'values-b+zh+Hant')]:
        translated, pl, paths = read_locale(res / source)
        strings = dict(english); strings.update(translated)
        plurals = dict(en_plurals); plurals.update(pl)
        fallbacks = sorted(set(english) - set(translated))
        for row in legacy:
            if row['name'] in strings: raise ValueError('Legacy key collides: ' + row['name'])
            strings[row['name']] = row['value']
        for key, value in extras[locale].items():
            if key in strings: raise ValueError('Harmony key collides: ' + key)
            strings[key] = value
        dest = output / 'entry/src/main/resources' / locale / 'element'
        emit_json(dest / 'string.json', {'string': [{'name': k, 'value': v} for k, v in sorted(strings.items())]}, check)
        emit_json(dest / 'plural.json', {'plural': [{'name': k, 'value': [
            {'quantity': q, 'value': values[q]} for q in QUANTITIES if q in values
        ]} for k, values in sorted(plurals.items())]}, check)
        report['locales'][locale] = {'sourceStrings': len(translated), 'sourcePlurals': len(pl),
            'portedStrings': len(english), 'generatedStrings': len(strings), 'generatedPlurals': len(plurals),
            'fallbackKeys': fallbacks, 'inputs': [origin(p, eta_root) for p in paths]}
    emit_json(output / 'docs/eta-strings.json', report, check)
    return report


if __name__ == '__main__':
    args = arguments(__doc__)
    report = generate(args.eta_root, args.output_root, args.check)
    for locale, counts in report['locales'].items():
        print(locale, counts['portedStrings'], 'Eta strings;', counts['generatedPlurals'], 'plurals')
