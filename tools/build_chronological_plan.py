#!/usr/bin/env python3
"""CHR-1: build the Chronological reading plan asset (alternate-schedules Sprint F).

Reproducible, byte-deterministic extraction of Blue Letter Bible's "Daily Bible Reading Program —
Chronological Plan" (Nathan Gammie) — a single-stream (N=1), date-anchored, 365-day, WHOLE-CHAPTERS-
ONLY chronological calendar — into app/src/main/assets/plans/chronological/plan.json (schemaVersion 3).

Designated single source (docs/data/README.md "Chronological plan", D-ALT-24, the owner-signed path (b)):
  Blue Letter Bible "1Yr_ChronologicalPlan.pdf" (Author: Nathan Gammie, 2024).
  https://www.blueletterbible.org/assets/pdf/dbrp/1Yr_ChronologicalPlan.pdf
  SHA-256 b055f5f4a14d86fb876237937478374de7c6811cdb70f951dc178dbd09e7fe54 · 74050 bytes · 2 pages.

This plan ships under a RIGOROUS SINGLE-SOURCE STRUCTURAL GATE (no independent second-source fixture —
that's the owner-signed FR-ALT-3 relaxation, D-ALT-24). The gate (ChronologicalPlanVerificationTest)
proves whole-Bible coverage EXACTLY ONCE — every chapter of all 66 books (1,189) appears once, no gaps,
no dupes — which is the strongest possible single-source completeness/correctness guarantee for a
zero-verse-split plan.

Parse approach (the PDF is a 3-/4-COLUMN calendar; pdftotext -layout interleaves the columns):
  * Every reading day is an EXPLICIT "N. <refs>" marker (N = 1..365). We slice each text line at its
    "N." markers into per-day cells and key strictly by N — column geometry is never trusted for the
    day number itself, only for attaching wrapped continuation lines.
  * A few long days WRAP to a continuation line (no leading "N.", indented, in the same column) — e.g.
    Day 131 "2 Samuel 10; 1 Chronicles 19;" then "Psalm 20" on the next line. A non-numbered cell that
    carries a ref-like token is attached to the most-recent day in the SAME column band.
  * Multi-book days split on ";" (Day 209 "2 Kings 19; Psalms 46, 80, 135"); non-contiguous chapter
    lists split on "," (Day 125 "Psalms 1-2, 15, 22-24, 47, 68"); a "&"/"," book-join lists whole
    books (Day 331 "1 & 2 Thessalonians"; Day 349 "Colossians, Philemon"; Day 361 "2, 3 John"); a
    bare book name with no chapter means the WHOLE book (Day 97 "Ruth"; Day 326 "James").

Usage: build_chronological_plan.py [path-to-1Yr_ChronologicalPlan.pdf]   (fetches the URL if no path).
Requires: pdftotext (poppler) for PDF extraction; Python stdlib only otherwise.
"""
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "plans", "chronological", "plan.json")
BLB_URL = "https://www.blueletterbible.org/assets/pdf/dbrp/1Yr_ChronologicalPlan.pdf"

# The designated single source is SHA-pinned: the script refuses any other bytes (the gate's first line
# of defence — a different PDF revision must be a conscious re-pin, never a silent re-extraction).
EXPECTED_SHA256 = "b055f5f4a14d86fb876237937478374de7c6811cdb70f951dc178dbd09e7fe54"
EXPECTED_LEN = 74050

DAYS_PER_MONTH = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]


def load_catalog():
    """The book catalog is the chapter authority (book_catalog.csv): name -> chapterCount, and a
    name->canonical alias map for every form the PDF uses (full names; 'Psalm'->'Psalms')."""
    path = os.path.join(ROOT, "app", "src", "test", "resources", "book_catalog.csv")
    chapters = {}
    alias = {}
    with open(path) as f:
        next(f)
        for line in f:
            line = line.strip()
            if not line:
                continue
            _order, name, count, _abbrev = line.split(",")
            chapters[name] = int(count)
            alias[name] = name
            alias[name.lower()] = name
    # The only spelling the PDF uses that is not the canonical catalog name: singular "Psalm".
    alias["Psalm"] = "Psalms"
    alias["psalm"] = "Psalms"
    return chapters, alias


def fetch_pdf():
    pdf = tempfile.mktemp(suffix=".pdf")
    req = urllib.request.Request(BLB_URL, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req) as r, open(pdf, "wb") as f:
        f.write(r.read())
    return pdf


def verify_source(pdf_path):
    data = open(pdf_path, "rb").read()
    digest = hashlib.sha256(data).hexdigest()
    if len(data) != EXPECTED_LEN:
        raise SystemExit(f"source length {len(data)} != expected {EXPECTED_LEN}; refusing other bytes")
    if digest != EXPECTED_SHA256:
        raise SystemExit(f"source SHA-256 {digest} != expected {EXPECTED_SHA256}; refusing other bytes")


def pdf_text(pdf_path):
    out = tempfile.mktemp(suffix=".txt")
    subprocess.run(["pdftotext", "-layout", pdf_path, out], check=True)
    return open(out).read()


# ---- Column-aware parser: explicit "N." markers + wrapped continuations ----------------------------
DAY_MARKER = re.compile(r"(\d{1,3})\.\s")
# A continuation cell carries a ref token (a capitalized book word, optionally a leading book number,
# followed by digits) and never a leading day number. Used to recognize a wrap line in a column band.
REF_TOKEN = re.compile(r"^(?:[1-3]\s)?[A-Z][a-z]+")


def line_cells(line):
    """Split one -layout line into column cells: a list of (col_offset, text) for each run of text
    separated by >=3 spaces. Empty runs are dropped. Day cells and continuation cells alike."""
    cells = []
    for m in re.finditer(r"\S(?:.*?\S)?(?=\s{3,}|$)", line):
        cells.append((m.start(), m.group().strip()))
    return [(off, t) for off, t in cells if t]


def parse_days(text):
    """Return {dayNum: 'raw ref string'} from the multi-/column -layout calendar.

    The calendar is 3-/4-column; pdftotext -layout interleaves the columns but each reading day is an
    EXPLICIT 'N. <refs>' cell (N=1..365). We split every line into column cells (runs separated by big
    gaps), key day cells strictly by their explicit number, and attach a continuation cell (no leading
    'N.') to the open day in its OWN column — disambiguated by the wrap signal: a long reading wraps
    only after a trailing ';', so the parent day's text ends with ';' (Day 131 '...1 Chronicles 19;',
    Day 200 '2 Kings 18;'). Column identity = nearest open-day offset; the ';' signal breaks ties.
    """
    days = {}
    # Per column: (col_offset, day_num, ends_with_semicolon) for the day currently open in that column,
    # so the very next continuation cell in that column appends to it. Keyed by approximate offset.
    open_in_col = {}  # col_offset -> day_num  (latest day opened at ~that offset)

    def append_continuation(off, seg):
        # Find the open day in the column nearest `off` whose text ends with ';' (the wrap marker).
        # The ';' test is what disambiguates the offset-0 / offset-1 near-tie between two columns.
        best, best_dist = None, 21
        for col_off, day in open_in_col.items():
            if not days[day].rstrip().endswith(";"):
                continue
            dist = abs(col_off - off)
            if dist < best_dist:
                best, best_dist = day, dist
        if best is not None:
            days[best] = (days[best].rstrip().rstrip(";").rstrip() + "; " + seg).strip()
            return True
        return False

    for line in text.split("\n"):
        for off, text_cell in line_cells(line):
            mk = DAY_MARKER.match(text_cell + " ")
            if mk:
                day = int(mk.group(1))
                if not (1 <= day <= 365):
                    continue
                body = text_cell[mk.end():].strip()
                if not body:
                    continue
                if day in days:
                    raise ValueError(f"duplicate day {day}: {days[day]!r} vs {body!r}")
                days[day] = body
                open_in_col[off] = day
            else:
                # A continuation cell: a ref-like fragment with no leading day number. Attach it to the
                # open, semicolon-ended day in its column. Ignore the page-1 prose/title band.
                if REF_TOKEN.match(text_cell) and re.search(r"\d", text_cell):
                    append_continuation(off, text_cell)
    return days


# ---- ref-field expansion: a day's text -> a list of whole-chapter {book,chapter} ref dicts ----------
def expand_day(raw, chapters, alias):
    """A raw day string -> ordered list of {book,chapter} refs (whole chapters only — NO verse fields).

    Split on ';' (separate book groups), then within each group on ',' (a non-contiguous chapter list
    OR a whole-book join). Chapter spans use '-'. A bare book name with no chapter => the WHOLE book.
    """
    raw = raw.strip().rstrip(".").strip()
    refs = []
    for group in [g.strip() for g in raw.split(";") if g.strip()]:
        refs.extend(expand_group(group, chapters, alias))
    if not refs:
        raise ValueError(f"no refs from day {raw!r}")
    return refs


# A "&" join lists whole books: "1 & 2 Thessalonians" -> 1 Thessalonians (whole) + 2 Thessalonians.
AMP_JOIN = re.compile(r"^([1-3])\s*&\s*([1-3])\s+([A-Za-z].*)$")


def norm_book(tok, alias):
    t = tok.strip()
    t = re.sub(r"\s+", " ", t)
    if t in alias:
        return alias[t]
    if t.lower() in alias:
        return alias[t.lower()]
    raise ValueError(f"unknown book token {tok!r}")


def whole_book(book, chapters):
    return [{"book": book, "chapter": c} for c in range(1, chapters[book] + 1)]


def expand_group(group, chapters, alias):
    """One ';'-delimited group -> refs. Handles the '&' book-join, the ',' book-join, the ',' chapter
    list, '-' chapter spans, and a bare whole-book name."""
    # "1 & 2 Thessalonians" — two whole numbered books sharing a base name.
    m = AMP_JOIN.match(group)
    if m:
        n1, n2, base = m.group(1), m.group(2), m.group(3).strip()
        b1 = norm_book(f"{n1} {base}", alias)
        b2 = norm_book(f"{n2} {base}", alias)
        return whole_book(b1, chapters) + whole_book(b2, chapters)

    parts = [p.strip() for p in group.split(",") if p.strip()]
    # Decide whether the commas join BOOKS (e.g. "Colossians, Philemon", "2 Peter, Jude", "2, 3 John")
    # or list CHAPTERS of one book (e.g. "Psalms 1-2, 15, 22-24"). Book-join iff at least one comma-part
    # past the first names its own book (carries an alpha token), OR the whole group is a numbered-book
    # family like "2, 3 John" (a bare leading numeral + a trailing "N <Book>").
    def names_book(part):
        return bool(re.search(r"[A-Za-z]", part))

    book_join = any(names_book(p) for p in parts[1:])
    # "2, 3 John": parts = ["2", "3 John"] — leading bare numeral(s) + a trailing numbered book; treat
    # each as a sibling numbered book of that base name.
    trailing_numbered = re.match(r"^([1-3])\s+([A-Za-z].*)$", parts[-1]) if parts else None
    leading_bare_numbers = all(re.fullmatch(r"[1-3]", p) for p in parts[:-1]) and len(parts) > 1
    if trailing_numbered and leading_bare_numbers and not any(names_book(p) for p in parts[:-1]):
        base = trailing_numbered.group(2).strip()
        refs = []
        for p in parts[:-1]:
            refs += whole_book(norm_book(f"{p} {base}", alias), chapters)
        refs += whole_book(norm_book(parts[-1], alias), chapters)
        return refs

    if book_join:
        refs = []
        for p in parts:
            refs += expand_group(p, chapters, alias)  # each part is its own whole-book / book+chapters
        return refs

    # Single-book group: optional leading book token, then a comma-list of chapters/spans.
    refs = []
    cur_book = None
    for idx, part in enumerate(parts):
        m = re.match(r"^((?:[1-3]\s)?[A-Za-z][A-Za-z ]*?)?\s*(\d.*)?$", part)
        if not m:
            raise ValueError(f"unparseable ref part {part!r} in group {group!r}")
        book_tok = (m.group(1) or "").strip()
        spec = (m.group(2) or "").strip()
        if book_tok:
            cur_book = norm_book(book_tok, alias)
        if cur_book is None:
            raise ValueError(f"chapter spec {part!r} with no book in {group!r}")
        if not spec:
            # bare whole book (only valid as the sole part of its group)
            refs += whole_book(cur_book, chapters)
            continue
        refs += expand_spec(cur_book, spec, chapters)
    return refs


def expand_spec(book, spec, chapters):
    """A chapter spec (no book token): 'N', 'A-B' span. No verse colons in this plan (asserted)."""
    if ":" in spec:
        raise ValueError(f"unexpected verse split {spec!r} for {book} (plan is whole-chapters-only)")
    m = re.fullmatch(r"(\d+)-(\d+)", spec)
    if m:
        lo, hi = int(m.group(1)), int(m.group(2))
        if not (1 <= lo <= hi <= chapters[book]):
            raise ValueError(f"chapter span {lo}-{hi} out of range for {book} (1..{chapters[book]})")
        return [{"book": book, "chapter": c} for c in range(lo, hi + 1)]
    m = re.fullmatch(r"(\d+)", spec)
    if m:
        c = int(m.group(1))
        if not (1 <= c <= chapters[book]):
            raise ValueError(f"chapter {c} out of range for {book} (1..{chapters[book]})")
        return [{"book": book, "chapter": c}]
    raise ValueError(f"unparseable spec {spec!r} for {book}")


def month_day(day_num):
    """Day index 1..365 -> (month, day) over a NON-leap year (Feb=28, no Feb 29; D1)."""
    n = day_num
    for m, length in enumerate(DAYS_PER_MONTH, start=1):
        if n <= length:
            return m, n
        n -= length
    raise ValueError(f"day index {day_num} out of 1..365")


def main():
    pdf = sys.argv[1] if len(sys.argv) > 1 else fetch_pdf()
    verify_source(pdf)
    chapters, alias = load_catalog()
    raw_days = parse_days(pdf_text(pdf))

    assert len(raw_days) == 365, f"expected 365 numbered days, got {len(raw_days)}"
    assert sorted(raw_days) == list(range(1, 366)), "day numbers must be exactly 1..365 with no gaps"

    days = []
    for n in range(1, 366):
        month, day = month_day(n)
        refs = expand_day(raw_days[n], chapters, alias)
        days.append({"month": month, "day": day, "portions": [{"stream": 1, "refs": refs}]})

    # Structural assertions in-script (the gate re-proves these on the shipped asset).
    assert (2, 29) not in {(d["month"], d["day"]) for d in days}, "must not carry a Feb 29 entry"
    for m, n in enumerate(DAYS_PER_MONTH, start=1):
        got = sum(1 for d in days if d["month"] == m)
        assert got == n, f"month {m}: expected {n} days, got {got}"

    def day_refs(idx):
        return [(r["book"], r["chapter"]) for r in days[idx]["portions"][0]["refs"]]

    assert day_refs(0) == [("Genesis", 1), ("Genesis", 2), ("Genesis", 3)], f"Day 1: {day_refs(0)}"
    assert day_refs(364) == [
        ("Revelation", 19), ("Revelation", 20), ("Revelation", 21), ("Revelation", 22)
    ], f"Day 365: {day_refs(364)}"

    # The exactly-once whole-Bible coverage invariant (the single-source gate's core, proven in-script):
    counts = {}
    for d in days:
        for r in d["portions"][0]["refs"]:
            assert "verseStart" not in r and "verseEnd" not in r, "no verse windows in chronological"
            key = (r["book"], r["chapter"])
            counts[key] = counts.get(key, 0) + 1
    total = sum(counts.values())
    assert total == 1189, f"expected 1,189 chapter readings, got {total}"
    errors = []
    for book, n in chapters.items():
        for c in range(1, n + 1):
            got = counts.get((book, c), 0)
            if got != 1:
                errors.append(f"{book} {c} read {got}x")
    assert not errors, f"coverage errors (first 10): {errors[:10]}"

    doc = {
        "schemaVersion": 3,
        "planId": "chronological",
        "name": "Chronological",
        "source": "Blue Letter Bible 'Daily Bible Reading Program — Chronological Plan' "
                  "(Nathan Gammie, 2024); 1Yr_ChronologicalPlan.pdf, "
                  "SHA-256 b055f5f4a14d86fb876237937478374de7c6811cdb70f951dc178dbd09e7fe54. "
                  "Designated single canonical source under the owner-signed single-source structural "
                  "gate (D-ALT-24); no independent second-source fixture.",
        "anchoring": "DATE",
        "dayCount": 365,
        "streams": [
            {"number": 1, "title": "Chronological"},
        ],
        "days": days,
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w") as f:
        json.dump(doc, f, indent=1)
        f.write("\n")
    print(f"wrote {OUT}: {len(days)} days, {total} chapter readings (each exactly once)")


if __name__ == "__main__":
    main()
