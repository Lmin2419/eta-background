// Integration boundaries for AgentModelPicker.kt @ 3b1794d; supplements the source tests.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import test from 'node:test';
import assert from 'node:assert/strict';
import { ModelPickerSession, projectModelPicker } from '../../entry/src/main/ets/core/eta/modelPicker';
import { foundationPickerProviders } from '../../entry/src/main/ets/core/eta/foundationModelPicker';
test('picker confirms once after 140ms; duplicate taps cannot change the pending choice', t => {
  t.mock.timers.enable({apis:['setTimeout']});
  const picker = new ModelPickerSession(), selected: string[] = [];
  picker.open();
  assert.equal(picker.choose('a', id => selected.push(id)), true);
  assert.equal(picker.choose('b', id => selected.push(id)), false);
  t.mock.timers.tick(139); assert.deepEqual(selected, []);
  t.mock.timers.tick(1); assert.deepEqual(selected, ['a']);
  t.mock.timers.tick(1000); assert.deepEqual(selected, ['a']);
});
test('dismiss, background or navigation invalidates delayed selection', t => {
  t.mock.timers.enable({apis:['setTimeout']});
  const picker = new ModelPickerSession(), selected: string[] = [];
  picker.open(); picker.choose('a', id => selected.push(id));
  picker.cancel(); t.mock.timers.tick(1000);
  assert.deepEqual(selected, []); assert.equal(picker.pendingId, null);
});
test('a reopened picker resets query/filter and cannot receive a previous delayed choice', t => {
  t.mock.timers.enable({apis:['setTimeout']});
  const picker = new ModelPickerSession(), selected: string[] = [];
  picker.query='old'; picker.provider='old'; picker.choose('a', id => selected.push(id));
  picker.open(); picker.choose('b', id => selected.push(id));
  assert.equal(picker.query, ''); assert.equal(picker.provider, null);
  t.mock.timers.tick(140); assert.deepEqual(selected, ['b']);
});
test('legacy anonymous profiles retain catalog, selection and limits without projecting endpoint or secret reference', () => {
  const providers = foundationPickerProviders([{id:'p',name:'Local',baseUrl:'http://localhost/v1',model:'old',models:['new'],screenshots:false,allowInsecureHttp:true,secretRef:'model-key-private',metadata:[{id:'old',name:'Old model',contextWindow:32768,source:'provider'}]}]);
  const picker = projectModelPicker(providers, 'p', 'p:old');
  assert.deepEqual(picker.providerGroups[0].models.map(m=>m.modelId), ['new','old']);
  assert.equal(picker.selectedModel?.contextWindow,32768);
  assert.equal(picker.selectedModel?.displayName,'Old model');
  assert.equal(JSON.stringify(providers).includes('private'),false);
  assert.equal(JSON.stringify(providers).includes('localhost'),false);
});
