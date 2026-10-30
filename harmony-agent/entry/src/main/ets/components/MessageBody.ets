import { pasteboard } from '@kit.BasicServicesKit';
import { tone } from '../core/theme';
import { MessageBlock, InlinePart, messageBlocks, inlineParts } from '../core/markdown';

@Component
export struct MessageBody {
 @StorageLink('darkTheme') private darkTheme:boolean=false;
 private color(value:string):string{return tone(value,this.darkTheme);}
  @Prop content:string='';
  private copy(text:string):void{try{void pasteboard.getSystemPasteboard().setData(pasteboard.createData(pasteboard.MIMETYPE_TEXT_PLAIN,text)).catch(()=>{});}catch(_){}}
  build() {
    Column({space:12}) {
      ForEach(messageBlocks(this.content),(block:MessageBlock)=>{
        if(block.kind==='code') {
          Button('复制代码').height(28).fontSize(11).onClick(()=>this.copy(block.text))
          Scroll(){Text(block.text||' ').fontSize(13).fontFamily('monospace').fontColor(this.color('#344460')).lineHeight(21)
            .copyOption(CopyOptions.LocalDevice).width('100%').padding(14).backgroundColor(this.color('#E9EDF5')).borderRadius(12)}.scrollable(ScrollDirection.Horizontal).width('100%')
        } else if(block.kind==='table'){
          Scroll(){Column(){ForEach(block.rows||[],(row:string[],rowIndex:number)=>{Row(){ForEach(row,(cell:string)=>{Text(cell).fontSize(13).fontWeight(rowIndex===0?FontWeight.Bold:FontWeight.Regular).width(150).padding(10).fontColor(this.color('#263248')).copyOption(CopyOptions.LocalDevice)})}.border({width:0.5,color:this.color('#697589')})})}}.scrollable(ScrollDirection.Horizontal).width('100%')
        } else {
          Text() {
            ForEach(inlineParts(block.text),(part:InlinePart)=>{
              Span(part.text).fontWeight(part.kind==='bold'||block.kind==='heading'?FontWeight.Bold:FontWeight.Regular)
                .fontFamily(part.kind==='code'?'monospace':'sans-serif')
            },(part:InlinePart,index:number)=>index+'-'+part.text)
          }.fontSize(block.kind==='heading'?(block.level===1?23:19):16).lineHeight(block.kind==='heading'?30:26)
            .fontColor(block.kind==='quote'?this.color('#718098'):this.color('#263248')).copyOption(CopyOptions.LocalDevice).width('100%')
        }
      },(block:MessageBlock,index:number)=>index+'-'+block.kind+'-'+block.text+'-'+JSON.stringify(block.rows||[]))
    }.width('100%').alignItems(HorizontalAlign.Start)
  }
}
