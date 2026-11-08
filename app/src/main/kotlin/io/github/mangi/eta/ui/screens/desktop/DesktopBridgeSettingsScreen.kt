package io.github.mangi.eta.ui.screens.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.remote.DesktopBridge
import io.github.mangi.eta.agent.remote.DesktopBridgeConfig
import io.github.mangi.eta.ui.components.EtaButton
import io.github.mangi.eta.ui.components.EtaButtonKind
import io.github.mangi.eta.ui.components.EtaIconButton
import io.github.mangi.eta.ui.components.EtaPreferenceDefaults
import io.github.mangi.eta.ui.components.EtaPreferenceGroup
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import io.github.mangi.eta.ui.components.StatusError
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun DesktopBridgeSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        DesktopBridge.acquire(context)
        onDispose { DesktopBridge.release() }
    }
    val saved by DesktopBridge.config.collectAsState()
    val connection by DesktopBridge.client.state.collectAsState()
    var address by rememberSaveable { mutableStateOf(saved?.address.orEmpty()) }
    var token by rememberSaveable { mutableStateOf("") }
    var tokenVisible by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val invalidAddress = stringResource(R.string.desktop_address_invalid)
    val tokenRequired = stringResource(R.string.desktop_token_required)
    val hasSavedToken = saved?.token?.isNotBlank() == true

    MiuixScaffoldPage(
        title = stringResource(R.string.desktop_settings_title),
        onBack = onBack,
        modifier = Modifier.imePadding(),
    ) {
        item(key = "status") {
            DesktopConnectionBanner(connection, configured = saved?.isComplete == true, onRetry = DesktopBridge::retry)
        }
        item(key = "fields") {
            EtaPreferenceGroup {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextField(
                        value = address,
                        onValueChange = { address = it; error = null },
                        label = stringResource(R.string.desktop_address),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextField(
                        value = token,
                        onValueChange = { token = it; error = null },
                        label = stringResource(if (hasSavedToken) R.string.desktop_token_saved_hint else R.string.desktop_token),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            EtaIconButton(onClick = { tokenVisible = !tokenVisible }) {
                                Icon(
                                    imageVector = if (tokenVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                    contentDescription = null,
                                )
                            }
                        },
                    )
                }
            }
            Text(
                text = stringResource(R.string.desktop_token_hint) + "\n" + stringResource(R.string.desktop_setup_note),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
            )
        }
        item(key = "actions") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = EtaPreferenceDefaults.SidePadding)
                    .padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EtaButton(
                    text = stringResource(R.string.desktop_save_connect),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = {
                        val effectiveToken = token.trim().ifEmpty { saved?.token.orEmpty() }
                        val config = DesktopBridgeConfig(address.trim(), effectiveToken)
                        when {
                            config.webSocketUrl == null -> error = invalidAddress
                            effectiveToken.isBlank() -> error = tokenRequired
                            else -> runCatching { DesktopBridge.configure(context, config) }
                                .onSuccess { token = "" }
                                .onFailure { error = it.message }
                        }
                    },
                )
                if (saved != null) {
                    EtaButton(
                        text = stringResource(R.string.desktop_forget),
                        modifier = Modifier.fillMaxWidth(),
                        kind = EtaButtonKind.Destructive,
                        onClick = {
                            runCatching { DesktopBridge.clearConfig(context) }
                            address = ""
                            token = ""
                        },
                    )
                }
                error?.let {
                    Text(text = it, style = MiuixTheme.textStyles.footnote2, color = StatusError, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
