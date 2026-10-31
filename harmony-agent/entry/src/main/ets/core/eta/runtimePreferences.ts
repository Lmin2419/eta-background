// Ported from app/src/main/kotlin/io/github/mangi/eta/config/Prefs.kt,
// data/repository/RuntimeConfigRepository.kt @ 3b1794d. Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
export interface RuntimePreferences { thinkingEnabled: boolean; autoCompactionEnabled: boolean; }
export const defaultRuntimePreferences = (): RuntimePreferences => ({ thinkingEnabled: true, autoCompactionEnabled: true });
export interface RuntimePreferencesStorage { read(): Promise<string>; write(raw: string): Promise<void>; }
export class RuntimePreferencesRepository {
  private value = defaultRuntimePreferences(); private loading: Promise<void> | null = null; private tail: Promise<void> = Promise.resolve();
  constructor(private storage: RuntimePreferencesStorage, private changed: () => void = () => {}) {}
  snapshot(): RuntimePreferences { return { ...this.value }; }
  load(): Promise<void> { if (!this.loading) this.loading = (async () => { const raw = await this.storage.read(); if (raw) { let v; try { v = JSON.parse(raw); } catch (_) { throw Error('运行偏好损坏，原文件已保留'); } if (!v || typeof v.thinkingEnabled !== 'boolean' || typeof v.autoCompactionEnabled !== 'boolean') throw Error('运行偏好无效，原文件已保留'); this.value = { thinkingEnabled: v.thinkingEnabled, autoCompactionEnabled: v.autoCompactionEnabled }; } this.changed(); })().catch(e => { this.loading = null; throw e; }); return this.loading; }
  update(patch: Partial<RuntimePreferences>): Promise<void> { const run = this.tail.then(async () => { await this.load(); const next = { ...this.value, ...patch }; if (typeof next.thinkingEnabled !== 'boolean' || typeof next.autoCompactionEnabled !== 'boolean') throw Error('运行偏好无效'); await this.storage.write(JSON.stringify(next)); this.value = next; this.changed(); }); this.tail = run.catch(() => {}); return run; }
}
