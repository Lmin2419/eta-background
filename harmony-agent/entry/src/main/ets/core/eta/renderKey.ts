/**
 * ArkUI ForEach / LazyForEach identity helpers.
 *
 * ArkUI reuses the child whose key is unchanged and does not run its item builder again, so a child
 * keyed only by a stable id never receives new content (Compose, where Eta comes from, recomposes it).
 * Two ways to stay correct:
 *  - renderKey: put a fingerprint of everything the row draws into the key, so a changed row is rebuilt.
 *    Use it for stateless rows (text runs, table cells, read-only list rows).
 *  - syncSlots: keep one observed slot per stable id and replace slot.value in place; the child takes the
 *    slot with @ObjectLink and re-renders without being recreated. Use it when the child owns state that
 *    must survive an update (reveal progress, expansion, focus, text input).
 */

/** FNV-1a over the UTF-16 code units of the JSON form; stable across runs, short enough for a key. */
export function fingerprint(value: unknown): string {
  const text = JSON.stringify(value) ?? 'undefined';
  let hash = 0x811c9dc5;
  for (let i = 0; i < text.length; i++) {
    hash ^= text.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193) >>> 0;
  }
  return hash.toString(36) + '.' + text.length.toString(36);
}

/** A ForEach key that changes whenever `value` (what the row draws) changes. */
export function renderKey(id: string | number, value: unknown): string {
  return String(id) + '#' + fingerprint(value);
}

export interface Slot<T> { value: T; }

/**
 * Reuse the slot with the same id, replacing its value only when the content differs (so unchanged rows
 * are not re-rendered); create slots for new ids; drop slots whose id disappeared. Order follows `values`.
 */
export function syncSlots<T, S extends Slot<T>>(slots: S[], values: T[], idOf: (value: T) => string, create: (value: T) => S): S[] {
  const byId = new Map<string, S>();
  for (const slot of slots) byId.set(idOf(slot.value), slot);
  return values.map(value => {
    const slot = byId.get(idOf(value));
    if (!slot) return create(value);
    byId.delete(idOf(value));
    if (slot.value !== value && JSON.stringify(slot.value) !== JSON.stringify(value)) slot.value = value;
    return slot;
  });
}
