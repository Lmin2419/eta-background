package io.github.mangi.eta.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.model.AgentChatMessageUi
import io.github.mangi.eta.ui.model.AgentMessageUi
import io.github.mangi.eta.ui.model.ThinkingMessageUi
import io.github.mangi.eta.ui.model.UserMessageUi
import io.github.mangi.eta.ui.theme.EtaTheme
import top.yukonga.miuix.kmp.basic.Text

/** 「选择文字」里的一段：思考或回答正文。 */
internal data class TurnSegment(val text: String, val thinking: Boolean)

/** 给某条回答取它所在这一轮的全部思考与回答段落（手机对话、电脑会话共用，AgentChatBody 提供）。 */
internal val LocalTurnSegments = staticCompositionLocalOf<((String) -> List<TurnSegment>)?> { null }

/** 某条消息所在这一轮在 [messages] 里的下标范围（上一条用户消息之后到下一条用户消息之前）；找不到时为 null。 */
internal fun turnRange(messages: List<AgentChatMessageUi>, messageId: String): IntRange? {
    val index = messages.indexOfFirst { it.id == messageId }
    if (index < 0) return null
    var start = index
    while (start > 0 && messages[start - 1] !is UserMessageUi) start--
    var end = index
    while (end < messages.lastIndex && messages[end + 1] !is UserMessageUi) end++
    return start..end
}

/**
 * 一轮 = 上一条用户消息之后、下一条用户消息之前的所有条目。按顺序取出思考与回答正文，
 * 工具步骤、统计等不算正文，不放进可选文字里。
 */
internal fun turnSegments(messages: List<AgentChatMessageUi>, answerId: String): List<TurnSegment> {
    val range = turnRange(messages, answerId) ?: return emptyList()
    return messages.subList(range.first, range.last + 1).mapNotNull { item ->
        when (item) {
            is ThinkingMessageUi -> item.content.trim().takeIf(String::isNotEmpty)?.let { TurnSegment(it, thinking = true) }
            is AgentMessageUi -> item.content.trim().takeIf(String::isNotEmpty)?.let { TurnSegment(it, thinking = false) }
            else -> null
        }
    }
}

/** 一键复制用的纯文本：段落之间空一行；[includeThinking] 为假时只取回答。 */
internal fun turnPlainText(segments: List<TurnSegment>, includeThinking: Boolean): String =
    segments.filter { includeThinking || !it.thinking }.joinToString("\n\n") { it.text }

/**
 * 「选择文字」面板内容：整轮的思考和回答放在同一个可选区域里，可以从思考一直选到回答、一次复制。
 * 「思考」小标题不可选，复制出来的只有正文。
 */
@Composable
internal fun TurnSelectionContent(segments: List<TurnSegment>) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.7f).dp
    // 09 第一期 1.3：长内容不用拖选择柄，一键复制整轮（含思考）或只复制回答。
    val context = androidx.compose.ui.platform.LocalContext.current
    @Suppress("DEPRECATION") val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val copied = stringResource(R.string.copy_copied)
    val copy: (String) -> Unit = { text ->
        clipboard.setText(androidx.compose.ui.text.AnnotatedString(text))
        EtaToastCenter.show(context, copied)
    }
    val hasThinking = segments.any { it.thinking }
    // 按钮行和可选区域包在同一个 Column 里：组件只往父布局发一个节点（父布局是 Box 时不会叠在一起）。
    Column(Modifier.fillMaxWidth()) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(bottom = 10.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        EtaButton(stringResource(R.string.eta_copy_all), { copy(turnPlainText(segments, includeThinking = true)) },
            size = EtaButtonSize.Small)
        if (hasThinking) EtaButton(stringResource(R.string.eta_copy_answer_only), { copy(turnPlainText(segments, includeThinking = false)) },
            size = EtaButtonSize.Small)
    }
    SelectionContainer {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState())) {
            segments.forEachIndexed { index, segment ->
                val top = if (index == 0) 0.dp else 14.dp
                if (segment.thinking) {
                    DisableSelection {
                        Text(stringResource(R.string.eta_turn_thinking_label), style = EtaTheme.type.caption,
                            color = EtaTheme.colors.textTertiary, modifier = Modifier.padding(top = top, bottom = 4.dp))
                    }
                    Text(segment.text, style = EtaTheme.type.body, color = EtaTheme.colors.textSecondary,
                        modifier = Modifier.fillMaxWidth())
                } else {
                    Text(segment.text, style = EtaTheme.type.body, color = EtaTheme.colors.textPrimary,
                        modifier = Modifier.fillMaxWidth().padding(top = top))
                }
            }
        }
    }
    }
}
