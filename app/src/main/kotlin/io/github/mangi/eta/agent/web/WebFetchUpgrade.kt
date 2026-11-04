package io.github.mangi.eta.agent.web

/** A single read-only upgrade, bounded by the caller's remaining wall-clock budget. */
internal enum class WebFetchRoute { HTTP_FINGERPRINT, BROWSER, CLOUDFLARE }

internal data class WebFetchUpgradeRequest(
    val url: String,
    val reason: String,
    val preferredRoute: WebFetchRoute,
    val timeoutMillis: Long,
)

/** Providers must honour timeout/cancellation and return the final page, never a success placeholder. */
internal data class WebFetchUpgradeResult(
    val content: String,
    val finalUrl: String,
    val contentType: String,
    /** Null when the browser did not observe an HTTP status; never synthesize 200 from navigation. */
    val status: Int?,
    val provider: String,
    val truncated: Boolean = false,
    val challengeDetected: Boolean = false,
    val timingMillis: Map<String, Long> = emptyMap(),
    val title: String? = null,
)
