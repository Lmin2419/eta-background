// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/EtaDropdownPreference.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { EtaComponentSizes as S, MiuixFontSizes as F } from '../../../core/eta/tokens';
import { EmptyEtaSlot } from './EtaCard';
import { EtaPreferenceRow } from './EtaPreferenceRow';

export class EtaDropdownItem {
  id: string;
  label: ResourceStr;
  isEnabled: boolean = true;
  onAction: () => void;
  constructor(id: string, label: ResourceStr, onAction: () => void = () => {}) {
    this.id = id; this.label = label; this.onAction = onAction;
  }
}
@Component
export struct EtaDropdownPreference {
  @Prop title: ResourceStr = '';
  @Prop summary: ResourceStr = '';
  @Prop @Watch('availabilityChanged') isEnabled: boolean = true;
  @Prop @Watch('availabilityChanged') items: EtaDropdownItem[] = [];
  @Prop selectedIndex: number = -1;
  @Prop hasLeading: boolean = false;
  @Prop hasBottom: boolean = false;
// ArkUI bindMenu is window-managed in both cases; Eta selection/dismissal semantics are preserved.
  @Prop useWindow: boolean = true;
  @BuilderParam bottomAction: () => void = EmptyEtaSlot;
  @BuilderParam startAction: () => void = EmptyEtaSlot;
  onSelectedIndexChange: (index: number) => void = () => {};
  @State private expanded: boolean = false;
  private availabilityChanged(): void {
    if (!this.isEnabled || this.items.length === 0) this.expanded = false;
  }
  private actionColor(): ResourceColor {
    return this.isEnabled && this.items.length > 0 ? $r('app.color.miuix_on_surface_variant_actions') : $r('app.color.miuix_disabled_on_surface');
  }
  @Builder private selectedLabel() {
    Row({ space: S.Dp8 }) {
      if (this.selectedIndex >= 0 && this.selectedIndex < this.items.length) {
        Text(this.items[this.selectedIndex].label).fontSize(F.Body2).fontColor(this.actionColor()).textAlign(TextAlign.End)
      }
      Image($r('app.media.ic_miuix_basic_arrow_up_down')).width(S.Dp10).height(S.Dp16)
        .fillColor(this.actionColor()).accessibilityLevel('no')
    }
  }
  @Builder private choices() {
    Menu() {
      ForEach(this.items, (item: EtaDropdownItem, index: number) => {
        MenuItem({ content: item.label }).selected(index === this.selectedIndex).enabled(item.isEnabled)
          .onClick(() => {
            if (!this.isEnabled || !item.isEnabled) return;
            this.expanded = false;
            this.onSelectedIndexChange(index);
            item.onAction();
          })
      }, (item: EtaDropdownItem) => item.id)
    }
  }
  build() {
    EtaPreferenceRow({ title: this.title, summary: this.summary,
      isEnabled: this.isEnabled && this.items.length > 0, holding: this.expanded,
      hasLeading: this.hasLeading, startAction: this.startAction, hasActions: true, hasBottom: this.hasBottom, bottomAction: this.bottomAction,
      endActions: this.selectedLabel, onAction: () => { this.expanded = !this.expanded; } })
      .bindMenu($$this.expanded, this.choices, { onDisappear: () => { this.expanded = false; } })
  }
}

@Component
export struct EtaWindowSpinnerPreference {
  @Prop title: ResourceStr = '';
  @Prop summary: ResourceStr = '';
  @Prop isEnabled: boolean = true;
  @Prop items: EtaDropdownItem[] = [];
  @Prop selectedIndex: number = -1;
  @Prop hasLeading: boolean = false;
  @BuilderParam startAction: () => void = EmptyEtaSlot;
  onSelectedIndexChange: (index: number) => void = () => {};
  build() {
    EtaDropdownPreference({ title: this.title, summary: this.summary, isEnabled: this.isEnabled, items: this.items,
      selectedIndex: this.selectedIndex, hasLeading: this.hasLeading, startAction: this.startAction,
      useWindow: true, onSelectedIndexChange: this.onSelectedIndexChange })
  }
}

@Component
export struct EtaOverlayDropdownPreference {
  @Prop title: ResourceStr = '';
  @Prop summary: ResourceStr = '';
  @Prop isEnabled: boolean = true;
  @Prop items: EtaDropdownItem[] = [];
  @Prop selectedIndex: number = -1;
  @Prop hasLeading: boolean = false;
  @BuilderParam startAction: () => void = EmptyEtaSlot;
  onSelectedIndexChange: (index: number) => void = () => {};
  build() {
    EtaDropdownPreference({ title: this.title, summary: this.summary, isEnabled: this.isEnabled, items: this.items,
      selectedIndex: this.selectedIndex, hasLeading: this.hasLeading, startAction: this.startAction,
      useWindow: false, onSelectedIndexChange: this.onSelectedIndexChange })
  }
}
