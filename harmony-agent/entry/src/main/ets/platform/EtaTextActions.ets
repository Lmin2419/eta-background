// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/components/ChatMessageItem.kt,
// ui/markdown/MarkdownContent.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { pasteboard } from '@kit.BasicServicesKit';
import { common } from '@kit.AbilityKit';
import { safeLinkUrl } from '../core/eta/markdown/markdownInline';
export async function copyEtaText(text:string):Promise<void> {
  await pasteboard.getSystemPasteboard().setData(pasteboard.createData(pasteboard.MIMETYPE_TEXT_PLAIN,text));
}
export async function openEtaLink(context:common.UIAbilityContext,url:string):Promise<void> {
  const safe=safeLinkUrl(url);if(safe)await context.startAbility({action:'ohos.want.action.viewData',uri:safe});
}
