import type { CoverLetterContent, ResumeContent } from "./types";

export type DiffOp<T> = { type: "same" | "add" | "remove"; value: T };

/**
 * Longest-common-subsequence diff. O(n*m) time and memory, which is fine for a
 * resume (a few hundred lines at most) and keeps the result minimal and stable.
 */
export function diff<T>(a: T[], b: T[], eq: (x: T, y: T) => boolean = (x, y) => x === y): DiffOp<T>[] {
  const n = a.length;
  const m = b.length;
  // lcs[i][j] = length of the LCS of a[i..] and b[j..]
  const lcs: number[][] = Array.from({ length: n + 1 }, () => new Array<number>(m + 1).fill(0));
  for (let i = n - 1; i >= 0; i--) {
    for (let j = m - 1; j >= 0; j--) {
      lcs[i][j] = eq(a[i], b[j]) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
    }
  }
  const out: DiffOp<T>[] = [];
  let i = 0;
  let j = 0;
  while (i < n && j < m) {
    if (eq(a[i], b[j])) {
      out.push({ type: "same", value: a[i] });
      i++;
      j++;
    } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
      out.push({ type: "remove", value: a[i++] });
    } else {
      out.push({ type: "add", value: b[j++] });
    }
  }
  while (i < n) out.push({ type: "remove", value: a[i++] });
  while (j < m) out.push({ type: "add", value: b[j++] });
  return out;
}

/** Words and the whitespace between them, so a word diff re-joins exactly. */
export function words(line: string): string[] {
  return line.match(/\s+|[^\s]+/g) ?? [];
}

export type LineChange =
  | { type: "same" | "add" | "remove"; text: string }
  | { type: "change"; before: DiffOp<string>[]; after: DiffOp<string>[] };

/**
 * Line diff, with removed/added runs paired up and word-diffed, so "Built APIs" ->
 * "Designed REST APIs" shows exactly which words changed rather than two whole lines.
 */
export function lineChanges(before: string[], after: string[]): LineChange[] {
  const ops = diff(before, after);
  const out: LineChange[] = [];
  for (let k = 0; k < ops.length; k++) {
    if (ops[k].type !== "remove") {
      out.push({ type: ops[k].type, text: ops[k].value });
      continue;
    }
    const removed: string[] = [];
    while (k < ops.length && ops[k].type === "remove") removed.push(ops[k++].value);
    const added: string[] = [];
    while (k < ops.length && ops[k].type === "add") added.push(ops[k++].value);
    k--;
    const pairs = Math.min(removed.length, added.length);
    for (let p = 0; p < pairs; p++) {
      const w = diff(words(removed[p]), words(added[p]));
      out.push({ type: "change", before: w.filter((o) => o.type !== "add"), after: w.filter((o) => o.type !== "remove") });
    }
    removed.slice(pairs).forEach((text) => out.push({ type: "remove", text }));
    added.slice(pairs).forEach((text) => out.push({ type: "add", text }));
  }
  return out;
}

/** A document as readable lines, the unit the comparison works in. */
export function documentLines(content: ResumeContent | CoverLetterContent): string[] {
  if ("paragraphs" in content) {
    return [content.greeting, ...content.paragraphs, content.closing].filter(Boolean);
  }
  const lines: string[] = [content.fullName, content.headline, content.contactLine, content.summary].filter(Boolean);
  for (const section of content.sections) {
    lines.push(`## ${section.heading}`);
    for (const e of section.entries) {
      lines.push([e.title, e.meta, e.subtitle, e.right, e.subtitleRight].filter(Boolean).join(" · "));
      e.bullets.filter(Boolean).forEach((b) => lines.push(`• ${b}`));
    }
    section.bullets.filter(Boolean).forEach((b) => lines.push(`• ${b}`));
  }
  if (content.skills.length) {
    lines.push("## Skills");
    content.skills.filter(Boolean).forEach((s) => lines.push(s));
  }
  return lines;
}
