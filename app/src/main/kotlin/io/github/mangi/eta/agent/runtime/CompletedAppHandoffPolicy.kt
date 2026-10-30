package io.github.mangi.eta.agent.runtime

import org.json.JSONObject

internal enum class CompletedAppHandoff { Ready, Pending, Launch, Unavailable }

/** A moved task must never receive a second launcher intent while its window is settling. */
internal fun completedAppHandoff(result: JSONObject?): CompletedAppHandoff = when {
    result == null -> CompletedAppHandoff.Unavailable
    result.optString("code") == "HANDOFF_PENDING" ||
        result.has("windowReady") && !result.optBoolean("windowReady") -> CompletedAppHandoff.Pending
    result.optBoolean("ok") -> CompletedAppHandoff.Ready
    result.optString("code") == "NOT_FOUND" && !result.optBoolean("moved") -> CompletedAppHandoff.Launch
    else -> CompletedAppHandoff.Unavailable
}
