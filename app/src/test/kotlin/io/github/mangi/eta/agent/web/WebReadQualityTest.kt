package io.github.mangi.eta.agent.web

import org.junit.Assert.*
import org.junit.Test

class WebReadQualityTest {
    @Test fun ordinaryArticlesAndShortAnswersAreNotChallengeOrJavaScriptPages() {
        for (html in listOf(
            "<article><h1>Cloudflare and JavaScript</h1>Turnstile setup guide</article><script src='/analytics.js'></script>",
            "<main>42</main>",
            "<main>A working result</main><script src='/app.js'></script>",
            "<title>404 error debugging guide</title><article>How to diagnose HTTP failures</article>",
            "<title>Login tutorial</title><article>Example form:</article><input type='password'>",
        )) assertNull(WebReadQuality.inspect(200, "text/html", html))
    }

    @Test fun soft404AndLoginFormsAreNotReadableDocuments() {
        assertEquals("WEB_NOT_FOUND", WebReadQuality.inspect(200, "text/html", "<title>404: Page is not found</title><h1>404</h1>")?.code)
        assertEquals("WEB_LOGIN_REQUIRED", WebReadQuality.inspect(200, "text/html", "<title>Sign in</title><input type='password'>")?.code)
        assertEquals("WEB_EMPTY_CONTENT", WebReadQuality.inspect(200, "text/plain", " ")?.code)
        assertEquals("WEB_CHALLENGE", WebReadQuality.inspect(403, "text/html", "", true)?.code)
    }
}
