import { KnownApp } from '../core/extensions';
import { workflowDraft } from '../core/workflows';
import { tone } from '../core/theme';
@Component
export struct QuickTasksPanel {
 @Prop apps:KnownApp[]=[];
 onPrepared:(goal:string)=>void=()=>{};
 onClose:()=>void=()=>{};
 @StorageLink('darkTheme') private darkTheme:boolean=false;
 @State private kind:string='inspect';
 @State private selected:number=0;
 @State private appName:string='';
 @State private time:string='07:30';
 @State private title:string='';
 @State private body:string='';
 @State private preview:string='';
 @State private status:string='';
 private appOptions():SelectOption[]{const options:SelectOption[]=[{value:'按桌面应用名称查找'}];return options.concat(this.apps.map((a:KnownApp):SelectOption=>({value:a.name+(a.verifiedAt?' · 曾观察到':' · 未核验')})));}
 private prepare():void{
  try{const app=this.selected>0?this.apps[this.selected-1]:undefined;this.preview=workflowDraft({kind:this.kind,app,appName:this.appName,time:this.time,title:this.title,body:this.body}).goal;this.status='核对下面的任务内容，再填入聊天输入框；此处不会操作手机。';}
  catch(e){this.status=(e as Error).message;}
 }
 build(){
  Column({space:12}){
   Row(){Text('常用手机任务').fontSize(22).fontWeight(FontWeight.Bold).layoutWeight(1);Button('关闭').onClick(()=>this.onClose())}.width('100%')
   Scroll(){Column({space:12}){
    Text('选择模板 → 核对任务 → 填入输入框 → 点击发送执行。模板按当前页面执行，不依赖固定坐标。').fontSize(13).width('100%')
    Select([{value:'查看应用页面'},{value:'设置一次闹钟'},{value:'新建便签'}]).selected(['inspect','alarm','note'].indexOf(this.kind)).value(this.kind==='inspect'?'查看应用页面':this.kind==='alarm'?'设置一次闹钟':'新建便签').onSelect((i:number)=>{this.kind=['inspect','alarm','note'][i];this.preview='';})
    Select(this.appOptions()).selected(this.selected).value(this.selected===0?'按桌面应用名称查找':this.apps[this.selected-1]?.name||'选择应用').onSelect((i:number)=>{this.selected=i;this.preview='';})
    if(this.selected===0){TextInput({text:this.appName,placeholder:'应用名称，例如：时钟、备忘录'}).maxLength(80).onChange((v:string)=>{this.appName=v;this.preview='';});Text('助手从桌面查找图标并确认页面；找不到或名称不明确时会停止。已知入口可在设置中维护。').fontSize(12).width('100%')}
    if(this.kind==='alarm'){TextInput({text:this.time,placeholder:'时间，例如 07:30'}).maxLength(5).onChange((v:string)=>{this.time=v;this.preview='';});TextInput({text:this.title,placeholder:'闹钟标签（可选）'}).maxLength(80).onChange((v:string)=>{this.title=v;this.preview='';})}
    if(this.kind==='note'){TextInput({text:this.title,placeholder:'便签标题'}).maxLength(120).onChange((v:string)=>{this.title=v;this.preview='';});TextArea({text:this.body,placeholder:'便签正文'}).height(130).maxLength(2000).onChange((v:string)=>{this.body=v;this.preview='';})}
    Button('生成任务预览').enabled(this.selected>0||Boolean(this.appName.trim())).onClick(()=>this.prepare())
    if(this.status){Text(this.status).fontSize(12).width('100%')}
    if(this.preview){Text(this.preview).fontSize(14).width('100%').copyOption(CopyOptions.LocalDevice).padding(12).backgroundColor(tone('#EEF1F8',this.darkTheme)).borderRadius(12);Button('填入输入框').onClick(()=>this.onPrepared(this.preview))}
   }.width('100%')}.layoutWeight(1).width('100%')
  }.padding(20).height('100%').width('100%')
 }
}
