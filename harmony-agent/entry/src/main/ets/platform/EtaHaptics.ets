// Ported platform calls from app/src/main/kotlin/io/github/mangi/eta/ui/components/* @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { vibrator } from '@kit.SensorServiceKit';
import { EtaHaptic } from '../core/eta/hapticFeedback';

// Optional feedback must never prevent the user's action. No permission dialog:
// VIBRATE is normal/system_grant. Unsupported presets degrade to no vibration.
export class EtaHaptics {
  static perform(kind: EtaHaptic): void {
    const effect = kind === EtaHaptic.LongPress ? vibrator.HapticFeedback.EFFECT_HARD :
      kind === EtaHaptic.Confirm ? vibrator.HapticFeedback.EFFECT_NOTICE_SUCCESS :
      kind === EtaHaptic.ToggleOn ? vibrator.HapticFeedback.EFFECT_SHARP : vibrator.HapticFeedback.EFFECT_SOFT;
    try {
      if (!vibrator.isSupportEffectSync(effect)) return;
      vibrator.startVibration({ type: 'preset', effectId: effect, count: 1 }, { usage: 'touch' }).catch(() => {});
    } catch (_) { /* Device without a supported vibrator: preserve the action. */ }
  }
}
