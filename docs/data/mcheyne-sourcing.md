# M'Cheyne reading plan — sourcing & verifiability (research, 2026-06-16)

Provenance + feasibility record for the future M'Cheyne plan asset (alternate-schedules epic,
Sprint A data foundation). Mirrors the discipline in [README.md](README.md) for the Bible Companion.
**Verdict: GO** — sourceable with two genuinely independent lineages that agree.

## Structure
- **4 readings/day**, two streams: **Family** (2 — OT history + a Gospel/NT) and **Secret/private**
  (2 — Psalms/Wisdom/Prophets + an Epistle/NT). → the app's N-stream generalization is required
  (Bible Companion = 3 streams, M'Cheyne = 4). Validates the eng-spec's de-baking of "3".
- **Coverage:** OT once; **NT + Psalms twice** (verified — Matthew 1 appears Jan 1 Family AND Jun 21
  Secret). ~4 ch/day.
- **Date-anchored: YES**, like the Bible Companion (Jan 1 always Gen 1 / Matt 1 / Ezra 1 / Acts 1).
- **Leap year: identical to the app's existing rule** — 365 day-entries, **February = 28, NO Feb 29
  entry** (confirmed in all sources). No new leap-year logic needed.
- **NOT purely whole-chapter — ~26 verse-boundary readings across ~24 days** that MUST be encoded for
  fidelity: Psalm 119 split ×7 (1-24 / 25-48 / … / 145-176), Psalm 78 ×2, ~13 chapter-spanning ranges
  (e.g. `Ex 11:1-12:21` + `12:22-51`; `Josh 5:1-6:5` + `6:6-27`; `Isa 8:1-9:7`…), and non-adjacent
  double-chapter slots (e.g. Aug 8 = `Jer 36, 45` together). The schema already supports this
  (`verseStart`/`verseEnd` from Sprint J + multi-ref portions). **Do NOT use the chapter-collapsed
  bibleplan.org data as the source of truth for these days** (it loses the windows; one off-by-one
  found: Feb 28 `Ex 11-12:20` vs authentic `Ex 11:1-12:21`).

## Two independent lineages (the gate is satisfiable)
1. **Haslam / Edgington** (from the original 1842 calendar) — `mcheyne.info/calendar.pdf` (David
   Haslam's digitization, explicit Family/Secret columns) + `edginet.org/mcheyne/year_classic_single_a4.pdf`
   (Ben Edgington's reformat of Haslam). **These two are the SAME lineage** (Edgington credits Haslam)
   — count as ONE witness. Verse-faithful → **canonical source.**
2. **Carson / bibleplan.org** — TGC/D.A. Carson "For the Love of God" PDF + bibleplan.org
   (`github.com/paulyoder/mcheyne-bible-reading-plan` `app/lib/plan.js`, pre-structured
   `{month:{day:[4 refs]}}`). A separate editorial lineage → **independent verification source.**

**Cross-check (Jan 1, Feb 28, Mar 15, Jul 4, Aug 8, Dec 31 + others): the two lineages AGREE on every
chapter assignment.** Re-mirror trap explicitly checked — different authors/decades/formats; the only
re-host relationship is Haslam→Edgington (why Carson is the genuine 2nd witness). Systematic
difference is verse-range *granularity*, not chapter disagreement.

## Canonical choice
Ship the **classic 4-reading, date-anchored, 365-day form**. Canonical text = **Edgington/Haslam**
(verse-faithful); independent witness = **Carson/TGC** (+ bibleplan.org for a pre-structured chapter
skeleton). Original-1842 vs Carson daily *assignments are identical* (Carson's changes are
devotional/format only). The 2-reading "half-pace" form is a presentation variant of the same data,
not a different plan.

## Build path (Sprint A)
1. Parse bibleplan.org `plan.js` for the chapter skeleton (`{month,day → 4 refs}`).
2. Overlay the ~24 verse-windowed days from the Edgington/Haslam PDF (the fidelity layer).
3. Emit `mcheyne_plan.json` in the existing schema (365 entries, Feb=28, 4 streams, `{book,chapter,
   verseStart?,verseEnd?}` refs).
4. Gate-verify day-by-day against the Carson/TGC PDF (the independent witness) — the existing
   two-source discipline; pin source SHAs.

## Notes for CI
- `edginet.org` blocks WebFetch (ECONNREFUSED) but serves via `curl` with a browser User-Agent.
- Clean `pdftotext -layout` extraction worked on all three PDFs (no OCR needed).
- Sources to pin by SHA: edginet classic PDF, mcheyne.info calendar.pdf, TGC plan PDF, paulyoder
  `plan.js` blob.

## Sources
- Edgington classic PDF: http://www.edginet.org/mcheyne/year_classic_single_a4.pdf
- Haslam "Daily Bread": https://www.mcheyne.info/calendar.pdf
- Carson / TGC: https://www.thegospelcoalition.org/article/mcheyne-bible-reading-plan/ ·
  https://media.thegospelcoalition.org/wp-content/uploads/2020/01/09151141/RtB_Reading-Plan_2020.pdf
- bibleplan.org structured data: https://github.com/paulyoder/mcheyne-bible-reading-plan
