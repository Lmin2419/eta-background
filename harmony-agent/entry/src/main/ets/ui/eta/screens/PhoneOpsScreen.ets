// 随身助手原创代码（D0b3）：设置 → 手机操作。
import { common } from '@kit.AbilityKit';
import { parseLayout } from '../../../core/device';
import { HdcConnectionTransport, nativeHdcAvailable } from '../../../platform/HdcConnectionTransport';
import { FakePhoneOpsTransport, PhoneOpsController, PhoneOpsTransport } from '../../../core/eta/device/phoneOpsController';
import { PhoneOpsSnapshot, nativeMissingSnapshot } from '../../../core/eta/device/phoneOps';
import { validateHdcTarget } from '../../../core/eta/device/hdcConnection';
import { EtaComponentSizes as S, MiuixFontSizes as F } from '../../../core/eta/tokens';
import { MiuixScaffoldPage } from '../components/MiuixScaffoldPage';
import { EtaCard } from '../components/EtaCard';
import { EtaTextButton } from '../components/EtaControls';
import { EtaPreferenceRow } from '../components/EtaPreferenceRow';

const SCREEN_WIDTH = 1080;
const SCREEN_HEIGHT = 1920;

@Component
export struct PhoneOpsScreen {
  @State private snapshot: PhoneOpsSnapshot = nativeMissingSnapshot('');
  @State private address: string = '';
  @State private invalid: boolean = false;
  @State private notice: string = '';
  private controller: PhoneOpsController | undefined = undefined;

  aboutToAppear(): void {
    const context = this.getUIContext().getHostContext() as common.UIAbilityContext;
    const transport: PhoneOpsTransport = nativeHdcAvailable()
      ? new HdcConnectionTransport(context)
      : new FakePhoneOpsTransport();
    this.controller = new PhoneOpsController(transport, (raw: string): number => {
      try { return parseLayout(raw, SCREEN_WIDTH, SCREEN_HEIGHT).nodes.length; } catch (_) { return 0; }
    }, (): void => { this.snapshot = this.controller === undefined ? this.snapshot : this.controller.snapshot(); });
    this.snapshot = this.controller.snapshot();
  }

  private connect(): void {
    if (this.controller === undefined) return;
    const target = validateHdcTarget(this.address);
    if (target === null) { this.invalid = true; return; }
    this.invalid = false;
    if (!this.controller.configure(target)) { this.invalid = true; return; }
    this.controller.connect().then((): void => {
      const controller = this.controller;
      if (controller !== undefined) this.snapshot = controller.snapshot();
    });
  }

  private dump(): void {
    if (this.controller === undefined) return;
    this.controller.testDump().then((): void => {
      const controller = this.controller;
      if (controller !== undefined) this.snapshot = controller.snapshot();
    });
  }

  @Builder private row(label: ResourceStr, value: ResourceStr) {
    Row({ space: S.Dp16 }) {
      Text(label).fontSize(F.Body2).fontColor($r('app.color.miuix_on_surface_variant_summary')).layoutWeight(1)
      Text(value).fontSize(F.Body2).fontColor($r('app.color.miuix_on_surface')).textAlign(TextAlign.End).layoutWeight(2)
    }.width('100%')
  }

  @Builder private content() {
    Column({ space: S.Dp16 }) {
      EtaCard({ insidePadding: S.Dp16, content: () => {
        Text($r('app.string.phone_ops_intro')).fontSize(F.Footnote1).fontColor($r('app.color.miuix_on_surface_variant_summary')).width('100%')
      } })
      TextInput({ text: this.address, placeholder: $r('app.string.phone_ops_address_hint') })
        .id('phone-ops-address')
        .fontSize(F.Body1)
        .onChange((value: string) => { this.address = value; this.invalid = false; })
      if (this.invalid) { Text($r('app.string.phone_ops_invalid')).fontSize(F.Footnote1).fontColor($r('app.color.miuix_error')) }
      EtaCard({ insidePadding: S.Dp16, content: () => {
        Column({ space: S.Dp12 }) {
          this.row($r('app.string.phone_ops_status'), $r(this.snapshot.statusKey))
          if (this.snapshot.reasonKey !== null) this.row($r('app.string.phone_ops_reason'), $r(String(this.snapshot.reasonKey)))
          if (this.snapshot.state === 'connected') {
            this.row($r('app.string.phone_ops_identity'), this.snapshot.identity === '' ? '-' : this.snapshot.identity)
            this.row($r('app.string.phone_ops_uitest'), this.snapshot.uitestReady ? $r('app.string.phone_ops_yes') : $r('app.string.phone_ops_no'))
          }
          this.row($r('app.string.phone_ops_node_count'), String(this.snapshot.nodeCount))
        }.width('100%')
      } })
      Row({ space: S.Dp8 }) {
        EtaTextButton({ text: $r('app.string.phone_ops_connect'), primary: true, onAction: () => this.connect() }).layoutWeight(1)
        EtaTextButton({ text: $r('app.string.phone_ops_dump'), isEnabled: this.snapshot.state === 'connected', onAction: () => this.dump() }).layoutWeight(1)
      }.width('100%')
      EtaTextButton({ text: $r('app.string.phone_ops_disconnect'), isEnabled: this.snapshot.state !== 'unconfigured', onAction: () => { this.controller?.disconnect(); this.snapshot = this.controller === undefined ? this.snapshot : this.controller.snapshot(); } }).width('100%')
    }.width('100%').padding({ left: S.Dp16, right: S.Dp16 })
  }

  build() {
    MiuixScaffoldPage({ title: $r('app.string.phone_ops_title'), blurEnabled: true, progressiveBlur: false, content: () => { this.content(); }, onBack: () => {} })
  }
}
