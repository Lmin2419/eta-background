// Ported from app/src/main/kotlin/io/github/mangi/eta/data/provider/GeminiModelCapabilities.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { geminiReasoningCapabilities } from './reasoningCapabilityResolver';
export { geminiReasoningCapabilities };
export function geminiSupportsHostedSearch(id:string):boolean {return geminiReasoningCapabilities(id)!==null;}
export function geminiRequestReasoningCapabilities(id:string){return geminiReasoningCapabilities(id)||{supportedEfforts:[]};}
