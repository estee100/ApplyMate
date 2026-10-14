import { afterEach, describe, expect, it, vi } from "vitest";
import { matchBand, parseDay, relativeTime, safeHref, safeNext, toDay } from "./utils";
import { fuzzyScore } from "@/components/layout/fuzzy";

describe("safeNext (post-login redirect)", () => {
  it("allows same-site paths", () => {
    expect(safeNext("/jobs/12?tab=match")).toBe("/jobs/12?tab=match");
  });
  it.each(["https://evil.com", "//evil.com", "/\\evil.com", "javascript:alert(1)", "", null])("refuses %s", (next) => {
    expect(safeNext(next as string | null)).toBe("/dashboard");
  });
});

describe("safeHref", () => {
  it("only renders http(s) links", () => {
    expect(safeHref("https://jobs.example/1")).toBe("https://jobs.example/1");
    expect(safeHref("javascript:alert(1)")).toBeNull();
    expect(safeHref("data:text/html,<script>")).toBeNull();
  });
});

describe("dates", () => {
  afterEach(() => vi.useRealTimers());

  it("parses date-only strings as local days, without a UTC shift", () => {
    const d = parseDay("2026-10-14");
    expect([d.getFullYear(), d.getMonth(), d.getDate()]).toEqual([2026, 9, 14]);
    expect(toDay(d)).toBe("2026-10-14");
  });

  it("never describes a posted date as being in the future", () => {
    // Evening in Mountain time, when "today in UTC" is already tomorrow.
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 9, 13, 23, 0));
    expect(relativeTime("2026-10-14")).toBe("today");
    expect(relativeTime("2026-10-12")).toBe("yesterday");
    expect(relativeTime("2026-10-03")).toBe("10 days ago");
  });
});

describe("matchBand", () => {
  it("labels scores without implying certainty", () => {
    expect(matchBand(85).label).toBe("Strong match");
    expect(matchBand(65).label).toBe("Good match");
    expect(matchBand(45).label).toBe("Partial match");
    expect(matchBand(10).label).toBe("Stretch");
  });
});

describe("fuzzyScore (command palette)", () => {
  it("matches subsequences and prefers word starts", () => {
    expect(fuzzyScore("trk", "Tracking board")).toBeGreaterThan(0);
    expect(fuzzyScore("zzz", "Tracking board")).toBe(0);
    expect(fuzzyScore("log app", "Log an application")).toBeGreaterThan(fuzzyScore("log app", "Analytics blog approvals"));
  });
});
