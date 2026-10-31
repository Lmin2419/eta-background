// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/screens/memory/AgentMemoryScreen.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
export function memoryFormatBytes(bytes:number):string{return bytes<1024?bytes+' B':bytes<1024*1024?(bytes/1024).toFixed(1)+' KiB':bytes%(1024*1024)===0?bytes/(1024*1024)+' MiB':(bytes/(1024*1024)).toFixed(2)+' MiB';}
