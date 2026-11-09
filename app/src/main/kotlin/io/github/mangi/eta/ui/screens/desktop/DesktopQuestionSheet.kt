package io.github.mangi.eta.ui.screens.desktop

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.ui.Alignment
import top.yukonga.miuix.kmp.basic.Icon
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.remote.DesktopBridge
import io.github.mangi.eta.agent.remote.DesktopBridgeException
import io.github.mangi.eta.agent.remote.DesktopQuestionAnswer
import io.github.mangi.eta.agent.remote.DesktopQuestionItem
import io.github.mangi.eta.ui.components.EtaButton
import io.github.mangi.eta.ui.components.EtaButtonKind
import io.github.mangi.eta.ui.components.EtaChoiceChip
import io.github.mangi.eta.ui.components.EtaSheet
import io.github.mangi.eta.ui.markdown.MarkdownTone
import io.github.mangi.eta.ui.markdown.StaticMarkdown
import io.github.mangi.eta.ui.theme.EtaTheme
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField

/**
 * 手机上要回答的一次提问：正在等待的（[questionId]，先答者生效），
 * 或前台等待已结束、仍可稍后回答的（[callId]，答案作为新消息送给 Agent）。
 */
internal data class DesktopAsk(
    val key: String,
    val sessionId: String,
    val items: List<DesktopQuestionItem>,
    val questionId: String? = null,
    val callId: String? = null,
) {
    val isPlanReview: Boolean get() = items.any { it.isPlanReview }
}

/** Agent 的提问与计划审阅：选项、多选、补充说明，计划用 Markdown 显示。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DesktopQuestionSheet(ask: DesktopAsk?, onDismiss: () -> Unit) {
    var last by remember { mutableStateOf(ask) }
    if (ask != null) last = ask
    val target = last ?: return
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val selections = remember(target.key) { mutableStateMapOf<String, List<String>>() }
    val customs = remember(target.key) { mutableStateMapOf<String, String>() }
    var sending by remember(target.key) { mutableStateOf(false) }

    fun submit(override: List<DesktopQuestionAnswer>? = null) {
        if (sending) return
        sending = true
        val answers = override ?: target.items.map { item ->
            DesktopQuestionAnswer(item.id, selections[item.id].orEmpty(), customs[item.id]?.trim()?.takeIf { it.isNotEmpty() })
        }
        scope.launch {
            try {
                val accepted = when {
                    target.questionId != null -> DesktopBridge.answerQuestion(target.questionId, answers)
                    target.callId != null -> DesktopBridge.replyQuestion(target.sessionId, target.callId, answers)
                    else -> false
                }
                if (!accepted) Toast.makeText(context, resources.getString(R.string.desktop_question_closed), Toast.LENGTH_SHORT).show()
                onDismiss()
            } catch (error: DesktopBridgeException) {
                Toast.makeText(context, resources.getString(R.string.desktop_failed, error.message), Toast.LENGTH_SHORT).show()
            } finally {
                sending = false
            }
        }
    }

    val title = stringResource(if (target.isPlanReview) R.string.desktop_plan_review_title else R.string.desktop_question_title)
    EtaSheet(show = ask != null, title = title, onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (target.callId != null && target.questionId == null) {
                Text(stringResource(R.string.desktop_question_later), style = EtaTheme.type.caption, color = EtaTheme.colors.textTertiary)
            }
            for (item in target.items) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.header?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = EtaTheme.type.caption, color = EtaTheme.colors.textTertiary)
                    }
                    Text(item.question, style = EtaTheme.type.body, color = EtaTheme.colors.textPrimary)
                    item.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                        if (item.isPlanReview) StaticMarkdown(content = detail, tone = MarkdownTone.Answer)
                        else Text(detail, style = EtaTheme.type.caption, color = EtaTheme.colors.textSecondary)
                    }
                    if (item.isPlanReview) {
                        // 计划审阅：批准或带修改意见退回（其余选项即为不批准）。
                        val approve = item.intent?.approve
                        val decline = item.options.map { it.label }.firstOrNull { it != approve }
                        TextField(
                            value = customs[item.id].orEmpty(),
                            onValueChange = { customs[item.id] = it },
                            label = stringResource(R.string.desktop_plan_feedback),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            EtaButton(
                                text = decline ?: stringResource(R.string.desktop_plan_revise),
                                kind = EtaButtonKind.Ghost,
                                enabled = !sending,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    submit(target.items.map { other ->
                                        if (other.id == item.id) DesktopQuestionAnswer(item.id, listOfNotNull(decline), customs[item.id]?.trim()?.takeIf { it.isNotEmpty() })
                                        else DesktopQuestionAnswer(other.id, selections[other.id].orEmpty(), customs[other.id])
                                    })
                                },
                            )
                            EtaButton(
                                text = approve ?: stringResource(R.string.desktop_plan_approve),
                                kind = EtaButtonKind.Primary,
                                enabled = !sending && approve != null,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    submit(target.items.map { other ->
                                        if (other.id == item.id) DesktopQuestionAnswer(item.id, listOfNotNull(approve))
                                        else DesktopQuestionAnswer(other.id, selections[other.id].orEmpty(), customs[other.id])
                                    })
                                },
                            )
                        }
                    } else {
                        if (item.options.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (option in item.options) {
                                    val picked = option.label in selections[item.id].orEmpty()
                                    EtaChoiceChip(option.label, picked, {
                                        val current = selections[item.id].orEmpty()
                                        selections[item.id] = when {
                                            item.multiSelect && picked -> current - option.label
                                            item.multiSelect -> current + option.label
                                            picked -> emptyList()
                                            else -> listOf(option.label)
                                        }
                                        if (!item.multiSelect) customs.remove(item.id)
                                    })
                                }
                            }
                            item.options.mapNotNull { option -> option.description?.takeIf { it.isNotBlank() }?.let { "${option.label}：$it" } }
                                .takeIf { it.isNotEmpty() }
                                ?.let { Text(it.joinToString("\n"), style = EtaTheme.type.caption, color = EtaTheme.colors.textTertiary) }
                        }
                        TextField(
                            value = customs[item.id].orEmpty(),
                            onValueChange = { text ->
                                customs[item.id] = text
                                if (!item.multiSelect && text.isNotBlank()) selections.remove(item.id)
                            },
                            label = stringResource(if (item.options.isEmpty()) R.string.desktop_question_answer else R.string.desktop_question_other),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            if (target.items.any { !it.isPlanReview }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    EtaButton(
                        text = stringResource(R.string.desktop_question_later_button),
                        kind = EtaButtonKind.Ghost,
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss,
                    )
                    EtaButton(
                        text = stringResource(R.string.desktop_question_submit),
                        kind = EtaButtonKind.Primary,
                        enabled = !sending,
                        modifier = Modifier.weight(1f),
                        onClick = { submit() },
                    )
                }
            }
        }
    }
}

/** 会话页顶部的提示条：Agent 在等你回答（或审阅计划），点开回答面板。 */
@Composable
internal fun DesktopAskBanner(count: Int, plan: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(EtaTheme.colors.accent.copy(alpha = 0.12f), EtaTheme.shapes.radiusMd)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.QuestionAnswer, contentDescription = null, tint = EtaTheme.colors.accent, modifier = Modifier.size(18.dp))
        Text(
            text = if (plan) stringResource(R.string.desktop_ask_banner_plan) else stringResource(R.string.desktop_ask_banner, count),
            style = EtaTheme.type.caption,
            color = EtaTheme.colors.accent,
            modifier = Modifier.weight(1f),
        )
        Text(stringResource(R.string.desktop_ask_open), style = EtaTheme.type.caption, color = EtaTheme.colors.accent)
    }
}

