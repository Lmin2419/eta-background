// Harmony port for app/src/main/kotlin/io/github/mangi/eta/data/repository/AgentMemoryRepository.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11). Uses existing RuntimeDatabase atomic writes; does not switch conversation storage.
import {cryptoFramework} from '@kit.CryptoArchitectureKit';
import {util} from '@kit.ArkTS';
import {AgentMemoryRepository} from '../core/eta/memory/agentMemoryRepository';
import {RuntimeDatabase,DatabaseDocumentStorage} from './RuntimeStorage';
async function memorySha256(content:string):Promise<string>{const digest=cryptoFramework.createMd('SHA256');await digest.update({data:new util.TextEncoder().encodeInto(content)});const bytes=(await digest.digest()).data;let hex='';for(let i=0;i<bytes.length;i++)hex+=bytes[i].toString(16).padStart(2,'0');return hex;}
export function createEtaMemoryRepository(db:RuntimeDatabase):AgentMemoryRepository{return new AgentMemoryRepository(new DatabaseDocumentStorage(db,'eta_memory_md'),memorySha256,new DatabaseDocumentStorage(db,'eta_memory_enabled'));}
