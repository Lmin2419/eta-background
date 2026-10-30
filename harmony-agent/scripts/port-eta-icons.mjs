#!/usr/bin/env node
// Ported from app/src/main/kotlin/** and app/src/main/res/drawable/** @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
const project = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ref = '3b1794d70881c3c849b53d7778dd2bfdf0a46cc5';
const sha = data => createHash('sha256').update(data).digest('hex');
const snake = name => name.replace(/([A-Z]+)([A-Z][a-z])/g, '$1_$2').replace(/([a-z0-9])([A-Z])/g, '$1_$2').replace(/([a-z])(\d)/g, '$1_$2').toLowerCase();
function walk(dir) { return fs.readdirSync(dir, { withFileTypes: true }).flatMap(e => e.isDirectory() ? walk(path.join(dir, e.name)) : [path.join(dir, e.name)]).sort(); }
export function scanIcons(etaRoot) {
  const found = new Map();
  for (const file of walk(path.join(etaRoot, 'app/src/main/kotlin')).filter(f => f.endsWith('.kt'))) {
    const text = fs.readFileSync(file, 'utf8');
    for (const match of text.matchAll(/(?<![\w.])(?:Icons\.(?:AutoMirrored\.)?(?:Filled|Outlined|Rounded|Sharp|TwoTone)|MiuixIcons\.[A-Za-z]+)\.[A-Za-z][A-Za-z0-9]*/g)) {
      const uses = found.get(match[0]) ?? new Set(); uses.add(path.relative(etaRoot, file).replaceAll(path.sep, '/')); found.set(match[0], uses);
    }
  }
  return [...found].sort(([a], [b]) => a.localeCompare(b, 'en')).map(([source, uses]) => ({ source, uses: [...uses] }));
}
export function miuixArrowSvg(source) {
  const builder = source.match(/ImageVector.Builder\("ArrowRight",\s*([\d.]+)\.dp,\s*([\d.]+)\.dp,\s*([\d.]+)f,\s*([\d.]+)f\)/);
  if (!builder) throw new Error('Unrecognized Miuix ArrowRight viewport');
  const body = source.split('pathFillType = PathFillType.EvenOdd,')[1]?.match(/\)\s*\{([\s\S]*?)\n\s*\}/)?.[1];
  if (!body) throw new Error('Unrecognized Miuix ArrowRight path');
  const commands = { moveTo: ['M', 2], lineTo: ['L', 2], curveTo: ['C', 6], close: ['Z', 0] };
  let d = '';
  for (const line of body.trim().split('\n')) {
    const m = line.trim().match(/^(\w+)\(([^)]*)\)$/);
    if (!m || !commands[m[1]]) throw new Error('Unsupported Miuix path command: ' + line);
    const args = m[2].trim() ? m[2].split(',').map(x => x.trim().replace(/f$/, '')) : [];
    if (args.length !== commands[m[1]][1] || args.some(x => !/^-?\d+(?:\.\d+)?$/.test(x))) throw new Error('Invalid Miuix path arguments');
    d += commands[m[1]][0] + args.join(' ') + ' ';
  }
  return '<!-- Ported from compose-miuix-ui/miuix ArrowRight.kt @ 39c40f9 (0.9.4); Copyright 2025, compose-miuix-ui contributors; Apache-2.0. -->\n' +
    `<svg xmlns="http://www.w3.org/2000/svg" width="${builder[1]}" height="${builder[2]}" viewBox="0 0 ${builder[3]} ${builder[4]}"><path fill="#000000" fill-rule="evenodd" d="${d.trim()}"/></svg>\n`;
}
export function generateIcons(etaRoot, outputRoot, check = false) {
  const pkg = path.join(project, 'node_modules/@material-design-icons/svg');
  if (JSON.parse(fs.readFileSync(path.join(pkg, 'package.json'))).version !== '0.14.15') throw new Error('Material Icons version must be 0.14.15');
  const outputs = new Map(); const rows = []; const missing = []; const names = new Set();
  for (const entry of scanIcons(etaRoot)) {
    let name, data, original;
    if (entry.source === 'MiuixIcons.Basic.ArrowRight') {
      name = 'ic_miuix_basic_arrow_right'; original = 'scripts/eta-upstream/miuix-0.9.4/ArrowRight.kt';
      data = miuixArrowSvg(fs.readFileSync(path.join(project, original), 'utf8'));
    } else if (entry.source.startsWith('Icons.')) {
      const parts = entry.source.split('.'); const style = parts.at(-2); const icon = snake(parts.at(-1));
      const folder = { Filled: 'filled', Outlined: 'outlined', Rounded: 'round', Sharp: 'sharp', TwoTone: 'two-tone' }[style];
      name = `ic_${style.toLowerCase()}_${icon}`; original = `@material-design-icons/svg@0.14.15/${folder}/${icon}.svg`;
      const file = path.join(pkg, folder, icon + '.svg');
      if (!fs.existsSync(file)) { missing.push({ ...entry, expected: original }); continue; }
      data = `<!-- Generated from ${original}; usage: ${entry.uses[0]} @ 3b1794d; Apache-2.0. -->\n` + fs.readFileSync(file, 'utf8').trimEnd() + '\n';
    } else { missing.push(entry); continue; }
    if (names.has(name)) throw new Error('Icon name collision: ' + name); names.add(name);
    const dest = `entry/src/main/resources/base/media/${name}.svg`; outputs.set(dest, data);
    rows.push({ ...entry, resource: name, output: dest, origin: original, autoMirrored: entry.source.includes('.AutoMirrored.'), sha256: sha(data) });
  }
  if (missing.length) throw new Error('Missing icons: ' + JSON.stringify(missing));
  const drawable = path.join(etaRoot, 'app/src/main/res/drawable');
  const converted = JSON.parse(execFileSync('python3', [path.join(project, 'scripts/port-eta-vectors.py'), drawable], { encoding: 'utf8', maxBuffer: 4e6 }));
  const vectors = [];
  for (const [name, data] of Object.entries(converted)) {
    const dest = `entry/src/main/resources/base/media/${name}.svg`; outputs.set(dest, data);
    vectors.push({ source: `app/src/main/res/drawable/${name}.xml`, resource: name, output: dest, sha256: sha(data) });
  }
  const brands = []; const branding = fs.readFileSync(path.join(etaRoot, 'app/src/main/kotlin/io/github/mangi/eta/ui/components/ProviderBranding.kt'), 'utf8');
  for (const name of [...new Set([...branding.matchAll(/R\.drawable\.((?:provider|model)_logo_[a-z_]+)/g)].map(m => m[1]))].sort()) {
    const source = `app/src/main/res/drawable-xxxhdpi/${name}.webp`; const data = fs.readFileSync(path.join(etaRoot, source));
    const dest = `entry/src/main/resources/base/media/${name}.webp`; outputs.set(dest, data); brands.push({ source, output: dest, sha256: sha(data) });
  }
  const report = { etaCommit: ref, materialVersion: '0.14.15', miuixVersion: '0.9.4',
    materialCount: rows.filter(r => r.source.startsWith('Icons.')).length, miuixCount: rows.filter(r => r.source.startsWith('MiuixIcons.')).length,
    drawableCount: vectors.length, brandCount: brands.length, missing, icons: rows, drawables: vectors, brands };
  outputs.set('docs/eta-icons.json', JSON.stringify(report, null, 2) + '\n');
  for (const [relative, data] of outputs) {
    const dest = path.join(outputRoot, relative); const bytes = Buffer.from(data);
    if (check) { if (!fs.existsSync(dest) || !fs.readFileSync(dest).equals(bytes)) throw new Error('Generated output differs: ' + relative); }
    else { fs.mkdirSync(path.dirname(dest), { recursive: true }); fs.writeFileSync(dest, bytes); }
  }
  return report;
}
if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  const args = process.argv.slice(2); const value = (name, fallback) => args.includes(name) ? path.resolve(args[args.indexOf(name) + 1]) : fallback;
  const result = generateIcons(value('--eta-root', path.dirname(project)), value('--output-root', project), args.includes('--check'));
  console.log(`${result.materialCount} Material + ${result.miuixCount} Miuix + ${result.drawableCount} drawable; missing: ${result.missing.length}; ${result.brandCount} original brand bitmaps`);
}
