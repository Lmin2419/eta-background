package io.github.mangi.eta.agent.browser

import org.json.JSONObject

/** Page-local, bounded observation. Web content is untrusted; no JavaScript-to-native bridge. */
internal object BrowserFeedbackScripts {
    fun install(): String = """
        if (window.__etaFeedbackV1) return { installed: true };
        var state = { id: '', until: 0, messages: [], responses: [], bytes: 0, changed: false, seq: 0, seen: 0 };
        window.__etaFeedbackV1 = state;
        function active() { return Date.now() <= state.until && !!state.id; }
        function saveMessage(node) {
          if (!active() || state.messages.length >= 8 || !node || node.nodeType !== 1) return;
          var target = node.closest('[role="alert"],[role="status"],[aria-live="polite"],[aria-live="assertive"],[class*="toast"],[class*="snackbar"],[class*="message"],[class*="notice"]');
          if (!target && node.matches('[role="alert"],[role="status"],[aria-live],[class*="toast"],[class*="snackbar"]')) target = node;
          if (!target || !visible(target)) return;
          var text = collectVisibleText(target, 320, 80, Date.now() + 10).text;
          if (!text || state.messages.some(function(item) { return item.text === text; })) return;
          state.messages.push({ seq: ++state.seq, action_id: state.id, text: text, at_ms: Date.now() });
        }
        var observer = new MutationObserver(function(records) {
          if (!active()) return;
          var deadline = Date.now() + 15;
          for (var i = 0; i < records.length && i < 40 && Date.now() < deadline; i++) {
            var r = records[i];
            if (r.type === 'childList' || r.type === 'characterData') state.changed = true;
            saveMessage(r.target.nodeType === 1 ? r.target : r.target.parentElement);
            for (var j = 0; j < r.addedNodes.length && j < 8 && Date.now() < deadline; j++) saveMessage(r.addedNodes[j]);
          }
        });
        observer.observe(document.documentElement, { subtree: true, childList: true, characterData: true, attributes: true, attributeFilter: ['class','style','hidden','aria-hidden'] });
        function safeUrl(value) {
          try { var u = new URL(value, location.href); return u.origin === location.origin ? (u.origin + u.pathname).slice(0, 240) : null; } catch (_) { return null; }
        }
        function allowed(url, mime) {
          return !!url && !/\/(?:oauth|auth|login|token|session|credential|password|payment|checkout)(?:\/|$)/i.test(url) && /(?:application\/json|text\/plain)/i.test(mime || '');
        }
        function sanitize(value, depth) {
          if (depth > 8) return '[depth limit]';
          if (Array.isArray(value)) return value.slice(0, 40).map(function(v) { return sanitize(v, depth + 1); });
          if (value && typeof value === 'object') {
            var output = {};
            Object.keys(value).slice(0, 60).forEach(function(k) {
              output[k] = /token|password|passwd|secret|authorization|cookie|credential|session.?id|api.?key/i.test(k) ? '[redacted]' : sanitize(value[k], depth + 1);
            });
            return output;
          }
          return typeof value === 'string' ? value.slice(0, 4000) : value;
        }
        function saveResponse(id, url, status, mime, raw, truncated) {
          if (!active() || state.id !== id || state.responses.length >= 12 || state.bytes >= 32768) return;
          // Raw text cannot be reliably redacted. Only expose structured JSON, never headers or request bodies.
          var body;
          try { body = JSON.stringify(sanitize(JSON.parse(raw), 0)); } catch (_) { body = ''; }
          body = body.slice(0, Math.min(8192, 32768 - state.bytes));
          state.bytes += body.length;
          state.responses.push({ action_id: id, url: url, status: status, content_type: mime.slice(0, 80), body: body, truncated: truncated || body.length >= 8192 });
        }
        if (window.fetch) {
          var nativeFetch = window.fetch;
          window.fetch = function() {
            var pending = nativeFetch.apply(this, arguments);
            var id = state.id;
            if (active()) pending.then(function(response) {
              var url = safeUrl(response.url), mime = response.headers.get('content-type') || '';
              if (!active() || state.id !== id || !allowed(url, mime) || state.responses.length >= 12 || !response.body || !response.body.getReader) return;
              var reader = response.clone().body.getReader(), decoder = new TextDecoder(), text = '', count = 0;
              function read() {
                reader.read().then(function(part) {
                  if (!active() || state.id !== id) { reader.cancel(); return; }
                  if (part.done) { saveResponse(id, url, response.status, mime, text, false); return; }
                  count += part.value.byteLength;
                  if (count > 8192) { reader.cancel(); saveResponse(id, url, response.status, mime, '', true); return; }
                  text += decoder.decode(part.value, { stream: true }); read();
                }).catch(function() { reader.cancel().catch(function() {}); });
              }
              read();
            }).catch(function() {});
            return pending;
          };
        }
        if (window.XMLHttpRequest) {
          var nativeSend = XMLHttpRequest.prototype.send;
          XMLHttpRequest.prototype.send = function() {
            var xhr = this, id = state.id;
            if (active()) xhr.addEventListener('load', function() {
              var url = safeUrl(xhr.responseURL), mime = xhr.getResponseHeader('content-type') || '';
              if (!active() || state.id !== id || !allowed(url, mime)) return;
              if (xhr.responseType === '' || xhr.responseType === 'text') {
                var text = xhr.responseText;
                saveResponse(id, url, xhr.status, mime, text.length <= 8192 ? text : '', text.length > 8192);
              }
            }, { once: true });
            return nativeSend.apply(this, arguments);
          };
        }
        return { installed: true };
    """.trimIndent()

    fun arm(actionId: String): String = """
        var state = window.__etaFeedbackV1;
        if (!state) return { available: false };
        state.id = ${JSONObject.quote(actionId)}; state.until = Date.now() + 5000;
        state.changed = false; state.messages = []; state.responses = []; state.bytes = 0; state.seen = state.seq;
        return { available: true, action_id: state.id };
    """.trimIndent()

    fun read(acknowledge: Boolean): String = """
        var state = window.__etaFeedbackV1;
        if (!state) return { available: false, messages: [], changed: false };
        var pending = state.messages.filter(function(item) { return item.seq > state.seen; });
        if ($acknowledge) state.seen = state.seq;
        return { available: true, action_id: state.id, messages: pending, changed: state.changed, observation_window_open: Date.now() <= state.until };
    """.trimIndent()

    fun responses(): String = """
        var state = window.__etaFeedbackV1;
        return { responses: state ? state.responses : [], capture_scope: 'same_origin_json_after_observed_action', source_role: 'untrusted_web_content', includes_request_headers: false, includes_request_bodies: false };
    """.trimIndent()
}
