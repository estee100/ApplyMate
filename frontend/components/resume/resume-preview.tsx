import type { CoverLetterContent, ResumeContent } from "@/lib/types";

/**
 * A classic one-page technical resume. Always black on white, in both themes, because
 * it is what gets printed to PDF. Serif type and tight spacing, the format most ATS
 * parsers and recruiters expect.
 */
export function ResumePreview({ content }: { content: ResumeContent }) {
  const contact = content.contactLine
    .split("|")
    .map((p) => p.trim())
    .filter(Boolean);

  return (
    <div className="print-page mx-auto min-h-[600px] w-full max-w-[816px] bg-white px-[6%] py-[5%] font-[Georgia,'Times_New_Roman',serif] text-[11px] leading-[1.35] text-black shadow-lift ring-1 ring-black/5 print:max-w-none print:px-[0.55in] print:py-[0.45in]">
      <header className="text-center">
        <h1 className="text-[22px] font-bold tracking-wide">{content.fullName || "Your Name"}</h1>
        {content.headline && <p className="mt-0.5 text-[11.5px] italic">{content.headline}</p>}
        {contact.length > 0 && <p className="mt-1 text-[10.5px]">{contact.join("  |  ")}</p>}
      </header>

      {content.summary && <p className="mt-3 text-justify">{content.summary}</p>}

      {content.sections.map((section, si) => (
        <section key={si} className="mt-3">
          <h2 className="border-b border-black pb-0.5 text-[12px] font-bold uppercase tracking-[0.08em]">{section.heading}</h2>
          {section.entries.map((e, ei) => (
            <div key={ei} className="mt-1.5 break-inside-avoid">
              <div className="flex items-baseline justify-between gap-3">
                <p>
                  <span className="font-bold">{e.title}</span>
                  {e.meta && <span className="italic"> | {e.meta}</span>}
                </p>
                <span className="shrink-0">{e.right}</span>
              </div>
              {(e.subtitle || e.subtitleRight) && (
                <div className="flex items-baseline justify-between gap-3 italic">
                  <span>{e.subtitle}</span>
                  <span className="shrink-0">{e.subtitleRight}</span>
                </div>
              )}
              {e.bullets.filter(Boolean).length > 0 && (
                <ul className="ml-4 mt-0.5 list-disc space-y-0.5">
                  {e.bullets.filter(Boolean).map((b, bi) => (
                    <li key={bi}>{b}</li>
                  ))}
                </ul>
              )}
            </div>
          ))}
          {section.entries.length === 0 && section.bullets.filter(Boolean).length > 0 && (
            <ul className="ml-4 mt-1 list-disc space-y-0.5">
              {section.bullets.filter(Boolean).map((b, bi) => (
                <li key={bi}>{b}</li>
              ))}
            </ul>
          )}
        </section>
      ))}

      {content.skills.filter(Boolean).length > 0 && (
        <section className="mt-3">
          <h2 className="border-b border-black pb-0.5 text-[12px] font-bold uppercase tracking-[0.08em]">Technical Skills</h2>
          <div className="mt-1 space-y-0.5">
            {content.skills.filter(Boolean).map((line, i) => {
              const at = line.indexOf(":");
              return at > 0 ? (
                <p key={i}>
                  <span className="font-bold">{line.slice(0, at)}:</span>
                  {line.slice(at + 1)}
                </p>
              ) : (
                <p key={i}>{line}</p>
              );
            })}
          </div>
        </section>
      )}
    </div>
  );
}

export function CoverLetterPreview({ content, author, company }: { content: CoverLetterContent; author: string | null; company: string }) {
  const date = new Date().toLocaleDateString(undefined, { year: "numeric", month: "long", day: "numeric" });
  return (
    <div className="print-page mx-auto min-h-[600px] w-full max-w-[816px] bg-white px-[9%] py-[8%] font-[Georgia,'Times_New_Roman',serif] text-[12px] leading-[1.6] text-black shadow-lift ring-1 ring-black/5 print:max-w-none print:px-[1in] print:py-[0.9in]">
      {author && <p className="text-[15px] font-bold">{author}</p>}
      <p className="mt-6">{date}</p>
      <p className="mt-1">{company}</p>
      <p className="mt-6">{content.greeting}</p>
      {content.paragraphs.filter(Boolean).map((p, i) => (
        <p key={i} className="mt-4 text-justify">
          {p}
        </p>
      ))}
      <p className="mt-6">{content.closing}</p>
      {author && <p className="mt-1">{author}</p>}
    </div>
  );
}
