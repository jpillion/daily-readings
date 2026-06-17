#!/usr/bin/env python3
"""SA-T8: build the M'Cheyne reading plan asset (alternate-schedules Sprint A).

Reproducible, byte-deterministic extraction of the classic 4-stream, date-anchored, 365-day
M'Cheyne calendar into app/src/main/assets/plans/mcheyne/plan.json (schemaVersion 3).

Sources (docs/data/mcheyne-sourcing.md, docs/data/README.md):
  CANONICAL (verse-faithful): edginet "year_classic_single_a4.pdf" (Edgington/Haslam lineage).
    The asset is built from THIS — it carries the ~24 verse windows faithfully.
  We deliberately do NOT use bibleplan.org plan.js for the verse-windowed days: its data is
    chapter-collapsed and wrong there (Feb 28 'Ex 11-12:20' vs faithful 'Ex 11:1-12:21';
    Mar 1 'Ex 12:21-50' vs faithful 'Ex 12:22-51').

The INDEPENDENT second witness (Carson/TGC RtB_Reading-Plan_2020.pdf) is parsed by the GATE
(McheynePlanVerificationTest), not here — so the two lineages stay genuinely independent.

Streams (4, classic M'Cheyne — Family x2, Personal x2; TGC/Carson confirm the column grouping):
  1 Family — OT history          (Genesis ...)
  2 Family — Gospels/NT          (Matthew ...)
  3 Personal — Psalms/Wisdom/Prophets (Ezra/Psalms ...)
  4 Personal — Epistles/NT         (Acts ...)

Usage: build_mcheyne_plan.py [path-to-edginet.pdf]   (fetches the URL if no path given)
Requires: pdftotext (poppler) for PDF extraction; Python stdlib only otherwise.
"""
import json, os, re, subprocess, sys, tempfile, urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "plans", "mcheyne", "plan.json")
EDGINET_URL = "http://www.edginet.org/mcheyne/year_classic_single_a4.pdf"

DAYS_PER_MONTH = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]

# M'Cheyne abbreviation -> canonical BookCatalog name (book_catalog.csv). The edginet/Carson
# abbreviations; both lineages share most. Numbered-book + epistle space variants are normalized.
BOOK = {
    "Gn": "Genesis", "Ex": "Exodus", "Lv": "Leviticus", "Nu": "Numbers", "Dt": "Deuteronomy",
    "Jsh": "Joshua", "Jdg": "Judges", "Ruth": "Ruth", "1Sa": "1 Samuel", "2Sa": "2 Samuel",
    "1Ki": "1 Kings", "2Ki": "2 Kings", "1Ch": "1 Chronicles", "2Ch": "2 Chronicles",
    "Ezra": "Ezra", "Neh": "Nehemiah", "Esth": "Esther", "Job": "Job", "Ps": "Psalms",
    "Pr": "Proverbs", "Eccl": "Ecclesiastes", "Song": "Song of Solomon", "Is": "Isaiah",
    "Jer": "Jeremiah", "La": "Lamentations", "Eze": "Ezekiel", "Dn": "Daniel", "Ho": "Hosea",
    "Joel": "Joel", "Am": "Amos", "Obadiah": "Obadiah", "Jonah": "Jonah", "Mic": "Micah",
    "Nah": "Nahum", "Hab": "Habakkuk", "Zph": "Zephaniah", "Hag": "Haggai", "Zech": "Zechariah",
    "Mal": "Malachi", "Mt": "Matthew", "Mk": "Mark", "Lk": "Luke", "Jn": "John", "Act": "Acts",
    "Ro": "Romans", "1Co": "1 Corinthians", "2Co": "2 Corinthians", "Gal": "Galatians",
    "Eph": "Ephesians", "Phil": "Philippians", "Col": "Colossians", "1Th": "1 Thessalonians",
    "2Th": "2 Thessalonians", "1Ti": "1 Timothy", "2Ti": "2 Timothy", "Tit": "Titus",
    "Philemon": "Philemon", "He": "Hebrews", "Jas": "James", "1Pe": "1 Peter", "2Pe": "2 Peter",
    "1Jn": "1 John", "2Jn": "2 John", "3Jn": "3 John", "Jude": "Jude", "Rev": "Revelation",
}

# Per-chapter KJV verse counts (the committed witness) — used to model cross-chapter ranges and
# to mark a "whole chapter" prefix as carrying NO verse window (start==1 and end==versecount).
def load_verse_counts():
    path = os.path.join(ROOT, "app", "src", "test", "resources", "bible", "kjv_verse_counts.csv")
    counts = {}
    with open(path) as f:
        next(f)
        for line in f:
            line = line.strip()
            if not line:
                continue
            book, ch, n = line.split(",")
            counts[(book, int(ch))] = int(n)
    return counts


def pdf_text(pdf_path):
    out = tempfile.mktemp(suffix=".txt")
    subprocess.run(["pdftotext", "-layout", pdf_path, out], check=True)
    return open(out).read()


def norm_book(tok):
    """Normalize an abbreviation token (handle '1 Jn'/'2 Jn'/'3 Jn' space variants)."""
    t = tok.strip().replace(" ", "")
    return BOOK[t]


# ---- edginet (canonical) parser: 6 fixed columns, two bands of months ----
# Top band columns -> months; bottom band columns -> months (Oct/Dec start partway down).
TOP_MONTHS = [1, 3, 5, 7, 9, 11]      # Jan Mar May Jul Sep Nov
BOTTOM_MONTHS = [2, 4, 6, 8, 10, 12]  # Feb Apr Jun Aug Oct Dec
COL_OFFSETS = [0, 42, 81, 123, 165, 205]


def parse_edginet(text):
    """Return {(month,day): 'raw ref string'} from the fixed-column A4 calendar.

    The calendar has two bands (top: Jan/Mar/May/Jul/Sep/Nov; bottom: Feb/Apr/Jun/Aug/Oct/Dec),
    six fixed columns each. Verse-windowed days are longer and WRAP to a continuation line in the
    same column (no leading day-number, indented) — those are joined to the day above. Trailing
    column-bleed digits/tokens are trimmed by re-validating against the day sequence + the gate.
    """
    lines = text.split("\n")
    top_hdr = next(i for i, l in enumerate(lines) if l.startswith("January"))
    bottom_hdr = next(i for i, l in enumerate(lines) if l.startswith("February"))
    days = {}

    def cell(line, ci):
        start = COL_OFFSETS[ci]
        end = COL_OFFSETS[ci + 1] if ci + 1 < len(COL_OFFSETS) else len(line)
        return line[start:end].rstrip()

    MONTH_WORDS = {"January", "February", "March", "April", "May", "June", "July", "August",
                   "September", "October", "November", "December"}

    def absorb_band(first_line, last_line, months):
        # current (month,day) being built per column, so a continuation line appends to it.
        current = {ci: None for ci in range(6)}
        for i in range(first_line, last_line):
            line = lines[i]
            for ci in range(6):
                c = cell(line, ci).strip()
                if not c or c in MONTH_WORDS:
                    continue
                m = re.match(r"^(\d{1,2})\s+(.+)$", c)
                if m:
                    day = int(m.group(1))
                    key = (months[ci], day)
                    if key in days:
                        # Band-overlap or a clipped duplicate day-number: skip (reconcile handles it).
                        current[ci] = None
                        continue
                    days[key] = m.group(2).strip()
                    current[ci] = key
                else:
                    # A continuation line (no leading day number) -> append IF it looks like a ref
                    # (a book token + number), never the copyright footer or stray text.
                    if current[ci] is not None and re.match(r"^[1-3]?\s?[A-Z][A-Za-z]*\s+\d", c):
                        days[current[ci]] = (days[current[ci]] + " " + c).strip()
                    else:
                        current[ci] = None
        return days

    # Top band runs from just after its header to just before the bottom header.
    absorb_band(top_hdr + 1, bottom_hdr, TOP_MONTHS)
    # Bottom band runs from the bottom header line (which carries Oct 1 / Dec 1) to the end.
    absorb_band(bottom_hdr, len(lines), BOTTOM_MONTHS)
    return days


# ---- ref-field expansion: a stream's text like 'Ex 11:1-12:21' or 'Jer 36,45' -> [refdicts] ----
def expand_field(field, counts):
    """One stream slot's text -> a list of {book,chapter[,verseStart,verseEnd]} ref dicts.

    Handles: whole chapter ('Gn 1'), hyphen chapter span ('Ps 126-128'), same-chapter verse
    window ('Ps 119:1-24'), cross-chapter verse range ('Ex 11:1-12:21'), comma-joined non-adjacent
    chapters ('Jer 36,45'), and single-verse-start windows. A chapter whose window is exactly its
    whole verse range is emitted as a WHOLE chapter (no verse fields) so the 'absent => whole' and
    'only intended days are windowed' invariants hold.
    """
    field = field.strip().rstrip(".").strip()
    refs = []
    # Split a slot on commas FIRST (non-adjacent chapter list like 'Jer 36,45' / 'Ps 40-41' has no
    # comma; comma only joins separate chapters of the SAME book).
    parts = re.split(r"\s*,\s*", field)
    cur_book = None
    for idx, part in enumerate(parts):
        part = part.strip()
        # A bare single-chapter book name with no chapter number (edginet: 'Philemon', 'Jude',
        # 'Obadiah') -> chapter 1.
        bare = re.match(r"^((?:[1-3]\s?)?[A-Za-z]+)$", part)
        if bare:
            cur_book = norm_book(bare.group(1))
            refs.append({"book": cur_book, "chapter": 1})
            continue
        m = re.match(r"^((?:[1-3]\s?)?[A-Za-z]+)?\s*(\d.*)$", part)
        if not m:
            raise ValueError(f"unparseable ref part {part!r} in field {field!r}")
        book_tok = (m.group(1) or "").strip()
        if book_tok:
            cur_book = norm_book(book_tok)
        book = cur_book
        spec = m.group(2).strip()
        refs.extend(expand_spec(book, spec, counts))
    return refs


def expand_spec(book, spec, counts):
    """Expand a chapter/verse spec (no book token) for `book`."""
    # cross-chapter verse range  e.g. '11:1-12:21'
    m = re.match(r"^(\d+):(\d+)-(\d+):(\d+)$", spec)
    if m:
        c1, v1, c2, v2 = (int(x) for x in m.groups())
        out = []
        # first chapter: v1..end-of-c1
        out += [window(book, c1, v1, counts[(book, c1)], counts)]
        # any whole chapters strictly between c1 and c2
        for c in range(c1 + 1, c2):
            out.append({"book": book, "chapter": c})
        # last chapter: 1..v2
        out += [window(book, c2, 1, v2, counts)]
        return out
    # same-chapter verse range  e.g. '119:1-24'
    m = re.match(r"^(\d+):(\d+)-(\d+)$", spec)
    if m:
        c, v1, v2 = (int(x) for x in m.groups())
        return [window(book, c, v1, v2, counts)]
    # same-chapter single verse  e.g. '13:1'  (Zech 12:1-13:1's tail is '13:1' after split? no)
    m = re.match(r"^(\d+):(\d+)$", spec)
    if m:
        c, v = (int(x) for x in m.groups())
        return [window(book, c, v, v, counts)]
    # chapter hyphen span  e.g. '126-128'
    m = re.match(r"^(\d+)-(\d+)$", spec)
    if m:
        lo, hi = int(m.group(1)), int(m.group(2))
        return [{"book": book, "chapter": c} for c in range(lo, hi + 1)]
    # single chapter  e.g. '1'
    m = re.match(r"^(\d+)$", spec)
    if m:
        return [{"book": book, "chapter": int(m.group(1))}]
    raise ValueError(f"unparseable spec {spec!r} for {book}")


def window(book, chapter, vstart, vend, counts):
    """A verse window, collapsed to a WHOLE chapter when it covers all verses (1..count)."""
    total = counts[(book, chapter)]
    if vstart == 1 and vend == total:
        return {"book": book, "chapter": chapter}
    return {"book": book, "chapter": chapter, "verseStart": vstart, "verseEnd": vend}


def build_day(month, day, raw, counts):
    """A raw 4-stream day string -> the schema-v3 day dict (4 portions)."""
    all_slots = [s.strip() for s in raw.split(";") if s.strip()]
    # edginet keeps 4 ';'-separated stream slots (a non-adjacent double like 'Jer 36,45' is ONE
    # comma-joined slot). Any 5th+ slot is trailing column-bleed from the fixed-width layout; the
    # day-by-day gate vs the independent TGC witness is the backstop that this truncation is safe.
    slots = all_slots[:4]
    if len(slots) != 4:
        raise ValueError(f"{month}/{day}: expected >=4 stream slots, got {all_slots!r}")
    portions = []
    for stream, slot in enumerate(slots, start=1):
        refs = expand_field(slot, counts)
        if not refs:
            raise ValueError(f"{month}/{day} stream {stream}: no refs from {slot!r}")
        portions.append({"stream": stream, "refs": refs})
    return {"month": month, "day": day, "portions": portions}


# ---- Reconciliation (documented source-extraction defect, corrected on EVIDENCE) ----
# docs/data/README.md M'Cheyne reconciliation log. ONE pdftotext column-extraction artifact in the
# edginet A4 calendar: the August column's "29" lost its leading "2" at the column boundary, so the
# Aug 29 cell extracts as "9 1Sa 21-22; 1Co 3; Eze 1; Ps 37" — a day-number that collides with the
# real Aug 9 (Ruth 2). This is NOT a plan disagreement: the reading CONTENT ("1Sa 21-22; 1Co 3;
# Eze 1; Ps 37") is confirmed identical to the independent TGC/Carson witness (Aug 29 = 1 Sam 21-22;
# 1 Cor 3; Ezek 1; Ps 37) and fits the 28->30->31 sequence. The fix re-keys that exact cell to 29.
RECONCILE_AUG29_CONTENT = "1Sa 21-22; 1Co 3; Eze 1; Ps 37"


# Jun 28's 4th slot extracts as "Mt 8 2" — the trailing "2" is column-bleed of the adjacent
# August column's day-2 number. The correct reading is Matthew 8 (TGC/Carson Jun 28 = Matt. 8).
RECONCILE_JUN28 = ("Dt 33-34; Ps 119:145-176; Is 60; Mt 8 2", "Dt 33-34; Ps 119:145-176; Is 60; Mt 8")


def reconcile(raw_days):
    """Correct the two documented edginet column-extraction artifacts (see the README log)."""
    fixed = dict(raw_days)
    # (1) Aug 29: leading "2" clipped -> the cell collided with Aug 9 and was dropped. Re-insert.
    if (8, 29) not in fixed:
        fixed[(8, 29)] = RECONCILE_AUG29_CONTENT
    # (2) Jun 28: a trailing column-bleed "2" appended to the 4th slot.
    wrong, right = RECONCILE_JUN28
    if fixed.get((6, 28)) == wrong:
        fixed[(6, 28)] = right
    return fixed


def main():
    pdf = sys.argv[1] if len(sys.argv) > 1 else None
    if pdf is None:
        pdf = tempfile.mktemp(suffix=".pdf")
        req = urllib.request.Request(EDGINET_URL, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req) as r, open(pdf, "wb") as f:
            f.write(r.read())
    counts = load_verse_counts()
    raw_days = parse_edginet(pdf_text(pdf))
    raw_days = reconcile(raw_days)

    assert len(raw_days) == 365, f"expected 365 days from edginet, got {len(raw_days)}"
    for m, n in enumerate(DAYS_PER_MONTH, start=1):
        got = sum(1 for (mm, _) in raw_days if mm == m)
        assert got == n, f"month {m}: expected {n} days, got {got}"
    assert (2, 29) not in raw_days, "edginet must not carry a Feb 29 entry"

    days = []
    for key in sorted(raw_days):
        m, d = key
        days.append(build_day(m, d, raw_days[key], counts))

    doc = {
        "schemaVersion": 3,
        "planId": "mcheyne",
        "name": "M'Cheyne",
        "source": "edginet.org year_classic_single_a4.pdf (R.M. M'Cheyne, Edgington/Haslam); "
                  "verified against The Gospel Coalition / D.A. Carson RtB_Reading-Plan_2020.pdf",
        "anchoring": "DATE",
        "dayCount": 365,
        "streams": [
            {"number": 1, "title": "Family — Old Testament"},
            {"number": 2, "title": "Family — Gospels"},
            {"number": 3, "title": "Personal — Psalms & Prophets"},
            {"number": 4, "title": "Personal — Epistles"},
        ],
        "days": days,
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w") as f:
        json.dump(doc, f, indent=1)
        f.write("\n")
    print(f"wrote {OUT}: {len(days)} days")


if __name__ == "__main__":
    main()
