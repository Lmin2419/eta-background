// Preview build without hvigor: compiles the entry module to modules.abc with the SDK's own ets-loader
// plugins (UI transform, ArkTS checker, es2abc) and packs resources with restool, for the Linux Previewer.
// usage: node scripts/ui-preview/build-preview.cjs <out dir> [extraPage ...]
// Needs OHOS_SDK_ETS (default ~/.cache/ohos-sdk-26/ets) and the rollup devDependencies.
// hvigor adds a few things to rollup that the plugins expect (this.share, ModuleInfo.importedIdMaps,
// meta, originalCode, async/signal); they are provided below.
'use strict';
const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const project = path.resolve(__dirname, '../..');
const out = path.resolve(process.argv[2]);
const extraPages = process.argv.slice(3);
const sdk = path.resolve(process.env.OHOS_SDK_ETS || path.join(require('os').homedir(), '.cache/ohos-sdk-26/ets'), '..');
const loader = path.join(sdk, 'ets/build-tools/ets-loader');
const moduleDir = path.join(project, 'entry');
const src = path.join(moduleDir, 'src/main/ets');
fs.rmSync(out, { recursive: true, force: true });
for (const d of ['ets', 'cache', 'res']) fs.mkdirSync(path.join(out, d), { recursive: true });

// 1. module.json as the build would merge it.
const read = f => JSON.parse(fs.readFileSync(f, 'utf8'));
const app = read(path.join(project, 'AppScope/app.json5')).app;
const mod = read(path.join(moduleDir, 'src/main/module.json5')).module;
Object.assign(app, { minAPIVersion: 20, targetAPIVersion: 20, apiReleaseType: 'Release', compileSdkVersion: '26.0.0', compileSdkType: 'OpenHarmony', debug: true });
Object.assign(mod, { packageName: 'entry', virtualMachine: 'ark', compileMode: 'esmodule', dependencies: [] });
fs.writeFileSync(path.join(out, 'module.json'), JSON.stringify({ app, module: mod }, null, 2));

// 1b. Preview-only resource overlay: extra pages join main_pages.json in the build output, never in the repo.
const resOverlay = path.join(out, 'overlay/main');
fs.cpSync(path.join(moduleDir, 'src/main/resources'), path.join(resOverlay, 'resources'), { recursive: true });
fs.copyFileSync(path.join(moduleDir, 'src/main/module.json5'), path.join(resOverlay, 'module.json5'));
const pagesFile = path.join(resOverlay, 'resources/base/profile/main_pages.json');
const pagesJson = read(pagesFile);
for (const page of extraPages) if (!pagesJson.src.includes(page)) pagesJson.src.push(page);
fs.writeFileSync(pagesFile, JSON.stringify(pagesJson, null, 2));

// 2. Resources with restool (also yields ResourceTable.txt for $r ids).
const restool = spawnSync(path.join(sdk, 'toolchains/restool'), ['-i', path.join(project, 'AppScope'), '-i', resOverlay,
  '-m', 'entry', '-p', app.bundleName, '-j', path.join(out, 'module.json'), '-o', path.join(out, 'res'), '-r', path.join(out, 'ResourceTable.txt'), '-f'], { encoding: 'utf8' });
if (restool.status !== 0) { console.error(restool.stdout, restool.stderr); process.exit(1); }

// 3. loader.json (aceBuildJson) as hvigor would write it.
const loaderJson = {
  modulePathMap: { entry: moduleDir }, compileMode: 'esmodule', projectRootPath: project,
  nodeModulesPath: path.join(out, 'node_modules'), moduleName: 'entry', harNameOhmMap: {}, hspNameOhmMap: {},
  packageManagerType: 'ohpm', compileEntry: [], otherCompileFiles: [],
  dynamicImportLibInfo: {}, routerMap: [], hspResourcesMap: {}, updateVersionInfo: {}, byteCodeHarInfo: {}, declarationEntry: [],
  anBuildMode: 'type', pandaMode: 'es2abc'
};
fs.writeFileSync(path.join(out, 'loader.json'), JSON.stringify(loaderJson, null, 2));

Object.assign(process.env, {
  aceModuleRoot: src, aceModuleBuild: path.join(out, 'ets'), aceModuleJsonPath: path.join(out, 'module.json'),
  aceProfilePath: path.join(resOverlay, 'resources/base/profile'), aceBuildJson: path.join(out, 'loader.json'),
  cachePath: path.join(out, 'cache'), appResource: path.join(out, 'ResourceTable.txt'),
  rawFileResource: path.join(moduleDir, 'src/main/resources/rawfile'), compileMode: 'esmodule', compileTool: 'rollup',
  isPreview: 'true', buildMode: 'debug', runtimeOS: 'OpenHarmony', watchMode: 'false', aceSoPath: path.join(out, 'cache/so.txt')
});

const plugin = require(path.join(loader, 'compile_plugin.js'));
const main = require(path.join(loader, 'main.js'));
const { entryObj, globalModulePaths } = plugin.initConfig();
const projectConfig = main.projectConfig;
Object.assign(projectConfig, {
  buildMode: 'Debug', isPreview: true, packageManagerType: 'ohpm', packageDir: 'oh_modules', packageJson: 'oh-package.json5',
  projectTopDir: project, projectRootPath: project, modulePath: moduleDir, moduleName: 'entry', bundleName: app.bundleName,
  aceModuleJsonPath: process.env.aceModuleJsonPath, aceBuildJson: process.env.aceBuildJson, compileMode: 'esmodule',
  etsLoaderPath: loader, allowEtsAnnotations: true, compatibleSdkVersion: 20, compileSdkVersion: 26, runtimeOS: 'OpenHarmony',
  pkgContextInfo: { entry: { packageName: 'entry', bundleName: '', moduleName: '', version: '', entryPath: 'src/main/', isSO: false, dependencyAlias: '' } },
  entryPackageName: 'entry', entryModuleVersion: '1.0.0', compatibleSdkReleaseType: 'Release',
  globalModulePaths  // hvigor supplies this; the API-version warning suppressor reads it
});

const logs = [];
const logger = { info: m => logs.push('I ' + m), debug: () => {}, warn: m => logs.push('W ' + m), error: m => { logs.push('E ' + m); console.error('ERROR', String(m).slice(0, 2000)); },
  printError: e => { console.error('PRINTERROR', JSON.stringify(e).slice(0, 3000)); },
  printErrorAndExit: e => { console.error('FATAL', JSON.stringify(e).slice(0, 3000)); throw new Error('ets-loader fatal error (see FATAL above)'); },
  returnErrorFileId: () => {}, getErrorFileId: () => '', };
const share = {
  projectConfig, arkProjectConfig: undefined, allFiles: new Set(), allComponents: new Map(), rawfilechanged: false,
  getLogger: () => logger, getHvigorConsoleLogger: () => logger, throwArkTsCompilerError: m => { throw new Error(String(m)); }
};

// hvigor's rollup adds importedIdMaps (specifier -> resolved id) and package meta to ModuleInfo.
const importMaps = new Map();
const moduleMeta = { pkgName: 'entry', pkgPath: moduleDir, moduleName: 'entry', isLocalDependency: true, belongProjectPath: project };
// hvigor also lets plugins register async work (ctx.async) and report completion (ctx.signal).
let pendingAsync = 0; const asyncWaiters = [];
// The callback runs immediately and its return value (e.g. the es2abc child process) is handed back.
const asyncWork = fn => { pendingAsync++; return fn(); };
const asyncSignal = () => { pendingAsync--; if (pendingAsync <= 0) asyncWaiters.splice(0).forEach(r => r()); };
const waitAsync = () => pendingAsync <= 0 ? Promise.resolve() : new Promise(r => asyncWaiters.push(r));
function augment(ctx) {
  ctx.share = share; ctx.async = asyncWork; ctx.signal = asyncSignal;
  if (ctx.__augmented) return;
  const original = ctx.getModuleInfo.bind(ctx);
  ctx.getModuleInfo = id => {
    const info = original(id);
    if (!info) return info;
    return new Proxy(info, { get(target, key) {
      if (key === 'importedIdMaps') return importMaps.get(id) || {};
      if (key === 'meta') return Object.assign(target.meta || {}, moduleMeta, target.meta || {});
      if (key === 'metaInfo') return moduleMeta;
      if (key === 'originalCode') return fs.existsSync(id) ? fs.readFileSync(id, 'utf8') : target.code;
      return target[key];
    } });
  };
  ctx.__augmented = true;
}
const recorder = {
  name: 'ohos-import-map',
  async resolveId(source, importer, options) {
    if (!importer) return null;
    const external = /^@(ohos|kit|system|arkts|hms|bundle|package|app|native|normalized)[.:]/.test(source) || /\.so$/.test(source);
    const resolved = external ? { id: source, external: true } : await this.resolve(source, importer, { ...options, skipSelf: true });
    if (!importMaps.has(importer)) importMaps.set(importer, {});
    importMaps.get(importer)[source] = resolved ? resolved.id : source;
    return resolved;
  }
};

function wrap(p) {
  if (!p) return p;
  const wrapped = { name: p.name };
  for (const [k, v] of Object.entries(p)) {
    if (typeof v === 'function') wrapped[k] = function (...args) { augment(this); return v.apply(this, args); };
    else if (v && typeof v === 'object' && typeof v.handler === 'function') wrapped[k] = { ...v, handler: function (...args) { augment(this); return v.handler.apply(this, args); } };
    else wrapped[k] = v;
  }
  return wrapped;
}

(async () => {
  const { rollup } = require('rollup');
  const { nodeResolve } = require('@rollup/plugin-node-resolve');
  const plugins = plugin.sdkPlugins(projectConfig).filter(Boolean).map(wrap);
  const input = Object.assign({}, entryObj);
  const bundle = await rollup({
    input,
    // System modules are provided by the runtime; the SDK only has their declarations.
    external: id => /^@(ohos|kit|system|arkts|hms|bundle|package|app|native|normalized)[.:]/.test(id) || /\.so$/.test(id),
    plugins: [recorder, nodeResolve({ extensions: ['.js', '.ets', '.ts'], modulePaths: [src] }), ...plugins],
    onwarn: w => logs.push('RW ' + w.message)
  });
  await bundle.write({ dir: path.join(out, 'rollup'), format: 'es', sourcemap: true });
  await bundle.close();
  await waitAsync();
  fs.writeFileSync(path.join(out, 'build.log'), logs.join('\n'));
  const abc = path.join(out, 'ets/modules.abc');
  console.log('modules.abc', fs.existsSync(abc) ? fs.statSync(abc).size + ' bytes' : 'MISSING');
})().catch(e => { fs.writeFileSync(path.join(out, 'build.log'), logs.join('\n')); console.error('BUILD FAILED', e && e.message, 'ID=', e && e.id, 'LOC=', JSON.stringify(e && e.loc), 'HOOK=', e && e.hook, 'PLUGIN=', e && e.plugin, '\nSTACK', e && e.stack && e.stack.split('\n').slice(0,8).join('\n')); process.exit(1); });
