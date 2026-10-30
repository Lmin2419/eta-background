import { DraftRepository } from '../drafts';
import { ModelDraft, copyModelDraft, emptyModelDraft, editModelAddress, modelSnapshot, profileDraft } from '../modelForm';
import { fetchModels, HttpTransport, OpenAIClient } from '../model';
import { ProfileRepository, ModelProfile } from '../profiles';
import { ModelProtocol } from '../protocols';
import { RunControl } from '../types';
import { redactSecrets } from '../conversations';

export interface ProviderEditorState {
  draft: ModelDraft; busy: boolean; working: string; error: string; message: string;
  connection: string; catalog: string[]; selected: string[]; query: string; tab: 'config' | 'models';
}
export type ProviderText = 'name' | 'baseUrl' | 'apiKey' | 'model';
export type ProviderFlag = 'rememberKey' | 'streaming' | 'allowInsecureHttp';
function fresh(): ProviderEditorState { return { draft: emptyModelDraft(), busy: false, working: '', error: '', message: '', connection: '', catalog: [], selected: [], query: '', tab: 'config' }; }

/** Provider edits never change the conversation's model. Results belong to a specific editor epoch. */
export class ProviderEditor {
  state = fresh();
  private epoch = 0;
  private io: HttpTransport | undefined;
  private control: RunControl | undefined;
  private timer: ReturnType<typeof setTimeout> | undefined;
  constructor(private profiles: ProfileRepository, private drafts: DraftRepository,
    private transport: () => HttpTransport, private changed: () => void) {}
  snapshot(): ProviderEditorState { return { ...this.state, draft: copyModelDraft(this.state.draft), catalog: this.state.catalog.slice(), selected: this.state.selected.slice() }; }
  private publish(): void { this.changed(); }
  private cache(): void {
    this.drafts.setProvider(this.state.draft);
    clearTimeout(this.timer);
    this.timer = setTimeout(() => { void this.flush().catch(e => { this.state.error = (e as Error).message; this.publish(); }); }, 400);
  }
  async flush(): Promise<void> { clearTimeout(this.timer); await this.drafts.flush(); }
  cancelRequest(): void {
    this.epoch++; this.control?.stop(); this.io?.cancel(); this.io = undefined; this.control = undefined;
    this.state.busy = false; this.state.working = '';
  }
  async open(id = ''): Promise<void> {
    this.cancelRequest(); const epoch = this.epoch;
    this.state = fresh(); this.state.busy = true; this.state.working = '载入'; this.publish();
    try {
      const profile = this.profiles.document.profiles.find(p => p.id === id);
      if (id && !profile) throw new Error('服务已删除，请重新选择');
      const cached = this.drafts.provider(id);
      let draft = cached || (profile ? profileDraft(profile, '') : emptyModelDraft());
      if (profile && !draft.apiKey && !this.drafts.hasProviderKey(id) && draft.baseUrl === profile.baseUrl) {
        const key = await this.profiles.key(profile);
        draft = { ...draft, apiKey: key };
      }
      if (epoch !== this.epoch) return;
      this.state.draft = copyModelDraft(draft);
    } catch (e) { if (epoch === this.epoch) this.state.error = (e as Error).message; }
    finally { if (epoch === this.epoch) { this.state.busy = false; this.state.working = ''; this.publish(); } }
  }
  text(field: ProviderText, value: string): void {
    if (this.state.working === '保存' || this.state.working === '删除' || this.state.working === '载入' || value === this.state.draft[field]) return;
    this.cancelRequest();
    this.state.draft = field === 'baseUrl' ? editModelAddress(this.state.draft, value) : { ...this.state.draft, [field]: value };
    this.state.catalog = []; this.state.selected = []; this.state.error = ''; this.state.message = ''; this.state.connection = '';
    this.cache(); this.publish();
  }
  flag(field: ProviderFlag, value: boolean): void {
    if (this.state.busy) return;
    this.state.draft = { ...this.state.draft, [field]: value }; this.cache(); this.publish();
  }
  protocol(value: ModelProtocol): void {
    if (this.state.busy) return;
    this.cancelRequest(); this.state.draft = { ...this.state.draft, protocol: value };
    this.state.catalog = []; this.state.connection = ''; this.cache(); this.publish();
  }
  tab(value: 'config' | 'models'): void { this.state.tab = value; this.publish(); }
  search(value: string): void { this.state.query = value; this.publish(); }
  filteredCatalog(): string[] { const q = this.state.query.trim().toLowerCase(); return this.state.catalog.filter(id => id.toLowerCase().includes(q)); }
  toggle(id: string): void {
    if (!this.state.catalog.includes(id)) return;
    this.state.selected = this.state.selected.includes(id) ? this.state.selected.filter(x => x !== id) : this.state.selected.concat(id); this.publish();
  }
  toggleVisible(): void {
    const visible = this.filteredCatalog();
    this.state.selected = visible.every(id => this.state.selected.includes(id)) ? this.state.selected.filter(id => !visible.includes(id)) : [...new Set([...this.state.selected, ...visible])]; this.publish();
  }
  dismissCatalog(): void { this.state.catalog = []; this.state.selected = []; this.publish(); }
  importCatalog(): void {
    const ids = this.state.selected.filter(id => this.state.catalog.includes(id));
    this.state.draft.models = [...new Set([...this.state.draft.models, ...ids])].slice(0, 5000);
    if (!this.state.draft.model) this.state.draft.model = ids[0] || '';
    this.state.message = '已添加 ' + ids.length + ' 个模型，保存服务后生效'; this.state.tab = 'models'; this.cache(); this.dismissCatalog();
  }
  removeModel(id: string): void {
    if (this.state.busy) return;
    this.state.draft.models = this.state.draft.models.filter(m => m !== id);
    if (this.state.draft.model === id) this.state.draft.model = this.state.draft.models[0] || '';
    this.cache(); this.publish();
  }
  addModel(id: string): void {
    const value = id.trim(); if (!value || value.length > 256 || this.state.busy) return;
    this.state.draft.models = [...new Set([...this.state.draft.models, value])].slice(0, 5000);
    if (!this.state.draft.model) this.state.draft.model = value;
    this.cache(); this.publish();
  }
  async probe(testFirst: boolean): Promise<boolean> {
    if (this.state.busy) return false;
    this.cancelRequest(); const epoch = this.epoch, snapshot = modelSnapshot(this.state.draft), io = this.transport(), control = new RunControl();
    this.io = io; this.control = control; this.state.busy = true; this.state.working = '连接'; this.state.error = ''; this.state.message = ''; this.state.connection = ''; this.publish();
    try {
      if (testFirst && snapshot.model.trim()) {
        const reply = await new OpenAIClient(snapshot, io, []).complete([{ role: 'user', content: '请只回复 OK。这是连接测试。' }], control);
        if (epoch !== this.epoch) return false;
        if (reply.finishReason !== 'stop' || !reply.message.content) throw new Error('未收到完整文本回复');
        this.state.connection = '文本连接成功'; this.publish();
      }
      const result = await fetchModels(snapshot, io);
      if (epoch !== this.epoch) return false;
      this.state.draft.metadata = result.metadata;
      this.state.catalog = result.ids.filter(id => !this.state.draft.models.includes(id)); this.state.selected = []; this.state.query = '';
      this.state.message = '已获取 ' + result.ids.length + ' 个模型' + (result.partial ? '（部分目录）' : '') + (this.state.catalog.length ? '，请选择要添加的模型' : '，均已添加');
      if (!this.state.connection) this.state.connection = '模型目录连接成功';
      return this.state.catalog.length > 0;
    } catch (e) {
      if (epoch === this.epoch) this.state.error = (this.state.connection ? this.state.connection + '；目录获取未完成，可手动添加模型。' : '') + redactSecrets((e as Error).message, [snapshot.apiKey]);
      return false;
    } finally {
      io.cancel();
      if (epoch === this.epoch) { this.io = undefined; this.control = undefined; this.state.busy = false; this.state.working = ''; this.publish(); }
    }
  }
  async save(): Promise<boolean> {
    if (this.state.busy) return false;
    const draft = copyModelDraft(this.state.draft), epoch = ++this.epoch;
    this.state.busy = true; this.state.working = '保存'; this.state.error = ''; this.publish();
    try {
      const profile: ModelProfile = { ...draft };
      const saved = await this.profiles.save(profile, draft.apiKey, draft.rememberKey);
      if (epoch !== this.epoch) return false;
      if (!draft.id) this.drafts.removeProvider('');
      this.state.draft = profileDraft(saved, draft.apiKey); this.cache();
      this.state.message = this.profiles.warning || '服务已保存'; return true;
    } catch (e) { if (epoch === this.epoch) this.state.error = redactSecrets((e as Error).message, [draft.apiKey]); return false; }
    finally { if (epoch === this.epoch) { this.state.busy = false; this.state.working = ''; this.publish(); } }
  }
  async remove(): Promise<boolean> {
    if (this.state.busy || !this.state.draft.id) return false;
    this.state.busy = true; this.state.working = '删除'; this.publish();
    try { await this.profiles.remove(this.state.draft.id); this.drafts.removeProvider(this.state.draft.id); await this.flush(); return true; }
    catch (e) { this.state.error = (e as Error).message; return false; }
    finally { this.state.busy = false; this.state.working = ''; this.publish(); }
  }
  close(): void { if (!['保存', '删除'].includes(this.state.working)) this.cancelRequest(); clearTimeout(this.timer); void this.flush().catch(() => {}); }
}
