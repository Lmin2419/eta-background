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

/**
 * 一轮 = 上一条用户消息之后、下一条用户消息之前的所有条目。按顺序取出思考与回答正文，
 * 工具步骤、统计等不算正文，不放进可选文字里。
 */
internal fun turnSegments(messages: List<AgentChatMessageUi>, answerId: String): List<TurnSegment> {
    val index = messages.indexOfFirst { it.id == answerId }
    if (index < 0) return emptyList()
    var start = index
    while (start > 0 && messages[start - 1] !is UserMessageUi) start--
    var end = index
    while (end < messages.lastIndex && messages[end + 1] !is UserMessageUi) end++
    return messages.subList(start, end + 1).mapNotNull { item ->
        when (item) {
            is ThinkingMessageUi -> item.content.trim().takeIf(String::isNotEmpty)?.let { TurnSegment(it, thinking = true) }
            is AgentMessageUi -> item.content.trim().takeIf(String::isNotEmpty)?.let { TurnSegment(it, thinking = false) }
            else -> null
        }
    }
}

/**
 * 「选择文字」面板内容：整轮的思考和回答放在同一个可选区域里，可以从思考一直选到回答、一次复制。
 * 「思考」小标题不可选，复制出来的只有正文。
 */
@Composable
internal fun TurnSelectionContent(segments: List<TurnSegment>) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.7f).dp
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
