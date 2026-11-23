package io.github.mangi.eta.ui.pages.providers

import io.github.mangi.eta.R
import io.github.mangi.eta.core.AppStrings
import androidx.compose.ui.res.stringResource
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.data.model.ProviderAuthModes
import io.github.mangi.eta.ui.components.EtaPreference
import io.github.mangi.eta.ui.components.EtaButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ProviderAccountControls(
    state: ProviderLoginState,
    authMode: String,
    enabled: Boolean,
    browserError: String?,
    onOpenBrowser: () -> Unit,
    onManageUsage: () -> Unit,
    onLogin: () -> Unit,
    onDisconnect: () -> Unit,
    onCancel: () -> Unit,
) {
    val prompt = state.prompt

    val account = state.account
    val loggedIn = account != null && !account.needsReauthentication
    val needsLogin = !loggedIn || !account.planEnabled
    val brand = if (authMode == ProviderAuthModes.XAI_GROK) "Grok" else "Codex"
    EtaPreference(
        title = account?.email ?: account?.displayName ?: stringResource(R.string.provider_i18n_account_title, brand),
        summary = browserError ?: state.message ?: when (state.phase) {
            ProviderLoginPhase.Loading -> stringResource(R.string.provider_i18n_login_loading)
            ProviderLoginPhase.Saving -> stringResource(R.string.provider_i18n_login_saving)
            ProviderLoginPhase.Disconnecting -> stringResource(R.string.provider_i18n_login_disconnecting)
            ProviderLoginPhase.Authorizing -> stringResource(
                if (prompt == null) R.string.provider_i18n_login_preparing else R.string.provider_i18n_login_browser_prompt,
            )
            ProviderLoginPhase.SyncingModels -> stringResource(R.string.provider_i18n_login_syncing_models)
            ProviderLoginPhase.Idle -> when {
                account == null -> stringResource(
                    if (authMode == ProviderAuthModes.OPENAI_CHATGPT) R.string.provider_i18n_login_chatgpt_hint
                    else R.string.provider_i18n_login_grok_hint,
                )
                account.needsReauthentication -> stringResource(R.string.provider_i18n_login_reauth)
                !account.planEnabled -> stringResource(R.string.provider_i18n_login_plan_disabled)
                authMode == ProviderAuthModes.OPENAI_CHATGPT -> stringResource(R.string.provider_i18n_logged_in_codex)
                else -> stringResource(R.string.provider_i18n_logged_in_grok)
            }
        },
    )
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        prompt?.userCode?.let { userCode ->
            SelectionContainer {
                Text(
                    text = userCode,
                    style = MiuixTheme.textStyles.headline1,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.isBusy) {
                if (prompt != null) {
                    EtaButton(text = stringResource(R.string.provider_i18n_open_browser), onClick = onOpenBrowser, modifier = Modifier.weight(1f))
                }
                if (state.phase in setOf(ProviderLoginPhase.Saving, ProviderLoginPhase.Authorizing, ProviderLoginPhase.SyncingModels)) {
                    EtaButton(text = stringResource(R.string.action_cancel), onClick = onCancel, modifier = Modifier.weight(1f))
                }
            } else {
                if (needsLogin) {
                    EtaButton(
                        text = if (loggedIn) stringResource(R.string.provider_i18n_reauthorize) else stringResource(R.string.provider_i18n_sign_in_with, brand),
                        enabled = enabled,
                        onClick = onLogin,
                        modifier = Modifier.weight(1f),
                    )
                } else if (authMode == ProviderAuthModes.OPENAI_CHATGPT) {
                    EtaButton(text = stringResource(R.string.provider_i18n_manage_usage), enabled = enabled, onClick = onManageUsage, modifier = Modifier.weight(1f))
                }
                if (loggedIn) {
                    EtaButton(text = stringResource(R.string.provider_i18n_sign_out), enabled = enabled, onClick = onDisconnect, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

internal fun openProviderAccountLink(context: Context, url: String): String? = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    null
} catch (_: ActivityNotFoundException) {
    AppStrings.get(R.string.provider_i18n_no_browser, "未找到可用浏览器")
} catch (_: SecurityException) {
    AppStrings.get(R.string.provider_i18n_cannot_open_browser, "无法打开浏览器，请检查系统设置")
}
