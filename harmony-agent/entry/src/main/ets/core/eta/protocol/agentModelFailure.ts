// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/model/AgentModelFailure.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { JsonObject, object, string, array } from './providerTypes';
import { describeParameterError } from './providerParameterDiagnostics';
const transientStatus = [408, 429, 500, 502, 503, 504, 524, 529];
const transientCodes = ['rate_limit_exceeded', 'rate_limit_error', 'overloaded_error', 'server_error', 'api_error', 'internal_error', 'provider_unavailable', 'service_unavailable', 'RESOURCE_EXHAUSTED', 'UNAVAILABLE', 'INTERNAL', 'DEADLINE_EXCEEDED'];
function permanent(error: JsonObject, body: string): boolean { return ['code', 'type'].some(k => ['insufficient_quota', 'quota_exceeded', 'billing_error', 'usage_limit_reached'].includes(string(error[k]))) || ['insufficient_quota', 'quota exceeded', 'out of budget', 'billing', 'usage limit'].some(c => body.toLowerCase().includes(c)); }
function special(error: JsonObject): AgentModelFailure | null {
  if (error.status === 'UNAUTHENTICATED' || array(error.details).some(d => ['API_KEY_INVALID', 'API_KEY_EXPIRED'].includes(string(object(d)?.reason)))) return new AgentModelFailure('PROVIDER_AUTH_FAILED', false, '模型接口认证失败，请检查 API Key。');
  const message = string(error.message).toLowerCase();
  if (['code', 'type'].some(k => ['context_length_exceeded', 'context_window_exceeded', 'prompt_too_long', 'input_too_long'].includes(string(error[k]))) || ['maximum context length', 'prompt is too long', 'exceeds the context window', 'input token count exceeds'].some(p => message.includes(p)) || message.includes('input token count') && message.includes('exceeds the maximum')) return new AgentModelFailure('CONTEXT_OVERFLOW', false, '模型上下文超过容量限制。'); return null;
}
/** Classification only: no automatic retry. Server message text is never exposed to UI/diagnostics. */
export class AgentModelFailure extends Error {
  constructor(readonly code: string, readonly retryable: boolean, message: string) { super(message); }
  static incompleteStream(message: string): AgentModelFailure { return new AgentModelFailure('STREAM_INCOMPLETE', true, message); }
  static stream(error: JsonObject): AgentModelFailure {
    const known = special(error); if (known) return known;
    const codes = [error.code, error.type, error.status, object(error.metadata)?.error_type].map(c => string(c)), detail = describeParameterError(error);
    return new AgentModelFailure('PROVIDER_STREAM_ERROR', !permanent(error, string(error.message)) && codes.some(c => transientCodes.includes(c) || transientStatus.includes(Number(c))), '模型接口 SSE 返回错误' + (detail ? '（' + detail + '）' : ''));
  }
  static http(status: number, body: string, parameters = false): AgentModelFailure {
    let error: JsonObject = {}; try { error = object(object(JSON.parse(body))?.error) || {}; } catch (_) { /* No server content is shown. */ }
    const known = special(error); if (known) return known; const fixed = permanent(error, body), detail = parameters && !fixed && status === 400 ? describeParameterError(error) : null;
    const messages: Record<number, string> = { 400: '模型请求参数无效（HTTP 400），请检查模型配置。', 401: '模型接口认证失败（HTTP 401），请检查 API Key。', 403: '模型接口拒绝访问（HTTP 403），请检查账户与模型权限。', 404: '模型接口或模型不存在（HTTP 404），请检查接口地址与模型名称。', 429: '模型接口暂时限流（HTTP 429）。' };
    return new AgentModelFailure('HTTP_' + status, transientStatus.includes(status) && !fixed, fixed ? `模型接口额度或计费受限（HTTP ${status}），请检查服务商账户。` : detail ? `模型请求参数无效（HTTP 400；${detail}），请检查模型配置。` : messages[status] || '模型接口返回 HTTP ' + status);
  }
}
