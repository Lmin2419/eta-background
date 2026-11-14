package io.github.mangi.eta.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Coffee
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
    BoxWithConstraints(modifier) {
        val leadingSpace = maxHeight * if (showSuggestions) 0.12f else 0.08f
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
            .padding(top = leadingSpace, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (character != null) {
                CharacterAvatar(character.characterName, character.avatarPath, size = 64.dp, shape = EtaTheme.shapes.radiusFull)
                Text(character.characterName, style = EtaTheme.type.display, modifier = Modifier.padding(top = 16.dp))
                Text(stringResource(R.string.chat_character_story_start), style = EtaTheme.type.subhead, color = EtaTheme.colors.textSecondary)
            } else {
                // 爪印先落下、脚趾依次冒出；问候和副标题随后由下往上浮现。
                EtaPawArrival(size = 52.dp)
                Text(stringResource(greeting), style = EtaTheme.type.display, color = EtaTheme.colors.textPrimary,
                    modifier = Modifier.padding(top = 12.dp).graphicsLayer {
                        alpha = arrival.value; translationY = (1f - arrival.value) * 8.dp.toPx()
                    })
                Text(stringResource(R.string.eta_home_subtitle), style = EtaTheme.type.subhead,
                    color = EtaTheme.colors.textSecondary, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp).graphicsLayer {
                        val t = ((arrival.value - 0.25f) / 0.75f).coerceIn(0f, 1f)
                        alpha = t; translationY = (1f - t) * 8.dp.toPx()
                    })
                if (showSuggestions) {
                    if (shortcuts.missingSetupCount > 0) {
                        Column(Modifier.fillMaxWidth().background(EtaTheme.colors.attentionSoft, EtaTheme.shapes.radiusMd)
                            .clickable(onClick = shortcuts.onOpenSettings).padding(14.dp)) {
                            Text(stringResource(R.string.eta_setup_remaining, shortcuts.missingSetupCount), style = EtaTheme.type.subhead,
                                color = EtaTheme.colors.attentionText)
                            Text(stringResource(R.string.eta_setup_complete), style = EtaTheme.type.caption, color = EtaTheme.colors.attentionText)
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    val examples = listOf(
                        Triple(R.string.eta_example_coffee, R.string.eta_prompt_coffee, Icons.Outlined.Coffee),
                        Triple(R.string.eta_example_notifications, R.string.eta_prompt_notifications, Icons.Outlined.NotificationsNone),
                        Triple(R.string.eta_example_battery, R.string.eta_prompt_battery, Icons.Outlined.BatteryChargingFull),
                        Triple(R.string.eta_example_phones, R.string.eta_prompt_phones, Icons.Outlined.Search),
                    )
                    val cards = shortcuts.tasks.take(2).map { task ->
                        HomeSuggestion(task.name, task.instruction, Icons.Outlined.PlayArrow) { shortcuts.onRunTask(task) }
                    } + examples.take(4 - shortcuts.tasks.size.coerceAtMost(2)).map { (title, prompt, icon) ->
                        val text = stringResource(prompt)
                        HomeSuggestion(stringResource(title), text, icon) { onFillDraft(text) }
                    }
                    // 建议是一组安静的单行列表（与侧栏同一风格），不再是长短不一的卡片网格。
                    Column(Modifier.fillMaxWidth().widthIn(max = 560.dp).clip(EtaTheme.shapes.radiusLg).background(EtaTheme.colors.surface)
                        .padding(vertical = 4.dp)) {
                        cards.forEachIndexed { index, card ->
                            HomeSuggestionRow(card, Modifier.graphicsLayer {
                                // 逐行错开淡入上移：只在首次出现时播放一次。
                                val local = ((arrival.value - index * 0.12f) / 0.64f).coerceIn(0f, 1f)
                                alpha = local
                                translationY = (1f - local) * 8.dp.toPx()
                            })
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

@Composable
private fun HomeSuggestionRow(card: HomeSuggestion, modifier: Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val background by animateColorAsState(if (pressed) EtaTheme.colors.surfaceSunken else EtaTheme.colors.surface,
        tween(EtaTheme.motion.micro, easing = EtaTheme.motion.standard), label = "suggestionPress")
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp).background(background)
        .etaPressScale(source, pressedScale = 0.98f)
        .clickable(role = Role.Button, interactionSource = source, indication = null, onClick = card.onClick)
        .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(card.icon, contentDescription = null, tint = EtaTheme.colors.textSecondary, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(card.title, style = EtaTheme.type.rowTitle, color = EtaTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(card.summary, style = EtaTheme.type.footnote, color = EtaTheme.colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
