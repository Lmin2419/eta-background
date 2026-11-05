package io.github.mangi.eta.ui.components

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
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun HomeEmptyState(showSuggestions: Boolean, onFillDraft: (String) -> Unit, modifier: Modifier = Modifier) {
    val character = LocalRoleplayChat.current
    val shortcuts = LocalChatHomeShortcuts.current
    val motion = EtaTheme.motion
    val arrival = remember { Animatable(0f) }
    LaunchedEffect(motion.reduceMotion) { if (!motion.reduceMotion) arrival.animateTo(1f, tween(600)) else arrival.snapTo(1f) }
    val greeting = when (LocalTime.now().hour) { in 5..11 -> R.string.eta_good_morning; in 12..17 -> R.string.eta_good_afternoon; else -> R.string.eta_good_evening }
    BoxWithConstraints(modifier) {
        val leadingSpace = maxHeight * if (showSuggestions) 0.12f else 0.08f
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
            .padding(top = leadingSpace, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (character != null) {
                CharacterAvatar(character.characterName, character.avatarPath, size = 64.dp, shape = EtaTheme.shapes.radiusFull)
                Text(character.characterName, style = EtaTheme.type.display, modifier = Modifier.padding(top = 16.dp))
                Text(stringResource(R.string.chat_character_story_start), style = EtaTheme.type.subhead, color = EtaTheme.colors.textSecondary)
            } else {
                val aurora = EtaTheme.colors.aurora
                // One entrance transition only; the static mark never owns an infinite animation.
                Text("η", style = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.SemiBold,
                    brush = Brush.linearGradient(*aurora.stops, start = Offset(-80f + arrival.value * 80f, 0f), end = Offset(120f, 60f))))
                Text(stringResource(greeting), style = EtaTheme.type.display, color = EtaTheme.colors.textPrimary)
                Text(stringResource(R.string.eta_home_subtitle), style = EtaTheme.type.subhead,
                    color = EtaTheme.colors.textSecondary, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
                if (showSuggestions) {
                    if (shortcuts.missingSetupCount > 0) {
                        Column(Modifier.fillMaxWidth().background(EtaTheme.colors.attentionSoft, EtaTheme.shapes.radiusMd)
                            .clickable(onClick = shortcuts.onOpenSettings).padding(14.dp)) {
                            Text(stringResource(R.string.eta_setup_remaining, shortcuts.missingSetupCount), style = EtaTheme.type.subhead,
                                color = EtaTheme.colors.attention)
                            Text(stringResource(R.string.eta_setup_complete), style = EtaTheme.type.caption, color = EtaTheme.colors.attention)
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    val examples = listOf(
                        Triple(R.string.eta_example_coffee, R.string.eta_prompt_coffee, Icons.Rounded.Coffee),
                        Triple(R.string.eta_example_notifications, R.string.eta_prompt_notifications, Icons.Rounded.NotificationsNone),
                        Triple(R.string.eta_example_battery, R.string.eta_prompt_battery, Icons.Rounded.BatteryChargingFull),
                        Triple(R.string.eta_example_phones, R.string.eta_prompt_phones, Icons.Rounded.Search),
                    )
                    val cards = shortcuts.tasks.take(2).map { task ->
                        HomeSuggestion(task.name, task.instruction, Icons.Rounded.PlayArrow) { shortcuts.onRunTask(task) }
                    } + examples.take(4 - shortcuts.tasks.size.coerceAtMost(2)).map { (title, prompt, icon) ->
                        val text = stringResource(prompt)
                        HomeSuggestion(stringResource(title), text, icon) { onFillDraft(text) }
                    }
                    cards.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEach { card -> HomeSuggestionCard(card, Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

private data class HomeSuggestion(val title: String, val summary: String, val icon: ImageVector, val onClick: () -> Unit)

@Composable
private fun HomeSuggestionCard(card: HomeSuggestion, modifier: Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed) 0.98f else 1f, tween(EtaTheme.motion.micro), label = "suggestionPress")
    Row(modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }
        .heightIn(min = 88.dp).clip(EtaTheme.shapes.radiusMd).background(EtaTheme.colors.surface)
        .clickable(role = Role.Button, interactionSource = source, indication = null, onClick = card.onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EtaIconTile(card.icon, tint = EtaTheme.colors.tileIndigo)
        Column(Modifier.weight(1f)) {
            Text(card.title, style = EtaTheme.type.rowTitle, color = EtaTheme.colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(card.summary, style = EtaTheme.type.footnote, color = EtaTheme.colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
