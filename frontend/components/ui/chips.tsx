"use client";

import { useState } from "react";
import { X } from "lucide-react";

/** A list of short strings edited as removable chips; Enter or comma adds one. */
export function ChipsInput({
  value,
  onChange,
  placeholder,
  max = 60,
  label,
}: {
  value: string[];
  onChange: (v: string[]) => void;
  placeholder?: string;
  max?: number;
  label: string;
}) {
  const [text, setText] = useState("");

  function add(raw: string) {
    const parts = raw
      .split(",")
      .map((p) => p.trim())
      .filter(Boolean);
    const next = [...value];
    for (const p of parts) {
      if (next.length >= max) break;
      if (!next.some((v) => v.toLowerCase() === p.toLowerCase())) next.push(p);
    }
    onChange(next);
    setText("");
  }

  return (
    <div className="field flex min-h-[46px] flex-wrap items-center gap-1.5 py-2 focus-within:border-primary focus-within:ring-4 focus-within:ring-primary/15">
      {value.map((v) => (
        <span key={v} className="inline-flex items-center gap-1 rounded-lg bg-primary/10 py-0.5 pl-2 pr-1 text-xs font-medium text-primary">
          {v}
          <button type="button" onClick={() => onChange(value.filter((x) => x !== v))} className="rounded p-0.5 hover:bg-primary/15" aria-label={`Remove ${v}`}>
            <X className="h-3 w-3" />
          </button>
        </span>
      ))}
      <input
        value={text}
        aria-label={label}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={(e) => {
          if ((e.key === "Enter" || e.key === ",") && text.trim()) {
            e.preventDefault();
            add(text);
          } else if (e.key === "Backspace" && !text && value.length) {
            onChange(value.slice(0, -1));
          }
        }}
        onBlur={() => text.trim() && add(text)}
        placeholder={value.length === 0 ? placeholder : "Add more"}
        className="min-w-[120px] flex-1 bg-transparent text-sm outline-none placeholder:text-muted-foreground/70"
      />
    </div>
  );
}
