'use strict';
// Executed by check:ets with the real SDK. npm test remains independent of SDK installation.
const test = require('node:test');
const assert = require('node:assert/strict');
const os = require('node:os');
const path = require('node:path');
const { loadSdk, checkSources } = require('../arkui-parent-check.cjs');
const loader = path.join(process.env.OHOS_SDK_ETS || path.join(os.homedir(), '.cache/ohos-sdk-26/ets'), 'build-tools/ets-loader');
const sdk = loadSdk(loader);
const check = body => checkSources(sdk, [{ file: 'Fixture.ets', text: '@Component\nstruct Fixture { build() {\n' + body + '\n} }' }]);

test('reproduces the reported 10905201 for a list anchor and preserves its source location', () => {
  const errors = check('List() {\n  ListItem() { Blank().width("100%").height(1) }\n}');
  assert.equal(errors.length, 1);
  assert.equal(errors[0].code, '10905201');
  assert.equal(errors[0].line, 4);
  assert.equal(errors[0].column, 16);
  assert.match(errors[0].message, /'Blank'.*'Row,Column,Flex'/);
});
test('a fixed-size empty Row anchor is a valid ListItem child', () => {
  assert.deepEqual(check('List() { ListItem() { Row() {}.width("100%").height(1) } }'), []);
});
test('Blank is allowed inside Row, Column and Flex, including an intervening conditional', () => {
  for (const parent of ['Row', 'Column', 'Flex']) assert.deepEqual(check(parent + '() { if (true) { Blank() } }'), []);
});
test('conditionals and ForEach do not conceal a forbidden native parent', () => {
  const errors = check('Stack() { if (true) { ForEach([1], (value: number) => { Blank() }) } }');
  assert.equal(errors.length, 1); assert.equal(errors[0].code, '10905201');
});
test('custom builder parents and standalone roots are not falsely treated as native parents', () => {
  assert.deepEqual(check('Blank().height(1)'), []);
  const source = '@Component struct Slot { build() { Column() {} } }\n@Component struct Host { build() { Slot() { Blank() } } }';
  assert.deepEqual(checkSources(sdk, [{ file: 'Custom.ets', text: source }]), []);
});
test('SDK restrictions also catch a ListItem in Column while accepting ListItemGroup', () => {
  assert.equal(check('Column() { ListItem() { Text("wrong parent") } }')[0].code, '10905201');
  assert.deepEqual(check('List() { ListItemGroup() { ListItem() { Text("valid") } } }'), []);
});
test('malformed ArkUI syntax fails instead of silently returning an incomplete scan', () => {
  assert.ok(check('List() { ListItem() {').length > 0);
});
