package io.github.mangi.eta.agent.voice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.components.EtaButton
import io.github.mangi.eta.ui.components.EtaButtonKind
import io.github.mangi.eta.ui.components.EtaButtonSize

@Composable
internal fun EtaAssistantSuggestions(onSuggestionClick: (String) -> Unit, colors: EtaVoicePanelColors,
    modifier: Modifier = Modifier, visible: Boolean = true) {
    val labels = listOf(stringResource(R.string.voice_suggestion_summary), stringResource(R.string.voice_suggestion_help),
        stringResource(R.string.voice_suggestion_highlights))
    val prompts = listOf("帮我总结当前屏幕内容", "这个页面怎么操作", "屏幕上有什么值得注意的信息")
    AnimatedVisibility(visible, modifier, enter = fadeIn(), exit = fadeOut()) {
        LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(labels.size) { index ->
                EtaButton(labels[index], { onSuggestionClick(prompts[index]) }, enabled = visible,
                    kind = EtaButtonKind.Secondary, size = EtaButtonSize.Small, minHeight = 48.dp, cornerRadius = 999.dp)
            }
        }
    }
}
