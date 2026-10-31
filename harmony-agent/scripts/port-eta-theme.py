#!/usr/bin/env python3
# Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/
# EtaPreferenceStyle.kt, EtaCard.kt, EtaControls.kt, StatusColors.kt @ 3b1794d.
# Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
"""Extract Eta defaults and Miuix 0.9.4 light/dark values from pinned Kotlin inputs."""
import ast
import json
import math
import re
from eta_port_common import PROJECT, ETA_COMMIT, MIUIX_COMMIT, arguments, emit, emit_json, origin


def snake(value):
    return re.sub(r'([a-z0-9])([A-Z])', r'\1_\2', value).lower()


def block(source, start):
    pos = source.index('{', source.index(start)); depth = 1; end = pos + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}'); end += 1
    return source[pos+1:end-1]


def numeric(expression, known):
    expression = re.sub(r'(\d+(?:\.\d+)?)\.(?:dp|sp)', r'\1', expression).strip()
    def evaluate(node):
        if isinstance(node, ast.Constant) and type(node.value) in (float, int): return node.value
        if isinstance(node, ast.Name) and node.id in known: return known[node.id]
        if isinstance(node, ast.BinOp) and isinstance(node.op, ast.Add): return evaluate(node.left) + evaluate(node.right)
        raise ValueError('Unsupported Kotlin numeric expression: ' + expression)
    return evaluate(ast.parse(expression, mode='eval').body)


def defaults(source, name):
    values = {}
    for key, expr in re.findall(r'^\s*val (\w+) = ([^\n]+)', block(source, 'object ' + name), re.M):
        if expr.startswith('PaddingValues('):
            match = re.fullmatch(r'PaddingValues\(horizontal = (.+), vertical = (.+)\)', expr)
            if not match: raise ValueError('Unsupported padding expression: ' + expr)
            values[key] = {'horizontal': numeric(match[1], values), 'vertical': numeric(match[2], values)}
        elif expr.startswith('DpSize('):
            match = re.fullmatch(r'DpSize\((.+), (.+)\)', expr)
            values[key] = {'width': numeric(match[1], values), 'height': numeric(match[2], values)}
        else: values[key] = numeric(expr, values)
    return values


def parse_color(expr):
    alpha = None
    if '.copy(' in expr:
        base, copy = expr.split('.copy(')
        match = re.fullmatch(r'alpha\s*=\s*([\d.]+)[Ff]\)', copy)
        if not match: raise ValueError('Unsupported color copy: ' + expr)
        alpha = math.floor(float(match[1]) * 255 + 0.5); expr = base
    if expr in ('Color.Black', 'Color.White', 'Color.Transparent'):
        value = {'Color.Black': 'FF000000', 'Color.White': 'FFFFFFFF', 'Color.Transparent': '00000000'}[expr]
    else:
        match = re.fullmatch(r'Color\(0x([0-9a-fA-F]{8})\)', expr)
        if not match: raise ValueError('Unsupported Kotlin color: ' + expr)
        value = match[1].upper()
    return '#' + (f'{alpha:02X}' + value[2:] if alpha is not None else value)


def miuix_colors(source, mode):
    body = source.split('fun ' + mode + 'ColorScheme(', 1)[1].split('): Colors', 1)[0]
    values = {name: parse_color(expr) for name, expr in re.findall(r'^\s*(\w+): Color = (.+),$', body, re.M)}
    if len(values) != 53: raise ValueError('Miuix color role count changed: ' + str(len(values)))
    return values


def generated_class(name, values, source):
    text = '// Ported from ' + source + '\nexport class ' + name + ' {\n'
    for key, value in values.items():
        kind = 'EtaAxisInsets' if isinstance(value, dict) and 'horizontal' in value else 'EtaSize' if isinstance(value, dict) else 'number'
        text += f'  static readonly {key}: {kind} = ' + json.dumps(value) + ';\n'
    if name == 'EtaPreferenceDefaults':
        text += '  static contentStart(hasLeading: boolean): number { return hasLeading ? EtaPreferenceDefaults.ContentStart : EtaPreferenceDefaults.SidePadding; }\n'
    return text + '}\n'


def generate(eta_root, output, check=False):
    components = eta_root / 'app/src/main/kotlin/io/github/mangi/eta/ui/components'
    source_names = ['EtaPreferenceStyle.kt', 'EtaCard.kt', 'EtaControls.kt', 'StatusColors.kt', 'AgentChatFileAttachments.kt', 'AgentChatInputBar.kt', 'ConversationSidePaneScaffold.kt',
                    'EtaPreferenceRow.kt', 'EtaPreferenceGroupItem.kt', 'EtaSelectionPreferences.kt', 'EtaDropdownPreference.kt',
                    'EtaFeatureCard.kt', 'EtaChoiceChip.kt', 'ToolChip.kt', 'SectionHeader.kt', 'ListEmptyState.kt',
                    'MiuixDialogActions.kt', 'ItemDescriptionDialog.kt', 'MiuixScaffoldPage.kt', 'AdaptiveTopAppBar.kt', 'MiuixBackButton.kt', 'TopBarBackdrop.kt', 'AgentChatBody.kt', 'AgentModelPicker.kt', 'AgentChatModelControls.kt', 'ChatMessageItem.kt', 'AgentWorkProcess.kt', 'AgentToolActivity.kt', 'AgentStatusCard.kt', 'AgentConversationLoading.kt', 'AgentChatQueueStrip.kt', 'ConversationPanePanel.kt']
    texts = {name: (components / name).read_text() for name in source_names}
    texts['AppearanceSettingsScreen.kt']=(components.parent/'AppearanceSettingsScreen.kt').read_text()
    for page in (components.parent/'pages/providers').glob('*.kt'): texts[page.name]=page.read_text()
    appearance_model=(components.parent.parent/'data/model/AppearanceSettings.kt').read_text()
    markdown_dir=components.parent / 'markdown'
    markdown_style=(markdown_dir/'MarkdownStyle.kt').read_text()
    markdown_content=(markdown_dir/'MarkdownContent.kt').read_text()
    upstream = PROJECT / 'scripts/eta-upstream/miuix-0.9.4'
    miuix = (upstream / 'Colors.kt').read_text(); style = texts['EtaPreferenceStyle.kt']; status = texts['StatusColors.kt']
    legacy = json.loads((PROJECT / 'scripts/eta-port/legacy-colors.json').read_text())['color']
    blue = parse_color(re.search(r'val Blue = (Color\(0x[0-9A-F]+\))', style)[1])
    orange = parse_color(re.search(r'val Orange = (Color\(0x[0-9A-F]+\))', style)[1])
    success = parse_color(re.search(r'val StatusSuccess = (Color\(0x[0-9A-F]+\))', status)[1])
    warning = parse_color(re.search(r'val StatusWarning = (Color\(0x[0-9A-F]+\))', status)[1])
    page = parse_color(re.search(r'colors.copy\(background = (Color\(0x[0-9A-F]+\))', style)[1])
    palettes = {}
    for mode, folder in [('light', 'base'), ('dark', 'dark')]:
        colors = miuix_colors(miuix, mode)
        result = {row['name']: row['value'] for row in legacy}
        result.update({'miuix_' + snake(k): v for k, v in colors.items()})
        result.update({'eta_preference_blue': blue, 'eta_preference_green': success, 'eta_preference_orange': orange,
            'eta_preference_yellow': warning, 'eta_status_success': success, 'eta_status_warning': warning,
            'eta_status_error': colors['error'], 'eta_status_running': colors['primary'], 'eta_status_idle': colors['onSurfaceVariantSummary'],
            'eta_settings_background': page if mode == 'light' else colors['background'],
            'eta_settings_primary': blue if mode == 'light' else colors['primary']})
        alpha = float(re.search(r'onBackground.copy\(alpha = ([\d.]+)f\)', style)[1])
        result['eta_preference_divider'] = '#' + f'{math.floor(alpha*255+0.5):02X}' + colors['onBackground'][3:]
        def blend(role, alpha):
            return '#' + f'{math.floor(alpha * 255 + 0.5):02X}' + colors[role][3:]
        row = texts['EtaPreferenceRow.kt']; chip = texts['EtaChoiceChip.kt']
        input_source=texts['AgentChatInputBar.kt']; body_source=texts['AgentChatBody.kt']
        result['eta_conversation_pane']=colors['surface'] if mode=='light' else colors['surfaceContainer']
        result['eta_queue_outline']=blend('outline',float(re.search(r'outline.copy\(alpha = ([\d.]+)f',texts['AgentChatQueueStrip.kt'])[1]))
        result['eta_queue_divider']=blend('outline',float(re.findall(r'outline.copy\(alpha = ([\d.]+)f',texts['AgentChatQueueStrip.kt'])[-1]))
        result['eta_message_action']=blend('onSurfaceVariantSummary',float(re.search(r'onSurfaceVariantSummary.copy\(alpha = ([\d.]+)f',texts['ChatMessageItem.kt'].split('internal fun AnswerActionButton',1)[1])[1]))
        result['eta_message_chevron']=blend('onSurfaceVariantSummary',float(re.search(r'onSurfaceVariantSummary.copy\(alpha = ([\d.]+)f',texts['AgentWorkProcess.kt'].split('internal fun ExpandChevron',1)[1])[1]))
        result['eta_markdown_divider']=blend('outline',float(re.search(r'dividerColor = colors.outline.copy\(alpha = ([\d.]+)f',markdown_style)[1]))
        result['eta_markdown_inline_code']=blend('onSurface',float(re.search(r'codeBackground = colors.onSurface.copy\(alpha = ([\d.]+)f',markdown_style)[1]))
        result['eta_markdown_marker']=blend('onSurface',float(re.search(r'markerColor = if \(answer\) colors.onSurface.copy\(alpha = ([\d.]+)f',markdown_style)[1]))
        result['eta_markdown_important']=parse_color(re.search(r'AlertImportantColor = (Color\(0x[0-9A-F]+\))',markdown_content)[1])
        result['eta_input_outline']=blend('outline',float(re.search(r'outline.copy\(alpha = ([\d.]+)f',input_source)[1]))
        result['eta_suggestion_outline']=blend('outline',float(re.search(r'outline.copy\(alpha = ([\d.]+)f',body_source.split('private fun SuggestionCard',1)[1])[1]))
        result['eta_input_shadow']=parse_color('Color.Black.copy(alpha = '+re.search(r'alpha = ([\d.]+)f',input_source)[1]+'f)')
        result['eta_pane_scrim'] = parse_color('Color.Black.copy(alpha = ' + re.search(r'ChatScrimAlpha = ([\d.]+)f', texts['ConversationSidePaneScaffold.kt'])[1] + 'f)')
        result['eta_transparent'] = parse_color('Color.Transparent')
        result['eta_preference_arrow'] = blend('onBackground', float(re.search(r'onBackground.copy\(alpha = ([\d.]+)f\)', row)[1]))
        # The first occurrence is the arrow (0.3); the row's held overlay is 0.06.
        result['eta_preference_held'] = blend('onBackground', float(re.findall(r'onBackground.copy\(alpha = ([\d.]+)f\)', row)[-1]))
        chip_alpha = float(re.search(r'primary.copy\(alpha = ([\d.]+)f\)', chip)[1])
        result['eta_chip_selected'] = blend('primary', chip_alpha)
        result['eta_top_bar_surface'] = blend('surface', float(re.search(r'TopBarSurfaceAlpha = ([\d.]+)f', texts['TopBarBackdrop.kt'])[1]))
        result['eta_top_bar_progressive_surface'] = blend('surface', float(re.search(r'ProgressiveTopBarSurfaceAlpha = ([\d.]+)f', texts['TopBarBackdrop.kt'])[1]))
        result['eta_settings_chip_selected'] = '#' + f'{math.floor(chip_alpha * 255 + 0.5):02X}' + result['eta_settings_primary'][3:]
        emit_json(output / f'entry/src/main/resources/{folder}/element/color.json', {'color': [{'name': k, 'value': v} for k, v in sorted(result.items())]}, check)
        palettes[mode] = result
    objects = {}
    source_for = {}
    for name, file in [('EtaPreferenceDefaults', 'EtaPreferenceStyle.kt'), ('EtaCardDefaults', 'EtaCard.kt'), ('EtaControlDefaults', 'EtaControls.kt')]:
        objects[name] = defaults(texts[file], name); source_for[name] = f'app/src/main/kotlin/io/github/mangi/eta/ui/components/{file} @ 3b1794d'
    objects['EtaPreferenceDefaults']['DividerThickness'] = float(re.search(r'thickness = ([\d.]+)\.dp', style)[1])
    objects['EtaPreferenceDefaults']['OpticalIconSize'] = float(re.search(r'MenuBook -> ([\d.]+)\.dp', style)[1])
    objects['EtaControlDefaults']['SwitchWidth'] = float(re.search(r'val width = ([\d.]+)\.dp', texts['EtaControls.kt'])[1])
    objects['EtaControlDefaults']['SwitchHeight'] = float(re.search(r'val height = ([\d.]+)\.dp', texts['EtaControls.kt'])[1])
    objects['EtaChatDefaults'] = {'ActionIconSize': float(re.search(r'ChatInputActionIconSize = ([\d.]+)\.dp', texts['AgentChatFileAttachments.kt'])[1])}
    objects['EtaChatDefaults'].update({k: float(v) for k, v in re.findall(r'private val (\w+) = ([\d.]+)\.dp', texts['AgentChatInputBar.kt'])})
    objects['EtaChatDefaults']['InputCornerRadius'] = float(re.search(r'InputContainerShape = RoundedCornerShape\(([\d.]+)\.dp\)', texts['AgentChatInputBar.kt'])[1])
    source_for['EtaChatDefaults'] = 'app/src/main/kotlin/io/github/mangi/eta/ui/components/AgentChatInputBar.kt, AgentChatFileAttachments.kt @ 3b1794d'
    objects['EtaChatDefaults']['InputLineHeight'] = float(re.search(r'lineHeight = ([\d.]+)\.sp', texts['AgentChatInputBar.kt'])[1])
    objects['EtaChatDefaults']['InputMaxLines'] = int(re.search(r'maxHeightInLines = (\d+)', texts['AgentChatInputBar.kt'])[1])
    objects['EtaChatDefaults']['ActionSize'] = float(re.search(r'ChatInputActionSize = ([\d.]+)\.dp', texts['AgentChatFileAttachments.kt'])[1])
    picker = texts['AgentModelPicker.kt']
    objects['EtaModelPickerDefaults'] = {
        'ModelPopupWidth': float(re.search(r'ModelPopupWidth = ([\d.]+)\.dp', picker)[1]),
        'ModelPopupLimit': int(re.search(r'ModelPopupLimit = (\d+)', picker)[1]),
        'SelectionConfirmMs': int(re.search(r'SelectionConfirmMs = (\d+)L', picker)[1]),
        'SheetHeightFraction': float(re.search(r'containerSize.height.toDp\(\) } \* ([\d.]+)f', picker)[1]),
        'FallbackIconFraction': float(re.search(r'size \* ([\d.]+)f',picker)[1]),
    }
    context=texts['AgentChatModelControls.kt']
    thresholds=re.findall(r'progress >= ([\d.]+)f',context)
    objects['EtaContextDefaults']={'ErrorThreshold':float(thresholds[0]),'WarningThreshold':float(thresholds[1])}
    source_for['EtaContextDefaults']='app/src/main/kotlin/io/github/mangi/eta/ui/components/AgentChatModelControls.kt @ 3b1794d'
    source_for['EtaModelPickerDefaults'] = 'app/src/main/kotlin/io/github/mangi/eta/ui/components/AgentModelPicker.kt @ 3b1794d'
    queue=texts['AgentChatQueueStrip.kt']
    objects['EtaProviderDefaults']={'SheetHeightFraction':float(re.search(r'\* ([\d.]+)f',texts['RemoteModelImportSheet.kt'])[1]),'DisabledAlpha':float(re.search(r'else ([\d.]+)f',texts['ModelProviderListScreen.kt'])[1]),'ModelDialogHeight':float(re.search(r'modelDialogBodyMaxHeight = ([\d.]+)\.dp',texts['ProviderModelsTab.kt'])[1]),'ModelDialogChrome':float(re.search(r'modelDialogChromeHeight = ([\d.]+)\.dp',texts['ProviderModelsTab.kt'])[1])}
    objects['EtaProviderDefaults']['HeadersExpandedRotation']=float(re.search(r'if \(expanded\) ([\d.]+)f',texts['ProviderHeadersEditor.kt'])[1])
    source_for['EtaProviderDefaults']='app/src/main/kotlin/io/github/mangi/eta/ui/pages/providers/RemoteModelImportSheet.kt; ModelProviderListScreen.kt; ProviderModelsTab.kt @ 3b1794d'
    objects['EtaAppearanceDefaults']={k:float(v) for k,v in re.findall(r'const val (\w+) = ([\d.]+)f',appearance_model)}
    objects['EtaAppearanceDefaults']['Percent']=100
    source_for['EtaAppearanceDefaults']='app/src/main/kotlin/io/github/mangi/eta/data/model/AppearanceSettings.kt; ui/AppearanceSettingsScreen.kt @ 3b1794d'
    objects['EtaFontWeights']={'SemiBold':600}
    source_for['EtaFontWeights']='Compose FontWeight.SemiBold used by ConversationPanePanel.kt @ 3b1794d (CSS/ArkUI weight 600)'
    objects['ConversationPanelMetrics']=defaults(texts['ConversationPanePanel.kt'],'ConversationPanelMetrics')
    source_for['ConversationPanelMetrics']='app/src/main/kotlin/io/github/mangi/eta/ui/components/ConversationPanePanel.kt @ 3b1794d'
    objects['EtaQueueDefaults']={k:float(v) for k,v in re.findall(r'private val (\w+) = ([\d.]+)\.dp',queue)}
    objects['EtaQueueDefaults']['ChevronExpandedDegrees']=float(re.search(r'targetValue = if \(expanded\) ([\d.]+)f',queue)[1])
    objects['EtaQueueDefaults']['ExpandDurationMs']=int(re.search(r'durationMillis = (\d+)',queue)[1])
    objects['EtaQueueDefaults']['StreamUiUpdateMs']=int(re.search(r'STREAM_UI_UPDATE_INTERVAL_MS = (\d+)L',(components.parent/'app/AgentRunStore.kt').read_text())[1])
    source_for['EtaQueueDefaults']='app/src/main/kotlin/io/github/mangi/eta/ui/components/AgentChatQueueStrip.kt; ui/app/AgentRunStore.kt @ 3b1794d'
    objects['EtaPaneDefaults'] = {'PaneMaxWidth': float(re.search(r'val PaneMaxWidth = ([\d.]+)\.dp', texts['ConversationSidePaneScaffold.kt'])[1])}
    objects['EtaPaneDefaults'].update({k: float(v) for k, v in re.findall(r'(?:const )?val (\w+) = ([\d.]+)f', texts['ConversationSidePaneScaffold.kt'])})
    source_for['EtaPaneDefaults'] = 'app/src/main/kotlin/io/github/mangi/eta/ui/components/ConversationSidePaneScaffold.kt @ 3b1794d'
    for name, file, fields in [('MiuixButtonDefaults', 'Button.kt', ['MinWidth', 'MinHeight', 'CornerRadius']),
                              ('MiuixDialogDefaults', 'DialogContentLayout.kt', ['MaxWidth', 'CornerRadius'])]:
        text = (upstream / file).read_text(); objects[name] = {k: float(re.search(r'val ' + k + r' = ([\d.]+)\.dp', text)[1]) for k in fields}
        source_for[name] = f'compose-miuix-ui/miuix {file} @ 39c40f9 (0.9.4), Apache-2.0'
    font_styles = (upstream / 'TextStyles.kt').read_text()
    objects['MiuixFontSizes'] = {k: float(v) for k, v in re.findall(r'private val (\w+): TextStyle\s+get\(\) =\s+TextStyle\(\s+fontSize = ([\d.]+)\.sp', font_styles)}
    if len(objects['MiuixFontSizes']) != 14: raise ValueError('Unexpected Miuix text style count')
    source_for['MiuixFontSizes'] = 'compose-miuix-ui/miuix TextStyles.kt @ 39c40f9 (0.9.4), Apache-2.0'
    # Literal dimensions are generated from the same pinned files as the components.
    size_sources = list(texts.values()) + [markdown_style,markdown_content] + [(upstream / name).read_text() for name in ['Card.kt', 'IconButton.kt', 'SmallTitle.kt', 'TopAppBar.kt', 'Checkbox.kt', 'RadioButton.kt']]
    sizes = sorted({float(value) for text in size_sources for value in re.findall(r'(?<![\w.])([\d.]+)\.dp', text)})
    objects['EtaMarkdownFonts'] = {'Fp'+format(float(v),'g').replace('.','_'):float(v) for v in sorted(set(re.findall(r'([\d.]+)\.sp',markdown_style)),key=float)}
    source_for['EtaMarkdownFonts']='app/src/main/kotlin/io/github/mangi/eta/ui/markdown/MarkdownStyle.kt @ 3b1794d'
    objects['EtaMarkdownDefaults']={k:float(v) for k,v in re.findall(r'const val (\w+) = ([\d.]+)',markdown_style)}
    objects['EtaMarkdownDefaults'].update({k:float(v) for k,v in re.findall(r'private val (Table\w+Width) = ([\d.]+)\.dp',markdown_content)})
    objects['EtaMarkdownDefaults']['ListMarkerDigitWidth']=float(re.search(r'digits \* (\d+) \+',markdown_content)[1])
    objects['EtaMarkdownDefaults']['CopyResetMs']=int(re.search(r'delay\(([\d_]+)\)',markdown_content)[1].replace('_',''))
    source_for['EtaMarkdownDefaults']='app/src/main/kotlin/io/github/mangi/eta/ui/markdown/MarkdownStyle.kt, MarkdownContent.kt @ 3b1794d'
    store=(components.parent/'app/AgentConversationStore.kt').read_text()
    objects['EtaMessageDefaults']={'PageSize':int(re.search(r'MESSAGE_LOAD_PAGE_SIZE = (\d+)',store)[1]),'ThinkingTailLines':int(re.search(r'THINKING_TAIL_WINDOW_LINES = (\d+)',texts['ChatMessageItem.kt'])[1])}
    objects['EtaMessageDefaults'].update({k:float(v) for k,v in re.findall(r'(?:private |internal )?val (Work\w+)(?:: Dp)? = ([\d.]+)\.dp',texts['AgentWorkProcess.kt'])})
    objects['EtaMessageDefaults']['ChevronExpandedDegrees']=float(re.search(r'targetValue = if \(expanded\) ([\d.]+)f',texts['AgentWorkProcess.kt'].split('internal fun ExpandChevron',1)[1])[1])
    source_for['EtaMessageDefaults']='app/src/main/kotlin/io/github/mangi/eta/ui/components/ChatMessageItem.kt, AgentWorkProcess.kt; ui/app/AgentConversationStore.kt @ 3b1794d'
    objects['EtaComponentSizes'] = {'Dp' + format(value, 'g').replace('.', '_'): value for value in sizes}
    source_for['EtaComponentSizes'] = 'Eta E02 components @ 3b1794d; Miuix 0.9.4 pinned sources (see docs/eta-theme.json)'
    row = texts['EtaPreferenceRow.kt']; feedback = (upstream / 'PressFeedback.kt').read_text()
    objects['EtaComponentBehavior'] = {
        'ActionWidthFraction': float(re.search(r'\* ([\d.]+)f', row)[1]),
        'SinkScale': float(re.search(r'sinkAmount: Float = ([\d.]+)f', feedback)[1]),
        'SinkDamping': float(re.search(r'spring\(([\d.]+)f,', feedback)[1]),
        'SinkStiffness': float(re.search(r'spring\([\d.]+f, ([\d.]+)f', feedback)[1]),
        'ChoiceDuration': int(re.search(r'durationMillis = (\d+)', texts['EtaChoiceChip.kt'])[1]),
    }
    source_for['EtaComponentBehavior'] = 'EtaPreferenceRow.kt / EtaChoiceChip.kt @ 3b1794d; Miuix PressFeedback.kt @ 39c40f9'
    objects['MiuixIconButtonDefaults'] = defaults((upstream / 'IconButton.kt').read_text(), 'IconButtonDefaults')
    source_for['MiuixIconButtonDefaults'] = 'compose-miuix-ui/miuix IconButton.kt @ 39c40f9, Apache-2.0'
    layout = (eta_root / 'app/src/main/kotlin/io/github/mangi/eta/ui/layout/AdaptivePageLayout.kt').read_text()
    objects['EtaPageDefaults'] = {k: float(v) for k, v in re.findall(r'private val (\w+) = ([\d.]+)\.dp', layout)}
    source_for['EtaPageDefaults'] = 'app/src/main/kotlin/io/github/mangi/eta/ui/layout/AdaptivePageLayout.kt @ 3b1794d'
    top = (upstream / 'TopAppBar.kt').read_text()
    objects['MiuixTopBarDefaults'] = {k: float(re.search(r'val ' + k + r' = ([\d.]+)\.dp', top)[1]) for k in ['TitlePadding', 'NavigationIconPadding', 'ActionIconPadding', 'CollapsedHeight', 'SmallTopAppBarCenterHeight', 'LargeTitleBottomPadding']}
    objects['MiuixTopBarDefaults']['TitleFadeMultiplier'] = float(re.search(r'frac \* ([\d.]+)f', top)[1])
    objects['MiuixTopBarDefaults']['SmallTitleTranslation'] = float(re.search(r'if \(smallTitleVisible\) 0f else ([\d.]+)f', top)[1])
    objects['MiuixTopBarDefaults']['TitleShowResponse'] = float(re.search(r'val showSpec = folmeSpring<Float>\(damping = [\d.]+f, response = ([\d.]+)f', top)[1])
    objects['MiuixTopBarDefaults']['TitleHideResponse'] = float(re.search(r'val hideSpec = folmeSpring<Float>\(damping = [\d.]+f, response = ([\d.]+)f', top)[1])
    objects['MiuixTopBarDefaults']['TitleDamping'] = float(re.search(r'val showSpec = folmeSpring<Float>\(damping = ([\d.]+)f', top)[1])
    source_for['MiuixTopBarDefaults'] = 'compose-miuix-ui/miuix TopAppBar.kt @ 39c40f9, Apache-2.0'
    objects['EtaBlurDefaults'] = {k: float(v) for k, v in re.findall(r'private const val (\w+) = ([\d.]+)f', texts['TopBarBackdrop.kt'])}
    objects['EtaBlurDefaults']['ProgressiveCurve'] = float(re.search(r'curve = ([\d.]+)f', texts['TopBarBackdrop.kt'])[1])
    source_for['EtaBlurDefaults'] = 'app/src/main/kotlin/io/github/mangi/eta/ui/components/TopBarBackdrop.kt @ 3b1794d'
    dialog = (upstream / 'DialogContentLayout.kt').read_text()
    objects['MiuixDialogDefaults']['LargeWidth'] = float(re.search(r'windowWidth >= ([\d.]+)\.dp', dialog)[1])
    objects['MiuixDialogDefaults']['LargeHeight'] = float(re.search(r'windowHeight >= ([\d.]+)\.dp', dialog)[1])
    token = '// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/* @ 3b1794d.\n// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).\n// Generated by scripts/port-eta-theme.py. dp → vp; sp → fp, values unchanged.\n\nexport interface EtaAxisInsets { horizontal: number; vertical: number; }\nexport interface EtaSize { width: number; height: number; }\n\n'
    token += '\n'.join(generated_class(k, v, source_for[k]) for k, v in objects.items())
    emit(output / 'entry/src/main/ets/core/eta/tokens.ts', token, check)
    report = {'etaCommit': ETA_COMMIT, 'miuixVersion': '0.9.4', 'miuixCommit': MIUIX_COMMIT,
              'inputs': [origin(components / n, eta_root) for n in source_names] + [origin(p, PROJECT) for p in sorted(upstream.glob('*.kt'))],
              'miuixColorRoles': 53, 'lightColorCount': len(palettes['light']), 'darkColorCount': len(palettes['dark']), 'tokens': objects}
    emit_json(output / 'docs/eta-theme.json', report, check)
    return report


if __name__ == '__main__':
    args = arguments(__doc__); report = generate(args.eta_root, args.output_root, args.check)
    print('Generated', report['lightColorCount'], 'light/dark colors;', sum(map(len, report['tokens'].values())), 'dimension/font tokens')
