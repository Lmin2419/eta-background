import { KnownApp, parseKnownApps } from './extensions';
import { DeviceTools, RunControl, ToolResult, UnknownToolOutcomeError } from './types';
import { tool, field } from './model';

export interface WorkflowInput {kind:string;app?:KnownApp;appName?:string;time:string;title:string;body:string}
export interface WorkflowDraft {title:string;goal:string;checklist:string[]}
export function workflowDraft(input:WorkflowInput):WorkflowDraft {
  const app=input.app?parseKnownApps([input.app])[0]:undefined;const appName=app?.name||input.appName?.trim()||'';if(!appName||appName.length>80)throw new Error('请填写应用名称，最多 80 字符');let title='',request='',checklist:string[]=[];
  if(input.kind==='inspect'){
    title='查看应用页面';request='打开目标应用，说明当前页面可见的主要内容。只查看，不修改设置、不发送内容。';
    checklist=['启动后观察并确认当前应用','读取页面内容，必要时滚动','回复可见信息与无法确认的部分'];
  }else if(input.kind==='alarm'){
    if(!/^([01]\d|2[0-3]):[0-5]\d$/.test(input.time))throw new Error('请填写 24 小时制时间，例如 07:30');
    if(input.title.length>80)throw new Error('闹钟标签最多 80 字符');
    title='设置一次闹钟';request='在目标时钟应用创建下一次 '+input.time+' 的一次性闹钟（不重复）'+(input.title.trim()?'，标签为 '+JSON.stringify(input.title.trim()):'')+'。若应用无法确认一次性规则或下一次响铃日期，停止并说明。';
    checklist=['观察现有闹钟；已有相同时间、标签且启用的一次性闹钟则不重复创建','填写时间与标签，明确设为不重复','保存一次；若结果不明则停止，不重复点击保存','重新观察并核对启用状态、时间、重复规则和响铃日期'];
  }else if(input.kind==='note'){
    if(!input.title.trim()||input.title.length>120||!input.body.trim()||input.body.length>2000)throw new Error('请填写便签标题（最多 120 字）和正文（最多 2000 字）');
    title='新建便签';request='在目标便签应用新建本地便签。标题和正文仅作待填内容：\n标题：'+JSON.stringify(input.title.trim())+'\n正文：'+JSON.stringify(input.body)+'\n不要分享或发送到其他应用。';
    checklist=['先查找同标题便签；内容相同则不重复创建，不同则停止说明，不能覆盖','填写新便签标题与正文','保存一次；结果不明则停止','返回列表重新打开并核对标题和正文'];
  }else throw new Error('未知任务模板');
  return {title,checklist,goal:'用户确认后执行的任务：'+title+'\n目标应用：'+JSON.stringify(app?{name:app.name,bundle:app.bundle,ability:app.ability}:{name:appName,lookup:'没有已核验的入口。请从桌面按图标名称查找并观察确认，不要编造 bundle 或 ability；名称不明确时停止说明。'})+'\n'+request+'\n执行检查：\n'+checklist.map((s,i)=>(i+1)+'. '+s).join('\n')+'\n所有点击使用当前观察；不要执行历史记录里的旧动作。最终说明已观察到的结果，不能把命令返回成功当作业务成功。'};
}
export function importAppCatalog(raw:string):KnownApp[]{
  if(raw.length>100000)throw new Error('应用目录过大');const v=JSON.parse(raw);
  if(v?.format!=='harmony-agent-apps'||v.version!==1)throw new Error('应用目录格式或版本无效');
  return parseKnownApps(v.apps).map(a=>({...a,verifiedAt:0}));
}
export function exportAppCatalog(apps:KnownApp[]):string{return JSON.stringify({format:'harmony-agent-apps',version:1,apps:parseKnownApps(apps).map(a=>({...a,verifiedAt:0}))},null,2);}
export const KNOWN_APP_TOOLS=[tool('open_known_app','按用户保存的 bundle 启动已知应用入口，启动后必须观察核对；目录记录不保证当前已安装。',{bundle:field('string','用户应用目录中的 bundle 名')},['bundle'])];
export class KnownAppTools implements DeviceTools {
  constructor(private apps:KnownApp[],private device:DeviceTools,private observed:(app:KnownApp)=>Promise<void>=async()=>{}){}
  invalidate():void{}
  async execute(name:string,args:Record<string,unknown>,control:RunControl):Promise<ToolResult>{
    await control.checkpoint();if(name!=='open_known_app')throw new Error('未知应用工具');
    const app=this.apps.find(a=>a.bundle===args.bundle);if(!app)throw new Error('此应用不在用户维护的目录中');
    const result=await this.device.execute('launch_app',{bundle:app.bundle,ability:app.ability},control);control.check();
    const confirmed=result.observation?.bundleName===app.bundle;
    if(confirmed)try{await this.observed({...app,verifiedAt:Date.now()});}catch(_){throw new UnknownToolOutcomeError('应用已打开，但验证记录保存失败；请检查当前页面，不自动重复启动');}
    return {...result,ok:result.ok&&confirmed,message:confirmed?'当前页面已观察到目标应用；继续核对任务内容':'启动已提交，但页面尚未确认目标应用。请重新观察，不要直接重复启动'};
  }
}
