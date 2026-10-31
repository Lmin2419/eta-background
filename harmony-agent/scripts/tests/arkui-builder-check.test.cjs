'use strict';
// SDK AST + JavaScript receiver semantics; not a native UI/device rendering test.
const test = require('node:test');
const assert = require('node:assert/strict');
const os = require('node:os');
const path = require('node:path');
const { loadSdk, readSources, analyzeSources } = require('../arkui-builder-check.cjs');
const loader = path.join(process.env.OHOS_SDK_ETS || path.join(os.homedir(), '.cache/ohos-sdk-26/ets'), 'build-tools/ets-loader');
const sdk = loadSdk(loader);
const slot = '@Component struct Slot { @BuilderParam content: () => void; build() { Column() { this.content() } } }';
const check = code => analyzeSources(sdk, [{ file: '/fixture/Main.ets', text: slot + '\n' + code }]);

test('bare builder references reproduce the launch failure and point to the slot assignment', () => {
  const result = check('@Component struct Host { @Builder body() { Text(this.state.busy) } build() { Slot({ content: this.body }) } }');
  assert.equal(result.errors.length, 1);
  assert.equal(result.errors[0].code, 'HA_BUILDER_RECEIVER');
  assert.equal(result.errors[0].line, 2);
  const parent = { state: { busy: false }, body() { return this.state.busy; } };
  const child = { content: parent.body };
  assert.throws(() => child.content(), /Cannot read properties of undefined \(reading 'busy'\)/);
});
test('owner-capturing arrows are accepted and observe replacement state on later calls', () => {
  assert.deepEqual(check('@Component struct Host { @Builder body() {} build() { Slot({content: () => { this.body(); }}) } }').errors, []);
  const parent = { state: { busy: false }, body() { return this.state.busy; } };
  const child = { content: (() => { return parent.body(); }) };
  assert.equal(child.content(), false);
  parent.state = { busy: true };
  assert.equal(child.content(), true);
});
test('forwarded BuilderParam slots also require an explicit owner', () => {
  assert.equal(check('@Component struct Forward { @BuilderParam body: () => void; build() { Slot({content: this.body}) } }').errors.length, 1);
  assert.deepEqual(check('@Component struct Forward { @BuilderParam body: () => void; build() { Slot({content: () => { this.body(); }}) } }').errors, []);
});
test('named import aliases resolve to the declaring component and inspect dialog calls', () => {
  const result = analyzeSources(sdk, [
    { file: '/fixture/Slot.ets', text: slot },
    { file: '/fixture/Host.ets', text: "import { Slot as DialogSlot } from './Slot'; @Component struct Host { @Builder body() {} private dialog = new CustomDialogController({builder: DialogSlot({content: this.body})}); build() { Column() {} } }" }
  ]);
  assert.equal(result.errors.length, 1); assert.equal(result.errors[0].component, 'DialogSlot');
});
test('ordinary props/callbacks and framework builder attributes are outside this custom-slot rule', () => {
  const result = check('@Component struct Normal { content: string; onClick: () => void; build() { Text(this.content) } } @Component struct Host { content: string; @Builder body() {} build() { Column() { Normal({content: this.content, onClick: this.body}) }.bindMenu(this.body) } }');
  assert.deepEqual(result.errors, []); assert.equal(result.slots.length, 0);
});
test('stateless global builders are allowed and parse errors are never silently ignored', () => {
  assert.deepEqual(check('@Builder function Body() {} @Component struct Host { build() { Slot({content: Body}) } }').errors, []);
  assert.ok(check('@Component struct Host { build() { Slot({content:').errors.length > 0);
});

const root = path.resolve(__dirname, '../../entry/src/main/ets');
const project = analyzeSources(sdk, readSources(root));
// Read the actual project slot expressions, so the regression fails if a future edit restores
// the unbound source. Invoke as a child method exactly as @BuilderParam does at runtime.
function slotFunction(fileSuffix, component, parameter, owner, expression) {
  const found = project.slots.find(item => item.file.endsWith(fileSuffix) && item.component === component &&
    item.parameter === parameter && (!expression || item.expression.includes(expression)));
  assert.ok(found, 'Missing real project slot: ' + fileSuffix + '/' + component + '.' + parameter);
  return Function('return (' + found.expression + ');').call(owner);
}
test('real home and pane slots retain shell state, live updates and controller actions', t => {
  // E02 has the common components/Gallery, and E03 adds the production shell.
  if (!project.slots.some(item => item.file.endsWith('/navigation/AgentAppShell.ets'))) { t.skip('Production shell is introduced in E03.'); return; }
  const calls = [], buttons = [];
  const owner = { state: { busy: false }, controller: { drawer: value => calls.push(value) },
    homeContent() { buttons.push({ enabled: !this.state.busy, click: () => this.controller.drawer(true) }); },
    pane() { calls.push(this.state.busy); } };
  const child = {
    content: slotFunction('/navigation/AgentAppShell.ets', 'ConversationSidePaneScaffold', 'content', owner),
    pane: slotFunction('/navigation/AgentAppShell.ets', 'ConversationSidePaneScaffold', 'pane', owner)
  };
  child.content(); child.pane(); buttons[0].click();
  owner.state = { busy: true }; child.content(); child.pane();
  assert.deepEqual(buttons.map(button => button.enabled), [true, false]);
  assert.deepEqual(calls, [false, true, true]);
});
test('real scaffold top bar and nested back-button slots keep the outer onBack handler', () => {
  const calls = []; const owner = { title: 'Settings', onBack: () => calls.push('back'),
    back() { return this.onBack(); }, topBar() { calls.push(this.title); this.back(); } };
  const backdrop = { content: slotFunction('/components/MiuixScaffoldPage.ets', 'TopBarBackdrop', 'content', owner) };
  const topbar = { navigationIcon: slotFunction('/components/MiuixScaffoldPage.ets', 'AdaptiveTopAppBar', 'navigationIcon', owner) };
  backdrop.content(); topbar.navigationIcon();
  assert.deepEqual(calls, ['Settings', 'back', 'back']);
});
test('real card/group forwarding preserves the original owner through two children', () => {
  const calls = []; const owner = { label: 'Current', body() { calls.push(this.label); } };
  const group = { content: () => owner.body() };
  const card = { content: slotFunction('/components/EtaPreferenceStyle.ets', 'EtaCard', 'content', group) };
  card.content(); owner.label = 'Updated'; card.content();
  assert.deepEqual(calls, ['Current', 'Updated']);
});
test('real gallery dialog actions keep the supplying page and all project slots pass the audit', () => {
  const calls = []; const gallery = { dialog: { close: () => calls.push('closed') },
    dialogActions() { this.dialog.close(); } };
  const dialog = { content: slotFunction('/pages/EtaGallery.ets', 'EtaWindowDialog', 'content', gallery) };
  dialog.content(); assert.deepEqual(calls, ['closed']);
  assert.deepEqual(project.errors, []);
});
