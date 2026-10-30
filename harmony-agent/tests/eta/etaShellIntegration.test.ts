// New integration boundaries for AgentAppRoot / StartupSplash ports.
import test from 'node:test';
import assert from 'node:assert/strict';
import { EtaShellNavigation } from '../../entry/src/main/ets/core/eta/etaShellNavigation';
import { StartupContentHost } from '../../entry/src/main/ets/core/eta/startupSplash';
import { AppRoute, AppRouteName as R, encodeRoutes } from '../../entry/src/main/ets/core/eta/routes';
test('legacy page intents use the Eta stack and restore the revealed pane on return', () => {
  const n=new EtaShellNavigation(); n.drawer=true; n.push('settings'); n.push('providers'); n.provider('saved');
  assert.deepEqual(n.routes.map(r=>r.name),[R.Home,R.Settings,R.ModelProviders,R.ModelProviderDetail]);
  n.sheet='catalog'; n.back(); assert.equal(n.screen,'provider');
  n.back(); assert.equal(n.screen,'providers'); n.back(); assert.equal(n.screen,'settings');
  n.push('diagnostics'); n.back(); assert.equal(n.screen,'settings');
  n.back(); assert.equal(n.screen,'chat'); assert.equal(n.drawer,true); n.back(); assert.equal(n.drawer,false);
});
test('provider removal prunes detail instead of ignoring an existing list route', () => {
  const n=new EtaShellNavigation(); n.push('providers'); n.provider('deleted'); n.providersAfterDelete();
  assert.equal(n.screen,'providers'); assert.equal(n.routes.length,2);
});
test('process restoration removes unported pages and unsaved editor routes throughout the stack', () => {
  const n=new EtaShellNavigation();
  assert.equal(n.restore(encodeRoutes([new AppRoute(R.Home),new AppRoute(R.Memory),new AppRoute(R.Settings),new AppRoute(R.ModelProviderDetail,'s')])),true);
  assert.deepEqual(n.routes.map(r=>r.name),[R.Home,R.Settings]);
  assert.equal(n.restore('invalid'),false); assert.equal(n.screen,'settings');
});
test('first Harmony content waits for appearance and is loaded only once',async()=>{
  const host=new StartupContentHost(); const calls:string[]=[]; let ready=()=>{};
  const task=host.load(()=>new Promise<void>(r=>{ready=r;calls.push('prepare');}),async()=>{calls.push('content');});
  assert.deepEqual(calls,['prepare']); ready(); await task;
  await host.load(async()=>{calls.push('duplicate');},async()=>{calls.push('duplicate');});
  assert.deepEqual(calls,['prepare','content']);
});
test('destroy during appearance preparation never loads a destroyed window',async()=>{
  const host=new StartupContentHost(); let ready=()=>{}; let shown=false;
  const task=host.load(()=>new Promise<void>(r=>{ready=r;}),async()=>{shown=true;});
  host.destroy(); ready(); await task; assert.equal(shown,false);
});
