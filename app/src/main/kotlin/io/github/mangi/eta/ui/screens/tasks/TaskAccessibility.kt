package io.github.mangi.eta.ui.screens.tasks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.overlay.localizedText
import io.github.mangi.eta.agent.runtime.TaskNotificationState
import io.github.mangi.eta.ui.components.label
import io.github.mangi.eta.ui.model.TaskVisualStatus
import io.github.mangi.eta.ui.model.toTaskVisualStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Full accessible text stays independent of visual ellipses and the one-second elapsed timer. */
internal fun taskAccessibilityText(app: String?, title: String, status: String, step: String): String =
    listOfNotNull(app?.takeIf { it.isNotBlank() }, title, status, step).filter { it.isNotBlank() }.distinct().joinToString(", ")

@Composable
internal fun taskAccessibilitySummary(task: TaskNotificationState): String {
    val context = LocalContext.current
    val appName by produceState<String?>(null, task.targetPackage) {
        value = withContext(Dispatchers.IO) { task.targetPackage?.let { pkg -> runCatching {
            context.packageManager.getApplicationInfo(pkg, 0).loadLabel(context.packageManager).toString()
        }.getOrNull() } }
    }
    val status = task.toTaskVisualStatus()
    val label = if (status == TaskVisualStatus.Running) stringResource(R.string.eta_accessibility_background_running) else status.label()
    return taskAccessibilityText(appName, task.title, label, task.status.localizedText())
}
