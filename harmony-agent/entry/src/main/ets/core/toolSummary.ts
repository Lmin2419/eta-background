export function toolSummary(name:string,raw:string):string{
 let a:any;try{a=JSON.parse(raw);}catch(_){return '参数格式待校验';}if(!a||typeof a!=='object')return '参数格式待校验';
 if(name==='input_text'||name==='browser_input')return '控件 '+String(a.node_id??'')+' · 输入 '+(typeof a.text==='string'?a.text.length:0)+' 字符';
 if(['tap','long_press','drag','swipe'].includes(name))return ['x','y','x1','y1','x2','y2'].filter(k=>typeof a[k]==='number').map(k=>k+'='+a[k]).join('，');
 if(name==='tap_element'||name==='browser_click'||name==='scroll')return '控件 '+String(a.node_id??'自动匹配')+(a.direction?' · '+String(a.direction):'');
 if(name==='launch_app')return String(a.bundle||'')+' / '+String(a.ability||'');
 if(name.startsWith('file_'))return typeof a.name==='string'?a.name.slice(0,128):'工作区';
 if(name.startsWith('mcp_'))return '远程工具；结果需核对';
 if(name.startsWith('browser_')||name==='web_fetch')return typeof a.url==='string'?a.url.split('?')[0].slice(0,160):'助手内网页';
 return '';
}
