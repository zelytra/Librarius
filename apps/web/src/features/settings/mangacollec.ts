/**
 * Mangacollec's collection (#448): the JSON document its site loads for a signed-in member —
 * flat arrays joined by id (`series`, `editions`, `volumes`) and the member's own
 * `possessions` and `reads`. The reader saves it from their browser; the API reads it.
 */
export function isMangacollecCollection(value: unknown): boolean {
  if (typeof value !== 'object' || value === null) return false;
  const doc = value as Record<string, unknown>;
  return Array.isArray(doc.volumes) && Array.isArray(doc.editions)
    && (Array.isArray(doc.possessions) || Array.isArray(doc.reads));
}
