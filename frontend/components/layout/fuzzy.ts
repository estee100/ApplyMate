/**
 * Subsequence match with a score: consecutive and word-start hits score higher, so
 * "trk" finds "Tracking" and "lg app" finds "Log an application".
 */
export function fuzzyScore(query: string, text: string): number {
  const q = query.toLowerCase().replace(/\s+/g, "");
  const t = text.toLowerCase();
  if (!q) return 1;
  let score = 0;
  let ti = 0;
  let streak = 0;
  for (const ch of q) {
    const found = t.indexOf(ch, ti);
    if (found === -1) return 0;
    streak = found === ti ? streak + 1 : 1;
    const wordStart = found === 0 || /\W/.test(t[found - 1]);
    score += 1 + streak + (wordStart ? 3 : 0);
    ti = found + 1;
  }
  return score / (t.length + 1);
}
