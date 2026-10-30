// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/EtaPreferenceRow.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { EtaPreferenceDefaults as P, EtaComponentSizes as S, EtaComponentBehavior as B } from './tokens';
export class PreferenceRowMeasure {
  leadingWidth = 0;
  available = 0;
  actionGap = 0;
  actionMaxWidth = 0;
  titleMaxWidth = 0;
}
/** Content width excludes the row's horizontal padding, exactly as Compose's Layout constraints. */
export function measurePreferenceRow(contentWidth: number, leadingWidth: number | null, hasActions: boolean, measuredActionWidth = 0): PreferenceRowMeasure {
  const result = new PreferenceRowMeasure();
  result.leadingWidth = leadingWidth === null ? 0 : leadingWidth + P.IconTextGap;
  result.available = Math.max(0, contentWidth - result.leadingWidth);
  result.actionGap = hasActions ? Math.min(S.Dp12, result.available) : 0;
  result.actionMaxWidth = hasActions ? Math.floor((result.available - result.actionGap) * B.ActionWidthFraction) : 0;
  result.titleMaxWidth = Math.max(0, result.available - result.actionGap - Math.min(measuredActionWidth, result.actionMaxWidth));
  return result;
}
