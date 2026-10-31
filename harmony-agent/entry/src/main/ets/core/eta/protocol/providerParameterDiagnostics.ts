// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/model/ProviderParameterDiagnostics.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { JsonObject, array, object, string } from './providerTypes';
const codes = ['INVALID_ARGUMENT', 'FAILED_PRECONDITION', 'invalid_request', 'invalid_argument', 'invalid_request_error', 'unsupported_parameter', 'model_not_found'];
const roots = ['model', 'input', 'system_instruction', 'tools', 'generation_config', 'response_format', 'stream', 'store', 'previous_interaction_id', 'background', 'safety_settings', 'service_tier'];
const names = [...roots, 'content', 'type', 'role', 'name', 'description', 'parameters', 'properties', 'items', 'required', 'additionalProperties', 'oneOf', 'anyOf', 'allOf', 'enum', 'const', 'format', 'minimum', 'maximum', 'minItems', 'maxItems', 'minLength', 'maxLength', 'pattern', 'thinking_level', 'thinking_summaries', 'tool_choice', 'max_output_tokens', 'temperature', 'top_p', 'text', 'data', 'uri', 'mime_type', 'schema', 'arguments', 'result', 'call_id', 'signature'];
function path(raw: string): string | null {
  if (raw.length > 192 || !/^[A-Za-z_][A-Za-z0-9_]*(?:\[[0-9]{1,6}\])?(?:\.[A-Za-z_][A-Za-z0-9_]*(?:\[[0-9]{1,6}\])?)*$/.test(raw)) return null;
  const segments = raw.split('.'); if (!roots.includes(segments[0].split('[')[0])) return null;
  return segments.map(s => names.includes(s.split('[')[0]) ? s : '<自定义字段>').join('.');
}
export function describeParameterError(error: JsonObject | null): string | null {
  if (!error) return null;
  const code = ['status', 'code', 'type'].map(k => string(error[k])).find(c => codes.includes(c)), fields = new Set<string>();
  const first = path(string(error.param)); if (first) fields.add(first);
  for (const detail of array(error.details)) {
    for (const violation of array(object(detail)?.fieldViolations)) { const p = path(string(object(violation)?.field)); if (p) fields.add(p); if (fields.size >= 3) break; }
    if (fields.size >= 3) break;
  }
  return [...(code ? [code] : []), ...[...fields].slice(0, 3).map(f => '字段 ' + f)].join('；') || null;
}
