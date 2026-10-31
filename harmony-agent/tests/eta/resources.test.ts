// Resource contracts for app/src/main/res/* and ui/components/* @ 3b1794d.
// New converter tests; upstream case count is recorded in localeResources.test.ts.
import test from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { readFileSync, mkdtempSync, mkdirSync, writeFileSync, rmSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve, join } from 'node:path';
import { EtaPreferenceDefaults, EtaCardDefaults, EtaControlDefaults, EtaChatDefaults, MiuixFontSizes, MiuixDialogDefaults } from '../../entry/src/main/ets/core/eta/tokens';
const json = (file: string) => JSON.parse(readFileSync(file, 'utf8'));
const sha = (file: string) => createHash('sha256').update(readFileSync(file)).digest('hex');
test('Android resource conversion contracts (12 Python cases)', () => {
  const result = execFileSync('python3', ['tests/eta/resourceConverters.test.py'], { encoding: 'utf8', stdio: 'pipe' });
  assert.equal(result, '');
});
test('all generated outputs are reproducible from pinned sources', () => {
  execFileSync('npm', ['run', 'port:eta:check'], { stdio: 'pipe' });
});
test('all Eta string keys and 22 plural keys are present in each locale', () => {
  const report = json('docs/eta-strings.json');
  assert.equal(report.locales.base.sourceStrings, 1536);
  assert.equal(report.locales.en_US.sourceStrings, 1538);
  assert.deepEqual(report.locales.base.fallbackKeys, ['route_skills', 'speech_api_key']);
  for (const locale of ['base', 'en_US', 'zh_Hant']) {
    const values = json(`entry/src/main/resources/${locale}/element/string.json`).string as { name: string; value: string }[];
    assert.equal(values.length, 1553); assert.equal(new Set(values.map(v => v.name)).size, values.length);
    assert.equal(json(`entry/src/main/resources/${locale}/element/plural.json`).plural.length, 22);
    assert.equal(values.find(v => v.name === 'app_name'), undefined, 'app_name belongs to AppScope only');
    assert.equal(report.locales[locale].portedStrings, 1537);
    assert.deepEqual(report.locales[locale].appScopeKeys, ['app_name']);
    assert.equal(values.find(v => v.name === 'eta_port_required_notice')?.value, 'Required Notice: Copyright © 2026 蛮吉 (Mangi-11).');
    assert.equal(values.find(v => v.name === 'module_desc')?.value, '在手机上运行的 AI 助手');
  }
  const scope = json('AppScope/resources/base/element/string.json').string as { name: string; value: string }[];
  assert.deepEqual(scope.filter(v => v.name === 'app_name'), [{ name: 'app_name', value: '随身助手' }]);
});
test('all 148 Material, 1 Miuix and 11 drawables have exact checked files', () => {
  const r = json('docs/eta-icons.json');
  assert.equal(r.materialCount, 148); assert.equal(r.miuixCount, 1); assert.equal(r.drawableCount, 11);
  assert.equal(r.brandCount, 22); assert.deepEqual(r.missing, []);
  const rows = [...r.icons, ...r.drawables, ...r.brands, ...r.controls] as { output: string; sha256: string }[];
  assert.equal(new Set(rows.map(x => x.output)).size, rows.length);
  for (const row of rows) assert.equal(sha(row.output), row.sha256, row.output);
});
test('Material SVG geometry and style are copied from the pinned npm package', () => {
  const report = json('docs/eta-icons.json');
  for (const icon of report.icons as { source: string; origin: string; output: string; autoMirrored: boolean }[]) {
    if (!icon.source.startsWith('Icons.')) continue;
    const original = readFileSync('node_modules/' + icon.origin.replace('@0.14.15', ''), 'utf8').trimEnd();
    assert.equal(readFileSync(icon.output, 'utf8').replace(/^<!--[^]*?-->\n/, '').trimEnd(), original);
    assert.equal(icon.autoMirrored, icon.source.includes('.AutoMirrored.'));
  }
});
test('Miuix ArrowRight retains its 10 by 16 viewport and even-odd path', () => {
  const svg = readFileSync('entry/src/main/resources/base/media/ic_miuix_basic_arrow_right.svg', 'utf8');
  assert.match(svg, /viewBox="0 0 10 16"/); assert.match(svg, /fill-rule="evenodd"/);
  assert.match(svg, /M1.65 1.469 C1.929 1.19 2.381 1.19 2.66 1.469/); assert.match(svg, /Z"/);
});
test('unknown icon fails generation before writing misleading assets', () => {
  const folder = mkdtempSync(join(tmpdir(), 'eta-missing-'));
  try {
    mkdirSync(join(folder, 'app/src/main/kotlin'), { recursive: true });
    writeFileSync(join(folder, 'app/src/main/kotlin/X.kt'), 'val x = Icons.Rounded.NotARealMaterialIcon');
    const output = join(folder, 'out');
    assert.throws(() => execFileSync('node', ['scripts/port-eta-icons.mjs', '--eta-root', folder, '--output-root', output], { stdio: 'pipe' }), /Missing icons/);
    assert.equal(existsSync(output), false);
  } finally { rmSync(folder, { recursive: true, force: true }); }
});
test('tokens preserve source dimensions, content alignment and fractional divider', () => {
  assert.equal(EtaPreferenceDefaults.SidePadding, 16); assert.equal(EtaPreferenceDefaults.GroupSpacing, 16);
  assert.equal(EtaPreferenceDefaults.IconSize, 24); assert.equal(EtaPreferenceDefaults.IconTextGap, 16);
  assert.equal(EtaPreferenceDefaults.RowMinHeight, 52); assert.equal(EtaPreferenceDefaults.DividerThickness, 0.33);
  assert.equal(EtaPreferenceDefaults.contentStart(true), 56); assert.equal(EtaPreferenceDefaults.contentStart(false), 16);
  assert.equal(EtaCardDefaults.CornerRadius, 24); assert.equal(EtaControlDefaults.ButtonCornerRadius, 22);
  assert.equal(EtaControlDefaults.ButtonMinHeight, 44); assert.equal(EtaControlDefaults.SwitchWidth, 44); assert.equal(EtaControlDefaults.SwitchHeight, 24);
  assert.deepEqual(EtaControlDefaults.DialogInsideMargin, { width: 24, height: 24 });
  assert.deepEqual(EtaControlDefaults.DialogOutsideMargin, { width: 16, height: 16 });
  assert.equal(EtaChatDefaults.ActionIconSize, 24); assert.equal(MiuixFontSizes.Body1, 16); assert.equal(MiuixDialogDefaults.MaxWidth, 420);
});
test('light and dark resources preserve Miuix roles, Eta overrides and alpha', () => {
  const palette = (locale: string): Map<string, string> => new Map(json(`entry/src/main/resources/${locale}/element/color.json`).color.map((c: { name: string; value: string }) => [c.name, c.value]));
  const light = palette('base'), dark = palette('dark'); assert.equal(light.size, 86); assert.equal(dark.size, 86);
  assert.deepEqual([...light.keys()], [...dark.keys()]);
  assert.equal(light.get('miuix_primary'), '#FF3482FF'); assert.equal(dark.get('miuix_primary'), '#FF277AF7');
  assert.equal(light.get('eta_settings_primary'), '#FF0080FF'); assert.equal(light.get('eta_settings_background'), '#FFF0F1F2');
  assert.equal(dark.get('eta_settings_background'), '#FF242424');
  assert.equal(light.get('miuix_window_dimming'), '#4D000000'); assert.equal(dark.get('miuix_window_dimming'), '#99000000');
  assert.equal(dark.get('eta_preference_divider'), '#1AFFFFFF'); assert.equal(light.get('eta_status_success'), '#FF00BD13');
});
test('font and brand binaries remain byte-identical to Eta assets', () => {
  for (const font of json('docs/eta-fonts.json').fonts as { path: string; sha256: string }[]) {
    const file = font.path.split('/').at(-1)!;
    assert.equal(sha(`entry/src/main/resources/rawfile/fonts/${file}`), font.sha256);
    assert.equal(sha(resolve('..', font.path)), font.sha256);
  }
  for (const row of json('docs/eta-icons.json').brands as { source: string; output: string }[]) assert.equal(sha(row.output), sha(resolve('..', row.source)));
});
test('license and Required Notice survive port and package resources', () => {
  assert.equal(readFileSync('LICENSE', 'utf8'), readFileSync('../LICENSE', 'utf8'));
  const license = readFileSync('entry/src/main/resources/rawfile/licenses/Eta-PolyForm-Noncommercial-1.0.0.txt', 'utf8');
  assert.match(license, /Required Notice: Copyright © 2026 蛮吉 \(Mangi-11\)\./);
  assert.match(readFileSync('entry/src/main/resources/rawfile/licenses/JetBrainsMono-OFL.txt', 'utf8'), /SIL OPEN FONT LICENSE/);
});
