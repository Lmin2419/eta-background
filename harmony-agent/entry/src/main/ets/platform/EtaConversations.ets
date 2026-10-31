// Ported platform boundaries from app/src/main/kotlin/io/github/mangi/eta/ui/app/AgentAppRoot.kt,
// ConversationTimeLabels.kt, ConversationMarkdownExporter.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { common } from '@kit.AbilityKit';
import { i18n, intl } from '@kit.LocalizationKit';
import { picker, fileIo } from '@kit.CoreFileKit';
import { util } from '@kit.ArkTS';
import { CalendarDate, ConversationDateFormat } from '../core/eta/conversationTimeLabels';
import { PaneDateContext, conversationCaseFold } from '../core/eta/conversationDrawer';
import { ExportLabels, ConversationMarkdownExporter } from '../core/eta/conversationMarkdownExporter';
import { SystemNoticeCode } from '../core/eta/model/conversation';
class HarmonyConversationDates implements ConversationDateFormat {
  calendarDate(timestamp:number,timeZone:string):CalendarDate {
    const calendar=i18n.getCalendar(timeZone,'gregory');calendar.setTime(timestamp);
    return {year:calendar.get('year'),month:calendar.get('month')+1,day:calendar.get('date')};
  }
  format(timestamp:number,pattern:string,locale:string,timeZone:string):string {
    const date=new Date(timestamp);
    if(pattern==='HH:mm'||pattern==='h:mm a')return new intl.DateTimeFormat(locale,{hour:pattern==='HH:mm'?'2-digit':'numeric',minute:'2-digit',hour12:pattern==='h:mm a',timeZone}).format(date);
    if(pattern==='EEE')return new intl.DateTimeFormat(locale,{weekday:'short',timeZone}).format(date);
    if(pattern!=='MMM d'){const d=this.calendarDate(timestamp,timeZone);return pattern.replace('M',String(d.month)).replace('d',String(d.day));}
    return new intl.DateTimeFormat(locale,{month:'short',day:'numeric',timeZone}).format(date);
  }
  mediumDate(timestamp:number,locale:string,timeZone:string):string{return new intl.DateTimeFormat(locale,{year:'numeric',month:'short',day:'numeric',timeZone}).format(new Date(timestamp));}
}
export class EtaConversations {
  private dates:HarmonyConversationDates=new HarmonyConversationDates();
  private context:common.UIAbilityContext;
  constructor(context:common.UIAbilityContext){this.context=context;}
  private string(resource:Resource):string{return this.context.resourceManager.getStringSync(resource.id);}
  dateContext():PaneDateContext{return {now:Date.now(),locale:i18n.System.getSystemLocale(),timeZone:i18n.getTimeZone().getID(),use24HourClock:i18n.System.is24HourClock(),yesterdayLabel:this.string($r('app.string.time_yesterday')),recentLabel:this.string($r('app.string.time_recent')),formatter:this.dates};}
  fileName(title:string):string{return ConversationMarkdownExporter.defaultFileName(title,this.string($r('app.string.conversation_export_default_name')));}
  labels():ExportLabels{return {
    user:this.string($r('app.string.conversation_export_user')),assistant:this.string($r('app.string.conversation_export_assistant')),thinking:this.string($r('app.string.conversation_export_thinking')),
    toolLineFormat:this.string($r('app.string.conversation_export_tool_line')),toolsLineFormat:this.string($r('app.string.conversation_export_tools_line')),
    argumentsFormat:this.string($r('app.string.conversation_export_tool_arguments')),resultFormat:this.string($r('app.string.conversation_export_tool_result')),imagesFormat:this.string($r('app.string.conversation_export_images')),
    toolStatusRunning:this.string($r('app.string.tool_status_running')),toolStatusSuccess:this.string($r('app.string.tool_status_success')),toolStatusFailed:this.string($r('app.string.tool_status_failed')),toolStatusUnknown:this.string($r('app.string.tool_status_unknown')),
    noticeStopped:this.string($r('app.string.system_notice_stopped')),noticeEmptyResult:this.string($r('app.string.system_notice_empty_result')),noticeModelRetry:this.string($r('app.string.system_notice_model_retry')),
    noticeRuntimeFailed:this.string($r('app.string.system_notice_runtime_failed')),noticeInterrupted:this.string($r('app.string.system_notice_interrupted')),noticeContextCompaction:this.string($r('app.string.context_compaction'))
  };}
  noticeSearch(query:string):SystemNoticeCode[]{
    if(!query.trim())return [];
    const keys:SystemNoticeCode[]=['Stopped','EmptyResult','RuntimeFailed','ModelRetry','ContextCompaction','Interrupted'];
    const l=this.labels(),values=[l.noticeStopped,l.noticeEmptyResult,l.noticeRuntimeFailed,l.noticeModelRetry,l.noticeContextCompaction||'',l.noticeInterrupted];
    return keys.filter((_code,index)=>conversationCaseFold(values[index]).includes(conversationCaseFold(query.trim())));
  }
  async exportMarkdown(fileName:string,markdown:string):Promise<boolean>{
    const uris=await new picker.DocumentViewPicker(this.context).save({newFileNames:[fileName],fileSuffixChoices:['Markdown|.md']});
    if(!uris.length)return false;
    const file=await fileIo.open(uris[0],fileIo.OpenMode.READ_WRITE|fileIo.OpenMode.CREATE|fileIo.OpenMode.TRUNC);
    try{
      const bytes=new util.TextEncoder().encodeInto(markdown);let offset=0;
      while(offset<bytes.byteLength){const chunk=bytes.slice(offset);const written=await fileIo.write(file.fd,chunk.buffer);if(written<=0)throw new Error(this.string($r('app.string.conversation_export_failed')));offset+=written;}
    }finally{await fileIo.close(file);}
    return true;
  }
}
