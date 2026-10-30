import { webview } from '@kit.ArkWeb';
import { BrowserHost } from '../platform/BrowserHost';
import { publicHttpsUrl } from '../core/webTools';
@Component
export struct BrowserPanel {
 @State private tabs:string[]=[];
 @State private url:string='https://www.example.com';
 @State private status:string='只控制助手内的网页；触摸页面会使旧观察失效';
 @State private loading:boolean=false;
 private host:BrowserHost=BrowserHost.instance;
 aboutToAppear():void{this.host.enabled=true;}
 aboutToDisappear():void{this.host.enabled=false;this.host.attached=false;this.host.invalidate();}
 private open():void{try{const url=publicHttpsUrl(this.url);this.host.invalidate();this.host.controller.loadUrl(url);}catch(e){this.status=(e as Error).message;}}
 build(){Column({space:6}){
  Row({space:6}){TextInput({text:this.url,placeholder:'HTTPS 网页地址'}).layoutWeight(1).onChange((v:string)=>this.url=v);Button('打开').onClick(()=>this.open());Button('返回').onClick(()=>{this.host.invalidate();if(this.host.controller.accessBackward())this.host.controller.backward();})}
  Scroll(){Row({space:6}){ForEach(this.tabs,(tab:string)=>{Button(tab.replace(/^https:\/\//,'').slice(0,25)).fontSize(10).height(26).onClick(()=>{this.url=tab;this.open();})},(tab:string)=>tab)}}.scrollable(ScrollDirection.Horizontal).width('100%')
  Web({src:'about:blank',controller:this.host.controller}).javaScriptAccess(true).fileAccess(false).domStorageAccess(true).mixedMode(MixedMode.None).height(240).width('100%')
   .onControllerAttached(()=>{this.host.attached=true;})
   .onPageBegin(()=>{this.loading=true;this.host.invalidate();})
   .onPageEnd((event)=>{this.loading=false;this.url=event?.url||this.url;if(this.url.startsWith('https://'))this.tabs=Array.from(new Set(this.tabs.concat([this.url]))).slice(-6);})
   .onTouch(()=>this.host.invalidate())
   .onLoadIntercept((event)=>{const url=event?.data.getRequestUrl()||'';if(url==='about:blank')return false;try{publicHttpsUrl(url);return false;}catch(_){this.status='已阻止非 HTTPS 或外部应用跳转';return true;}})
  Text(this.loading?'页面加载中…':this.status).fontSize(11).width('100%')
 }.width('100%')}
}
