// Port contracts for app/src/main/kotlin/io/github/mangi/eta/ui/components/* @ 3b1794d.
// New platform guardrails, not counted as upstream Eta unit tests.
import test from 'node:test';
import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
function files(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap(e => e.isDirectory() ? files(join(dir, e.name)) : [join(dir, e.name)]);
}
test('Eta UI uses generated color resources and retains port provenance', () => {
  for (const path of files('entry/src/main/ets/ui/eta').filter(p => p.endsWith('.ets'))) {
    const text = readFileSync(path, 'utf8');
    assert.doesNotMatch(text, /#[\da-fA-F]{6}(?:[\da-fA-F]{2})?\b/, path);
    assert.match(text, /^\/\/ Ported from app\//, path);
    assert.match(text, /Copyright © 2026 蛮吉 \(Mangi-11\)\./, path);
  }
});
test('gallery is checked but not shipped as a production route', () => {
  const pages = JSON.parse(readFileSync('entry/src/main/resources/base/profile/main_pages.json', 'utf8'));
  assert.ok(!pages.src.includes('pages/EtaGallery'));
  assert.match(readFileSync('scripts/check-ets.cjs', 'utf8'), /entries\.etaGallery =/);
});
test('generated Miuix selection glyphs retain source viewports and check geometry', () => {
  const radio = readFileSync('entry/src/main/resources/base/media/ic_miuix_radio_check.svg', 'utf8');
  const checkbox = readFileSync('entry/src/main/resources/base/media/ic_miuix_checkbox_check.svg', 'utf8');
  assert.match(radio, /viewBox="0 0 56 56"/);
  assert.match(radio, /M10.9 29 L23.1 40.8 L44 16/);
  assert.match(checkbox, /viewBox="0 0 23 23"/);
  assert.match(checkbox, /M5 9.4 L10.3 14.9 L17.9 5.1/);
});
