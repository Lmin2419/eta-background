package io.github.mangi.eta.ui.screens.desktop

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.remote.DesktopApproval
import io.github.mangi.eta.ui.components.EtaButton
import io.github.mangi.eta.ui.components.EtaButtonKind
import io.github.mangi.eta.ui.components.EtaButtonSize
import io.github.mangi.eta.ui.components.StatusRing
import io.github.mangi.eta.ui.model.TaskVisualStatus
import io.github.mangi.eta.ui.theme.EtaTheme
import top.yukonga.miuix.kmp.basic.Text

/**
 * 输入框上方的一条运行状态：「运行中 · 1 分 12 秒」+ 明确的「停止」。
 * 取代原来挤在顶部的统计行与藏在长按里的打断。
 */
@Composable
internal fun DesktopRunStrip(runningForMs: Long?, onStop: () -> Unit, onDetails: () -> Unit) {
    val res = LocalResources.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 6.dp)
            .background(EtaTheme.colors.runningSoft, EtaTheme.shapes.radiusFull)
            .clickable(onClick = onDetails)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusRing(TaskVisualStatus.Running, size = 14.dp, contentDescription = null)
        Text(
            text = runningForMs?.let { res.getString(R.string.desktop_running_for, formatDuration(res, it)) }
                ?: stringResource(R.string.desktop_running),
            style = EtaTheme.type.caption,
            color = EtaTheme.colors.onAccentSoft,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        EtaButton(text = stringResource(R.string.action_stop), onClick = onStop, kind = EtaButtonKind.Ghost, size = EtaButtonSize.Small)
    }
}

/** 需要你处理的卡片外壳：左侧「需要你」状态圈，标题、正文和操作按钮。 */
@Composable
private fun AttentionCard(title: String, body: (@Composable () -> Unit)? = null, actions: @Composable RowScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp)
            .background(EtaTheme.colors.surfaceRaised, EtaTheme.shapes.radiusLg)
            .padding(14.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusRing(TaskVisualStatus.Attention, size = 16.dp, contentDescription = null)
            Text(title, style = EtaTheme.type.subhead, color = EtaTheme.colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        body?.invoke()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { actions() }
    }
}

/** 审批：在会话里直接允许或拒绝，不再弹窗打断。说明较长时折叠，点开看全文。 */
@Composable
internal fun DesktopApprovalCard(approval: DesktopApproval, onAnswer: (Boolean) -> Unit) {
    var expanded by remember(approval.id) { mutableStateOf(false) }
    AttentionCard(
        title = stringResource(R.string.desktop_approval_title, approval.toolName),
        body = approval.reason?.takeIf { it.isNotBlank() }?.let { reason ->
            {
                Text(
                    text = reason,
                    style = EtaTheme.type.caption,
                    color = EtaTheme.colors.textSecondary,
                    maxLines = if (expanded) 40 else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { expanded = !expanded },
                )
            }
        },
    ) {
        EtaButton(stringResource(R.string.desktop_approval_reject), { onAnswer(false) }, kind = EtaButtonKind.Secondary, modifier = Modifier.weight(1f))
        EtaButton(stringResource(R.string.desktop_approval_allow), { onAnswer(true) }, kind = EtaButtonKind.Primary, modifier = Modifier.weight(1f))
    }
}

/** 提问或计划审阅：卡片提示，点「回答 / 审阅」打开作答面板（不再自动弹出）。 */
@Composable
internal fun DesktopAskCard(count: Int, plan: Boolean, onOpen: () -> Unit) {
    AttentionCard(
        title = if (plan) stringResource(R.string.desktop_ask_banner_plan) else stringResource(R.string.desktop_ask_banner, count),
    ) {
        EtaButton(
            text = stringResource(if (plan) R.string.desktop_plan_review_open else R.string.desktop_ask_open),
            onClick = onOpen,
            kind = EtaButtonKind.Primary,
            modifier = Modifier.weight(1f),
        )
    }
}
