import { DeviceTools, RunControl, ToolResult } from './types';
import { tool, field } from './model';
import { KnownApp } from './extensions';
export interface SystemPort {status():Promise<string>;contact():Promise<string>;calendar(title:string,start:number,end:number):Promise<number>;location():Promise<string>;foreground():boolean}
export const SYSTEM_TOOLS=[tool('system_status','读取本机电量、网络类型与时间，不读取设备唯一标识。',{},[]),tool('contact_pick','请用户通过系统选择器主动选择一个联系人，不读取完整通讯录。',{},[]),tool('calendar_draft','打开系统日历编辑页供用户核对并保存；只在前台使用。',{title:field('string','事件标题'),start_time:field('string','包含时区的 ISO 开始时间'),end_time:field('string','包含时区的 ISO 结束时间')},['title','start_time','end_time']),tool('location_once','用户开启系统工具后，按需申请一次大致位置授权；不持续定位。',{},[])];
export const APP_TOOLS=[tool('apps_list','列出用户维护的已知应用入口；未核验条目不能视为已安装。',{},[])];
export class SystemTools implements DeviceTools{
 constructor(private port:SystemPort,private apps:KnownApp[]){}invalidate():void{}
 async execute(name:string,args:Record<string,unknown>,control:RunControl):Promise<ToolResult>{
  await control.checkpoint();if(name==='apps_list')return {ok:true,data:{apps:this.apps}};
  if(name==='system_status'){const data=await this.port.status();control.check();return {ok:true,data:{status:data}};}
  if(!this.port.foreground())throw new Error('此系统选择器或位置授权需返回助手前台后使用');
  if(name==='contact_pick'){const data=await this.port.contact();control.check();return {ok:true,data:{selected:data,source:'用户主动选择的联系人'}};}
  if(name==='location_once'){const data=await this.port.location();control.check();return {ok:true,data:{location:data}};}
  if(name==='calendar_draft'){
   if(typeof args.title!=='string'||!args.title.trim()||args.title.length>200)throw new Error('日历标题无效');
   for(const key of ['start_time','end_time'])if(typeof args[key]!=='string'||!/(Z|[+-]\d\d:\d\d)$/.test(String(args[key])))throw new Error('日历时间必须包含明确时区');
   const start=Date.parse(String(args.start_time)),end=Date.parse(String(args.end_time));if(!Number.isFinite(start)||!Number.isFinite(end)||end<=start||end-start>366*86400000)throw new Error('日历时间范围无效');
   const id=await this.port.calendar(args.title,start,end);control.check();return {ok:id>0,message:id>0?'系统日历返回保存成功':'用户取消了日历编辑，未创建',data:{eventId:id}};
  }
  throw new Error('系统工具未注册');
 }
}
