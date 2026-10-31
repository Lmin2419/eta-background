// Ported from app/src/main/kotlin/io/github/mangi/eta/agent/skill/SkillRuntime.kt (SkillRegistryStore) @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import {object} from '../protocol/providerTypes';
import {SkillRegistryEntry,validSkillId} from './skillTypes';
export function decodeSkillRegistry(raw:string):Record<string,SkillRegistryEntry>{if(!raw)return {};const parsed=object(JSON.parse(raw));if(!parsed)throw new Error('SKILL_REGISTRY_INVALID');const result:Record<string,SkillRegistryEntry>={};for(const [key,value] of Object.entries(parsed)){const s=object(value);if(!validSkillId(key)||!s||typeof s.enabled!=='boolean'||(s.source!=='builtin'&&s.source!=='user')||(s.installState!=='installed'&&s.installState!=='removed_builtin')||(s.source==='user'&&s.installState!=='installed'))throw new Error('SKILL_REGISTRY_INVALID');result[key]={enabled:s.enabled,source:s.source,installState:s.installState};}return result;}
