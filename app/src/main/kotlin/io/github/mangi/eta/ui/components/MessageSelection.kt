package io.github.mangi.eta.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.model.AgentFileReferencePromptCodec
import io.github.mangi.eta.ui.model.AgentChatMessageUi
import io.github.mangi.eta.ui.model.AgentMessageUi
import io.github.mangi.eta.ui.model.UserMessageUi
import io.github.mangi.eta.ui.theme.EtaTheme
import top.yukonga.miuix.kmp.basic.Text

/**
 * 消息多选（etax/design/09 第五期 5.1）：[selected] 为 null 表示不在多选。由 App 根提供，
 * 标题胶囊菜单（「多选消息」）和消息长按菜单（「多选」）都能进入；切换对话、开始回复时清掉。
 */
@Stable
internal class MessageSelectionState {
    var selected by mutableStateOf<Set<String>?>(null)
        private set

    val active: Boolean get() = selected != null

    fun start(first: String? = null) { selected = setOfNotNull(first) }
    fun toggle(id: String) { selected = selected?.let { if (id in it) it - id else it + id } }
    fun clear() { selected = null }
}

internal val LocalMessageSelection = staticCompositionLocalOf<MessageSelectionState?> { null }

/**
 * 选中的消息按对话顺序拼成纯文本：每条前面是「我：」或助手名（角色对话用角色名），条与条之间空一行。
 * 用户消息去掉文件引用的编码，只留看得到的那段话；只认用户消息和回答。
 */
internal fun selectionPlainText(messages: List<AgentChatMessageUi>, ids: Set<String>, userName: String, assistantName: String): String =
    messages.asSequence().filter { it.id in ids }.mapNotNull { message ->
        when (message) {
            is UserMessageUi -> "$userName：" + AgentFileReferencePromptCodec.parse(message.content).request.trim()
            is AgentMessageUi -> "$assistantName：" + message.content.trim()
            else -> null
        }
    }.joinToString("\n\n")

/**
 * 多选时的消息外观：不改布局，只在画的时候铺一层强调色 10% 的圆角底，右上角一个 18dp 对勾。
 */
@Composable
internal fun Modifier.selectionHighlight(selected: Boolean): Modifier {
    val accent = EtaTheme.colors.accent
    val check = rememberVectorPainter(Icons.Filled.CheckCircle)
    return drawWithContent {
        if (selected) drawRoundRect(accent.copy(alpha = 0.10f), cornerRadius = CornerRadius(14.dp.toPx()))
        drawContent()
        if (selected) {
            val box = 18.dp.toPx()
            val inset = 6.dp.toPx()
            translate(left = size.width - box - inset, top = inset) {
                with(check) { draw(androidx.compose.ui.geometry.Size(box, box), colorFilter = ColorFilter.tint(accent)) }
            }
        }
    }
}

/**
 * 多选时替换输入框的那条玻璃栏：「已选 N 条」、复制、分享、取消。圆角与输入框相同，
 * 采样源用输入框那一个（调用方在同一个 LocalEtaCouiBackdrop 下组合）。
 */
@Composable
internal fun MessageSelectionBar(count: Int, onCopy: () -> Unit, onShare: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    io.github.mangi.eta.ui.components.coui.EtaFloatingSurface(
        // 高度定死：底部栏给的是整屏的高度约束，不定高时玻璃面会铺满（真机模式截图里发现）。
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(28.dp), capsule = false, onClick = null, cornerRadius = 28.dp, fillAlpha = 0.6f,
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 18.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(pluralStringResource(R.plurals.message_selection_count, count, count), style = EtaTheme.type.subhead,
                color = EtaTheme.colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1)
            SelectionAction(Icons.Outlined.ContentCopy, stringResource(R.string.ui_copy_4edd1d), count > 0, onCopy)
            SelectionAction(Icons.Outlined.IosShare, stringResource(R.string.message_selection_share), count > 0, onShare)
            EtaButton(stringResource(R.string.action_cancel), onCancel, kind = EtaButtonKind.Ghost, size = EtaButtonSize.Small)
        }
    }
}

@Composable
private fun SelectionAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        top.yukonga.miuix.kmp.basic.Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp),
            tint = if (enabled) EtaTheme.colors.textPrimary else EtaTheme.colors.textTertiary)
    }
}
