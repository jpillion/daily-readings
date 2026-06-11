#!/usr/bin/env python3
"""S1-T3: Independently extract the Bible Companion plan from the SECOND source
(antipas.org booklet PDF) into data/reading_plan_verify.json.

Independent of tools/extract_primary.py: different source document, different
layout (4 print-imposed month bands, abbreviated book names, ditto continuation,
comma chapter notation), different parsing strategy (column slicing).

Usage: extract_antipas.py [path-to-booklet.pdf]   (fetches the URL if no path given)
Requires: pdftotext (poppler).

Normalization (see docs/data/README.md):
  - comma/hyphen chapter lists expand as the contiguous min..max span
    ("145,147" == primary's "145-147"; every Companion portion is contiguous).
  - "v." verse-part rows are the Psalm 119 days -> chapter 119.
  - "2,3 John" / "2 & 3 John" -> 2 John 1 + 3 John 1.
  - bare book (Philemon, Jude, "Obadiah ..") -> chapter 1.
"""
import json, re, subprocess, sys, tempfile, urllib.request, os

URL = ("https://antipas.org/library/Robert%20Roberts/Booklets/"
       "The%20Bible%20Companion.pdf")
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "test", "resources", "reading_plan_verify.json")
DAYS_PER_MONTH = [31,28,31,30,31,30,31,31,30,31,30,31]

# band -> (data start line, data end line (exclusive, 0-based),
#          [(month, slice_start), ...], last month slice end)
BANDS = [
    (1,   40,  [(9, 0), (10, 67)], 130),
    (41,  75,  [(11, 0), (12, 67)], 130),
    (76,  110, [(1, 0), (2, 64), (5, 128), (6, 192)], 258),
    (111, 999, [(3, 0), (4, 64), (7, 128), (8, 192)], 258),
]

ABBREV = {
    "gen":"Genesis","exo":"Exodus","exod":"Exodus","lev":"Leviticus","num":"Numbers",
    "deut":"Deuteronomy","joshua":"Joshua","judges":"Judges","ruth":"Ruth",
    "1sam":"1 Samuel","2sam":"2 Samuel","1kings":"1 Kings","2kings":"2 Kings",
    "1chron":"1 Chronicles","2chron":"2 Chronicles","ezra":"Ezra","neh":"Nehemiah",
    "esther":"Esther","job":"Job","psa":"Psalms","prov":"Proverbs","eccl":"Ecclesiastes",
    "song":"Song of Solomon","isaiah":"Isaiah","isa":"Isaiah","jer":"Jeremiah",
    "lam":"Lamentations","ezek":"Ezekiel","dan":"Daniel","daniel":"Daniel","hosea":"Hosea","hos":"Hosea",
    "joel":"Joel","amos":"Amos","obadiah":"Obadiah","jonah":"Jonah","micah":"Micah",
    "nahum":"Nahum","hab":"Habakkuk","zeph":"Zephaniah","hag":"Haggai","zech":"Zechariah",
    "malachi":"Malachi","mal":"Malachi",
    "matt":"Matthew","mark":"Mark","luke":"Luke","john":"John","acts":"Acts",
    "rom":"Romans","1cor":"1 Corinthians","2cor":"2 Corinthians","gal":"Galatians",
    "eph":"Ephesians","phil":"Philippians","col":"Colossians","colos":"Colossians",
    "1thess":"1 Thessalonians","2thess":"2 Thessalonians","1tim":"1 Timothy",
    "2tim":"2 Timothy","titus":"Titus","philemon":"Philemon","heb":"Hebrews",
    "james":"James","1peter":"1 Peter","2peter":"2 Peter","1john":"1 John",
    "2john":"2 John","3john":"3 John","jude":"Jude","rev":"Revelation",
}
SINGLE_CHAPTER_OK = {"Obadiah","Philemon","Jude","2 John","3 John"}

def pdf_text(pdf_path):
    out = tempfile.mktemp(suffix=".txt")
    subprocess.run(["pdftotext","-layout",pdf_path,out],check=True)
    return open(out).read()

def book_key(s):
    return re.sub(r"[.\s&,]", "", s).lower()

def parse_field(field, carry_book):
    """One stream field -> (book, [chapters]).  carry_book used for ditto rows."""
    f = field.strip()
    if book_key(f) in ("23john",):                    # "2,3 John" / "2 & 3 John"
        return ("2 John+3 John", None)
    # split optional book part (letters, with optional leading 1/2/3) from chapters
    m = re.match(r"^([1-3]?\s?[A-Za-z][A-Za-z&. ]*)?[\s.]*(.*)$", f)
    raw_book, chs = (m.group(1) or "").strip(), m.group(2).strip()
    if raw_book in ("v",):                            # "v.41-80" psalm-119 parts
        raw_book, chs = "", f
    chs_part = chs
    if raw_book:
        key = book_key(raw_book)
        if key not in ABBREV:
            raise ValueError(f"unknown book abbrev {raw_book!r} in field {field!r}")
        book = ABBREV[key]
    else:
        if not carry_book:
            raise ValueError(f"ditto field {field!r} with no carry book")
        book = carry_book
    if "v." in chs_part:                              # Psalm 119 verse parts
        assert book == "Psalms", f"verse notation outside Psalms: {field!r}"
        return (book, [119])
    nums = [int(n) for n in re.findall(r"\d+", chs_part)]
    if not nums:
        assert book in SINGLE_CHAPTER_OK, f"no chapters for {book!r} in {field!r}"
        return (book, [1])
    return (book, list(range(min(nums), max(nums)+1)))

# S1-T6 reconciliation overrides — proven SECOND-SOURCE (booklet) defects, corrected
# to the reconciled value (evidence in docs/data/README.md reconciliation log):
#  (3,23): booklet prints "Prov 11" where Proverbs 1 must start (its own Mar 24 = Prov 2;
#          Proverbs 1 otherwise never read). Chart "Proverbs 1" is correct.
#  (9,30): booklet "1 Chron 13" omits ch 14 (never read otherwise; chart "13-14").
OVERRIDES = {
    (3,23): (2, [("Proverbs",1)]),
    (9,30): (1, [("1 Chronicles",13),("1 Chronicles",14)]),
}

def main():
    if len(sys.argv) > 1:
        pdf = sys.argv[1]
    else:
        pdf = tempfile.mktemp(suffix=".pdf")
        urllib.request.urlretrieve(URL, pdf)
    lines = pdf_text(pdf).splitlines()

    raw = {}          # month -> [(day, [field1, field2, field3]) ...]
    for bstart, bend, months, last_end in BANDS:
        for m, _ in months:
            raw.setdefault(m, [])
        for line in lines[bstart : bend]:
            for i, (month, start) in enumerate(months):
                end = months[i+1][1] if i+1 < len(months) else last_end
                seg = line[start:end].rstrip()
                if not seg.strip():
                    continue
                sm = re.match(r"^\s*(\d{1,2})\s+(.*)$", seg)
                if not sm:
                    continue        # prose fragment, header remnant, etc.
                day = int(sm.group(1))
                fields = [x for x in re.split(r"\s{2,}", sm.group(2).strip()) if x]
                if len(fields) != 3:
                    raise ValueError(f"month {month} day {day}: {len(fields)} fields {fields!r}")
                raw[month].append((day, fields))

    days = []
    carry = {1: None, 2: None, 3: None}   # per-stream book carry, calendar order
    for month in range(1, 13):
        rows = sorted(raw[month])
        assert [d for d,_ in rows] == list(range(1, DAYS_PER_MONTH[month-1]+1)), \
            f"month {month}: days {[d for d,_ in rows]}"
        for day, fields in rows:
            portions = []
            for stream, f in enumerate(fields, start=1):
                book, chs = parse_field(f, carry[stream])
                if chs is None:                       # 2 John + 3 John day
                    refs = [{"book":"2 John","chapter":1},{"book":"3 John","chapter":1}]
                    carry[stream] = "3 John"
                else:
                    refs = [{"book":book,"chapter":c} for c in chs]
                    carry[stream] = book
                portions.append({"stream": stream, "refs": refs})
            days.append({"month": month, "day": day, "portions": portions})

    for (m,d),(stream,refs) in OVERRIDES.items():
        e = next(x for x in days if x["month"]==m and x["day"]==d)
        e["portions"][stream-1]["refs"] = [{"book":b,"chapter":c} for b,c in refs]

    assert len(days) == 365, f"expected 365 days, got {len(days)}"
    doc = {"schemaVersion": 1,
           "source": "antipas.org 'The Bible Companion' booklet PDF (Robert Roberts)",
           "days": days}
    with open(OUT, "w") as fp:
        json.dump(doc, fp, indent=1)
        fp.write("\n")
    print(f"wrote {OUT}: {len(days)} days")

if __name__ == "__main__":
    main()
