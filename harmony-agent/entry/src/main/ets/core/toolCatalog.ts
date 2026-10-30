import { TOOLS } from './model';
import { HarmonyDevice } from './device';
import { WORKSPACE_TOOLS } from './workspace';
import { WEB_TOOLS } from './webTools';
export function availableDeviceTools(device:HarmonyDevice):Record<string,object>[] {
  return TOOLS.filter(t=>device.supports((t.function as {name:string}).name)) as Record<string,object>[];
}
export function auxiliaryTools(workspace:boolean,memory:boolean,web:boolean,search:boolean):Record<string,object>[]{
  return [...(workspace?WORKSPACE_TOOLS.filter(t=>memory||!(t.function as {name:string}).name.startsWith('memory_')):[]),...(web?WEB_TOOLS.filter(t=>search||(t.function as {name:string}).name!=='web_search'):[])] as Record<string,object>[];
}
export function toolNames(tools:Record<string,object>[]):string[]{return tools.map(t=>(t.function as {name:string}).name);}
