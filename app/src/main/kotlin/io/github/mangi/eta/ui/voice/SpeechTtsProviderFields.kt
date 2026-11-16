package io.github.mangi.eta.ui.voice

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.GeminiTtsModel
import io.github.mangi.eta.data.model.MiniMaxTtsModel
import io.github.mangi.eta.data.model.SpeechCredentialField
import io.github.mangi.eta.data.model.TtsProvider
import io.github.mangi.eta.ui.components.EtaOverlayDropdownPreference
import io.github.mangi.eta.ui.components.EtaPreferenceDivider
import io.github.mangi.eta.ui.components.EtaPreferenceGroup
import io.github.mangi.eta.ui.components.EtaPreferenceGroupTitle
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

@Composable
internal fun GeminiConnectionSection(store: SpeechSettingsStore) {
    val config = store.settings.geminiTts
    SpeechApiKeyConnectionSection(
        baseUrl = config.baseUrl,
        apiKey = store.credentials.geminiTts,
        onBaseUrl = { store.edit(store.settings.copy(geminiTts = config.copy(baseUrl = it))) },
        onApiKey = { store.credential(SpeechCredentialField.GEMINI_TTS, it) },
        note = stringResource(R.string.speech_gemini_connection_note),
    ) {
        EtaOverlayDropdownPreference(
            title = stringResource(R.string.speech_tts_model),
            items = listOf(
                stringResource(R.string.speech_gemini_flash),
                stringResource(R.string.speech_gemini_flash_lite),
            ),
            selectedIndex = config.model.ordinal,
            onSelectedIndexChange = { index ->
                GeminiTtsModel.entries.getOrNull(index)?.let {
                    store.edit(store.settings.copy(geminiTts = config.copy(model = it)))
                }
            },
        )
    }
}

@Composable
internal fun MiniMaxConnectionSection(store: SpeechSettingsStore) {
    val config = store.settings.minimaxTts
    SpeechApiKeyConnectionSection(
        baseUrl = config.baseUrl,
        apiKey = store.credentials.minimaxTts,
        onBaseUrl = { store.edit(store.settings.copy(minimaxTts = config.copy(baseUrl = it))) },
        onApiKey = { store.credential(SpeechCredentialField.MINIMAX_TTS, it) },
        note = stringResource(R.string.speech_minimax_connection_note),
    ) {
        EtaOverlayDropdownPreference(
            title = stringResource(R.string.speech_tts_model),
            items = listOf(
                stringResource(R.string.speech_minimax_turbo),
                stringResource(R.string.speech_minimax_hd),
            ),
            selectedIndex = config.model.ordinal,
            onSelectedIndexChange = { index ->
                MiniMaxTtsModel.entries.getOrNull(index)?.let {
                    store.edit(store.settings.copy(minimaxTts = config.copy(model = it)))
                }
            },
        )
    }
}

@Composable
internal fun SpeechApiKeyConnectionSection(
    baseUrl: String,
    apiKey: String,
    onBaseUrl: (String) -> Unit,
    onApiKey: (String) -> Unit,
    note: String,
    model: (@Composable () -> Unit)? = null,
) {
    Column {
        EtaPreferenceGroupTitle(stringResource(R.string.speech_group_connection))
        EtaPreferenceGroup {
            if (model != null) {
                model()
                EtaPreferenceDivider(hasLeading = false)
            }
            SpeechFieldColumn {
                SpeechField("API Key", apiKey, secret = true, onChange = { onApiKey(it.trim()) })
                SpeechField(stringResource(R.string.speech_base_url), baseUrl, onChange = { onBaseUrl(it.trim()) })
            }
        }
        SpeechNote(note)
    }
}

@Composable
internal fun MimoTtsConnectionSection(store: SpeechSettingsStore) {
    val config = store.settings.mimoTts
    SpeechApiKeyConnectionSection(
        baseUrl = config.baseUrl,
        apiKey = store.credentials.mimoTts,
        onBaseUrl = { store.edit(store.settings.copy(mimoTts = config.copy(baseUrl = it))) },
        onApiKey = { store.credential(SpeechCredentialField.MIMO_TTS, it) },
        note = stringResource(R.string.speech_mimo_tts_connection_note),
    )
}

/** 只呈现该服务实际支持的发声参数。 */
@Composable
internal fun DeepSeekTtsConnectionSection(store: SpeechSettingsStore) {
    val config = store.settings.deepseekTts
    SpeechApiKeyConnectionSection(
        baseUrl = config.baseUrl,
        apiKey = store.credentials.deepseekTts,
        onBaseUrl = { store.edit(store.settings.copy(deepseekTts = config.copy(baseUrl = it))) },
        onApiKey = { store.credential(SpeechCredentialField.DEEPSEEK_TTS, it) },
        note = stringResource(
            if (store.hasProviderDeepSeekKey) {
                R.string.speech_deepseek_tts_connection_note_with_provider
            } else {
                R.string.speech_deepseek_tts_connection_note
            },
        ),
    )
}

@Composable
internal fun SpeechDeliveryFields(store: SpeechSettingsStore) {
    when (store.settings.tts) {
        TtsProvider.GEMINI, TtsProvider.MIMO -> {
            val settings = store.settings
            val mimo = settings.tts == TtsProvider.MIMO
            EtaPreferenceDivider(hasLeading = false)
            SpeechFieldColumn {
                SpeechField(
                    label = stringResource(R.string.speech_style),
                    value = if (mimo) settings.mimoTts.style else settings.geminiTts.style,
                    singleLine = false,
                    onChange = {
                        store.edit(
                            if (mimo) settings.copy(mimoTts = settings.mimoTts.copy(style = it))
                            else settings.copy(geminiTts = settings.geminiTts.copy(style = it)),
                        )
                    },
                )
            }
        }
        TtsProvider.MINIMAX -> {
            val config = store.settings.minimaxTts
            val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
                .plus(config.speed).distinct().sorted()
            EtaPreferenceDivider(hasLeading = false)
            EtaOverlayDropdownPreference(
                title = stringResource(R.string.speech_speed),
                items = speeds.map { "$it×" },
                selectedIndex = speeds.indexOf(config.speed),
                onSelectedIndexChange = { index ->
                    speeds.getOrNull(index)?.let {
                        store.edit(store.settings.copy(minimaxTts = config.copy(speed = it)))
                    }
                },
            )
        }
        TtsProvider.DEEPSEEK -> {
            val config = store.settings.deepseekTts
            val onToggle: (Boolean) -> Unit = { checked ->
                store.edit(store.settings.copy(deepseekTts = config.copy(filterMarkdown = checked)))
            }
            EtaPreferenceDivider(hasLeading = false)
            io.github.mangi.eta.ui.components.EtaSwitchPreference(
                title = stringResource(R.string.speech_filter_markdown),
                checked = config.filterMarkdown,
                onCheckedChange = onToggle,
            )
        }
        TtsProvider.NONE, TtsProvider.QWEN, TtsProvider.DOUBAO -> Unit
    }
}
