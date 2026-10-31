// New port boundary tests; not counted as original Eta platform tests.
import test from 'node:test';
import assert from 'node:assert/strict';
import { ScrollEndHapticGate } from '../../entry/src/main/ets/core/eta/hapticFeedback';
import { progressiveTopStops } from '../../entry/src/main/ets/core/eta/progressiveBlur';
test('progressive blur is limited to the measured top bar across viewport changes', () => {
  for (const [bar, viewport] of [[52,800],[110,400],[60,40]]) {
    const stops = progressiveTopStops(bar,viewport);
    assert.equal(stops[0].strength,1);
    assert.equal(stops.at(-1)?.strength,0);
    assert.equal(stops.at(-1)?.position,1);
    for(let i=1;i<stops.length;i++) {
      assert.ok(stops[i].position > stops[i-1].position);
      assert.ok(stops[i].strength <= stops[i-1].strength);
      if(stops[i].position >= bar/viewport) assert.equal(stops[i].strength,0);
    }
    assert.ok(Math.abs(stops[8].strength - (1-Math.pow(.5,2.2))) < 1e-10);
  }
  assert.ok(progressiveTopStops(0,800).every(s=>s.strength===0));
  assert.ok(progressiveTopStops(52,0).every(s=>s.strength===0));
});
test('scroll feedback ignores layout/drag, deduplicates edge and rearms after leaving', () => {
  const gate=new ScrollEndHapticGate();
  assert.equal(gate.update(0,0,false,true),false);
  assert.equal(gate.update(-5,0,false,false),false);
  assert.equal(gate.update(-5,0,false,true),true);
  assert.equal(gate.update(-5,0,false,true),false);
  assert.equal(gate.update(5,5,false,false),false);
  assert.equal(gate.update(-5,0,false,true),true);
  gate.reset();
  assert.equal(gate.update(5,100,true,true),true);
  assert.equal(gate.update(5,100,true,true),false);
});
