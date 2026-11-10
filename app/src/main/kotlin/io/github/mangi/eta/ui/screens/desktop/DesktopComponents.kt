package io.github.mangi.eta.ui.screens.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.remote.DesktopApproval
import io.github.mangi.eta.agent.remote.DesktopBridge
import io.github.mangi.eta.agent.remote.DesktopBridgeClient
import io.github.mangi.eta.agent.remote.DesktopBridgeException
import io.github.mangi.eta.ui.components.EtaButton
import io.github.mangi.eta.ui.components.EtaButtonKind
import io.github.mangi.eta.ui.components.EtaPreferenceDefaults
import io.github.mangi.eta.ui.components.EtaWindowDialog
import io.github.mangi.eta.ui.components.StatusError
import io.github.mangi.eta.ui.components.StatusRunning
import io.github.mangi.eta.ui.components.StatusSuccess
import io.github.mangi.eta.ui.components.StatusWarning
import io.github.mangi.eta.ui.theme.EtaTheme
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 一行连接状态：圆点 + 文案，断开或被拒绝时附带重试。 */
@Composable
internal fun DesktopConnectionBanner(
    state: DesktopBridgeClient.State,
    configured: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (color, text) = when (state) {
        is DesktopBridgeClient.State.Connected ->
            StatusSuccess to stringResource(R.string.desktop_state_connected, state.host.ifBlank { "dsh-bridge" }, state.latencyMs.toInt())
        is DesktopBridgeClient.State.Connecting ->
            StatusWarning to (state.lastError?.let { stringResource(R.string.desktop_state_reconnecting, it) } ?: stringResource(R.string.desktop_state_connecting))
        is DesktopBridgeClient.State.Unauthorized -> StatusError to stringResource(R.string.desktop_state_refused, state.message)
        DesktopBridgeClient.State.Idle -> EtaTheme.colors.textTertiary to stringResource(
            if (configured) R.string.desktop_state_idle else R.string.desktop_not_configured,
        )
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = EtaPreferenceDefaults.SidePadding + 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            color = EtaTheme.colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (configured && (state is DesktopBridgeClient.State.Unauthorized || state is DesktopBridgeClient.State.Connecting)) {
            EtaButton(text = stringResource(R.string.desktop_retry), onClick = onRetry, kind = EtaButtonKind.Ghost)
        }
    }
}

/** 按到达顺序依次弹出待审批操作；答完一个自动显示下一个。 */
@Composable
internal fun DesktopApprovalHost(approvals: List<DesktopApproval>, sessionId: String? = null) {
    val approval = approvals.firstOrNull { sessionId == null || it.sessionId == sessionId } ?: return
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    fun answer(allow: Boolean) {
        scope.launch {
            try {
                DesktopBridge.answerApproval(approval.id, allow)
            } catch (error: DesktopBridgeException) {
                Toast.makeText(context, error.display(resources), Toast.LENGTH_SHORT).show()
            }
        }
    }
    EtaWindowDialog(
        show = true,
        title = stringResource(R.string.desktop_approval_title, approval.toolName),
        onDismissRequest = null,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            approval.reason?.let {
                Text(
                    text = it,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                EtaButton(
                    text = stringResource(R.string.desktop_approval_reject),
                    onClick = { answer(false) },
                    kind = EtaButtonKind.Destructive,
                    modifier = Modifier.weight(1f),
                )
                EtaButton(
                    text = stringResource(R.string.desktop_approval_allow),
                    onClick = { answer(true) },
                    kind = EtaButtonKind.Primary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
internal fun DesktopRunningDot(running: Boolean) {
    if (!running) return
    Box(Modifier.size(8.dp).background(StatusRunning, CircleShape))
}

/**
 * 给用户看的失败说明。常见情况有明确的中文解释；电脑端内部错误多半是插件或 DSH 版本不匹配，
 * 保留原文方便排查。
 */
internal fun DesktopBridgeException.display(resources: android.content.res.Resources): String = when (code) {
    "not-queued" -> resources.getString(R.string.desktop_error_not_queued)
    "busy" -> resources.getString(R.string.desktop_error_busy)
    "not-live" -> resources.getString(R.string.desktop_error_not_live)
    "unknown-op", "bad-request" -> resources.getString(R.string.desktop_error_old_plugin)
    "internal", "gateway/internal" -> resources.getString(R.string.desktop_error_internal, message.orEmpty())
    "offline", "timeout" -> message ?: resources.getString(R.string.desktop_error_offline)
    else -> resources.getString(R.string.desktop_failed, message)
}

