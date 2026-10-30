// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/TopBarBackdrop.kt @ 3b1794d.
// Miuix ProgressiveBlur.Top / internal/Shaders.kt @ 39c40f9, Apache-2.0.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { EtaBlurDefaults } from './tokens';
export interface BlurStop { strength: number; position: number; }

// Sample the source shader's 1 - pow(smoothstep(0,1,y), curve), then keep the
// rest of the viewport clear. This is an approximation by linear segments;
// it is not a claim of identical Android/Harmony Gaussian kernels.
export function progressiveTopStops(barHeight: number, viewportHeight: number): BlurStop[] {
  if (!Number.isFinite(barHeight) || !Number.isFinite(viewportHeight) || barHeight <= 0 || viewportHeight <= 0) {
    return [{ strength: 0, position: 0 }, { strength: 0, position: 1 }];
  }
  const extent = Math.min(1, barHeight / viewportHeight);
  const result: BlurStop[] = [];
  const segments = 16;
  for (let i = 0; i <= segments; i++) {
    const t = i / segments;
    result.push({ strength: 1 - Math.pow(t * t * (3 - 2 * t), EtaBlurDefaults.ProgressiveCurve), position: t * extent });
  }
  if (extent < 1) result.push({ strength: 0, position: 1 });
  return result;
}
