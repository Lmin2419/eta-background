import { image } from '@kit.ImageKit';
import { util } from '@kit.ArkTS';
import { webview } from '@kit.ArkWeb';
import { BrowserPort, BrowserTools } from '../core/browser';
export class BrowserHost implements BrowserPort {
 static instance:BrowserHost=new BrowserHost();
 controller:webview.WebviewController=new webview.WebviewController();
 constructor(){try{webview.WebviewController.enableWholeWebPageDrawing();}catch(_){}}
 attached=false;enabled=false;owner='';private foreground=true;
 setForeground(value:boolean):void{this.foreground=value;this.invalidate();}
 tools:BrowserTools=new BrowserTools(this);
 ready():boolean{return this.foreground&&this.attached&&this.enabled;}
 acquire(id:string):boolean{if(!this.ready()||(this.owner&&this.owner!==id))return false;this.owner=id;return true;}
 release(id:string):void{if(this.owner===id){this.owner='';this.tools.invalidate();}}
 invalidate():void{this.tools.invalidate();}
 async navigate(url:string):Promise<void>{if(!this.ready())throw new Error('浏览器已关闭');this.tools.invalidate();this.controller.loadUrl(url);}
 async snapshot():Promise<string>{
  if(!this.ready())throw new Error('浏览器不可用');
  const pixel=await new Promise<image.PixelMap>((resolve,reject)=>{let settled=false;const timer=setTimeout(()=>{settled=true;reject(new Error('网页截图超时'));},10000);this.controller.webPageSnapshot({id:util.generateRandomUUID(),size:{width:1024,height:1024}},(error,result)=>{clearTimeout(timer);if(settled){void result?.imagePixelMap?.release();return;}settled=true;if(error||!result.status||!result.imagePixelMap){void result?.imagePixelMap?.release();reject(new Error('网页截图不可用'));}else resolve(result.imagePixelMap);});});
  const packer=image.createImagePacker();try{const bytes=await packer.packing(pixel,{format:'image/png',quality:90});if(bytes.byteLength>4_000_000)throw new Error('网页截图超过上限');return 'data:image/png;base64,'+new util.Base64Helper().encodeToStringSync(new Uint8Array(bytes));}finally{await packer.release();await pixel.release();}
 }
 async script(code:string):Promise<string>{if(!this.ready())throw new Error('浏览器不可用');const result=await this.controller.runJavaScript(code);if(!this.ready())throw new Error('浏览器已被关闭');return result;}
}
