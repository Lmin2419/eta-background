package io.github.mangi.eta.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.model.TaskVisualStatus
import io.github.mangi.eta.ui.theme.EtaTheme
import java.time.LocalTime
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun HomeEmptyState(showSuggestions: Boolean, onFillDraft: (String) -> Unit, modifier: Modifier = Modifier,
    onContentBottom: (Float) -> Unit = {}) {
    val character = LocalRoleplayChat.current
    val shortcuts = LocalChatHomeShortcuts.current
    val motion = EtaTheme.motion
    val arrival = remember { Animatable(0f) }
    LaunchedEffect(motion.reduceMotion) { arrival.animateTo(1f, tween(motion.long, easing = motion.emphasizedDecelerate)) }
    val greeting = when (LocalTime.now().hour) { in 5..11 -> R.string.eta_good_morning; in 12..17 -> R.string.eta_good_afternoon; in 18..23 -> R.string.eta_good_evening; else -> R.string.eta_good_late }
    // 09 第四期 4.1：有在跑的任务或最近的对话时，新对话页是「工作台」——问候缩成一行，下面「正在进行」「接着做」。
    val running = if (character == null) rememberBackgroundItems(shortcuts.conversationId) else emptyList()
    val workbench = character == null && (running.isNotEmpty() || shortcuts.recent.isNotEmpty())
    // 07 第四期：问候在上三分之一，建议改成输入框正上方一排可横滑的胶囊（手指离得近，中间不空一大块）。
    // 外层可竖向滚动：键盘弹出、横屏或大字体时放不下也不会裁掉。
    BoxWithConstraints(modifier) {
        val pageHeight = maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().heightIn(min = pageHeight), verticalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = if (workbench) 16.dp else pageHeight * 0.14f),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    if (workbench) {
                        HomeWorkbench(stringResource(greeting), running, shortcuts, arrival.value)
                    } else if (character != null) {
                        CharacterAvatar(character.characterName, character.avatarPath, size = 64.dp, shape = EtaTheme.shapes.radiusFull)
                        Text(character.characterName, style = EtaTheme.type.display, modifier = Modifier.padding(top = 16.dp))
                        Text(stringResource(R.string.chat_character_story_start), style = EtaTheme.type.subhead, color = EtaTheme.colors.textSecondary)
                    } else {
                        // 爪印先落下、脚趾依次冒出；问候和副标题随后由下往上浮现。
                        EtaPawArrival(size = 52.dp)
                        Text(stringResource(greeting), style = EtaTheme.type.display, color = EtaTheme.colors.textPrimary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 12.dp).graphicsLayer {
                                alpha = arrival.value; translationY = (1f - arrival.value) * 8.dp.toPx()
                            })
                        Text(stringResource(R.string.eta_home_subtitle), style = EtaTheme.type.subhead,
                            color = EtaTheme.colors.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 8.dp).graphicsLayer {
                                val t = ((arrival.value - 0.25f) / 0.75f).coerceIn(0f, 1f)
                                alpha = t; translationY = (1f - t) * 8.dp.toPx()
                            })
                        if (showSuggestions && shortcuts.missingSetupCount > 0) {
                            Column(Modifier.padding(top = 24.dp).fillMaxWidth().widthIn(max = 560.dp)
                                .background(EtaTheme.colors.attentionSoft, EtaTheme.shapes.radiusMd)
                                .clickable(onClick = shortcuts.onOpenSettings).padding(14.dp)) {
                                Text(stringResource(R.string.eta_setup_remaining, shortcuts.missingSetupCount), style = EtaTheme.type.subhead,
                                    color = EtaTheme.colors.attentionText)
                                Text(stringResource(R.string.eta_setup_complete), style = EtaTheme.type.caption, color = EtaTheme.colors.attentionText)
                            }
                        }
                    }
                }
                if (character == null && showSuggestions) {
                    val examples = listOf(
                        Triple(R.string.eta_example_coffee, R.string.eta_prompt_coffee, Icons.Outlined.Coffee),
                        Triple(R.string.eta_example_notifications, R.string.eta_prompt_notifications, Icons.Outlined.NotificationsNone),
                        Triple(R.string.eta_example_battery, R.string.eta_prompt_battery, Icons.Outlined.BatteryChargingFull),
                        Triple(R.string.eta_example_phones, R.string.eta_prompt_phones, Icons.Outlined.Search),
                    )
                    // 常用任务点一下直接运行；示例点一下填进输入框，看过再发。
                    val cards = shortcuts.tasks.take(2).map { task ->
                        HomeSuggestion(task.name, task.instruction, Icons.Outlined.PlayArrow) { shortcuts.onRunTask(task) }
                    } + examples.take(4 - shortcuts.tasks.size.coerceAtMost(2)).map { (title, prompt, icon) ->
                        val text = stringResource(prompt)
                        HomeSuggestion(stringResource(title), text, icon) { onFillDraft(text) }
                    }
                    Box(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 10.dp), contentAlignment = Alignment.Center) {
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            cards.forEachIndexed { index, card ->
                                HomeSuggestionChip(card, Modifier.graphicsLayer {
                                    // 逐个错开淡入上移：只在首次出现时播放一次。
                                    val local = ((arrival.value - index * 0.12f) / 0.64f).coerceIn(0f, 1f)
                                    alpha = local
                                    translationY = (1f - local) * 8.dp.toPx()
                                })
                            }
                        }
                    }
                }
            }
            // 报告内容底部，输入胶囊上移时不压到建议。
            Spacer(Modifier.height(0.dp).onGloballyPositioned { onContentBottom(it.positionInRoot().y) })
        }
    }
}

private data class HomeSuggestion(val title: String, val summary: String, val icon: ImageVector, val onClick: () -> Unit)

/** 输入框上方一枚建议胶囊：图标加一行标题，点一下运行或填进输入框（完整内容作为点击说明读出）。 */
@Composable
private fun HomeSuggestionChip(card: HomeSuggestion, modifier: Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val background by animateColorAsState(if (pressed) EtaTheme.colors.surfaceSunken else EtaTheme.colors.surface,
        tween(EtaTheme.motion.micro, easing = EtaTheme.motion.standard), label = "suggestionPress")
    Row(modifier.heightIn(min = 40.dp)
        .etaPressScale(source, pressedScale = 0.96f)
        .clip(EtaTheme.shapes.radiusFull)
        .background(background)
        .border(0.5.dp, EtaTheme.colors.outline, EtaTheme.shapes.radiusFull)
        .clickable(role = Role.Button, interactionSource = source, indication = null, onClickLabel = card.summary, onClick = card.onClick)
        .padding(start = 12.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(card.icon, contentDescription = null, tint = EtaTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
        Text(card.title, style = EtaTheme.type.subhead, color = EtaTheme.colors.textPrimary, maxLines = 1)
    }
}

/**
 * 新对话页的工作台（09 第四期 4.1）：一行问候，下面两段实色卡片——「正在进行」（别的对话、电脑上在跑的，最多 3 条，
 * 多了末行「全部任务」）、「接着做」（最近 3 个对话：标题、时间、跑完没看的圆点）。行高与侧栏会话行一致。
 */
@Composable
private fun HomeWorkbench(greeting: String, running: List<HomeBackgroundItem>, shortcuts: ChatHomeShortcuts, arrival: Float) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val untitled = stringResource(R.string.desktop_untitled)
    val unseen by io.github.mangi.eta.agent.runtime.FinishedUnseen.state.collectAsState()
    Column(Modifier.fillMaxWidth().widthIn(max = 560.dp).graphicsLayer {
        alpha = arrival; translationY = (1f - arrival) * 8.dp.toPx()
    }) {
        Text(greeting, style = EtaTheme.type.headline, color = EtaTheme.colors.textPrimary, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        if (shortcuts.missingSetupCount > 0) {
            Text(stringResource(R.string.eta_setup_remaining, shortcuts.missingSetupCount), style = EtaTheme.type.caption,
                color = EtaTheme.colors.attentionText, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    .clickable(onClick = shortcuts.onOpenSettings))
        }
        if (running.isNotEmpty()) {
            WorkbenchSection(stringResource(R.string.home_workbench_running)) {
                running.take(WorkbenchRows).forEachIndexed { index, item ->
                    if (index > 0) WorkbenchDivider()
                    when (item) {
                        is HomeBackgroundItem.Local -> WorkbenchRow(item.task.title.ifBlank { untitled }, leading = {
                            StatusRing(TaskVisualStatus.Running, size = 12.dp, contentDescription = null)
                        }) {
                            if (item.task.opensPreview()) io.github.mangi.eta.ui.screens.tasks.openTaskPreview(context, item.task)
                            else item.task.conversationId?.let(shortcuts.onOpenConversation)
                        }
                        is HomeBackgroundItem.Desktop -> WorkbenchRow(item.title.ifBlank { untitled }, leading = {
                            StatusRing(TaskVisualStatus.Running, size = 12.dp, contentDescription = null)
                        }, trailing = {
                            Icon(Icons.Outlined.Computer, null, Modifier.size(16.dp),
                                tint = EtaTheme.colors.textTertiary)
                        }) { shortcuts.onOpenDesktopSession(item.sessionId) }
                    }
                }
                if (running.size > WorkbenchRows) {
                    WorkbenchDivider()
                    WorkbenchRow(stringResource(R.string.eta_tasks_view_all), muted = true, onClick = shortcuts.onOpenTasks)
                }
            }
        }
        if (shortcuts.recent.isNotEmpty()) {
            WorkbenchSection(stringResource(R.string.home_workbench_recent)) {
                shortcuts.recent.take(WorkbenchRows).forEachIndexed { index, conversation ->
                    if (index > 0) WorkbenchDivider()
                    val dot = unseen[conversation.id]?.let { failed -> if (failed) UnseenLevel.Failed else UnseenLevel.Done }
                    WorkbenchRow(conversation.title, trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (dot != null) UnseenDot(dot)
                            Text(conversation.timeLabel, style = EtaTheme.type.caption, color = EtaTheme.colors.textTertiary, maxLines = 1)
                        }
                    }) { shortcuts.onOpenConversation(conversation.id) }
                }
            }
        }
    }
}

@Composable
private fun WorkbenchSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, style = EtaTheme.type.caption, color = EtaTheme.colors.textTertiary,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 6.dp))
    Column(Modifier.fillMaxWidth().clip(EtaTheme.shapes.radiusLg).background(EtaTheme.colors.surface)
        .border(0.5.dp, EtaTheme.colors.outline, EtaTheme.shapes.radiusLg), content = content)
}

@Composable
private fun WorkbenchRow(title: String, muted: Boolean = false, leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        leading?.invoke()
        Text(title, style = EtaTheme.type.subhead, color = if (muted) EtaTheme.colors.textSecondary else EtaTheme.colors.textPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
private fun WorkbenchDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(0.5.dp).background(EtaTheme.colors.outline))
}

private const val WorkbenchRows = 3
