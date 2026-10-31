// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/markdown/SmoothTextReveal.kt @ 3b1794d
// Copyright © 2026 蛮吉 (Mangi-11). PolyForm Noncommercial 1.0.0.
import { graphemeSegments } from "./vendor/unicode-segmenter/grapheme.js";
export function graphemeBoundaries(text: string): number[] {
  const result = [0];
  for (const item of graphemeSegments(text))
    result.push(item.index + item.segment.length);
  return result;
}
export function updateGraphemeBoundaries(
  previousText: string,
  previousBoundaries: number[],
  text: string,
): number[] {
  if (
    !previousText ||
    !text.startsWith(previousText) ||
    !previousBoundaries.length ||
    previousBoundaries[0] !== 0 ||
    previousBoundaries[previousBoundaries.length - 1] !== previousText.length
  )
    return graphemeBoundaries(text);
  if (text === previousText) return previousBoundaries;
  const restartIndex = Math.max(previousBoundaries.length - 2, 0);
  const restart = previousBoundaries[restartIndex];
  return [
    ...previousBoundaries.slice(0, restartIndex),
    ...graphemeBoundaries(text.slice(restart)).map((value) => restart + value),
  ];
}
export function smoothRevealSpeed(totalBacklog: number): number {
  return Math.max(36, totalBacklog / 0.2);
}
export function advanceSmoothReveal(
  current: number,
  target: number,
  elapsedSeconds: number,
  totalBacklog: number,
): number {
  if (current >= target) return target;
  return Math.min(
    current + Math.max(0, smoothRevealSpeed(totalBacklog) * elapsedSeconds),
    target,
  );
}
/** Platform layout identity is opaque: text measurement/drawing belongs to E04b. */
export interface RevealLayout {
  identity: string;
}
export interface RevealNode {
  onRevealDataChanged(): void;
}
class RevealRecord {
  node: RevealNode | null = null;
  text = "";
  layout: RevealLayout | null = null;
  boundaries = [0];
  progress = 0;
  targetCount = 0;
  constructor(readonly key: number) {}
}
export class RevealDrawSnapshot {
  constructor(private record: RevealRecord) {}
  get layout(): RevealLayout | null {
    return this.record.layout;
  }
  get boundaries(): number[] {
    return this.record.boundaries;
  }
  get progress(): number {
    return this.record.progress;
  }
}
export class SmoothTextRevealCoordinator {
  private records = new Map<number, RevealRecord>();
  private paused = false;
  drained = true;
  started = new Set<number>();
  get isAnimationPaused(): boolean {
    return this.paused;
  }
  pauseAnimationsAndCatchUp(): void {
    this.paused = true;
    this.records.forEach((record) => this.complete(record));
    this.updateDrained();
  }
  resumeAnimationsAfterCatchUp(): void {
    this.records.forEach((record) => this.complete(record));
    this.updateDrained();
    this.paused = false;
  }
  retainBlocks(active: Set<number>): void {
    for (const key of this.records.keys())
      if (!active.has(key)) this.records.delete(key);
    this.started = new Set([...this.started].filter((key) => active.has(key)));
    this.updateDrained();
  }
  attach(
    key: number,
    node: RevealNode,
    text: string | null,
    layout: RevealLayout | null,
  ): void {
    const record = this.getRecord(key);
    record.node = node;
    if (text !== null && layout !== null) this.update(record, text, layout);
  }
  detach(key: number, node: RevealNode): void {
    const record = this.records.get(key);
    if (!record || record.node !== node) return;
    record.node = null;
    this.complete(record);
    this.updateDrained();
  }
  updateLayout(
    key: number,
    node: RevealNode | null,
    text: string,
    layout: RevealLayout,
  ): void {
    const record = this.getRecord(key);
    if (node) record.node = node;
    this.update(record, text, layout);
  }
  drawSnapshot(key: number): RevealDrawSnapshot | null {
    const record = this.records.get(key);
    return record?.layout ? new RevealDrawSnapshot(record) : null;
  }
  /** The platform frame clock calls this once per elapsed frame; no per-block timers. */
  advanceFrame(elapsedSeconds: number): void {
    if (this.paused) return;
    const records = [...this.records.values()].sort((a, b) => a.key - b.key);
    const backlog = records.reduce(
      (sum, record) => sum + Math.max(0, record.targetCount - record.progress),
      0,
    );
    if (records.length > 0)
      console.info(
        `RevealDebug n=${records.length} backlog=${backlog} node=${records[0].node !== null} layout=${records[0].layout !== null} p=${records[0].progress} t=${records[0].targetCount} drained=${this.drained}`,
      );
    let remaining = advanceSmoothReveal(
      0,
      backlog,
      Math.max(0, Math.min(0.05, elapsedSeconds)),
      backlog,
    );
    for (const record of records) {
      if (remaining <= 0) break;
      if (!record.node || !record.layout) continue;
      const pending = record.targetCount - record.progress;
      if (pending <= 0) continue;
      const advance = Math.min(remaining, pending);
      record.progress = Math.min(record.progress + advance, record.targetCount);
      remaining -= advance;
      this.started.add(record.key);
      record.node.onRevealDataChanged();
    }
    this.updateDrained();
  }
  private getRecord(key: number): RevealRecord {
    let record = this.records.get(key);
    if (!record) {
      record = new RevealRecord(key);
      this.records.set(key, record);
    }
    return record;
  }
  private update(
    record: RevealRecord,
    text: string,
    layout: RevealLayout,
  ): void {
    if (text !== record.text) {
      record.boundaries = updateGraphemeBoundaries(
        record.text,
        record.boundaries,
        text,
      );
      record.text = text;
      record.targetCount = record.boundaries.length - 1;
      record.progress = Math.min(record.progress, record.targetCount);
    }
    record.layout = layout;
    if (this.paused || !record.node) this.complete(record);
    this.updateDrained();
    record.node?.onRevealDataChanged();
  }
  private complete(record: RevealRecord): void {
    record.progress = record.targetCount;
    if (record.targetCount > 0) this.started.add(record.key);
    record.node?.onRevealDataChanged();
  }
  private updateDrained(): void {
    this.drained = [...this.records.values()].every(
      (record) => record.progress >= record.targetCount,
    );
  }
}
export class SmoothTextRevealState {
  private node: RevealNode | null = null;
  private text: string | null = null;
  private layout: RevealLayout | null = null;
  constructor(
    readonly key: number,
    private coordinator: SmoothTextRevealCoordinator,
  ) {}
  onTextLayout(text: string, layout: RevealLayout): void {
    this.text = text;
    this.layout = layout;
    this.coordinator.updateLayout(this.key, this.node, text, layout);
  }
  attach(node: RevealNode): void {
    this.node = node;
    this.coordinator.attach(this.key, node, this.text, this.layout);
  }
  detach(node: RevealNode): void {
    if (this.node === node) this.node = null;
    this.coordinator.detach(this.key, node);
  }
  drawSnapshot(): RevealDrawSnapshot | null {
    return this.coordinator.drawSnapshot(this.key);
  }
}
