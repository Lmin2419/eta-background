// Harmony 0.9 adapter for app/src/main/kotlin/io/github/mangi/eta/data/db/ConversationEntities.kt,
// HistoryPayloadMigration.kt and ui/app/AgentConversationStore.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import { ConversationDocument, Conversation, ChatTurn } from '../../conversations';
import { conversationRow, messageRow, MessageRow } from './conversationDao';
import { putConversation, putMessage } from './agentConversationStore';
import { storeText, restoreText } from './chunkedText';
import { conversationMessage } from '../model/conversation';
import { encodeTranscript } from './conversationCodec';
import { LegacySnapshotSource, LegacySnapshot, MigrationDatabase, MigrationDao, MigrationFailure } from './migrationPorts';
export const LEGACY_MIGRATION_KEY = 'harmony-0.9-to-eta-v1';
export interface LegacyModelResolver { resolve(profileId: string, preferredModel: string): string | null; }
export interface MigrationManifest {
  version: 1; sourceHash: string; evidenceHash: string; conversations: number; messages: number;
  selectedId: string; bodies: { id: string; hash: string; imagesHash: string }[];
  bindings: { id: string; modelId: string | null }[];
}
export interface MigrationResult { status: 'verified' | 'already_verified'; manifest: MigrationManifest; }
function invalid(): never { throw new MigrationFailure('source_invalid'); }
function record(v: unknown): Record<string, unknown> { if (!v || typeof v !== 'object' || Array.isArray(v)) invalid(); return v as Record<string, unknown>; }
function string(v: unknown): string { if (typeof v !== 'string') invalid(); return v; }
function time(v: unknown): number { if (typeof v !== 'number' || !Number.isFinite(v) || v < 0) invalid(); return v; }
function mode(v: unknown): void { if (v !== 'chat' && v !== 'device') invalid(); }
function id(v: unknown, seen: Set<string>): void { const key = string(v); if (!key || seen.has(key)) invalid(); seen.add(key); }
function optionalStrings(v: Record<string, unknown>, names: string[]): void { for (const name of names) if (v[name] !== undefined) string(v[name]); }
/** Validation does not clamp lengths, discard unknown fields, fix selection, or mutate the source. */
export function decodeLegacySnapshot(raw: string): ConversationDocument {
  let value: Record<string, unknown>;
  try { value = record(JSON.parse(raw)); } catch (_) { return invalid(); }
  if (value.version !== 1 || !Array.isArray(value.conversations)) invalid();
  const ids = new Set<string>();
  for (const item of value.conversations) {
    const c = record(item); id(c.id, ids); string(c.title); string(c.profileId); time(c.createdAt); time(c.updatedAt); mode(c.mode);
    optionalStrings(c, ['preferredModel']); if (!Array.isArray(c.turns)) invalid(); const turns = new Set<string>();
    for (const item of c.turns) {
      const t = record(item); id(t.id, turns); string(t.request); string(t.reply); string(t.notice); string(t.modelLabel); mode(t.mode); time(t.startedAt);
      if (!['running', 'completed', 'stopped', 'failed', 'interrupted'].includes(String(t.status))) invalid();
      if (t.endedAt !== undefined) time(t.endedAt); optionalStrings(t, ['reasoning', 'revisionOf', 'contextNote']);
      if (t.attachments !== undefined) { if (!Array.isArray(t.attachments)) invalid(); for (const a of t.attachments) string(a); }
      if (t.usage !== undefined) { const u = record(t.usage); for (const k of ['input', 'output', 'total']) time(u[k]); }
      if (!Array.isArray(t.steps)) invalid(); const steps = new Set<string>();
      for (const item of t.steps) {
        const s = record(item); id(s.id, steps); string(s.tool); time(s.startedAt);
        if (!['running', 'completed', 'failed', 'interrupted'].includes(String(s.status))) invalid();
        if (s.endedAt !== undefined) time(s.endedAt); optionalStrings(s, ['detail']);
      }
    }
  }
  const selected = string(value.selectedId);
  if (ids.size ? !ids.has(selected) : selected !== '') invalid();
  return value as unknown as ConversationDocument;
}
/** Old profile IDs remain authoritative. A resolver supplies today's stable model ID; raw bindings are also retained. */
export function legacyRows(c: Conversation): MessageRow[] {
  const rows: MessageRow[] = [];
  const append = (turn: ChatTurn, suffix: string, type: string, content: string): MessageRow => {
    // Conversation qualification prevents old per-conversation turn IDs from colliding in Eta's global PK.
    const row = messageRow(JSON.stringify([c.id, turn.id, suffix]), c.id, rows.length, type, content); rows.push(row); return row;
  };
  for (const t of c.turns) {
    append(t, 'user', 'user', t.request).imagesJson = JSON.stringify(t.attachments || []);
    if (t.reasoning) append(t, 'thinking', 'thinking', t.reasoning);
    for (const s of t.steps) {
      const row = append(t, 'step:' + s.id, 'tool', ''); row.toolName = s.tool;
      row.toolStatus = s.status === 'completed' ? 'Success' : s.status === 'failed' ? 'Failed' : 'Unknown'; row.resultSummary = s.detail || null;
    }
    if (t.reply) { const row = append(t, 'assistant', 'assistant', t.reply); row.renderMarkdown = true; row.inputTokens = t.usage?.input ?? null; row.outputTokens = t.usage?.output ?? null; }
    const notice = t.status === 'running' || t.status === 'interrupted' ? 'interrupted' : t.status === 'stopped' ? 'stopped' : t.status === 'failed' ? 'runtime_failed' : null;
    if (notice) { const row = append(t, 'notice', 'system_notice', notice); row.resultSummary = t.notice || null; }
  }
  return rows;
}
async function writeRecord(dao: MigrationDao, key: string, raw: string): Promise<void> { await dao.putMigrationRecord(key, await storeText(dao, 'migration_records', key, 'value', raw)); }
export async function readMigrationRecord(dao: MigrationDao, key: string): Promise<string | null> { const raw = await dao.migrationRecord(key); return raw === null ? null : restoreText(dao, 'migration_records', key, 'value', raw); }
/** Candidate conversion only. This class cannot switch the app, start a task, or write to the legacy store. */
export class LegacyConversationMigration {
  constructor(private target: MigrationDatabase, private source: LegacySnapshotSource, private hash: (text: string) => Promise<string>, private models: LegacyModelResolver, private now: () => number = Date.now) {}
  private async fingerprint(source: LegacySnapshot): Promise<[string, string]> { return [await this.hash(source.document), await this.hash(source.evidence)]; }
  async run(): Promise<MigrationResult> {
    try {
      const source = await this.source.read(), document = decodeLegacySnapshot(source.document), [sourceHash, evidenceHash] = await this.fingerprint(source);
      const rows = document.conversations.flatMap(legacyRows), manifest: MigrationManifest = { version: 1, sourceHash, evidenceHash,
        conversations: document.conversations.length, messages: rows.length, selectedId: document.selectedId, bodies: [],
        bindings: document.conversations.map(c => ({ id: c.id, modelId: this.models.resolve(c.profileId, c.preferredModel || '') })) };
      for (const row of rows) manifest.bodies.push({ id: row.id, hash: await this.hash(JSON.stringify(row.content)), imagesHash: await this.hash(row.imagesJson) });
      return await this.target.migrationTransaction(async dao => {
        const marker = await readMigrationRecord(dao, LEGACY_MIGRATION_KEY);
        if (marker !== null) {
          let previous: MigrationManifest; try { previous = JSON.parse(marker); } catch (_) { throw new MigrationFailure('verification_failed'); }
          if (previous.version !== 1 || previous.sourceHash !== sourceHash || previous.evidenceHash !== evidenceHash) throw new MigrationFailure('source_changed');
          // A changing model catalog must not silently rebind an already verified snapshot.
          if (JSON.stringify(previous) !== JSON.stringify(manifest)) throw new MigrationFailure('verification_failed');
          await this.verify(dao, source, document, rows, manifest); await this.stillCurrent(sourceHash, evidenceHash);
          return { status: 'already_verified', manifest };
        }
        if ((await dao.conversationIdentities()).length || (await dao.searchRowsPage(1, 0)).length || await dao.selectedId() !== null) throw new MigrationFailure('target_occupied');
        await writeRecord(dao, LEGACY_MIGRATION_KEY + ':source', source.document);
        await writeRecord(dao, LEGACY_MIGRATION_KEY + ':evidence', source.evidence);
        for (const c of document.conversations) {
          await putConversation(dao, { ...conversationRow(c.id, c.title, c.updatedAt), createdAt: c.createdAt, modelId: manifest.bindings.find(b => b.id === c.id)!.modelId });
          // Preserve every 0.9 field (mode, profile ID, turns, timestamps, statuses, usage, edits, context and attachments).
          await writeRecord(dao, LEGACY_MIGRATION_KEY + ':conversation:' + c.id, JSON.stringify(c));
          const history = c.turns.flatMap(t => [conversationMessage('user', t.request), ...(t.reply ? [conversationMessage('assistant', t.reply)] : [])]);
          await dao.putCheckpoint({ conversationId: c.id, historyJson: await storeText(dao, 'conversation_context_checkpoints', c.id, 'history', encodeTranscript(history)), journalJson: '[]' });
        }
        for (const row of rows) await putMessage(dao, row);
        await dao.setSelectedId(document.selectedId || null);
        await this.verify(dao, source, document, rows, manifest); await this.stillCurrent(sourceHash, evidenceHash);
        // Marker is last, in the same transaction. Failure, including COMMIT failure, cannot expose success.
        await writeRecord(dao, LEGACY_MIGRATION_KEY, JSON.stringify(manifest));
        return { status: 'verified', manifest };
      });
    } catch (error) {
      const failure = error instanceof MigrationFailure ? error : new MigrationFailure('write_failed');
      // A separate transaction records only a fixed error code and a timestamp, never SQL or content.
      try { await this.target.migrationTransaction(dao => dao.diagnostic(failure.code, this.now())); } catch (_) { /* Target unavailable; the redacted failure still reaches the caller. */ }
      throw failure;
    }
  }
  private async stillCurrent(sourceHash: string, evidenceHash: string): Promise<void> {
    const fresh = await this.fingerprint(await this.source.read());
    if (fresh[0] !== sourceHash || fresh[1] !== evidenceHash) throw new MigrationFailure('source_changed');
  }
  private async verify(dao: MigrationDao, source: LegacySnapshot, document: ConversationDocument, expected: MessageRow[], manifest: MigrationManifest): Promise<void> {
    const fail = (): never => { throw new MigrationFailure('verification_failed'); };
    const bodies = new Map(manifest.bodies.map(body => [body.id, body])), expectedRows = new Map(expected.map(row => [row.id, row]));
    const counts = new Map<string, number>(); for (const row of expected) counts.set(row.conversationId, (counts.get(row.conversationId) || 0) + 1);
    if ((await dao.conversationIdentities()).length !== manifest.conversations || (await dao.selectedId() || '') !== document.selectedId) fail();
    if (await readMigrationRecord(dao, LEGACY_MIGRATION_KEY + ':source') !== source.document || await readMigrationRecord(dao, LEGACY_MIGRATION_KEY + ':evidence') !== source.evidence) fail();
    for (const c of document.conversations) {
      const meta = await dao.metadataRow(c.id), cp = await dao.checkpointRow(c.id);
      if (!meta || meta.title !== c.title || meta.createdAt !== c.createdAt || meta.updatedAt !== c.updatedAt || meta.modelId !== manifest.bindings.find(b => b.id === c.id)?.modelId) fail();
      if (await readMigrationRecord(dao, LEGACY_MIGRATION_KEY + ':conversation:' + c.id) !== JSON.stringify(c)) fail();
      if (!cp || await restoreText(dao, 'conversation_context_checkpoints', c.id, 'history', cp.historyJson) !== encodeTranscript(c.turns.flatMap(t => [conversationMessage('user', t.request), ...(t.reply ? [conversationMessage('assistant', t.reply)] : [])]))) fail();
      if (await dao.messageCount(c.id) !== (counts.get(c.id) || 0)) fail();
    }
    let total = 0;
    for (let offset = 0;; offset += 128) {
      const rows = await dao.searchRowsPage(128, offset); total += rows.length;
      for (const row of rows) {
        const body = bodies.get(row.id), before = expectedRows.get(row.id);
        const content = await restoreText(dao, 'conversation_messages', row.id, 'content', row.content), images = await restoreText(dao, 'conversation_messages', row.id, 'images', row.imagesJson);
        if (!body || !before || await this.hash(JSON.stringify(content)) !== body.hash || await this.hash(images) !== body.imagesHash) fail();
        if (JSON.stringify({ ...row, content, imagesJson: images }) !== JSON.stringify(before)) fail();
      }
      if (rows.length < 128) break;
    }
    if (total !== manifest.messages) fail();
  }
}
