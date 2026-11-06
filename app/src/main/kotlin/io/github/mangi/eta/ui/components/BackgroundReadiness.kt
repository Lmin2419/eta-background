package io.github.mangi.eta.ui.components

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.eta.shadow.ShadowTools
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.model.PermissionHealthUiState
import io.github.mangi.eta.ui.model.PermissionStatusUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class ReadinessItem(val id: String, val title: Int, val status: PermissionStatusUi, val checking: Boolean = false)

internal fun readinessItems(state: PermissionHealthUiState, engineReady: Boolean?): List<ReadinessItem> {
    val statuses = state.items.associate { it.id to it.status }
    return listOf("root" to R.string.eta_ready_root, "accessibility" to R.string.eta_ready_accessibility,
        "overlay" to R.string.eta_ready_overlay, "notifications" to R.string.eta_ready_notifications).map { (id, title) ->
        ReadinessItem(id, title, statuses[id] ?: PermissionStatusUi.Missing, id !in statuses)
    } + ReadinessItem("engine", R.string.eta_ready_engine,
        if (engineReady == true) PermissionStatusUi.Available else PermissionStatusUi.Missing, engineReady == null)
}

internal fun interface EngineReadinessProbe { suspend fun read(context: Context): Boolean }
internal val LocalEngineReadinessProbe = staticCompositionLocalOf<EngineReadinessProbe> {
    EngineReadinessProbe { context ->
        withContext(Dispatchers.IO) {
            // Read-only diagnostic: 2s connect / 2.5s read timeout; no engine start or root request.
            val snapshot = ShadowTools.diagnosticSnapshot()
            val expected = context.assets.open("vdrive.version").bufferedReader().use { it.readText().trim() }
            snapshot.optBoolean("reachable") && snapshot.optBoolean("healthy") && snapshot.optString("build") == expected
        }
    }
}

/** Check once on entry/resume or explicit refresh. Retained/stopped pages do not poll. */
@Composable
internal fun rememberEngineReadiness(state: PermissionHealthUiState, refresh: Int = 0): Boolean? {
    val context = LocalContext.current.applicationContext
    val rootStatus = state.items.firstOrNull { it.id == "root" }?.status
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val probe = LocalEngineReadinessProbe.current
    val ready by produceState<Boolean?>(null, rootStatus, resumed, refresh, probe) {
        value = null
        if (rootStatus == null) return@produceState
        if (rootStatus != PermissionStatusUi.Available) { value = false; return@produceState }
        if (!resumed) return@produceState
        value = try { probe.read(context) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { false }
    }
    return ready
}
