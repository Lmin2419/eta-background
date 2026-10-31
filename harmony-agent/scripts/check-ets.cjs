#!/usr/bin/env node
/*
 * ArkTS (.ets) type and rule check without DevEco, using the official ets-loader checker from the
 * public OpenHarmony SDK (API 26). It runs the same TypeScript program and ArkTS linter that the
 * build runs, starting from the app entries, but it does not produce a HAP.
 *
 *   bash scripts/fetch-ohos-sdk.sh
 *   OHOS_SDK_ETS=~/.cache/ohos-sdk-26/ets npm run check:ets            # errors only
 *   OHOS_SDK_ETS=~/.cache/ohos-sdk-26/ets npm run check:ets -- --warnings
 *
 * Exit code 1 when any error remains after the known false positives below.
 */
'use strict';
const fs = require('fs');
const os = require('os');
const path = require('path');

const project = path.resolve(__dirname, '..');
const sdk = path.resolve(process.env.OHOS_SDK_ETS || path.join(os.homedir(), '.cache/ohos-sdk-26/ets'));
const loader = path.join(sdk, 'build-tools/ets-loader');
const showWarnings = process.argv.includes('--warnings');
if (!fs.existsSync(path.join(loader, 'main.js'))) {
  console.error('OpenHarmony SDK not found at ' + sdk + '. Run: bash scripts/fetch-ohos-sdk.sh, then set OHOS_SDK_ETS.');
  process.exit(2);
}

// The build preprocesses two-way binding ($$this.x) before type checking; the standalone checker does
// not. This exact message is therefore not a real error. Anything else is reported.
const FALSE_POSITIVES = [/^Cannot find name '\$\$this'\.$/];

const src = path.join(project, 'entry/src/main/ets');
const pages = JSON.parse(fs.readFileSync(path.join(project, 'entry/src/main/resources/base/profile/main_pages.json'), 'utf8')).src;
const entries = {};
for (const page of pages) entries[page] = path.join(src, page + '.ets');
entries.ability = path.join(src, 'entryability/EntryAbility.ets');
entries.preview = path.join(src, 'pages/FoundationPreview.ets');
entries.etaGallery = path.join(src, 'pages/EtaGallery.ets');

const cache = fs.mkdtempSync(path.join(os.tmpdir(), 'ets-check-'));
const config = {
  projectPath: src, projectRootPath: project, modulePath: path.join(project, 'entry'),
  cachePath: cache, buildPath: cache, aceModuleBuild: cache,
  aceModuleJsonPath: path.join(project, 'entry/src/main/module.json5'),
  etsLoaderPath: loader, compilerTypes: [], packageManagerType: 'ohpm',
  compatibleSdkVersion: 20, compileSdkVersion: 26, runtimeOS: 'HarmonyOS', arkTSVersion: '1.1',
  bundleName: 'com.example.harmonyagent', moduleName: 'entry', allowEtsAnnotations: true
};

const found = { error: [], warn: [] };
const clean = message => String(message).replace(/\x1b\[\d+m/g, '').trim();
const logger = {
  error: m => found.error.push(clean(m)), warn: m => found.warn.push(clean(m)),
  info: () => {}, debug: () => {}, printError: e => found.error.push(clean(JSON.stringify(e)))
};

require(path.join(loader, 'main.js'));
const checker = require(path.join(loader, 'lib/ets_checker.js'));
checker.etsStandaloneChecker(entries, logger, config);
// A bare @Builder passed to @BuilderParam changes receiver at runtime. Type checks
// accept it, so also enforce explicit owner capture and exercise the real slot expressions.
const builders = require('./arkui-builder-check.cjs').checkProject(loader, src);
found.error.push(...builders.errors);
const builderRegression = require('node:child_process').spawnSync(process.execPath,
  ['--test', path.join(__dirname, 'tests/arkui-builder-check.test.cjs')],
  { encoding: 'utf8', env: { ...process.env, OHOS_SDK_ETS: sdk }, timeout: 30000 });
if (builderRegression.error || builderRegression.status !== 0) {
  found.error.push('ArkUI builder-context regression failed:\n' + (builderRegression.stdout || '') +
    (builderRegression.stderr || '') + (builderRegression.error?.message || ''));
}
fs.rmSync(cache, { recursive: true, force: true });

const relative = text => text.split(project + path.sep).join('');
const isFalsePositive = text => FALSE_POSITIVES.some(re => re.test(text.split('\n').slice(1).join('\n').trim()));
const errors = found.error.filter(e => !isFalsePositive(e)).map(relative);
const version = JSON.parse(fs.readFileSync(path.join(sdk, 'oh-uni-package.json'), 'utf8')).version;
console.log('ArkTS check with OpenHarmony SDK ' + version + ': ' + errors.length + ' error(s), ' + found.warn.length + ' warning(s), '
  + (found.error.length - errors.length) + ' known false positive(s) ignored.');
for (const e of errors) console.log('\n' + e);
if (showWarnings) for (const w of found.warn) console.log('\n' + relative(w));
console.log('\nThis checks types and ArkTS rules only. It does not build a HAP; DevEco remains the final compile check.');
console.log('ArkUI builder owner check: ' + builders.slots + ' slot assignments, ' + builders.errors.length +
  ' error(s); receiver regression tests: ' + (builderRegression.status === 0 ? 'passed' : 'FAILED') + '.');
process.exit(errors.length ? 1 : 0);
