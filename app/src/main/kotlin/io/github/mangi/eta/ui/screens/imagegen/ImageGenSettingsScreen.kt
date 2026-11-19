package io.github.mangi.eta.ui.screens.imagegen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.imagegen.ImageGenModelCapability
import io.github.mangi.eta.data.model.DoubaoImageConfig
import io.github.mangi.eta.data.model.GeminiEndpointMode
import io.github.mangi.eta.data.model.GeminiProviderSetting
import io.github.mangi.eta.data.model.ImageGenAspectRatio
import io.github.mangi.eta.data.model.ImageGenBackend
import io.github.mangi.eta.data.model.ImageGenCredentials
import io.github.mangi.eta.data.model.ImageGenModelPresets
import io.github.mangi.eta.data.model.ImageGenResolution
import io.github.mangi.eta.data.model.ImageGenSettings
import io.github.mangi.eta.data.model.OpenAiImageConfig
import io.github.mangi.eta.data.model.SiliconFlowImageConfig
import io.github.mangi.eta.data.model.SpeechRegion
import io.github.mangi.eta.data.model.XaiImageConfig
import io.github.mangi.eta.ui.components.EtaArrowPreference
import io.github.mangi.eta.ui.components.EtaButton
import io.github.mangi.eta.ui.components.EtaIconButton
import io.github.mangi.eta.ui.components.EtaListGroup
import io.github.mangi.eta.ui.components.EtaOverlayDropdownPreference
import io.github.mangi.eta.ui.components.EtaPreference
import io.github.mangi.eta.ui.components.EtaPreferenceColors
import io.github.mangi.eta.ui.components.EtaPreferenceDivider
import io.github.mangi.eta.ui.components.EtaPreferenceIcon
import io.github.mangi.eta.ui.components.EtaSwitchPreference
import io.github.mangi.eta.ui.components.LocalChatImagePreviewer
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import io.github.mangi.eta.ui.components.StatusError
import io.github.mangi.eta.ui.components.StatusSuccess
import io.github.mangi.eta.ui.components.StatusWarning
import io.github.mangi.eta.ui.components.rememberChatImageBitmap
import io.github.mangi.eta.ui.theme.EtaTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField

/**
 * AI 生图设置（胖小胖样式）。逻辑全在上游 fe3b0a25 的 [ImageGenSettingsStore]，这里只换界面：
 * 开关、渠道 / 凭据来源 / 模型预设、连接参数（各渠道专属字段）、默认比例与清晰度、试画、画廊。
 */
@Composable
internal fun ImageGenSettingsScreen(
    onBack: () -> Unit,
    onOpenGallery: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ImageGenSettingsStore(context, scope) }
    val previewer = LocalChatImagePreviewer.current
    var apiKeyVisible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { store.refreshGalleryStats() }
    DisposableEffect(store) { onDispose { store.cancelActiveTest() } }

    val settings = store.settings
    val backend = settings.backend
    val backendList = ImageGenBackend.entries
    val ratioList = ImageGenAspectRatio.entries
    val presets = ImageGenModelPresets.forBackend(backend)
    val linkedOptions = store.linkedProviders[backend].orEmpty()
    val resolvedLink = store.resolvedLinkedProvider(backend)
    val currentApiKey = store.currentApiKey(backend)
    val usingLinkedProvider = resolvedLink != null && resolvedLink.hasApiKey && currentApiKey.isBlank()
    val linkChoiceIds = remember(linkedOptions, backend) {
        buildList {
            if (backend != ImageGenBackend.DOUBAO) add("")
            linkedOptions.forEach { add(it.id) }
            add(ImageGenSettings.STANDALONE_LINK_ID)
        }
    }
    val linkChoiceLabels = remember(linkedOptions, backend) {
        buildList {
            if (backend != ImageGenBackend.DOUBAO) add("自动匹配已有服务商")
            linkedOptions.forEach { opt -> add(opt.name + if (opt.hasApiKey) "" else "（未填 Key）") }
            add("独立填写 API Key")
        }
    }
    val selectedLinkIndex = linkChoiceIds.indexOf(store.currentLinkId(backend)).let { if (it >= 0) it else 0 }
    val onSelectLink: (Int) -> Unit = { idx ->
        val id = linkChoiceIds.getOrElse(idx) { ImageGenSettings.STANDALONE_LINK_ID }
        // 切回关联服务商时清掉独立 Key，避免覆盖关联状态；切到独立模式保留已填 Key。
        if (id != ImageGenSettings.STANDALONE_LINK_ID) store.editCredentials(store.credentials.withKey(backend, ""))
        store.edit(settings.withLink(backend, id))
    }
    val currentModel = settings.modelFor(backend)
    val effectiveBaseUrlForCapability = when {
        usingLinkedProvider -> resolvedLink!!.baseUrl
        backend == ImageGenBackend.OPENAI_COMPATIBLE -> settings.openAi.baseUrl
        else -> ""
    }
    val capability = remember(backend, currentModel, effectiveBaseUrlForCapability) {
        ImageGenModelCapability.resolve(backend, currentModel, effectiveBaseUrlForCapability)
    }
    val supportedResolutions = capability.supportedResolutions
    val effectiveResolution = capability.clampResolution(settings.defaultResolution)
    val resolutionLabels = remember(supportedResolutions, capability.resolutionIsQualityTier) {
        if (capability.resolutionIsQualityTier) {
            supportedResolutions.map { if (it == ImageGenResolution.R_1K) "标准画质（自动）" else "高清画质（High）" }
        } else {
            supportedResolutions.map { it.displayLabel }
        }
    }
    val onModelChange: (String) -> Unit = { value ->
        val nextCap = ImageGenModelCapability.resolve(backend, value, effectiveBaseUrlForCapability)
        store.edit(settings.withModel(backend, value).copy(defaultResolution = nextCap.clampResolution(settings.defaultResolution)))
    }
    val presetLabels = remember(presets) { presets.map { "${it.label} · ${it.note}" } + "自定义模型 ID" }
    val selectedPresetIndex = presets.indexOfFirst { it.id == currentModel.trim() }.let { if (it >= 0) it else presets.size }
    val gallerySummary = if (store.galleryCount > 0) {
        "${store.galleryCount} 张 · ${formatImageStorageSize(store.galleryBytes)}"
    } else {
        "暂无本地图片"
    }

    MiuixScaffoldPage(
        title = stringResource(R.string.image_gen_settings_title),
        onBack = onBack,
        modifier = Modifier.imePadding(),
    ) {
        if (store.loading) {
            item(key = "loading") {
                Text("加载中…", style = EtaTheme.type.subhead, color = EtaTheme.colors.textSecondary,
                    modifier = Modifier.padding(24.dp))
            }
            return@MiuixScaffoldPage
        }
        item(key = "enable") {
            EtaListGroup {
                EtaSwitchPreference(
                    title = "启用 AI 生图",
                    summary = "开启后任何对话模型都能调用生图和改图工具，成图直接显示在对话里",
                    checked = settings.enabled,
                    onCheckedChange = { store.edit(settings.copy(enabled = it)) },
                    startAction = { EtaPreferenceIcon(Icons.Outlined.Image, tint = EtaPreferenceColors.Orange) },
                )
            }
        }
        item(key = "service") {
            EtaListGroup(title = "生图服务") {
                EtaOverlayDropdownPreference(
                    title = "服务商渠道",
                    items = backendList.map { it.displayName() },
                    selectedIndex = backendList.indexOf(backend).coerceAtLeast(0),
                    onSelectedIndexChange = { idx ->
                        val nextBackend = backendList[idx]
                        val nextCap = ImageGenModelCapability.resolve(nextBackend, settings.modelFor(nextBackend))
                        store.edit(settings.copy(backend = nextBackend, defaultResolution = nextCap.clampResolution(settings.defaultResolution)))
                    },
                )
                EtaPreferenceDivider(hasLeading = false)
                EtaOverlayDropdownPreference(
                    title = "凭据来源",
                    items = linkChoiceLabels,
                    selectedIndex = selectedLinkIndex,
                    onSelectedIndexChange = onSelectLink,
                )
                EtaPreferenceDivider(hasLeading = false)
                EtaOverlayDropdownPreference(
                    title = "常用模型预设",
                    items = presetLabels,
                    selectedIndex = selectedPresetIndex,
                    onSelectedIndexChange = { idx -> presets.getOrNull(idx)?.let { onModelChange(it.id) } },
                )
                if (usingLinkedProvider) {
                    EtaPreferenceDivider(hasLeading = false)
                    EtaPreference(
                        summary = "当前直接用「${resolvedLink!!.name}」的 API Key 和连接地址（${resolvedLink.baseUrl.ifBlank { "官方默认" }}）。" +
                            "要换地址或地域，把「凭据来源」切到「独立填写 API Key」。",
                    )
                }
                capability.deprecationNotice?.let { notice ->
                    EtaPreferenceDivider(hasLeading = false)
                    EtaPreference(summary = notice, summaryColor = top.yukonga.miuix.kmp.basic.BasicComponentDefaults.summaryColor(color = StatusWarning))
                }
            }
        }
        item(key = "connection") {
            EtaListGroup(title = "连接与模型参数") {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextField(
                        value = currentModel,
                        onValueChange = onModelChange,
                        label = if (backend == ImageGenBackend.DOUBAO) "模型 ID 或 Endpoint ID" else "模型 ID",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // 只在独立配置时显示地址与平台参数，避免把关联服务商的 Key 发到别的主机。
                    if (!usingLinkedProvider) {
                        when (backend) {
                            ImageGenBackend.GEMINI -> {
                                val vertex = GeminiEndpointMode.normalize(settings.gemini.endpointMode) == GeminiEndpointMode.VERTEX_AI
                                TextField(
                                    value = settings.gemini.baseUrl,
                                    onValueChange = { store.edit(settings.copy(gemini = settings.gemini.copy(baseUrl = it))) },
                                    label = "接口地址（留空用官方默认 ${if (vertex) GeminiProviderSetting.DEFAULT_VERTEX_BASE_URL else GeminiProviderSetting.DEFAULT_BASE_URL}）",
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (vertex) {
                                    TextField(
                                        value = settings.gemini.vertexProject,
                                        onValueChange = { store.edit(settings.copy(gemini = settings.gemini.copy(vertexProject = it))) },
                                        label = "Vertex Project（留空走 global Express 端点）",
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    TextField(
                                        value = settings.gemini.vertexLocation,
                                        onValueChange = { store.edit(settings.copy(gemini = settings.gemini.copy(vertexLocation = it))) },
                                        label = "Vertex Location（默认 global）",
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                            ImageGenBackend.OPENAI_COMPATIBLE -> TextField(
                                value = settings.openAi.baseUrl,
                                onValueChange = { store.edit(settings.copy(openAi = settings.openAi.copy(baseUrl = it))) },
                                label = "Base URL（OpenAI 或兼容中转，默认 ${OpenAiImageConfig.DEFAULT_BASE_URL}）",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            ImageGenBackend.XAI -> TextField(
                                value = settings.xai.baseUrl,
                                onValueChange = { store.edit(settings.copy(xai = settings.xai.copy(baseUrl = it))) },
                                label = "xAI Base URL（默认 ${XaiImageConfig.DEFAULT_BASE_URL}）",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            ImageGenBackend.DOUBAO -> TextField(
                                value = settings.doubao.baseUrl,
                                onValueChange = { store.edit(settings.copy(doubao = settings.doubao.copy(baseUrl = it))) },
                                label = "火山方舟 Base URL（默认 ${DoubaoImageConfig.DEFAULT_BASE_URL}）",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            ImageGenBackend.QWEN -> TextField(
                                value = settings.qwen.baseUrl,
                                onValueChange = { store.edit(settings.copy(qwen = settings.qwen.copy(baseUrl = it))) },
                                label = "专属业务空间域名（可选，留空用 ${settings.qwen.defaultEndpoint()}）",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            ImageGenBackend.SILICONFLOW -> TextField(
                                value = settings.siliconFlow.baseUrl,
                                onValueChange = { store.edit(settings.copy(siliconFlow = settings.siliconFlow.copy(baseUrl = it))) },
                                label = "硅基流动 Base URL（默认 ${SiliconFlowImageConfig.DEFAULT_BASE_URL}）",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        TextField(
                            value = currentApiKey,
                            onValueChange = { store.editCredentials(store.credentials.withKey(backend, it)) },
                            label = "API Key（只存在本机，加密保存）",
                            singleLine = true,
                            visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                EtaIconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                    Icon(
                                        if (apiKeyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = "切换 API Key 可见性",
                                        tint = EtaTheme.colors.textSecondary,
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (!usingLinkedProvider && backend == ImageGenBackend.GEMINI) {
                    val modes = listOf(GeminiEndpointMode.AI_STUDIO, GeminiEndpointMode.VERTEX_AI)
                    EtaPreferenceDivider(hasLeading = false)
                    EtaOverlayDropdownPreference(
                        title = "接入平台",
                        items = listOf("Google AI Studio", "Google Cloud Vertex AI"),
                        selectedIndex = modes.indexOf(GeminiEndpointMode.normalize(settings.gemini.endpointMode)).coerceAtLeast(0),
                        onSelectedIndexChange = { store.edit(settings.copy(gemini = settings.gemini.copy(endpointMode = modes[it]))) },
                    )
                }
                if (!usingLinkedProvider && backend == ImageGenBackend.QWEN) {
                    val regions = listOf(SpeechRegion.BEIJING, SpeechRegion.SINGAPORE)
                    EtaPreferenceDivider(hasLeading = false)
                    EtaOverlayDropdownPreference(
                        title = "百炼地域",
                        items = listOf("华北2（北京）", "新加坡（国际站）"),
                        selectedIndex = regions.indexOf(settings.qwen.region).coerceAtLeast(0),
                        onSelectedIndexChange = { store.edit(settings.copy(qwen = settings.qwen.copy(region = regions[it]))) },
                    )
                }
            }
        }
        item(key = "defaults") {
            EtaListGroup(title = "生成默认偏好") {
                EtaOverlayDropdownPreference(
                    title = "默认画面比例",
                    items = ratioList.map { it.displayLabel },
                    selectedIndex = ratioList.indexOf(settings.defaultAspectRatio).coerceAtLeast(0),
                    onSelectedIndexChange = { store.edit(settings.copy(defaultAspectRatio = ratioList[it])) },
                )
                EtaPreferenceDivider(hasLeading = false)
                if (supportedResolutions.size > 1) {
                    EtaOverlayDropdownPreference(
                        title = if (capability.resolutionIsQualityTier) "默认画质档位" else "默认清晰度",
                        items = resolutionLabels,
                        selectedIndex = supportedResolutions.indexOf(effectiveResolution).coerceAtLeast(0),
                        onSelectedIndexChange = { store.edit(settings.copy(defaultResolution = supportedResolutions[it])) },
                    )
                } else {
                    EtaPreference(title = "默认清晰度", summary = "${resolutionLabels.firstOrNull().orEmpty()}（当前模型固定）")
                }
                if (backend == ImageGenBackend.DOUBAO) {
                    EtaPreferenceDivider(hasLeading = false)
                    EtaSwitchPreference(
                        title = "添加 AI 生成水印",
                        summary = "默认关闭，开启后图片右下角显示火山方舟水印",
                        checked = settings.doubao.watermark,
                        onCheckedChange = { store.edit(settings.copy(doubao = settings.doubao.copy(watermark = it))) },
                    )
                }
                if (backend == ImageGenBackend.QWEN) {
                    EtaPreferenceDivider(hasLeading = false)
                    EtaSwitchPreference(
                        title = "提示词智能润色",
                        summary = "由千问自动补充画面细节（推荐开启）",
                        checked = settings.qwen.promptExtend,
                        onCheckedChange = { store.edit(settings.copy(qwen = settings.qwen.copy(promptExtend = it))) },
                    )
                    EtaPreferenceDivider(hasLeading = false)
                    EtaSwitchPreference(
                        title = "添加 AI 生成水印",
                        checked = settings.qwen.watermark,
                        onCheckedChange = { store.edit(settings.copy(qwen = settings.qwen.copy(watermark = it))) },
                    )
                }
            }
        }
        item(key = "actions") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EtaButton(
                    text = if (store.testing) "试画中…" else "试画测试",
                    onClick = store::testGenerate,
                    enabled = !store.testing,
                    modifier = Modifier.weight(1f),
                )
                EtaButton(
                    text = if (store.saving) "保存中…" else "保存配置",
                    onClick = store::save,
                    enabled = !store.saving,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item(key = "feedback") {
            ImageGenFeedback(store) { path -> previewer?.open(listOf(path)) }
        }
        item(key = "gallery") {
            EtaListGroup {
                EtaArrowPreference(
                    title = stringResource(R.string.image_gen_gallery_title),
                    summary = gallerySummary,
                    startAction = { EtaPreferenceIcon(Icons.Outlined.Collections, tint = EtaPreferenceColors.Teal) },
                    onClick = onOpenGallery,
                )
            }
        }
    }
}

@Composable
private fun ImageGenFeedback(store: ImageGenSettingsStore, onPreview: (String) -> Unit) {
    val error = store.errorMessage
    val status = store.statusMessage
    val record = store.testPreviewRecord
    if (error == null && status == null && record == null) return
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!error.isNullOrBlank()) Notice(error, StatusError)
        if (!status.isNullOrBlank()) Notice(status, StatusSuccess)
        if (record != null) {
            val bitmap = rememberChatImageBitmap(record.filePath, maxDimensionPx = 800)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(EtaTheme.colors.surface)
                    .clickable { onPreview(record.filePath) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(EtaTheme.colors.surfaceSunken)) {
                    if (bitmap != null) {
                        Image(bitmap, contentDescription = "试画预览", contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp))
                    }
                }
                Text("试画成功，点开看大图", style = EtaTheme.type.subhead, color = EtaTheme.colors.textPrimary)
            }
        }
    }
}

@Composable
private fun Notice(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text, style = EtaTheme.type.subhead, color = color)
    }
}

private fun ImageGenSettings.modelFor(backend: ImageGenBackend): String = when (backend) {
    ImageGenBackend.GEMINI -> gemini.model
    ImageGenBackend.OPENAI_COMPATIBLE -> openAi.model
    ImageGenBackend.XAI -> xai.model
    ImageGenBackend.DOUBAO -> doubao.model
    ImageGenBackend.QWEN -> qwen.model
    ImageGenBackend.SILICONFLOW -> siliconFlow.model
}

private fun ImageGenSettings.withModel(backend: ImageGenBackend, value: String): ImageGenSettings = when (backend) {
    ImageGenBackend.GEMINI -> copy(gemini = gemini.copy(model = value))
    ImageGenBackend.OPENAI_COMPATIBLE -> copy(openAi = openAi.copy(model = value))
    ImageGenBackend.XAI -> copy(xai = xai.copy(model = value))
    ImageGenBackend.DOUBAO -> copy(doubao = doubao.copy(model = value))
    ImageGenBackend.QWEN -> copy(qwen = qwen.copy(model = value))
    ImageGenBackend.SILICONFLOW -> copy(siliconFlow = siliconFlow.copy(model = value))
}

private fun ImageGenSettings.withLink(backend: ImageGenBackend, id: String): ImageGenSettings = when (backend) {
    ImageGenBackend.GEMINI -> copy(gemini = gemini.copy(linkedProviderId = id))
    ImageGenBackend.OPENAI_COMPATIBLE -> copy(openAi = openAi.copy(linkedProviderId = id))
    ImageGenBackend.XAI -> copy(xai = xai.copy(linkedProviderId = id))
    ImageGenBackend.DOUBAO -> copy(doubao = doubao.copy(linkedProviderId = id))
    ImageGenBackend.QWEN -> copy(qwen = qwen.copy(linkedProviderId = id))
    ImageGenBackend.SILICONFLOW -> copy(siliconFlow = siliconFlow.copy(linkedProviderId = id))
}

private fun ImageGenCredentials.withKey(backend: ImageGenBackend, value: String): ImageGenCredentials = when (backend) {
    ImageGenBackend.GEMINI -> copy(geminiApiKey = value)
    ImageGenBackend.OPENAI_COMPATIBLE -> copy(openAiApiKey = value)
    ImageGenBackend.XAI -> copy(xaiApiKey = value)
    ImageGenBackend.DOUBAO -> copy(doubaoApiKey = value)
    ImageGenBackend.QWEN -> copy(qwenApiKey = value)
    ImageGenBackend.SILICONFLOW -> copy(siliconFlowApiKey = value)
}
