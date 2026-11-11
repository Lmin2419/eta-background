package io.github.mangi.eta.ui.screens.enhance

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.app.description
import io.github.mangi.eta.ui.app.rememberDeviceCapabilities
import io.github.mangi.eta.ui.components.EtaCard
import io.github.mangi.eta.ui.components.EtaPreference
import io.github.mangi.eta.ui.components.EtaPreferenceColors
import io.github.mangi.eta.ui.components.EtaPreferenceDefaults
import io.github.mangi.eta.ui.components.EtaPreferenceDivider
import io.github.mangi.eta.ui.components.EtaPreferenceGroup
import io.github.mangi.eta.ui.components.EtaPreferenceGroupTitle
import io.github.mangi.eta.ui.components.EtaPreferenceIcon
import io.github.mangi.eta.ui.components.EtaButton
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import io.github.mangi.eta.ui.model.AgentSystemEnhanceAction

@Composable
fun SystemEnhanceScreen(
    onAction: (AgentSystemEnhanceAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val capabilities = rememberDeviceCapabilities()
    val canRequestRoot = capabilities.root.suPresent && !capabilities.root.isGranted
    MiuixScaffoldPage(
        title = stringResource(R.string.capability_enhancements),
        onBack = { onAction(AgentSystemEnhanceAction.NavigateBack) },
        modifier = modifier,
    ) {
        item(key = "access") {
            EtaPreferenceGroup(modifier = Modifier.padding(horizontal = EtaPreferenceDefaults.SidePadding).padding(top = 8.dp, bottom = EtaPreferenceDefaults.GroupSpacing)) {
                EtaPreference(
                    title = "Root",
                    summary = capabilities.root.description(context),
                    startAction = { EtaPreferenceIcon(Icons.Outlined.Key, tint = EtaPreferenceColors.Yellow) },
                    endActions = {
                        EtaButton(
                            text = stringResource(
                                if (canRequestRoot) R.string.capability_root_request
                                else R.string.capability_root_refresh,
                            ),
                            enabled = !capabilities.root.isChecking,
                            onClick = {
                                onAction(
                                    if (canRequestRoot) AgentSystemEnhanceAction.RequestRoot
                                    else AgentSystemEnhanceAction.RefreshRoot,
                                )
                            },
                        )
                    },
                )
                EtaPreferenceDivider(hasLeading = true)
                EtaPreference(
                    title = stringResource(R.string.capability_xposed_service),
                    summary = stringResource(
                        if (capabilities.xposedConnected) R.string.capability_xposed_connected
                        else R.string.capability_xposed_disconnected,
                    ),
                    startAction = { EtaPreferenceIcon(Icons.Outlined.AccountTree, tint = EtaPreferenceColors.Teal) },
                )
            }
        }
        item(key = "framework-help") {
            EtaPreferenceGroup {
                EtaPreference(
                    title = stringResource(R.string.capability_xposed_help),
                    summary = stringResource(R.string.capability_xposed_help_summary),
                    startAction = { EtaPreferenceIcon(Icons.Outlined.Info, tint = EtaPreferenceColors.Slate) },
                )
            }
        }
        item(key = "root-title") { EtaPreferenceGroupTitle(stringResource(R.string.capability_root_features)) }
        item(key = "root-features") {
            EtaPreferenceGroup {
                EtaPreference(
                    title = stringResource(R.string.capability_root_device),
                    summary = stringResource(R.string.capability_root_device_summary),
                    startAction = { EtaPreferenceIcon(Icons.Outlined.AdminPanelSettings, tint = EtaPreferenceColors.Blue) },
                )
                EtaPreferenceDivider(hasLeading = true)
                EtaPreference(
                    title = stringResource(R.string.capability_root_data),
                    summary = stringResource(R.string.capability_root_data_summary),
                    startAction = { EtaPreferenceIcon(Icons.Outlined.Lock, tint = EtaPreferenceColors.Blue) },
                )
                EtaPreferenceDivider(hasLeading = true)
                EtaPreference(
                    title = stringResource(R.string.capability_root_linux),
                    summary = stringResource(R.string.capability_root_linux_summary),
                    startAction = { EtaPreferenceIcon(Icons.Outlined.Terminal, tint = EtaPreferenceColors.Slate) },
                )
            }
        }
        item(key = "hook-title") { EtaPreferenceGroupTitle(stringResource(R.string.capability_system_features)) }
        item(key = "hook-features") {
            EtaPreferenceGroup {
                EtaPreference(
                    title = stringResource(R.string.capability_hook_assistants),
                    summary = stringResource(R.string.capability_hook_assistants_summary),
                    startAction = { EtaPreferenceIcon(Icons.Outlined.AutoAwesome, tint = EtaPreferenceColors.Green) },
                )
                EtaPreferenceDivider(hasLeading = true)
                EtaPreference(
                    title = stringResource(R.string.capability_hook_google),
                    summary = stringResource(R.string.capability_hook_google_summary),
                    startAction = { EtaPreferenceIcon(Icons.AutoMirrored.Outlined.ManageSearch, tint = EtaPreferenceColors.Blue) },
                )
                EtaPreferenceDivider(hasLeading = true)
                EtaPreference(
                    title = stringResource(R.string.capability_hook_accessibility),
                    summary = stringResource(R.string.capability_hook_accessibility_summary),
                    startAction = { EtaPreferenceIcon(Icons.Outlined.VerifiedUser, tint = EtaPreferenceColors.Green) },
                )
            }
        }
    }
}
