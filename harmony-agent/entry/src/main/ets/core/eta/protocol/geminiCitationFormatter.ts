// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/model/GeminiCitationFormatter.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import {JsonObject,object,string} from './providerTypes';import {formatCitations} from './citationFormatter';
export function geminiCitations(text:string,annotations:unknown[]):string{
 if(!annotations.length)return text;const boundaries=new Map<number,number>([[0,0]]);let bytes=0,chars=0;
 for(const char of text){const n=char.codePointAt(0)!;bytes+=n<=0x7f?1:n<=0x7ff?2:n<=0xffff?3:4;chars+=char.length;boundaries.set(bytes,chars);}
 return formatCitations(text,annotations.map(object).filter((v):v is JsonObject=>v?.type==='url_citation').map(a=>{
  const offset=(key:string):number|null=>{const n=a[key];return typeof n==='number'&&Number.isInteger(n)&&n>=0&&n<=bytes?boundaries.get(n)??null:null;};return {start:offset('start_index'),end:offset('end_index'),url:string(a.url),title:string(a.title)};
 }));
}
