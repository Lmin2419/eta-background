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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.imagegen.ImageGenModelCapability
import io.github.mangi.eta.data.model.GeminiEndpointMode
import io.github.mangi.eta.data.model.ImageGenAspectRatio
import io.github.mangi.eta.data.model.ImageGenBackend
import io.github.mangi.eta.data.model.ImageGenCredentials
import io.github.mangi.eta.data.model.ImageGenModelPresets
import io.github.mangi.eta.data.model.ImageGenResolution
import io.github.mangi.eta.data.model.ImageGenSettings
import io.github.mangi.eta.data.model.SpeechRegion
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
    val autoMatchLabel = stringResource(R.string.imagegen_link_auto_match)
    val standaloneLabel = stringResource(R.string.imagegen_link_standalone)
    val resources = LocalResources.current
    val linkChoiceLabels = remember(linkedOptions, backend, autoMatchLabel, standaloneLabel, resources) {
        buildList {
            if (backend != ImageGenBackend.DOUBAO) add(autoMatchLabel)
            linkedOptions.forEach { opt ->
                add(if (opt.hasApiKey) opt.name else resources.getString(R.string.imagegen_link_provider_no_key, opt.name))
            }
            add(standaloneLabel)
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
    val qualityStandardLabel = stringResource(R.string.imagegen_quality_standard_auto)
    val qualityHighLabel = stringResource(R.string.imagegen_quality_high)
    val resolutionLabels = remember(supportedResolutions, capability.resolutionIsQualityTier, qualityStandardLabel, qualityHighLabel) {
        if (capability.resolutionIsQualityTier) {
            supportedResolutions.map { if (it == ImageGenResolution.R_1K) qualityStandardLabel else qualityHighLabel }
        } else {
            supportedResolutions.map { it.displayLabel }
        }
    }
    val onModelChange: (String) -> Unit = { value ->
        val nextCap = ImageGenModelCapability.resolve(backend, value, effectiveBaseUrlForCapability)
        store.edit(settings.withModel(backend, value).copy(defaultResolution = nextCap.clampResolution(settings.defaultResolution)))
    }
    val customModelLabel = stringResource(R.string.imagegen_preset_custom_model)
    val presetLabels = remember(presets, customModelLabel) { presets.map { it.label } + customModelLabel }
    val selectedPresetIndex = presets.indexOfFirst { it.id == currentModel.trim() }.let { if (it >= 0) it else presets.size }
    val gallerySummary = if (store.galleryCount > 0) {
        pluralStringResource(
            R.plurals.imagegen_gallery_summary_count,
            store.galleryCount,
            store.galleryCount,
            formatImageStorageSize(store.galleryBytes),
        )
    } else {
        stringResource(R.string.imagegen_gallery_summary_empty)
    }

    MiuixScaffoldPage(
        title = stringResource(R.string.image_gen_settings_title),
        onBack = onBack,
        modifier = Modifier.imePadding(),
    ) {
        if (store.loading) {
            item(key = "loading") {
                Text(stringResource(R.string.imagegen_loading), style = EtaTheme.type.subhead, color = EtaTheme.colors.textSecondary,
                    modifier = Modifier.padding(24.dp))
            }
            return@MiuixScaffoldPage
        }
        item(key = "enable") {
            EtaListGroup {
                EtaSwitchPreference(
                    title = stringResource(R.string.imagegen_enable_title),
                    summary = stringResource(R.string.imagegen_enable_summary),
                    checked = settings.enabled,
                    onCheckedChange = { store.edit(settings.copy(enabled = it)) },
                    startAction = { EtaPreferenceIcon(Icons.Outlined.Image, tint = EtaPreferenceColors.Orange) },
                )
            }
        }
        item(key = "service") {
            EtaListGroup(title = stringResource(R.string.imagegen_section_service)) {
                EtaOverlayDropdownPreference(
                    title = stringResource(R.string.imagegen_provider_channel),
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
                    title = stringResource(R.string.imagegen_credential_source),
                    items = linkChoiceLabels,
                    selectedIndex = selectedLinkIndex,
                    onSelectedIndexChange = onSelectLink,
                )
                EtaPreferenceDivider(hasLeading = false)
                EtaOverlayDropdownPreference(
                    title = stringResource(R.string.imagegen_model_presets),
                    items = presetLabels,
                    selectedIndex = selectedPresetIndex,
                    onSelectedIndexChange = { idx -> presets.getOrNull(idx)?.let { onModelChange(it.id) } },
                )
                if (usingLinkedProvider) {
                    EtaPreferenceDivider(hasLeading = false)
                    EtaPreference(
                        summary = stringResource(
                            R.string.imagegen_linked_provider_footer_with_url,
                            resolvedLink!!.name,
                            resolvedLink.baseUrl.ifBlank { stringResource(R.string.imagegen_official_default) },
                        ),
                    )
                }
                capability.deprecationNotice?.let { notice ->
                    EtaPreferenceDivider(hasLeading = false)
                    EtaPreference(summary = notice, summaryColor = top.yukonga.miuix.kmp.basic.BasicComponentDefaults.summaryColor(color = StatusWarning))
                }
            }
        }
        item(key = "connection") {
            EtaListGroup(title = stringResource(R.string.imagegen_section_connection)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextField(
                        value = currentModel,
                        onValueChange = onModelChange,
                        label = stringResource(
                            if (backend == ImageGenBackend.DOUBAO) R.string.imagegen_field_model_or_endpoint_id else R.string.imagegen_field_model_id,
                        ),
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
                                    label = stringResource(R.string.imagegen_field_base_url_optional),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (vertex) {
                                    TextField(
                                        value = settings.gemini.vertexProject,
                                        onValueChange = { store.edit(settings.copy(gemini = settings.gemini.copy(vertexProject = it))) },
                                        label = stringResource(R.string.imagegen_field_vertex_project),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    TextField(
                                        value = settings.gemini.vertexLocation,
                                        onValueChange = { store.edit(settings.copy(gemini = settings.gemini.copy(vertexLocation = it))) },
                                        label = stringResource(R.string.imagegen_field_vertex_location_default),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                            ImageGenBackend.OPENAI_COMPATIBLE -> TextField(
                                value = settings.openAi.baseUrl,
                                onValueChange = { store.edit(settings.copy(openAi = settings.openAi.copy(baseUrl = it))) },
                                label = stringResource(R.string.imagegen_field_openai_base_url),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            ImageGenBackend.XAI -> TextField(
                                value = settings.xai.baseUrl,
                                onValueChange = { store.edit(settings.copy(xai = settings.xai.copy(baseUrl = it))) },
                                label = "xAI Base URL",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            ImageGenBackend.DOUBAO -> TextField(
                                value = settings.doubao.baseUrl,
                                onValueChange = { store.edit(settings.copy(doubao = settings.doubao.copy(baseUrl = it))) },
                                label = stringResource(R.string.imagegen_field_doubao_base_url),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            ImageGenBackend.QWEN -> TextField(
                                value = settings.qwen.baseUrl,
                                onValueChange = { store.edit(settings.copy(qwen = settings.qwen.copy(baseUrl = it))) },
                                label = stringResource(R.string.imagegen_field_qwen_base_url),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            ImageGenBackend.SILICONFLOW -> TextField(
                                value = settings.siliconFlow.baseUrl,
                                onValueChange = { store.edit(settings.copy(siliconFlow = settings.siliconFlow.copy(baseUrl = it))) },
                                label = stringResource(R.string.imagegen_field_siliconflow_base_url),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        TextField(
                            value = currentApiKey,
                            onValueChange = { store.editCredentials(store.credentials.withKey(backend, it)) },
                            label = stringResource(R.string.imagegen_field_api_key_local),
                            singleLine = true,
                            visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                EtaIconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                    Icon(
                                        if (apiKeyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = stringResource(R.string.imagegen_toggle_api_key_visibility),
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
                        title = stringResource(R.string.imagegen_gemini_platform),
                        items = listOf("Google AI Studio", "Google Cloud Vertex AI"),
                        selectedIndex = modes.indexOf(GeminiEndpointMode.normalize(settings.gemini.endpointMode)).coerceAtLeast(0),
                        onSelectedIndexChange = { store.edit(settings.copy(gemini = settings.gemini.copy(endpointMode = modes[it]))) },
                    )
                }
                if (!usingLinkedProvider && backend == ImageGenBackend.QWEN) {
                    val regions = listOf(SpeechRegion.BEIJING, SpeechRegion.SINGAPORE)
                    EtaPreferenceDivider(hasLeading = false)
                    EtaOverlayDropdownPreference(
                        title = stringResource(R.string.imagegen_qwen_region),
                        items = listOf(
                            stringResource(R.string.imagegen_qwen_region_beijing),
                            stringResource(R.string.imagegen_qwen_region_singapore),
                        ),
                        selectedIndex = regions.indexOf(settings.qwen.region).coerceAtLeast(0),
                        onSelectedIndexChange = { store.edit(settings.copy(qwen = settings.qwen.copy(region = regions[it]))) },
                    )
                }
            }
        }
        item(key = "defaults") {
            EtaListGroup(title = stringResource(R.string.imagegen_section_defaults)) {
                EtaOverlayDropdownPreference(
                    title = stringResource(R.string.imagegen_default_aspect_ratio),
                    items = ratioList.map { it.displayLabel },
                    selectedIndex = ratioList.indexOf(settings.defaultAspectRatio).coerceAtLeast(0),
                    onSelectedIndexChange = { store.edit(settings.copy(defaultAspectRatio = ratioList[it])) },
                )
                EtaPreferenceDivider(hasLeading = false)
                if (supportedResolutions.size > 1) {
                    EtaOverlayDropdownPreference(
                        title = stringResource(
                            if (capability.resolutionIsQualityTier) R.string.imagegen_default_quality_tier else R.string.imagegen_default_resolution,
                        ),
                        items = resolutionLabels,
                        selectedIndex = supportedResolutions.indexOf(effectiveResolution).coerceAtLeast(0),
                        onSelectedIndexChange = { store.edit(settings.copy(defaultResolution = supportedResolutions[it])) },
                    )
                } else {
                    EtaPreference(
                        title = stringResource(R.string.imagegen_default_resolution),
                        summary = stringResource(R.string.imagegen_resolution_fixed_by_model, resolutionLabels.firstOrNull().orEmpty()),
                    )
                }
                if (backend == ImageGenBackend.DOUBAO) {
                    EtaPreferenceDivider(hasLeading = false)
                    EtaSwitchPreference(
                        title = stringResource(R.string.imagegen_doubao_watermark_title),
                        summary = stringResource(R.string.imagegen_doubao_watermark_summary),
                        checked = settings.doubao.watermark,
                        onCheckedChange = { store.edit(settings.copy(doubao = settings.doubao.copy(watermark = it))) },
                    )
                }
                if (backend == ImageGenBackend.QWEN) {
                    EtaPreferenceDivider(hasLeading = false)
                    EtaSwitchPreference(
                        title = stringResource(R.string.imagegen_qwen_prompt_extend_title),
                        summary = stringResource(R.string.imagegen_qwen_prompt_extend_summary),
                        checked = settings.qwen.promptExtend,
                        onCheckedChange = { store.edit(settings.copy(qwen = settings.qwen.copy(promptExtend = it))) },
                    )
                    EtaPreferenceDivider(hasLeading = false)
                    EtaSwitchPreference(
                        title = stringResource(R.string.imagegen_qwen_watermark_title),
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
                    text = stringResource(if (store.testing) R.string.imagegen_action_testing else R.string.imagegen_action_test),
                    onClick = store::testGenerate,
                    enabled = !store.testing,
                    modifier = Modifier.weight(1f),
                )
                EtaButton(
                    text = stringResource(if (store.saving) R.string.imagegen_action_saving else R.string.imagegen_action_save_config),
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
                        Image(bitmap, contentDescription = stringResource(R.string.imagegen_test_preview_desc), contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp))
                    }
                }
                Text(stringResource(R.string.imagegen_test_preview_title), style = EtaTheme.type.subhead, color = EtaTheme.colors.textPrimary)
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
