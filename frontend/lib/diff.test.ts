import { describe, expect, it } from "vitest";
import { diff, documentLines, lineChanges, words } from "./diff";
import type { ResumeContent } from "./types";

describe("diff", () => {
  it("finds a minimal edit script", () => {
    const ops = diff("ABCBDAB".split(""), "BDCABA".split(""));
    const kept = ops.filter((o) => o.type === "same").map((o) => o.value).join("");
    expect(kept).toHaveLength(4); // the LCS of the classic example has length 4
    // Replaying the script turns a into b.
    expect(ops.filter((o) => o.type !== "remove").map((o) => o.value).join("")).toBe("BDCABA");
    expect(ops.filter((o) => o.type !== "add").map((o) => o.value).join("")).toBe("ABCBDAB");
  });

  it("handles empty inputs", () => {
    expect(diff([], ["a"])).toEqual([{ type: "add", value: "a" }]);
    expect(diff(["a"], [])).toEqual([{ type: "remove", value: "a" }]);
    expect(diff([], [])).toEqual([]);
  });

  it("splits words keeping whitespace so they re-join exactly", () => {
    expect(words("Built  REST APIs").join("")).toBe("Built  REST APIs");
  });

  it("pairs a removed and an added line into a word-level change", () => {
    const changes = lineChanges(["• Built APIs", "same"], ["• Designed REST APIs", "same"]);
    expect(changes[0].type).toBe("change");
    if (changes[0].type !== "change") throw new Error("expected a change");
    expect(changes[0].after.filter((w) => w.type === "add").map((w) => w.value.trim()).filter(Boolean)).toEqual(["Designed", "REST"]);
    expect(changes[0].before.filter((w) => w.type === "remove").map((w) => w.value)).toEqual(["Built"]);
    expect(changes[1]).toEqual({ type: "same", text: "same" });
  });

  it("flattens a resume into readable lines", () => {
    const resume: ResumeContent = {
      fullName: "Jordan",
      headline: "",
      contactLine: "",
      summary: "",
      sections: [{ heading: "Experience", bullets: [], entries: [{ title: "Dev", meta: "", right: "2025", subtitle: "Acme", subtitleRight: "", bullets: ["Built APIs"] }] }],
      skills: ["Languages: Java"],
    };
    expect(documentLines(resume)).toEqual(["Jordan", "## Experience", "Dev · Acme · 2025", "• Built APIs", "## Skills", "Languages: Java"]);
  });
});
