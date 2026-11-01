// 随身助手原创代码（D0b3）。
// Only the preview entry may supply a fake phone-operation transport. The production page never falls back to
// a fake: without the native library it shows "native library not loaded" instead of pretending to connect.
import { PhoneOpsTransport } from './phoneOpsController';

let previewFactory: (() => PhoneOpsTransport) | null = null;

export function setPhoneOpsPreviewTransport(factory: (() => PhoneOpsTransport) | null): void {
  previewFactory = factory;
}

export function phoneOpsPreviewTransport(): PhoneOpsTransport | null {
  return previewFactory === null ? null : previewFactory();
}
