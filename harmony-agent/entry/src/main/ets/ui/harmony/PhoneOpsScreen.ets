// 随身助手原创代码（D0b3）：设置 → 手机操作（鸿蒙版独有页面）。
import { common } from '@kit.AbilityKit';
import { display } from '@kit.ArkUI';
import { FoundationController } from '../../core/foundation/controller';
import { phoneOpsPreviewTransport } from '../../core/eta/device/phoneOpsPreview';
import { parseLayout } from '../../core/device';
import { HdcConnectionTransport, nativeHdcAvailable } from '../../platform/HdcConnectionTransport';
import { PhoneOpsController, PhoneOpsTransport } from '../../core/eta/device/phoneOpsController';
import { PhoneOpsSnapshot, nativeMissingSnapshot } from '../../core/eta/device/phoneOps';
import { validateHdcTarget } from '../../core/eta/device/hdcConnection';
import { EtaComponentSizes as S, MiuixFontSizes as F } from '../../core/eta/tokens';
import { MiuixScaffoldPage } from '../eta/components/MiuixScaffoldPage';
import { EtaCard } from '../eta/components/EtaCard';
import { EtaTextButton } from '../eta/components/EtaControls';

interface InfoRowParams {
  label: ResourceStr;
  value: ResourceStr;
}

// One object-literal parameter, so ArkUI re-evaluates label/value when the page state changes. A @Builder
// with plain positional parameters keeps showing the values from its first call.
@Builder
function infoRow(params: InfoRowParams) {
  Row({ space: S.Dp16 }) {
    Text(params.label).fontSize(F.Body2).fontColor($r('app.color.miuix_on_surface_variant_summary')).layoutWeight(1)
    Text(params.value).fontSize(F.Body2).fontColor($r('app.color.miuix_on_surface')).textAlign(TextAlign.End).layoutWeight(2)
  }.width('100%')
}

@Component
export struct PhoneOpsScreen {
  @State private snapshot: PhoneOpsSnapshot = nativeMissingSnapshot('');
  @State private address: string = '';
  @State private invalid: boolean = false;
  @State private notice: string = '';
  /** The app shell's controller, used only to leave this page. */
  shell: FoundationController | undefined = undefined;
  private controller: PhoneOpsController | undefined = undefined;

  aboutToAppear(): void {
    const context = this.getUIContext().getHostContext() as common.UIAbilityContext;
    // Real device: the native HDC client. Preview: the fake supplied by the preview entry. Otherwise there is no
    // transport and the page stays in the "native library not loaded" state.
    const preview = phoneOpsPreviewTransport();
    const transport: PhoneOpsTransport | null = nativeHdcAvailable() ? new HdcConnectionTransport(context) : preview;
    if (transport === null) { console.error('[phoneOps] native library not loaded'); return; }
    this.controller = new PhoneOpsController(transport, (raw: string): number => this.countNodes(raw),
      (): void => { this.refresh(); });
    this.snapshot = this.controller.snapshot();
  }

  private countNodes(raw: string): number {
    try {
      // The previewer's display is a mock that can report a zero size; do not drop every node then.
      const screen = display.getDefaultDisplaySync();
      const width = screen.width > 0 ? screen.width : Number.MAX_SAFE_INTEGER;
      const height = screen.height > 0 ? screen.height : Number.MAX_SAFE_INTEGER;
      return parseLayout(raw, width, height).nodes.length;
    } catch (_) {
      return 0;
    }
  }

  private refresh(): void {
    const controller = this.controller;
    if (controller !== undefined) this.snapshot = controller.snapshot();
  }

  /** $r only takes literal names, so every status and reason key maps to its own resource. */
  private keyText(key: string): Resource {
    switch (key) {
      case 'phone_ops_status_unconfigured': return $r('app.string.phone_ops_status_unconfigured');
      case 'phone_ops_status_connecting': return $r('app.string.phone_ops_status_connecting');
      case 'phone_ops_status_authorizing': return $r('app.string.phone_ops_status_authorizing');
      case 'phone_ops_status_connected': return $r('app.string.phone_ops_status_connected');
      case 'phone_ops_status_disconnected': return $r('app.string.phone_ops_status_disconnected');
      case 'hdc_disconnected_user': return $r('app.string.hdc_disconnected_user');
      case 'hdc_disconnected_timeout': return $r('app.string.hdc_disconnected_timeout');
      case 'hdc_disconnected_refused': return $r('app.string.hdc_disconnected_refused');
      case 'hdc_disconnected_unauthorized': return $r('app.string.hdc_disconnected_unauthorized');
      case 'hdc_disconnected_lost': return $r('app.string.hdc_disconnected_lost');
      case 'hdc_disconnected_protocol': return $r('app.string.hdc_disconnected_protocol');
      case 'hdc_disconnected_unknown': return $r('app.string.hdc_disconnected_unknown');
      default: return $r('app.string.phone_ops_status_native_missing');
    }
  }

  private connect(): void {
    if (this.controller === undefined) return;
    const target = validateHdcTarget(this.address);
    if (target === null) { this.invalid = true; return; }
    this.invalid = false;
    if (!this.controller.configure(target)) { this.invalid = true; return; }
    this.controller.connect().then((): void => { this.refresh(); }).catch((): void => { this.refresh(); });
  }

  private dump(): void {
    if (this.controller === undefined) return;
    this.controller.testDump().then((): void => { this.refresh(); }).catch((): void => { this.refresh(); });
  }

  @Builder private intro() {
    Text($r('app.string.phone_ops_intro')).fontSize(F.Footnote1).fontColor($r('app.color.miuix_on_surface_variant_summary')).width('100%')
  }

  @Builder private statusCard() {
    Column({ space: S.Dp12 }) {
      infoRow({ label: $r('app.string.phone_ops_status'), value: this.keyText(this.snapshot.statusKey) })
      if (this.snapshot.reasonKey !== null) {
        infoRow({ label: $r('app.string.phone_ops_reason'), value: this.keyText(this.snapshot.reasonKey) })
      }
      if (this.snapshot.state === 'connected') {
        infoRow({ label: $r('app.string.phone_ops_identity'), value: this.snapshot.identity === '' ? '-' : this.snapshot.identity })
        infoRow({ label: $r('app.string.phone_ops_uitest'), value: this.snapshot.uitestReady ? $r('app.string.phone_ops_yes') : $r('app.string.phone_ops_no') })
      }
      infoRow({ label: $r('app.string.phone_ops_node_count'), value: String(this.snapshot.nodeCount) })
    }.width('100%')
  }

  @Builder private content() {
    Column({ space: S.Dp16 }) {
      EtaCard({ insidePadding: S.Dp16, content: () => { this.intro(); } })
      TextInput({ text: this.address, placeholder: $r('app.string.phone_ops_address_hint') })
        .id('phone-ops-address')
        .fontSize(F.Body1)
        .enabled(this.controller !== undefined)
        .onChange((value: string) => { this.address = value; this.invalid = false; })
      if (this.invalid) {
        Text($r('app.string.phone_ops_invalid')).fontSize(F.Footnote1).fontColor($r('app.color.miuix_error'))
      }
      EtaCard({ insidePadding: S.Dp16, content: () => { this.statusCard(); } })
      Row({ space: S.Dp8 }) {
        EtaTextButton({ text: $r('app.string.phone_ops_connect'), primary: true, isEnabled: this.controller !== undefined, onAction: () => this.connect() }).layoutWeight(1)
        EtaTextButton({ text: $r('app.string.phone_ops_dump'), isEnabled: this.snapshot.state === 'connected', onAction: () => this.dump() }).layoutWeight(1)
      }.width('100%')
      EtaTextButton({ text: $r('app.string.phone_ops_disconnect'), isEnabled: this.controller !== undefined && this.snapshot.state !== 'unconfigured', onAction: () => { this.controller?.disconnect(); this.refresh(); } }).width('100%')
    }.width('100%').padding({ left: S.Dp16, right: S.Dp16 })
  }

  build() {
    MiuixScaffoldPage({ title: $r('app.string.phone_ops_title'), blurEnabled: true, progressiveBlur: false, content: () => { this.content(); }, onBack: () => { this.shell?.back(); } })
  }
}
