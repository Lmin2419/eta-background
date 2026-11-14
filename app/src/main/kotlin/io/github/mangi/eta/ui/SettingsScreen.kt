package io.github.mangi.eta.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import io.github.mangi.eta.data.repository.AppearanceSettingsRepository
import io.github.mangi.eta.ui.app.LocalAppearanceSettings
import io.github.mangi.eta.ui.components.EtaListGroup
import io.github.mangi.eta.ui.components.EtaButton
import io.github.mangi.eta.ui.components.ListEmptyState
import io.github.mangi.eta.ui.components.PermissionHealthCard
import io.github.mangi.eta.ui.model.PermissionHealthUiState
import io.github.mangi.eta.ui.theme.EtaTheme
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.ImportContacts
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsVoice
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.SportsBar
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.SwipeUp
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.mangi.eta.EtaApp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.accessibility.AccessibilityProtectionClient
import io.github.mangi.eta.agent.accessibility.AgentAccessibilityService
import io.github.mangi.eta.agent.runtime.AgentExecutionService
import io.github.mangi.eta.agent.runtime.RuntimeDiagnostics
import io.github.mangi.eta.config.PowerAssistantTarget
import io.github.mangi.eta.config.Prefs
import io.github.mangi.eta.data.repository.ProviderRepository
import io.github.mangi.eta.data.repository.RuntimeConfigRepository
import io.github.mangi.eta.data.update.AppLatestRelease
import io.github.mangi.eta.data.update.AppUpdateChecker
import io.github.mangi.eta.systemizer.GoogleAppSystemizerInstaller
import io.github.mangi.eta.systemizer.RootManager
import io.github.mangi.eta.systemizer.SystemizerInstallResult
import io.github.mangi.eta.ui.app.EnhancementSettingsHistory
import io.github.mangi.eta.ui.app.rememberDeviceCapabilities
import io.github.mangi.eta.ui.components.EtaArrowPreference
import io.github.mangi.eta.ui.components.EtaDropdownPreference
import io.github.mangi.eta.ui.components.EtaPreference
import io.github.mangi.eta.ui.components.EtaPreferenceColors
import io.github.mangi.eta.ui.components.EtaPreferenceDivider
import io.github.mangi.eta.ui.components.EtaPreferenceIcon
import io.github.mangi.eta.ui.components.EtaSwitchPreference
import io.github.mangi.eta.ui.components.EtaWindowDialog
import io.github.mangi.eta.ui.components.LanguagePreference
import io.github.mangi.eta.ui.components.MiuixDialogActions
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import io.github.mangi.eta.ui.navigation.AppRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 模块配置界面。
 *
 * 开关默认值由 [Prefs.Keys.BOOLEAN_DEFAULTS] 统一定义。Eta Runtime 自己消费的开关写入
 * App 本地配置；仅 Hook 消费的开关通过 RemotePreferences 提交到 LSPosed。
 */
@Composable
internal fun SettingsScreen(
    context: Context,
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
    permissionHealthState: PermissionHealthUiState = PermissionHealthUiState(emptyList()),
) {
    SettingsPageContent(context = context, onNavigate = onNavigate, onBack = onBack, permissionHealthState = permissionHealthState)
}

@Composable
internal fun AssistantSettingsScreen(context: Context, onNavigate: (AppRoute) -> Unit, onBack: () -> Unit) {
    SettingsPageContent(context, onNavigate, onBack, assistantDetails = true)
}

@Composable
private fun SettingsPageContent(
    context: Context,
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
    permissionHealthState: PermissionHealthUiState = PermissionHealthUiState(emptyList()),
    assistantDetails: Boolean = false,
) {
    val coroutineScope = rememberCoroutineScope()
    var exportingDiagnostics by remember { mutableStateOf(false) }
    var taskNotificationsEnabled by remember { mutableStateOf(AgentExecutionService.taskNotificationsEnabled(context)) }
    val exportDiagnostics = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) coroutineScope.launch {
            exportingDiagnostics = true
            val saved = runCatching { withContext(Dispatchers.IO) {
                val report = RuntimeDiagnostics.snapshot(context.applicationContext)
                requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use { it.write(report) }
            } }.isSuccess
            exportingDiagnostics = false
            io.github.mangi.eta.ui.components.EtaToastCenter.show(context, context.getString(if (saved) R.string.background_diagnostics_saved
                else R.string.background_diagnostics_failed), Toast.LENGTH_SHORT)
        }
    }
    val capabilities = rememberDeviceCapabilities()
    val enhancementHistory = remember(context.applicationContext) { EnhancementSettingsHistory(context) }
    var hasConnectedFramework by remember { mutableStateOf(enhancementHistory.hasConnected) }
    var hasUsedSystemizer by remember { mutableStateOf(enhancementHistory.hasUsedSystemizer) }
    var showSystemizerDialog by remember { mutableStateOf(false) }
    var installingSystemizer by remember { mutableStateOf(false) }

    // 悬浮窗权限状态：授权后从系统设置返回时（ON_RESUME）刷新。
    var overlayGranted by remember {
        mutableStateOf(android.provider.Settings.canDrawOverlays(context))
    }
    var accessibilityGranted by remember {
        mutableStateOf(isAgentAccessibilityEnabled(context))
    }
    var accessibilityProtectionEnabled by remember {
        mutableStateOf(AccessibilityProtectionClient.isEnabled(context))
    }
    var accessibilityProtectionPending by remember { mutableStateOf(false) }
    val openAssistantSettings: () -> Unit = {
        val failed = runCatching {
            context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
        }.isFailure
        if (failed) {
            io.github.mangi.eta.ui.components.EtaToastCenter.show(context, context.getString(R.string.settings_open_assistant_failed), Toast.LENGTH_SHORT)
        }
    }

    // 关于组：版本信息与更新检查。结果对话框在列表外渲染，状态需要页面级 owner。
    val appPackageInfo = remember {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    val appVersionName = appPackageInfo.versionName.orEmpty()
    val appVersionSummary = "${appPackageInfo.versionName} (${appPackageInfo.longVersionCode})"
    val openUrl: (String) -> Unit = { url ->
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
    var checkingUpdate by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<AppLatestRelease?>(null) }
    val checkForUpdate: () -> Unit = {
        if (!checkingUpdate) {
            checkingUpdate = true
            coroutineScope.launch {
                val release = runCatching {
                    withContext(Dispatchers.IO) { AppUpdateChecker.fetchLatest() }
                }.getOrNull()
                checkingUpdate = false
                when {
                    release == null -> io.github.mangi.eta.ui.components.EtaToastCenter.show(
                        context.applicationContext,
                        context.getString(R.string.ui_update_check_failed),
                        Toast.LENGTH_SHORT,
                    )
                    AppUpdateChecker.isNewer(release.version, appVersionName) -> availableUpdate = release
                    else -> io.github.mangi.eta.ui.components.EtaToastCenter.show(
                        context.applicationContext,
                        context.getString(R.string.ui_update_already_latest),
                        Toast.LENGTH_SHORT,
                    )
                }
            }
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                taskNotificationsEnabled = AgentExecutionService.taskNotificationsEnabled(context)
                overlayGranted = android.provider.Settings.canDrawOverlays(context)
                accessibilityGranted = isAgentAccessibilityEnabled(context)
                accessibilityProtectionEnabled =
                    AccessibilityProtectionClient.isEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Provider / Model 选中状态展示
    val providers by ProviderRepository.providersFlow().collectAsState(initial = emptyList())
    val selectedProviderId by RuntimeConfigRepository.selectedProviderIdFlow()
        .collectAsState(initial = null)
    val selectedModelId by RuntimeConfigRepository.selectedModelIdFlow()
        .collectAsState(initial = null)
    val selectedProvider = remember(providers, selectedProviderId) {
        providers.find { it.id == selectedProviderId }
    }
    val selectedModel = remember(selectedProvider, selectedModelId) {
        selectedProvider?.models?.find { it.id == selectedModelId }
    }
    val providerSummary = selectedProvider?.let { provider ->
        "${provider.name} / ${selectedModel?.displayName ?: stringResource(R.string.settings_model_not_selected)}"
    } ?: stringResource(R.string.settings_not_configured)

    // prefs 绑定到 XposedService：service 到达时切换到 RemotePreferences（跨进程提交到
    // LSPosed 数据库）；未就绪时保持 null，UI 禁止修改。
    var prefs by remember { mutableStateOf(Prefs.remotePreferencesForUi(EtaApp.serviceInstance)) }
    val agentPrefs = remember { Prefs.localAgentPreferences() }
    var powerAssistantTarget by remember(prefs) {
        mutableStateOf(prefs?.let(Prefs::powerAssistantTarget) ?: enhancementHistory.powerTarget())
    }
    DisposableEffect(prefs) {
        val targetPrefs = prefs ?: return@DisposableEffect onDispose {}
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { changedPrefs, key ->
            if (key == Prefs.Keys.POWER_KEY_ASSISTANT_TARGET ||
                key == Prefs.Keys.POWER_KEY_TAKEOVER
            ) {
                powerAssistantTarget = Prefs.powerAssistantTarget(changedPrefs)
            }
        }
        targetPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { targetPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    DisposableEffect(Unit) {
        val listener = object : EtaApp.ServiceStateListener {
            override fun onServiceStateChanged(service: io.github.libxposed.service.XposedService?) {
                prefs = Prefs.remotePreferencesForUi(service)
                prefs?.let { connected ->
                    enhancementHistory.captureConnected(connected)
                    hasConnectedFramework = true
                }
                Prefs.reconcileAgentPreferences(service)
                coroutineScope.launch {
                    RuntimeConfigRepository.ensureDefaults(service)
                }
            }
        }
        EtaApp.addServiceStateListener(listener, notifyImmediately = true)
        onDispose { EtaApp.removeServiceStateListener(listener) }
    }
    val powerAssistantTargets = PowerAssistantTarget.entries
    val powerAssistantItems = powerAssistantTargets.map { target ->
        DropdownItem(text = target.displayName(context))
    }

    val appearance = LocalAppearanceSettings.current
    MiuixScaffoldPage(
        title = stringResource(if (assistantDetails) R.string.eta_more_assistant_settings else R.string.route_settings),
        onBack = onBack,
        containerColor = EtaTheme.colors.surfaceSunken,
    ) {
        if (!assistantDetails) {
            item(key = "readiness") {
                PermissionHealthCard(permissionHealthState, onOpenPermissions = { onNavigate(AppRoute.Permissions) })
            }
            item(key = "section_models") {
                EtaListGroup(title = stringResource(R.string.eta_settings_models)) {
                    EtaArrowPreference(
                        title = stringResource(R.string.ui_model_provider_e8c7f5),
                        summary = providerSummary,
                        startAction = {
                            EtaPreferenceIcon(
                                icon = Icons.Outlined.Cloud,
                                tint = EtaPreferenceColors.Blue,
                            )
                        },
                        onClick = { onNavigate(AppRoute.ModelProviders) },
                    )
                    EtaPreferenceDivider()
                    SwitchPref(
                        context = context,
                        prefs = agentPrefs,
                        title = stringResource(R.string.ui_deep_thinking_enabled_by_default_c032d6),
                        key = Prefs.Keys.AGENT_THINKING_ENABLED,
                        icon = Icons.Outlined.Psychology,
                        iconTint = EtaPreferenceColors.Blue,
                    )
                    EtaPreferenceDivider()
                    SwitchPref(
                        context = context,
                        prefs = agentPrefs,
                        title = stringResource(R.string.settings_auto_compaction),
                        key = Prefs.Keys.AGENT_AUTO_COMPACTION_ENABLED,
                        icon = Icons.Outlined.Layers,
                        iconTint = EtaPreferenceColors.Blue,
                    )
                    EtaPreferenceDivider()
                    EtaArrowPreference(
                        title = stringResource(R.string.conversation_title_settings_title),
                        startAction = { EtaPreferenceIcon(Icons.Outlined.AutoAwesome, tint = EtaPreferenceColors.Blue) },
                        onClick = { onNavigate(AppRoute.ConversationTitleSettings) },
                    )
                    EtaPreferenceDivider()
                    EtaArrowPreference(
                        title = stringResource(R.string.ui_memory_b55ff5),
                        startAction = {
                            EtaPreferenceIcon(
                                icon = Icons.AutoMirrored.Outlined.MenuBook,
                                tint = EtaPreferenceColors.Violet,
                            )
                        },
                        onClick = { onNavigate(AppRoute.Memory) },
                    )
                    EtaPreferenceDivider()
                    EtaArrowPreference(
                        title = "Subagent",
                        startAction = {
                            EtaPreferenceIcon(
                                icon = Icons.Outlined.Hub,
                                tint = EtaPreferenceColors.Violet,
                            )
                        },
                        onClick = { onNavigate(AppRoute.Subagents) },
                    )
                    EtaPreferenceDivider()
                    EtaArrowPreference(
                        title = stringResource(R.string.desktop_title),
                        summary = stringResource(R.string.desktop_settings_entry_summary),
                        startAction = {
                            EtaPreferenceIcon(
                                icon = Icons.Outlined.Computer,
                                tint = EtaPreferenceColors.Blue,
                            )
                        },
                        onClick = { onNavigate(AppRoute.DesktopSessions) },
                    )
                }
            }
            item(key = "section_capabilities") {
                EtaListGroup(title = stringResource(R.string.eta_settings_capabilities)) {
                    EtaArrowPreference(
                        title = stringResource(R.string.settings_tools_list),
                        startAction = { EtaPreferenceIcon(Icons.Outlined.Dashboard, tint = EtaPreferenceColors.Teal) },
                        onClick = { onNavigate(AppRoute.Tools) },
                    )
                    EtaPreferenceDivider()
                    SwitchPref(
                        context = context,
                        prefs = agentPrefs,
                        title = stringResource(R.string.ui_enable_web_browsing_tools_8b6b03),
                        key = Prefs.Keys.AGENT_BROWSER_TOOLS,
                        icon = Icons.Outlined.Language,
                        iconTint = EtaPreferenceColors.Teal,
                    )
                    EtaPreferenceDivider()
                    EtaArrowPreference(
                        title = stringResource(R.string.web_tools_settings_title),
                        startAction = {
                            EtaPreferenceIcon(
                                icon = Icons.Outlined.TravelExplore,
                                tint = EtaPreferenceColors.Teal,
                            )
                        },
                        onClick = { onNavigate(AppRoute.WebToolsSettings) },
                    )
                    EtaPreferenceDivider()
                    SwitchPref(
                        context = context,
                        prefs = agentPrefs,
                        title = stringResource(R.string.ui_enable_device_direct_tools_e2d595),
                        key = Prefs.Keys.AGENT_DEVICE_DIRECT_TOOLS,
                        icon = Icons.Outlined.Smartphone,
                        iconTint = EtaPreferenceColors.Teal,
                    )
                    EtaPreferenceDivider()
                    SwitchPref(
                        context = context,
                        prefs = agentPrefs,
                        title = stringResource(R.string.ui_allow_reading_of_sensitive_device_information_feaec0),
                        summary = stringResource(R.string.eta_sensitive_read_summary),
                        key = Prefs.Keys.AGENT_DEVICE_SENSITIVE_READ_TOOLS,
                        icon = Icons.Outlined.Visibility,
                        iconTint = EtaPreferenceColors.Teal,
                    )
                    EtaPreferenceDivider()
                    SwitchPref(
                        context = context,
                        prefs = agentPrefs,
                        title = stringResource(R.string.ui_allow_sensitive_device_operation_3d42ea),
                        summary = stringResource(R.string.eta_sensitive_action_summary),
                        key = Prefs.Keys.AGENT_DEVICE_SENSITIVE_ACTION_TOOLS,
                        icon = Icons.Outlined.GppMaybe,
                        iconTint = EtaPreferenceColors.Teal,
                    )
                    EtaPreferenceDivider()
                    SwitchPref(
                        context = context,
                        prefs = agentPrefs,
                        title = stringResource(R.string.ui_enable_terminal_file_tools_18bb43),
                        summary = stringResource(R.string.eta_terminal_tools_summary),
                        key = Prefs.Keys.AGENT_TERMINAL_TOOLS,
                        icon = Icons.Outlined.Terminal,
                        iconTint = EtaPreferenceColors.Slate,
                    )
                    EtaPreferenceDivider()
                    EtaArrowPreference(
                        title = stringResource(R.string.ui_linux_tool_environment_314d22),
                        startAction = {
                            EtaPreferenceIcon(
                                icon = Icons.Outlined.Inventory2,
                                tint = EtaPreferenceColors.Slate,
                            )
                        },
                        onClick = { onNavigate(AppRoute.LinuxEnvironment) },
                    )
                    EtaPreferenceDivider()
                    // 终端入口从侧栏移到这里（阶段 12）。
                    EtaArrowPreference(
                        title = stringResource(R.string.route_terminal),
                        startAction = {
                            EtaPreferenceIcon(
                                icon = Icons.Outlined.Terminal,
                                tint = EtaPreferenceColors.Slate,
                            )
                        },
                        onClick = { onNavigate(AppRoute.Terminal) },
                    )
                    EtaPreferenceDivider()
                    EtaArrowPreference(
                        title = stringResource(R.string.route_skills),
                        startAction = {
                            EtaPreferenceIcon(
                                icon = Icons.Outlined.ImportContacts,
                                tint = EtaPreferenceColors.Violet,
                            )
                        },
                        onClick = { onNavigate(AppRoute.Skills) },
                    )
                    EtaPreferenceDivider()
                    EtaArrowPreference(
                        title = stringResource(R.string.route_mcp_servers),
                        startAction = {
                            EtaPreferenceIcon(
                                icon = Icons.Outlined.AccountTree,
                                tint = EtaPreferenceColors.Teal,
                            )
                        },
                        onClick = { onNavigate(AppRoute.McpServers) },
                    )
                    EtaPreferenceDivider()
                    EtaArrowPreference(
                        title = stringResource(R.string.eta_characters),
                        startAction = {
                            EtaPreferenceIcon(
                                icon = Icons.Outlined.SportsBar,
                                tint = EtaPreferenceColors.Pink,
                            )
                        },
                        onClick = { onNavigate(AppRoute.Characters) },
                    )
                }
            }
            item(key = "section_background") {
                EtaListGroup(title = stringResource(R.string.eta_settings_background)) {
                    EtaArrowPreference(
                        title = stringResource(R.string.background_notifications_title),
                        summary = stringResource(if (taskNotificationsEnabled) R.string.background_notifications_enabled
                            else R.string.background_notifications_disabled),
                        startAction = { EtaPreferenceIcon(icon = Icons.Outlined.Notifications, tint = EtaPreferenceColors.Indigo) },
                        onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) },
                    )
                    EtaPreferenceDivider()
                    EtaSwitchPreference(title = stringResource(R.string.appearance_task_completion_alerts),
                        summary = stringResource(R.string.appearance_task_completion_alerts_summary),
                        checked = appearance.taskCompletionAlertsEnabled,
                        startAction = { EtaPreferenceIcon(Icons.Outlined.NotificationsActive, tint = EtaPreferenceColors.Indigo) },
                        onCheckedChange = { enabled -> coroutineScope.launch {
                            AppearanceSettingsRepository.update { it.copy(taskCompletionAlertsEnabled = enabled) }
                        } })
                    EtaPreferenceDivider()
                    EtaArrowPreference(
                        title = stringResource(R.string.background_diagnostics_title),
                        summary = stringResource(R.string.background_diagnostics_summary),
                        startAction = { EtaPreferenceIcon(icon = Icons.Outlined.BugReport, tint = EtaPreferenceColors.Indigo) },
                        enabled = !exportingDiagnostics,
                        onClick = { exportDiagnostics.launch("Etax-diagnostics-${System.currentTimeMillis()}.json") },
                    )
                }
            }
            item(key = "section_assistant") {
                EtaListGroup(title = stringResource(R.string.eta_settings_assistant)) {
                    EtaArrowPreference(
                        title = stringResource(R.string.ui_eta_system_assistant_003e9b),
                        startAction = {
                            EtaPreferenceIcon(
                                icon = Icons.Outlined.SupportAgent,
                                tint = EtaPreferenceColors.Orange,
                            )
                        },
                        onClick = openAssistantSettings,
                    )
                    EtaPreferenceDivider()
                    NativeAssistantPreference(context)
                    if (prefs != null || hasConnectedFramework) {
                        EtaPreferenceDivider()
                        EtaDropdownPreference(
                            title = stringResource(R.string.ui_long_press_the_power_button_1958d0),
                            items = powerAssistantItems,
                            selectedIndex = powerAssistantTargets.indexOf(powerAssistantTarget),
                            onSelectedIndexChange = { index ->
                                val target = powerAssistantTargets.getOrNull(index)
                                    ?: return@EtaDropdownPreference
                                val targetPrefs = prefs ?: return@EtaDropdownPreference
                                if (putStringSync(
                                        prefs = targetPrefs,
                                        key = Prefs.Keys.POWER_KEY_ASSISTANT_TARGET,
                                        value = target.persistedValue,
                                    )
                                ) {
                                    powerAssistantTarget = target
                                    enhancementHistory.recordCommittedTarget(target)
                                } else {
                                    io.github.mangi.eta.ui.components.EtaToastCenter.show(
                                        context.applicationContext,
                                        context.getString(R.string.settings_write_failed),
                                        Toast.LENGTH_SHORT,
                                    )
                                }
                            },
                            startAction = {
                                EtaPreferenceIcon(
                                    icon = Icons.Outlined.PowerSettingsNew,
                                    tint = EtaPreferenceColors.Orange,
                                    enabled = prefs != null,
                                )
                            },
                            enabled = prefs != null,
                        )
                    }
                    if (prefs != null || hasConnectedFramework) {
                        EtaPreferenceDivider()
                        SwitchPref(
                            context = context,
                            prefs = prefs,
                            title = stringResource(R.string.ui_automatically_set_default_assistant_f86963),
                            summary = stringResource(R.string.eta_auto_assistant_summary),
                            key = Prefs.Keys.ASSISTANT_AUTO_CONFIG,
                            icon = Icons.Outlined.Settings,
                            iconTint = EtaPreferenceColors.Orange,
                        )
                    }
                    EtaPreferenceDivider()
                    EtaArrowPreference(title = stringResource(R.string.eta_more_assistant_settings),
                        startAction = { EtaPreferenceIcon(Icons.Outlined.SettingsVoice, tint = EtaPreferenceColors.Orange) },
                        onClick = { onNavigate(AppRoute.AssistantSettings) })
                }
            }
            item(key = "section_permissions") {
                EtaListGroup(title = stringResource(R.string.eta_settings_permissions)) {
                    EtaArrowPreference(title = stringResource(R.string.ui_permission_health_3048bb),
                        startAction = { EtaPreferenceIcon(Icons.Outlined.VerifiedUser, tint = EtaPreferenceColors.Green) },
                        onClick = { onNavigate(AppRoute.Permissions) })
                    EtaPreferenceDivider()
                    EtaArrowPreference(
                        title = stringResource(R.string.ui_floating_window_permissions_076b77),
                        startAction = {
                            EtaPreferenceIcon(
                                icon = Icons.Outlined.Layers,
                                tint = EtaPreferenceColors.Green,
                            )
                        },
                        endActions = {
                            Text(
                                text = stringResource(
                                    if (overlayGranted) R.string.status_authorized else R.string.status_unauthorized,
                                ),
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = if (overlayGranted) {
                                    MiuixTheme.colorScheme.onSurfaceVariantActions
                                } else {
                                    MiuixTheme.colorScheme.error
                                },
                            )
                        },
                        onClick = {
                            io.github.mangi.eta.ui.app.OverlayPermissionSettings.open(context)
                        },
                    )
                    EtaPreferenceDivider()
                    EtaArrowPreference(
                        title = stringResource(R.string.ui_accessibility_enhancement_tools_8fd257),
                        startAction = {
                            EtaPreferenceIcon(
                                icon = Icons.Outlined.AccessibilityNew,
                                tint = EtaPreferenceColors.Green,
                            )
                        },
                        endActions = {
                            val enabled = accessibilityGranted || AgentAccessibilityService.isAvailable()
                            Text(
                                text = stringResource(
                                    if (enabled) R.string.status_enabled else R.string.status_disabled,
                                ),
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = if (enabled) {
                                    MiuixTheme.colorScheme.onSurfaceVariantActions
                                } else {
                                    MiuixTheme.colorScheme.primary
                                },
                            )
                        },
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS),
                                )
                            }
                        },
                    )
                    if (AccessibilityProtectionClient.isSupported() && (prefs != null || hasConnectedFramework)) {
                        EtaPreferenceDivider()
                        EtaSwitchPreference(
                            title = stringResource(R.string.ui_enforce_accessibility_55e838),
                            checked = accessibilityProtectionEnabled,
                            onCheckedChange = { enabled ->
                                if (accessibilityProtectionPending) {
                                    return@EtaSwitchPreference
                                }
                                accessibilityProtectionPending = true
                                AccessibilityProtectionClient.setEnabled(
                                    context = context,
                                    enabled = enabled,
                                ) { result ->
                                    accessibilityProtectionPending = false
                                    accessibilityProtectionEnabled = result.enabled
                                    accessibilityGranted = isAgentAccessibilityEnabled(context)
                                    val failureMessage = when (result.status) {
                                        AccessibilityProtectionClient.ControlStatus.APPLIED -> null
                                        AccessibilityProtectionClient.ControlStatus.UNAVAILABLE ->
                                            context.getString(R.string.accessibility_protection_unavailable)
                                        AccessibilityProtectionClient.ControlStatus.REJECTED ->
                                            context.getString(R.string.accessibility_protection_rejected)
                                    }
                                    if (failureMessage != null) {
                                        io.github.mangi.eta.ui.components.EtaToastCenter.show(
                                            context.applicationContext,
                                            failureMessage,
                                            Toast.LENGTH_SHORT,
                                        )
                                    }
                                }
                            },
                            startAction = {
                                EtaPreferenceIcon(
                                    icon = Icons.Outlined.VerifiedUser,
                                    tint = EtaPreferenceColors.Green,
                                    enabled = prefs != null && !accessibilityProtectionPending,
                                )
                            },
                            enabled = prefs != null && !accessibilityProtectionPending,
                        )
                    }
                    EtaPreferenceDivider()
                    EtaArrowPreference(
                        title = stringResource(R.string.capability_enhancements),
                        startAction = { EtaPreferenceIcon(Icons.Outlined.Security, tint = EtaPreferenceColors.Green) },
                        onClick = { onNavigate(AppRoute.SystemEnhance) },
                    )
                }
            }
            item(key = "section_personalization") {
                EtaListGroup(title = stringResource(R.string.eta_settings_personalization)) {
                    EtaArrowPreference(
                        title = stringResource(R.string.appearance_title),
                        startAction = {
                            EtaPreferenceIcon(
                                icon = Icons.Outlined.Palette,
                                tint = EtaPreferenceColors.Pink,
                            )
                        },
                        onClick = { onNavigate(AppRoute.AppearanceSettings) },
                    )
                    EtaPreferenceDivider()
                    EtaArrowPreference(
                        title = stringResource(R.string.speech_settings_title),
                        startAction = { EtaPreferenceIcon(icon = Icons.Outlined.Mic, tint = EtaPreferenceColors.Pink) },
                        onClick = { onNavigate(AppRoute.SpeechSettings) },
                    )
                    EtaPreferenceDivider()
                    LanguagePreference(iconTint = EtaPreferenceColors.Pink)
                    EtaPreferenceDivider()
                    val hapticsContext = androidx.compose.ui.platform.LocalContext.current
                    val hapticsOn by remember(hapticsContext) {
                        io.github.mangi.eta.ui.components.EtaHaptics.init(hapticsContext)
                        io.github.mangi.eta.ui.components.EtaHaptics.enabled
                    }.collectAsState()
                    EtaSwitchPreference(title = stringResource(R.string.settings_haptics_title),
                        summary = stringResource(R.string.settings_haptics_summary),
                        checked = hapticsOn,
                        startAction = { EtaPreferenceIcon(Icons.Outlined.Vibration, tint = EtaPreferenceColors.Pink) },
                        onCheckedChange = { io.github.mangi.eta.ui.components.EtaHaptics.setEnabled(hapticsContext, it) })
                }
            }
            item(key = "section_data") {
                EtaListGroup(title = stringResource(R.string.eta_settings_data)) {
                    EtaArrowPreference(
                        title = stringResource(R.string.data_backup_title),
                        startAction = {
                            EtaPreferenceIcon(
                                icon = Icons.Outlined.Description,
                                tint = EtaPreferenceColors.Slate,
                            )
                        },
                        onClick = { onNavigate(AppRoute.DataBackup) },
                    )
                }
            }
            item(key = "section_about") {
                EtaListGroup(title = stringResource(R.string.ui_about_bed172)) {
                    EtaPreference(
                        title = stringResource(R.string.ui_about_version_title),
                        summary = appVersionSummary,
                        startAction = {
                            EtaPreferenceIcon(
                                icon = Icons.Outlined.Info,
                                tint = EtaPreferenceColors.Slate,
                            )
                        },
                    )
                    EtaPreferenceDivider()
                    EtaArrowPreference(
                        title = stringResource(R.string.ui_about_update_title),
                        summary = if (checkingUpdate) {
                            stringResource(R.string.ui_about_update_checking)
                        } else {
                            null
                        },
                        startAction = {
                            EtaPreferenceIcon(
                                icon = Icons.Outlined.SystemUpdate,
                                tint = EtaPreferenceColors.Slate,
                                enabled = !checkingUpdate,
                            )
                        },
                        enabled = !checkingUpdate,
                        onClick = checkForUpdate,
                    )
                    EtaPreferenceDivider()
                    EtaArrowPreference(
                        title = stringResource(R.string.ui_about_feedback_title),
                        startAction = {
                            EtaPreferenceIcon(
                                icon = Icons.Outlined.BugReport,
                                tint = EtaPreferenceColors.Slate,
                            )
                        },
                        onClick = { openUrl("https://github.com/Mangi-11/Eta/issues") },
                    )
                    EtaPreferenceDivider()
                    EtaArrowPreference(
                        title = stringResource(R.string.ui_about_github_star_title),
                        summary = stringResource(R.string.ui_about_github_star_hint),
                        startAction = {
                            EtaPreferenceIcon(
                                icon = Icons.Outlined.Code,
                                tint = EtaPreferenceColors.Slate,
                            )
                        },
                        onClick = { openUrl("https://github.com/Mangi-11/Eta") },
                    )
                }
            }
        } else {
            item(key = "assistant_details_description") {
                Text(stringResource(R.string.eta_assistant_details_summary), Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = EtaTheme.type.subhead, color = EtaTheme.colors.textSecondary)
            }
            if (prefs != null || hasConnectedFramework) {
                item(key = "assistant_vendor") {
                    EtaListGroup(title = stringResource(R.string.ui_xiaobu_xiaoai_compatible_entrance_ae918a)) {
                        SwitchPref(
                            context = context,
                            prefs = prefs,
                            title = stringResource(R.string.ui_enable_vendor_assistant_custom_models_c8e465),
                            key = Prefs.Keys.AGENT_CUSTOM_MODEL,
                            icon = Icons.Outlined.Cloud,
                            iconTint = EtaPreferenceColors.Orange,
                        )
                        EtaPreferenceDivider()
                        SwitchPref(
                            context = context,
                            prefs = prefs,
                            title = stringResource(R.string.ui_only_take_over_with_agent_prefix_d17556),
                            key = Prefs.Keys.AGENT_REQUIRE_PREFIX,
                            icon = Icons.Outlined.FilterAlt,
                            iconTint = EtaPreferenceColors.Orange,
                        )
                    }
                }
            }
            if (prefs != null || hasConnectedFramework || capabilities.root.isGranted || hasUsedSystemizer) {
                item(key = "assistant_google") {
                    EtaListGroup(title = "Gemini") {
                        if (prefs != null || hasConnectedFramework) {
                            SwitchPref(
                                context = context,
                                prefs = prefs,
                                title = stringResource(R.string.ui_maintain_hey_google_detection_after_screen_rest_9d6877),
                                key = Prefs.Keys.HOTWORD_SELF_HEAL,
                                icon = Icons.Outlined.Hearing,
                                iconTint = EtaPreferenceColors.Orange,
                            )
                        }
                        if (prefs != null || hasConnectedFramework) {
                            EtaPreferenceDivider()
                            SwitchPref(
                                context = context,
                                prefs = prefs,
                                title = stringResource(R.string.ui_lock_screen_evokes_automatic_voice_input_1cde18),
                                key = Prefs.Keys.LOCKSCREEN_VOICE_COMMAND,
                                icon = Icons.Outlined.Lock,
                                iconTint = EtaPreferenceColors.Orange,
                            )
                        }
                        if (prefs != null || hasConnectedFramework) {
                            EtaPreferenceDivider()
                            SwitchPref(
                                context = context,
                                prefs = prefs,
                                title = stringResource(R.string.ui_bright_screen_evokes_automatic_voice_input_4358fe),
                                key = Prefs.Keys.SCREEN_ON_VOICE_COMMAND,
                                icon = Icons.Outlined.SettingsVoice,
                                iconTint = EtaPreferenceColors.Orange,
                            )
                        }
                        if (capabilities.root.isGranted || hasUsedSystemizer) {
                            if (prefs != null || hasConnectedFramework) EtaPreferenceDivider()
                            EtaArrowPreference(
                                title = stringResource(R.string.ui_convert_google_apps_to_system_apps_0f6d89),
                                startAction = {
                                    EtaPreferenceIcon(
                                        icon = Icons.Outlined.Inventory,
                                        tint = EtaPreferenceColors.Orange,
                                        enabled = !installingSystemizer,
                                    )
                                },
                                summary = if (capabilities.root.isGranted) null else stringResource(R.string.capability_root_required),
                                enabled = !installingSystemizer,
                                holdDownState = showSystemizerDialog,
                                onClick = {
                                    if (!capabilities.root.isGranted) {
                                        onNavigate(AppRoute.SystemEnhance)
                                    } else if (!installingSystemizer) {
                                        showSystemizerDialog = true
                                    }
                                },
                            )
                        }
                    }
                }
            }
            if (prefs != null || hasConnectedFramework) {
                item(key = "assistant_circle") {
                    EtaListGroup(title = stringResource(R.string.ui_search_in_one_turn_179584)) {
                        SwitchPref(
                            context = context,
                            prefs = prefs,
                            title = stringResource(R.string.ui_long_press_on_the_gesture_bar_triggers_a_circle_to_s_b80117),
                            key = Prefs.Keys.GESTURE_BAR_CIRCLE_TO_SEARCH,
                            icon = Icons.Outlined.SwipeUp,
                            iconTint = EtaPreferenceColors.Orange,
                        )
                        EtaPreferenceDivider()
                        SwitchPref(
                            context = context,
                            prefs = prefs,
                            title = stringResource(R.string.ui_long_press_with_two_fingers_to_trigger_a_circle_sear_ab597a),
                            key = Prefs.Keys.DOUBLE_FINGER_CIRCLE_TO_SEARCH,
                            icon = Icons.Outlined.TouchApp,
                            iconTint = EtaPreferenceColors.Orange,
                        )
                    }
                }
            }
            if (!(prefs != null || hasConnectedFramework || capabilities.root.isGranted || hasUsedSystemizer)) {
                item(key = "assistant_details_empty") {
                    ListEmptyState(title = stringResource(R.string.eta_assistant_details_unavailable),
                        icon = Icons.Outlined.SettingsVoice,
                        action = { EtaButton(stringResource(R.string.capability_enhancements), { onNavigate(AppRoute.SystemEnhance) }) })
                }
            }
        }
        item(key = "settings_bottom_space") { Spacer(Modifier.height(8.dp)) }
    }

        SystemizerConfirmDialog(
            show = showSystemizerDialog,
            installing = installingSystemizer,
            onDismissRequest = {
                if (!installingSystemizer) {
                    showSystemizerDialog = false
                }
            },
            onConfirm = {
                if (installingSystemizer) return@SystemizerConfirmDialog
                if (!capabilities.root.isGranted) {
                    showSystemizerDialog = false
                    onNavigate(AppRoute.SystemEnhance)
                    return@SystemizerConfirmDialog
                }
                enhancementHistory.recordSystemizerUse()
                hasUsedSystemizer = true
                showSystemizerDialog = false
                installingSystemizer = true
                coroutineScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        GoogleAppSystemizerInstaller(context.applicationContext).install()
                    }
                    installingSystemizer = false
                    io.github.mangi.eta.ui.components.EtaToastCenter.show(
                        context.applicationContext,
                        result.toToastMessage(context),
                        Toast.LENGTH_LONG,
                    )
                }
            },
        )

        availableUpdate?.let { update ->
            EtaWindowDialog(
                show = true,
                title = stringResource(R.string.ui_update_available_title),
                summary = stringResource(R.string.ui_update_available_message, update.version, appVersionName),
                onDismissRequest = { availableUpdate = null },
            ) {
                MiuixDialogActions(
                    confirmText = stringResource(R.string.ui_update_go_download),
                    onCancel = { availableUpdate = null },
                    onConfirm = {
                        availableUpdate = null
                        openUrl(update.url)
                    },
                )
            }
        }
}

// ── 系统化确认对话框 ─────────────────────────────────────────────────────────

@Composable
private fun SystemizerConfirmDialog(
    show: Boolean,
    installing: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    EtaWindowDialog(
        show = show,
        title = stringResource(R.string.ui_convert_google_apps_to_system_apps_0f6d89),
        summary = stringResource(R.string.ui_system_applications_have_voice_wake_up_permissions_f_0190f2),
        onDismissRequest = onDismissRequest,
    ) {
        MiuixDialogActions(
            confirmText = if (installing) {
                stringResource(R.string.status_processing)
            } else {
                stringResource(R.string.action_confirm)
            },
            cancelEnabled = !installing,
            confirmEnabled = !installing,
            onCancel = onDismissRequest,
            onConfirm = onConfirm,
        )
    }
}

// ── 带图标的布尔开关 ─────────────────────────────────────────────────────────

/**
 * 单个布尔开关：状态随 [prefs]/[key] 变化重读，切换时同步写入。
 *
 * 配置来源由调用方按能力边界传入。Hook 开关仍可能因 LSPosed 未连接而禁用；Agent
 * Runtime 开关始终使用 App 本地配置。
 */
@Composable
private fun SwitchPref(
    context: Context,
    prefs: SharedPreferences?,
    title: String,
    summary: String? = null,
    key: String,
    icon: ImageVector,
    iconTint: Color,
) {
    val enabled = prefs != null
    val history = remember(context.applicationContext) { EnhancementSettingsHistory(context) }
    val default = Prefs.Keys.BOOLEAN_DEFAULTS[key] ?: true
    var checked by remember(prefs, key) {
        mutableStateOf(prefs?.getBoolean(key, default) ?: history.checked(key, default))
    }
    DisposableEffect(prefs, key) {
        val targetPrefs = prefs ?: return@DisposableEffect onDispose {}
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { changedPrefs, changedKey ->
            if (changedKey == key) {
                checked = changedPrefs.getBoolean(key, default)
            }
        }
        targetPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { targetPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    EtaSwitchPreference(
        title = title,
        summary = summary,
        checked = checked,
        onCheckedChange = { value ->
            // 同步提交；RemotePreferences.commit() 失败（binder 提交失败）时回滚 UI 状态，
            // 避免 UI 显示已切换而 hook 进程实际未收到。
            val targetPrefs = prefs ?: return@EtaSwitchPreference
            if (putBooleanSync(targetPrefs, key, value)) {
                checked = value
                history.recordCommittedBoolean(key, value)
                if (key in Prefs.Keys.LOCAL_AGENT_KEYS) {
                    Prefs.reconcileAgentPreferences(EtaApp.serviceInstance)
                }
            } else {
                io.github.mangi.eta.ui.components.EtaToastCenter.show(
                    context.applicationContext,
                    context.getString(R.string.settings_write_failed),
                    Toast.LENGTH_SHORT,
                )
            }
        },
        startAction = {
            EtaPreferenceIcon(icon = icon, enabled = enabled, tint = iconTint)
        },
        enabled = enabled,
    )
}

/**
 * 同步写入布尔值。RemotePreferences 的 [commit] 先更新本进程 map 再同步等待 binder 提交，
 * 失败（binder RemoteException）返回 false 但本进程 map 已被改写——此时 hook 进程收不到新值。
 * 返回是否提交成功，供调用方决定是否更新 UI。
 */
private fun putBooleanSync(
    prefs: SharedPreferences,
    key: String,
    value: Boolean
): Boolean =
    runCatching { prefs.edit().putBoolean(key, value).commit() }.getOrDefault(false)

private fun putStringSync(
    prefs: SharedPreferences,
    key: String,
    value: String
): Boolean =
    runCatching { prefs.edit().putString(key, value).commit() }.getOrDefault(false)

private fun PowerAssistantTarget.displayName(context: Context): String =
    when (this) {
        PowerAssistantTarget.OEM -> context.getString(R.string.power_assistant_system_default)
        PowerAssistantTarget.GEMINI -> "Gemini"
        PowerAssistantTarget.ETA -> "胖小胖"
    }

private fun isAgentAccessibilityEnabled(context: Context): Boolean {
    val expected = ComponentName(
        context,
        AgentAccessibilityService::class.java
    ).flattenToString()
    val enabledServices = android.provider.Settings.Secure.getString(
        context.contentResolver,
        android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ).orEmpty()
    return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
}

private fun SystemizerInstallResult.toToastMessage(context: Context): String =
    when (this) {
        SystemizerInstallResult.AlreadySystemized -> context.getString(R.string.systemizer_already_system)
        SystemizerInstallResult.GoogleAppMissing -> context.getString(R.string.systemizer_google_missing)
        SystemizerInstallResult.UnsupportedRootManager -> context.getString(R.string.systemizer_root_manager_missing)
        SystemizerInstallResult.KernelSuMetamoduleMissing -> context.getString(R.string.systemizer_metamodule_missing)
        is SystemizerInstallResult.RootPermissionUnavailable -> when (rootManager) {
            RootManager.KERNEL_SU -> context.getString(R.string.systemizer_grant_kernelsu)
            RootManager.MAGISK -> context.getString(R.string.systemizer_grant_magisk)
            RootManager.UNSUPPORTED -> context.getString(R.string.systemizer_root_denied)
        }
        is SystemizerInstallResult.InstalledRebootRequired -> context.getString(R.string.systemizer_installed)
        is SystemizerInstallResult.Failed -> commandOutput
            .lineSequence()
            .map { it.trim() }
            .lastOrNull { it.isNotEmpty() }
            ?.let { "$message：$it" }
            ?: message
    }
