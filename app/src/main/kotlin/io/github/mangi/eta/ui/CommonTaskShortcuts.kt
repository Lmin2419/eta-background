package io.github.mangi.eta.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.mangi.eta.R
import io.github.mangi.eta.data.repository.CommonTask

data class CommonTaskLaunchRequest(val taskId: String, val token: String, val requestId: String)

internal object CommonTaskShortcuts {
    private const val PREFIX = "eta-common-task-"

    fun requestPin(context: Context, task: CommonTask): Boolean =
        ShortcutManagerCompat.isRequestPinShortcutSupported(context) &&
            ShortcutManagerCompat.requestPinShortcut(context, info(context, task), null)

    fun update(context: Context, task: CommonTask): Boolean =
        ShortcutManagerCompat.updateShortcuts(context, listOf(info(context, task)))

    fun disable(context: Context, id: String) {
        ShortcutManagerCompat.disableShortcuts(context, listOf(PREFIX + id),
            context.getString(R.string.common_tasks_missing))
    }

    internal fun intent(context: Context, task: CommonTask): Intent = Intent(context, MainActivity::class.java)
        .setAction(MainActivity.ACTION_RUN_COMMON_TASK)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        // Only a local identifier and an unguessable capability; never an externally supplied instruction.
        .putExtra(MainActivity.EXTRA_COMMON_TASK_ID, task.id)
        .putExtra(MainActivity.EXTRA_COMMON_TASK_TOKEN, task.shortcutToken)

    private fun info(context: Context, task: CommonTask) = ShortcutInfoCompat.Builder(context, PREFIX + task.id)
        .setShortLabel(task.name)
        .setLongLabel(task.name)
        .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
        .setIntent(intent(context, task))
        .build()
}
