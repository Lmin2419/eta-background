export interface MessageBlock { kind:string; text:string; level:number;rows?:string[][] }
export interface InlinePart { kind:string; text:string }
/** Native text only: no HTML, remote images or navigation side effects in model output. */
export function messageBlocks(content:string):MessageBlock[] {
  const result:MessageBlock[]=[];let paragraph:string[]=[],code:string[]=[],fence=false;
  const flush=():void=>{if(paragraph.length){result.push({kind:'text',text:paragraph.join('\n'),level:0});paragraph=[];}};
  for(const line of content.replace(/\r\n/g,'\n').split('\n')) {
    if(/^\s*```/.test(line)) {
      flush();if(fence){result.push({kind:'code',text:code.join('\n'),level:0});code=[];}fence=!fence;continue;
    }
    if(fence){code.push(line);continue;}
    if(!line.trim()){flush();continue;}
    const heading=/^(#{1,6})\s+(.+)$/.exec(line);
    if(heading){flush();result.push({kind:'heading',text:heading[2],level:heading[1].length});continue;}
    if(/^\s*>\s?/.test(line)){flush();result.push({kind:'quote',text:line.replace(/^\s*>\s?/,''),level:0});continue;}
    if(/^\s*[-*+]\s+/.test(line)){flush();result.push({kind:'text',text:line.replace(/^\s*[-*+]\s+/,'• '),level:0});continue;}
    paragraph.push(line);
  }
  flush();if(fence)result.push({kind:'code',text:code.join('\n'),level:0});
  return result.map(block=>{
    if(block.kind!=='text')return block;const lines=block.text.split('\n');
    if(lines.length>=2&&lines[0].includes('|')&&/^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$/.test(lines[1])){
      const rows=lines.filter((_line,i)=>i!==1).slice(0,101).map(line=>line.trim().replace(/^\||\|$/g,'').split(/(?<!\\)\|/).slice(0,20).map(cell=>cell.trim().replace(/\\\|/g,'|')));
      return {...block,kind:'table',rows};
    }return block;
  });
}
export function inlineParts(text:string):InlinePart[] {
  const parts:InlinePart[]=[];const pattern=/(\*\*([^*\n]+)\*\*|`([^`\n]+)`)/g;let offset=0;let match:RegExpExecArray|null;
  while((match=pattern.exec(text))) {
    if(match.index>offset)parts.push({kind:'text',text:text.slice(offset,match.index)});
    parts.push({kind:match[2]?'bold':'code',text:match[2]||match[3]});offset=match.index+match[0].length;
  }
  if(offset<text.length)parts.push({kind:'text',text:text.slice(offset)});return parts;
}
