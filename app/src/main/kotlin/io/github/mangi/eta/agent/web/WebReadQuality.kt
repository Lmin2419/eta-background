package io.github.mangi.eta.agent.web

import java.util.Locale
import org.jsoup.Jsoup

/** Conservative structural checks. Mentioning JavaScript/Cloudflare in an article is not a failure. */
internal object WebReadQuality {
    data class Problem(val code: String, val message: String, val route: WebFetchRoute? = null)

    fun inspect(status: Int?, contentType: String?, text: String, challengeDetected: Boolean = false): Problem? {
        if (challengeDetected) return challenge()
        if (status != null && status !in 200..299) return when (status) {
            401 -> Problem("WEB_LOGIN_REQUIRED", "目标网站要求登录；请在浏览器中完成登录后读取")
            403 -> Problem("WEB_ACCESS_DENIED", "目标网站拒绝读取（HTTP 403）", WebFetchRoute.HTTP_FINGERPRINT)
            404, 410 -> Problem("WEB_NOT_FOUND", "目标网页不存在（HTTP $status），请使用实际检索到的链接")
            429 -> Problem("WEB_RATE_LIMITED", "目标网站限制请求，请稍后再试")
            else -> Problem("WEB_HTTP_ERROR", "网页请求失败（HTTP $status）")
        }
        if (contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT) !in setOf("text/html", "application/xhtml+xml")) {
            return if (text.isBlank()) Problem("WEB_EMPTY_CONTENT", "响应成功，但没有可读取的正文") else null
        }
        val document = Jsoup.parse(text.take(WebPageContent.MAX_INPUT_CHARS))
        val title = document.title().trim().lowercase(Locale.ROOT)
        val body = document.body().clone().apply { select("script,style,noscript,template").remove() }.text().trim()
        val shortPage = body.length < 1_200
        if (shortPage && (title in setOf("just a moment...", "just a moment", "attention required! | cloudflare") ||
                document.selectFirst("#challenge-running, #challenge-stage, #cf-challenge-running, form#challenge-form") != null ||
                (document.selectFirst("script[src*=/cdn-cgi/challenge-platform/]") != null && body.length < 500))) return challenge()
        if (shortPage && (title.matches(Regex("^(404(?:\\s*[-:|]?\\s*(?:page (?:is )?not found|not found))?|page not found|not found|页面不存在|页面未找到)$")) ||
                document.selectFirst("h1")?.text()?.trim()?.lowercase(Locale.ROOT) in setOf("404", "404 not found", "page not found"))) {
            return Problem("WEB_NOT_FOUND", "网站返回了不存在页面，未把 HTTP 200 当作有效正文")
        }
        if (shortPage && document.selectFirst("input[type=password]") != null &&
            title.matches(Regex("^(sign in|log in|login|登录)(\\s*[-|·]\\s*.+)?$"))) {
            return Problem("WEB_LOGIN_REQUIRED", "当前内容是登录页面，请在浏览器中完成登录后读取")
        }
        if (body.length < 120 && document.selectFirst("script[src]") != null &&
            (body.isBlank() || document.selectFirst("#root, #app, #__next, app-root") != null ||
                document.select("noscript").text().contains("javascript", ignoreCase = true))) {
            return Problem("WEB_JAVASCRIPT_REQUIRED", "静态响应只有页面外壳，需要浏览器渲染", WebFetchRoute.BROWSER)
        }
        return if (body.isBlank()) Problem("WEB_EMPTY_CONTENT", "响应成功，但没有可读取的正文") else null
    }

    private fun challenge() = Problem("WEB_CHALLENGE", "网站仍在进行人机验证，尚未取得目标正文", WebFetchRoute.CLOUDFLARE)
}
