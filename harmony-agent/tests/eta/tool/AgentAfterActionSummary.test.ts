// Ported from app/src/test/kotlin/io/github/mangi/eta/agent/tool/AgentAfterActionSummaryTest.kt @ 3b1794d
import test from 'node:test';
import assert from 'node:assert/strict';
import { ElementObservation, ElementSource, UiNode } from '../../../entry/src/main/ets/core/eta/device/observation';
import { buildAfterActionSummary } from '../../../entry/src/main/ets/core/eta/tool/AgentAfterActionSummary';

function node(index: number, text: string, clickable = false): UiNode {
  return {
    index,
    text,
    desc: '',
    className: 'android.widget.TextView',
    packageName: 'app',
    viewId: '',
    bounds: { left: 0, top: 0, right: 100, bottom: 50 },
    clickable,
    longClickable: false,
    scrollable: false,
    focused: false,
    editable: false,
    password: false,
    enabled: true,
  };
}

function observation(id: string, pkg: string, nodes: UiNode[]): ElementObservation {
  return {
    id,
    source: ElementSource.ACCESSIBILITY,
    packageName: pkg,
    windowId: 1,
    nodes,
    maxNodes: 30,
    truncated: false,
  };
}

test('unchangedScreenIsReportedSoTheModelDoesNotRepeatTheSameAction', () => {
  const before = observation('o1', 'app.a', [node(0, '搜索')]);
  const summary = buildAfterActionSummary(before, observation('o2', 'app.a', [node(0, '搜索')]));
  assert.equal(summary.screen_changed, false);
  assert.equal(summary.package_changed, false);
  assert.equal(summary.observation_id, 'o2');
});

test('changedScreenCarriesCompactNodesAndPackageSwitch', () => {
  const before = observation('o1', 'app.a', [node(0, '搜索')]);
  const after = observation('o2', 'app.b', [node(0, '结果', true)]);
  const summary = buildAfterActionSummary(before, after);
  assert.equal(summary.screen_changed, true);
  assert.equal(summary.package_changed, true);
  const compact = summary.ui_nodes[0];
  assert.equal(compact.text, '结果');
  assert.equal(compact.class, 'TextView');
  assert.equal(compact.clickable, true);
  assert.equal('editable' in compact, false);
  assert.equal('password' in compact, false);
});

test('toggleStateAndHintAreVisibleToTheModel', () => {
  const toggle: UiNode = { ...node(0, '蓝牙'), checked: false };
  const field: UiNode = { ...node(1, ''), editable: true, hint: '搜索联系人' };
  const nodes = buildAfterActionSummary(null, observation('o1', 'app', [toggle, field])).ui_nodes;
  assert.equal(nodes[0].checked, false);
  assert.equal(nodes[1].hint, '搜索联系人');
  assert.equal('checked' in nodes[1], false);
});
