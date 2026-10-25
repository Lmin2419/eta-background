package io.github.mangi.eta.ui.voice

import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.SpeechCredentialField
import io.github.mangi.eta.ui.components.EtaPreferenceGroup
import io.github.mangi.eta.ui.components.EtaPreferenceGroupTitle
import io.github.mangi.eta.ui.components.MiuixScaffoldPage

/** 自有 OSS 配置只做保存不做整体校验，允许先存存储配置再补齐识别配置。 */
@Composable
internal fun SpeechOssScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SpeechSettingsStore(context, scope) }
    SpeechLifecycle { store.stop() }
    val settings = store.settings

    MiuixScaffoldPage(
        title = stringResource(R.string.speech_oss_title),
        onBack = onBack,
        modifier = Modifier.imePadding(),
    ) {
        if (!store.loaded) {
            item(key = "loading") { SpeechLoadingState(store.message) }
            return@MiuixScaffoldPage
        }
        item(key = "note") {
            SpeechNote(stringResource(R.string.speech_oss_note))
        }
        item(key = "storage_title") {
            EtaPreferenceGroupTitle(stringResource(R.string.speech_group_storage))
        }
        item(key = "storage") {
            EtaPreferenceGroup {
                SpeechFieldColumn {
                    SpeechField(
                        label = stringResource(R.string.speech_oss_region),
                        value = settings.oss.region,
                        onChange = { store.edit(settings.copy(oss = settings.oss.copy(region = it.trim()))) },
                    )
                    SpeechField(
                        label = "Endpoint",
                        value = settings.oss.endpoint,
                        onChange = { store.edit(settings.copy(oss = settings.oss.copy(endpoint = it.trim()))) },
                    )
                    SpeechField(
                        label = "Bucket",
                        value = settings.oss.bucket,
                        onChange = { store.edit(settings.copy(oss = settings.oss.copy(bucket = it.trim()))) },
                    )
                    SpeechField(
                        label = stringResource(R.string.speech_oss_prefix),
                        value = settings.oss.prefix,
                        onChange = { store.edit(settings.copy(oss = settings.oss.copy(prefix = it.trim()))) },
                    )
                }
            }
        }
        item(key = "credentials_title") {
            EtaPreferenceGroupTitle(stringResource(R.string.speech_group_credentials))
        }
        item(key = "credentials") {
            EtaPreferenceGroup {
                SpeechFieldColumn {
                    SpeechField(
                        label = "AccessKey ID",
                        value = store.credentials.ossAccessKeyId,
                        secret = true,
                        onChange = { store.credential(SpeechCredentialField.OSS_KEY_ID, it.trim()) },
                    )
                    SpeechField(
                        label = "AccessKey Secret",
                        value = store.credentials.ossAccessKeySecret,
                        secret = true,
                        onChange = { store.credential(SpeechCredentialField.OSS_KEY_SECRET, it.trim()) },
                    )
                }
            }
        }
        item(key = "save") {
            SpeechSaveBlock(store) {}
        }
    }
}
