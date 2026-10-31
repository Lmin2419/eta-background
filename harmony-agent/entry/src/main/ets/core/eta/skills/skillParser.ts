// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/skill/SkillParser.kt, SkillPackageInstaller.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import {skillFail,validSkillId} from './skillTypes';
export function parseSimpleFrontmatter(text:string):Record<string,string>{
 const lines=text.split(/\r\n|\n|\r/),result:Record<string,string>={};let index=0;
 while(index<lines.length){const match=/^([A-Za-z0-9_-]+):\s*(.*)$/.exec(lines[index]);if(!match){index++;continue;}const key=match[1],value=match[2];index++;
  if(/^[>|][+-]?$/.test(value)){const block:string[]=[];while(index<lines.length&&(lines[index].startsWith('  ')||!lines[index].trim()))block.push(lines[index++].trim());
   if(value.startsWith('|'))result[key]=block.join('\n').trim();else{let out='',blanks=0;for(const line of block){if(!line){blanks++;continue;}if(out)out+=blanks?'\n'.repeat(blanks+1):' ';out+=line;blanks=0;}result[key]=out.trim();}continue;}
  if(!value.trim()){const block:string[]=[];while(index<lines.length&&(/^(  |\t)/.test(lines[index])))block.push(lines[index++].trimEnd());result[key]=block.join('\n').trim();continue;}
  const scalar=value.trim();result[key]=scalar.length>=2&&((scalar.startsWith('"')&&scalar.endsWith('"'))||(scalar.startsWith("'")&&scalar.endsWith("'")))?scalar.slice(1,-1):scalar;
 }return result;
}
export function parseSkillText(raw:string):{frontmatter:Record<string,string>;body:string}{const end=raw.indexOf('\n---',3);if(!raw.startsWith('---')||end<=0)return {frontmatter:{},body:raw.trim()};return {frontmatter:parseSimpleFrontmatter(raw.slice(3,end).replace(/^[\r\n]+|[\r\n]+$/g,'')),body:raw.slice(end+4).trim()};}
export function parseIndentedBlock(raw:string):Record<string,string>{const result:Record<string,string>={};for(const line of raw.split(/\r\n|\n|\r/)){const match=/^\s*([A-Za-z0-9_.-]+):\s*(.*)$/.exec(line);if(match)result[match[1]]=match[2].trim().replace(/^"+|"+$/g,'');}return result;}
export function sanitizeSkillId(directory:string,name?:string):string{return (name?.trim()||directory).toLowerCase().replace(/[^a-z0-9-]+/g,'-').replace(/^-+|-+$/g,'')||directory.toLowerCase();}
export function normalizeSkillLookup(value:string):string{return value.trim().toLowerCase().replace(/\\/g,'/').replace(/\/skill\.md$/,'').replace(/\/$/,'').replace(/[\s_-]+/g,'');}
export function validateSkillText(raw:string):{name:string;description:string}{const match=/^---\r?\n([\s\S]*?)(?:^---\r?$)/m.exec(raw);if(!raw.startsWith('---\n')&&!raw.startsWith('---\r\n')||!match)skillFail('INVALID_SKILL');const parsed=parseSimpleFrontmatter(match[1]),name=(parsed.name||'').trim(),description=(parsed.description||'').trim();if(!validSkillId(name)||!description||description.length>1024)skillFail('INVALID_SKILL');return {name,description};}
