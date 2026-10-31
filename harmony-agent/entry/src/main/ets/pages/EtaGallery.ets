// Ported component scenarios from app/src/main/kotlin/io/github/mangi/eta/ui/components/* @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
// Preview only: intentionally absent from main_pages.json. Selection state here is a fixture.
import { EtaComponentSizes as S, MiuixFontSizes as F, MiuixDialogDefaults as D } from '../core/eta/tokens';
import { AgentStatusCard } from '../ui/eta/components/AgentStatusCard';
import { EtaCard } from '../ui/eta/components/EtaCard';
import { EtaPreferenceGroup, EtaPreferenceGroupTitle, EtaPreferenceIcon, EtaPreferenceDivider } from '../ui/eta/components/EtaPreferenceStyle';
import { EtaPreference, EtaArrowPreference, EtaPreferenceRow } from '../ui/eta/components/EtaPreferenceRow';
import { EtaPreferenceGroupItem } from '../ui/eta/components/EtaPreferenceGroupItem';
import { EtaSwitchPreference, EtaRadioButtonPreference, EtaCheckboxPreference } from '../ui/eta/components/EtaSelectionPreferences';
import { EtaDropdownPreference, EtaDropdownItem, EtaWindowSpinnerPreference, EtaOverlayDropdownPreference } from '../ui/eta/components/EtaDropdownPreference';
import { EtaFeatureCard, EtaChoiceChip, ToolChip, SectionHeader, ListEmptyState } from '../ui/eta/components/EtaSmallComponents';
import { EtaTextButton, EtaSwitch } from '../ui/eta/components/EtaControls';
import { EtaWindowDialog, EtaOverlayDialog, ItemDescriptionDialog, MiuixDialogActions, EtaDialogContent } from '../ui/eta/components/EtaDialogs';
import { MiuixScaffoldPage } from '../ui/eta/components/MiuixScaffoldPage';
import { EtaRunStatus, EtaPermissionStatus, runStatusColor, runStatusLabel, permissionStatusColor, permissionStatusLabel } from '../ui/eta/components/StatusColors';

@Entry
@Component
struct EtaGallery {
  @Prop previewFontScale: number = 1;
  aboutToAppear(): void { this.getUIContext().getHostContext()?.getApplicationContext().setFontSizeScale(this.previewFontScale); }
  @State private blurStyle: number = 0;
  @State private checked: boolean = true;
  @State private selected: number = 0;
  @State private pageWidth: number = S.Dp0;
  @State private pageHeight: number = S.Dp0;
  private windowDialog?: CustomDialogController;
  private overlayDialog?: CustomDialogController;
  private descriptionDialog?: CustomDialogController;
  private choices: EtaDropdownItem[] = [
    new EtaDropdownItem('system', $r('app.string.appearance_theme_system')),
    new EtaDropdownItem('light', $r('app.string.appearance_theme_light')),
    new EtaDropdownItem('dark', $r('app.string.appearance_theme_dark'))
  ];
  private alignment(): DialogAlignment {
    return this.pageWidth >= D.LargeWidth && this.pageHeight >= D.LargeHeight ? DialogAlignment.Center : DialogAlignment.Bottom;
  }
  private openWindow(): void {
    this.windowDialog = new CustomDialogController({
      builder: EtaWindowDialog({ title: $r('app.string.appearance_title'), content: () => { this.dialogActions(); } }),
      customStyle: true, alignment: this.alignment(), maskColor: $r('app.color.miuix_window_dimming'),
      onDidDisappear: () => { this.windowDialog = undefined; }
    });
    this.windowDialog.open();
  }
  private openOverlay(): void {
    this.overlayDialog = new CustomDialogController({
      builder: EtaOverlayDialog({ title: $r('app.string.appearance_title'), summary: $r('app.string.appearance_blur_summary'), content: () => { this.overlayActions(); } }),
      customStyle: true, alignment: this.alignment(), showInSubWindow: false,
      maskColor: $r('app.color.miuix_window_dimming'), onDidDisappear: () => { this.overlayDialog = undefined; }
    });
    this.overlayDialog.open();
  }
  private openDescription(): void {
    this.descriptionDialog = new CustomDialogController({
      builder: ItemDescriptionDialog({ title: $r('app.string.appearance_title'), description: $r('app.string.appearance_blur_summary') }),
      customStyle: true, alignment: this.alignment(), maskColor: $r('app.color.miuix_window_dimming'),
      onDidDisappear: () => { this.descriptionDialog = undefined; }
    });
    this.descriptionDialog.open();
  }
  aboutToDisappear(): void { this.getUIContext().getHostContext()?.getApplicationContext().setFontSizeScale(1); this.windowDialog?.close(); this.overlayDialog?.close(); this.descriptionDialog?.close(); }
  @Builder private dialogActions() {
    MiuixDialogActions({ onCancel: () => { this.windowDialog?.close(); }, onConfirm: () => { this.windowDialog?.close(); } })
  }
  @Builder private overlayActions() {
    MiuixDialogActions({ destructive: true, onCancel: () => { this.overlayDialog?.close(); }, onConfirm: () => { this.overlayDialog?.close(); } })
  }
  @Builder private icon() { EtaPreferenceIcon({ icon: $r('app.media.ic_rounded_settings') }) }
  @Builder private disabledIcon() { EtaPreferenceIcon({ icon: $r('app.media.ic_rounded_settings'), isEnabled: false }) }
  @Builder private opticalIcon() { EtaPreferenceIcon({ icon: $r('app.media.ic_rounded_extension'), optical: true }) }
  @Builder private bottom() { ToolChip({ label: $r('app.string.status_ready') }) }
  @Builder private longSummary() {
    Text($r('app.string.appearance_blur_summary')).fontSize(F.Body2).fontColor($r('app.color.miuix_on_surface_variant_summary'))
  }
  @Builder private preferenceRows() {
    EtaPreference({ title: $r('app.string.appearance_title'), hasLeading: true, startAction: () => { this.icon(); }, onAction: () => { this.openWindow(); } })
    EtaPreferenceDivider()
    EtaArrowPreference({ title: $r('app.string.appearance_blur'), summary: $r('app.string.appearance_blur_summary'), hasLeading: true, startAction: () => { this.icon(); }, onAction: () => { this.openDescription(); } })
    EtaPreferenceDivider()
    EtaDropdownPreference({ title: $r('app.string.appearance_blur'), items: [new EtaDropdownItem('uniform', $r('app.string.appearance_blur_style_uniform')), new EtaDropdownItem('progressive', $r('app.string.appearance_blur_style_progressive')), new EtaDropdownItem('off', $r('app.string.status_disabled'))], selectedIndex: this.blurStyle, onSelectedIndexChange: (value: number) => { this.blurStyle = value; } })
    EtaPreferenceDivider({ hasLeading: false })
    EtaSwitchPreference({ title: $r('app.string.appearance_blur'), checked: this.checked, onCheckedChange: (value: boolean) => { this.checked = value; } })
    EtaSwitchPreference({ title: $r('app.string.status_disabled'), checked: true, isEnabled: false })
    EtaRadioButtonPreference({ title: $r('app.string.appearance_theme_light'), selected: this.selected === 1, onAction: () => { this.selected = 1; } })
    EtaRadioButtonPreference({ title: $r('app.string.appearance_theme_dark'), selected: this.selected === 2, onAction: () => { this.selected = 2; } })
    EtaRadioButtonPreference({ title: $r('app.string.status_disabled'), selected: true, isEnabled: false })
    EtaCheckboxPreference({ title: $r('app.string.action_confirm'), checked: this.checked, checkboxAtStart: true, hasLeading: true, startAction: () => { this.opticalIcon(); }, onCheckedChange: (value: boolean) => { this.checked = value; } })
    EtaCheckboxPreference({ title: $r('app.string.status_disabled'), checked: false, isEnabled: false })
    EtaDropdownPreference({ title: $r('app.string.appearance_title'), items: this.choices, selectedIndex: this.selected, onSelectedIndexChange: (index: number) => { this.selected = index; } })
    EtaDropdownPreference({ title: $r('app.string.status_disabled'), items: [] })
    EtaWindowSpinnerPreference({ title: $r('app.string.appearance_title'), items: this.choices, selectedIndex: this.selected, onSelectedIndexChange: (index: number) => { this.selected = index; } })
    EtaOverlayDropdownPreference({ title: $r('app.string.appearance_title'), items: this.choices, selectedIndex: this.selected, onSelectedIndexChange: (index: number) => { this.selected = index; } })
    EtaPreferenceRow({ title: $r('app.string.appearance_blur'), hasCustomTitle: true, titleAction: () => { this.longSummary(); }, hasBottom: true, bottomAction: () => { this.bottom(); } })
  }
  @Builder private firstRow() { EtaPreference({ title: $r('app.string.appearance_theme_light') }) }
  @Builder private lastRow() { EtaPreference({ title: $r('app.string.appearance_theme_dark') }) }
  @Builder private emptyAction() { EtaTextButton({ text: $r('app.string.action_confirm'), onAction: () => { this.openOverlay(); } }) }
  @Builder private body() {
    AgentStatusCard({title:$r('app.string.work_completed'),currentStep:$r('app.string.status_ready'),status:'Success'})
    EtaPreferenceGroupTitle({ title: $r('app.string.appearance_group_interface') })
    EtaPreferenceGroup({ content: () => { this.preferenceRows(); } })
    EtaPreferenceGroupItem({ first: true, content: () => { this.firstRow(); } })
    EtaPreferenceGroupItem({ last: true, content: () => { this.lastRow(); } })
    SectionHeader({ title: $r('app.string.appearance_group_color') })
    Column({ space: S.Dp12 }) {
      EtaFeatureCard({ title: $r('app.string.appearance_title'), summary: $r('app.string.appearance_blur_summary'), icon: () => { this.icon(); }, onAction: () => { this.openWindow(); } })
      Row({ space: S.Dp8 }) {
        EtaChoiceChip({ text: $r('app.string.appearance_theme_light'), selected: this.selected === 1, onAction: () => { this.selected = 1; } })
        EtaChoiceChip({ text: $r('app.string.appearance_theme_dark'), selected: this.selected === 2, onAction: () => { this.selected = 2; } })
        EtaChoiceChip({ text: $r('app.string.status_disabled'), isEnabled: false })
      }
      ToolChip({ label: $r('app.string.status_ready') })
      EtaSwitch({ checked: this.checked, onCheckedChange: (value: boolean) => { this.checked = value; } })
      EtaTextButton({ text: $r('app.string.action_confirm'), primary: true, onAction: () => { this.openWindow(); } })
      EtaTextButton({ text: $r('app.string.status_disabled'), isEnabled: false })
      EtaCard({ content: () => { this.bottom(); } })
      MiuixDialogActions({ confirmEnabled: false })
      Text(runStatusLabel(EtaRunStatus.Running)).fontSize(F.Body2).fontColor(runStatusColor(EtaRunStatus.Running))
      Text(permissionStatusLabel(EtaPermissionStatus.Warning)).fontSize(F.Body2).fontColor(permissionStatusColor(EtaPermissionStatus.Warning))
      EtaDialogContent({ title: $r('app.string.action_confirm'), content: () => { this.dialogActions(); } })
    }.width('100%').padding(S.Dp16)
    ListEmptyState({ title: $r('app.string.memory_empty_title') })
    ListEmptyState({ title: $r('app.string.mcp_empty_title'), summary: $r('app.string.mcp_empty_summary'), hasIcon: true, icon: () => { this.icon(); }, hasAction: true, action: () => { this.emptyAction(); } })
  }
  build() {
    MiuixScaffoldPage({ title: $r('app.string.appearance_title'), blurEnabled: this.blurStyle !== 2, progressiveBlur: this.blurStyle === 1, content: () => { this.body(); } })
      .onAreaChange((_old, area) => { this.pageWidth = Number(area.width); this.pageHeight = Number(area.height); })
  }
}

// Preview selector supplies the system dark palette and 150% font scaling; no fake screenshot.
@Preview({ title: 'Eta · 深色 · 大字体', colorMode: 'dark', dpi: 320, width: 800, height: 1200 })
@Component
struct EtaGalleryDarkPreview {
  build() { EtaGallery({ previewFontScale: 1.5 }) }
}
