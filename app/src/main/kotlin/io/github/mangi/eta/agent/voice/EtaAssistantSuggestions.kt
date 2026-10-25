package io.github.mangi.eta.agent.voice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text

private val assistantSuggestions = listOf(
    "帮我总结当前屏幕内容",
    "这个页面怎么操作",
    "屏幕上有什么值得注意的信息",
)

@Composable
internal fun EtaAssistantSuggestions(
    onSuggestionClick: (String) -> Unit,
    colors: EtaVoicePanelColors,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = EnterTransition.None,
        exit = shrinkVertically(
            shrinkTowards = Alignment.Bottom,
            animationSpec = tween(180, easing = FastOutSlowInEasing),
        ) + fadeOut(tween(140)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            assistantSuggestions.forEachIndexed { index, suggestion ->
                val transform = remember(suggestion) { Animatable(0f) }
                val opacity = remember(suggestion) { Animatable(0f) }
                LaunchedEffect(visible) {
                    if (visible) {
                        transform.snapTo(0f)
                        opacity.snapTo(0f)
                        delay(900L + when (index) {
                            0 -> 200L
                            1 -> 117L
                            else -> 0L
                        })
                        coroutineScope {
                            launch {
                                transform.animateTo(
                                    targetValue = 1f,
                                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 109.66f),
                                )
                            }
                            launch { opacity.animateTo(1f, tween(350)) }
                        }
                    } else {
                        coroutineScope {
                            launch { transform.animateTo(0f, tween(140)) }
                            launch { opacity.animateTo(0f, tween(140)) }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .graphicsLayer {
                            alpha = opacity.value
                            scaleX = transform.value
                            scaleY = transform.value
                            translationY = (1f - transform.value) * 18.dp.toPx()
                        }
                        .clip(CircleShape)
                        .background(colors.input)
                        .border(0.75.dp, colors.chipStroke, CircleShape)
                        .clickable(enabled = visible) { onSuggestionClick(suggestion) }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = suggestion,
                        color = colors.inputPrimary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
