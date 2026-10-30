#!/usr/bin/env node
/*
 * Resource packaging check without DevEco: compiles AppScope and entry resources with the SDK's
 * own restool, the same tool the build runs, so invalid names, malformed JSON, undefined
 * references and duplicate declarations fail here instead of on the user's machine.
 *
 *   bash scripts/fetch-ohos-sdk.sh
 *   OHOS_SDK_ETS=~/.cache/ohos-sdk-26/ets npm run check:res
 *
 * Any restool error or warning fails the check (the 0.9.1 baseline has none).
 */
'use strict';
const fs = require('fs');
const os = require('os');
const path = require('path');
const { spawnSync } = require('child_process');

const project = path.resolve(__dirname, '..');
const sdkEts = path.resolve(process.env.OHOS_SDK_ETS || path.join(os.homedir(), '.cache/ohos-sdk-26/ets'));
const toolchains = path.resolve(process.env.OHOS_SDK_TOOLCHAINS || path.join(sdkEts, '..', 'toolchains'));
const restool = path.join(toolchains, 'restool');
if (!fs.existsSync(restool)) {
  console.error('restool not found at ' + restool + '. Run: bash scripts/fetch-ohos-sdk.sh (it also extracts the toolchains).');
  process.exit(2);
}

const read = file => JSON.parse(fs.readFileSync(path.join(project, file), 'utf8'));
const app = read('AppScope/app.json5').app;
const moduleConfig = read('entry/src/main/module.json5').module;
// The build merges app.json5 and module.json5 into module.json before packing resources.
Object.assign(app, { minAPIVersion: 20, targetAPIVersion: 20, apiReleaseType: 'Release', compileSdkVersion: '26.0.0', compileSdkType: 'HarmonyOS' });
Object.assign(moduleConfig, { packageName: moduleConfig.packageName || 'entry', virtualMachine: 'ark', compileMode: 'esmodule' });

const work = fs.mkdtempSync(path.join(os.tmpdir(), 'res-check-'));
const out = path.join(work, 'res');
fs.mkdirSync(out);
fs.writeFileSync(path.join(work, 'module.json'), JSON.stringify({ app, module: moduleConfig }));
const result = spawnSync(restool, [
  '-i', path.join(project, 'AppScope'), '-i', path.join(project, 'entry/src/main'),
  '-m', 'entry', '-p', app.bundleName, '-j', path.join(work, 'module.json'),
  '-o', out, '-r', path.join(work, 'ResourceTable.txt'), '-f'
], { encoding: 'utf8' });

const lines = (result.stdout + result.stderr).split('\n').map(l => l.trim()).filter(Boolean);
const problems = lines.filter(l => !l.startsWith('Info'));
const table = path.join(work, 'ResourceTable.txt');
const count = fs.existsSync(table) ? fs.readFileSync(table, 'utf8').split('\n').filter(Boolean).length : 0;
fs.rmSync(work, { recursive: true, force: true });

const relative = text => text.split(project + path.sep).join('');
const failed = result.status !== 0 || problems.length > 0;
const version = JSON.parse(fs.readFileSync(path.join(toolchains, 'oh-uni-package.json'), 'utf8')).version;
console.log('Resource check with restool (OpenHarmony SDK toolchains ' + version + '): ' + (failed ? 'FAILED' : 'passed') + ', ' + count + ' resources.');
for (const line of problems) console.log('  ' + relative(line));
console.log('\nThis packs resources only. It does not render SVGs or fonts; DevEco remains the final compile check.');
process.exit(failed ? 1 : 0);
