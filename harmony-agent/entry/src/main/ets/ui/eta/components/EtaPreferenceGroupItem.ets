// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/EtaPreferenceGroupItem.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { EtaCardDefaults as C, EtaPreferenceDefaults as P, EtaComponentSizes as S } from '../../../core/eta/tokens';
import { EmptyEtaSlot } from './EtaCard';
import { EtaPreferenceDivider } from './EtaPreferenceStyle';
@Component
export struct EtaPreferenceGroupItem {
  @Prop first: boolean = false;
  @Prop last: boolean = false;
  @Prop hasLeading: boolean = true;
  @BuilderParam content: () => void = EmptyEtaSlot;
  build() {
    Column() {
      this.content()
      if (!this.last) { EtaPreferenceDivider({ hasLeading: this.hasLeading }) }
    }.width('100%').backgroundColor($r('app.color.miuix_surface_container'))
      .borderRadius({ topLeft: this.first ? C.CornerRadius : S.Dp0, topRight: this.first ? C.CornerRadius : S.Dp0,
        bottomLeft: this.last ? C.CornerRadius : S.Dp0, bottomRight: this.last ? C.CornerRadius : S.Dp0 })
      .clip(true).margin({ left: P.SidePadding, right: P.SidePadding })
  }
}
