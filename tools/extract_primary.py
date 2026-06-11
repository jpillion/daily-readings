#!/usr/bin/env python3
"""S1-T2: Extract the Bible Companion plan from the PRIMARY source
(christadelphia.org chart.pdf) into data/reading_plan.json (ESpec 5.1 schema).

Usage: extract_primary.py [path-to-chart.pdf]   (fetches the URL if no path given)
Requires: pdftotext (poppler).

Normalization (see docs/data/README.md):
  - Feb 29 row (duplicate of Feb 28) is DROPPED per decision D1 -> 365 days.
  - "Psalms 119:<verses>" parts -> {"book":"Psalms","chapter":119}.
  - "2 John -3JO" -> 2 John 1 + 3 John 1.
  - Chart typos: Zepheniah->Zephaniah, Haggia->Haggai.
  - Hyphen spans expand per-chapter: "Genesis 1-2" -> ch 1, ch 2.
"""
import json, re, subprocess, sys, tempfile, urllib.request, os

URL = "https://christadelphia.org/chart.pdf"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "reading_plan.json")

MONTHS = {"Jan":1,"Feb":2,"Mar":3,"Apr":4,"May":5,"Jun":6,
          "Jul":7,"Aug":8,"Sep":9,"Oct":10,"Nov":11,"Dec":12}
BOOK_FIX = {"Zepheniah":"Zephaniah","Haggia":"Haggai","Psalm":"Psalms",
            "Obadia":"Obadiah","Habbakuk":"Habakkuk"}
DAYS_PER_MONTH = [31,28,31,30,31,30,31,31,30,31,30,31]

def pdf_text(pdf_path):
    out = tempfile.mktemp(suffix=".txt")
    subprocess.run(["pdftotext","-layout",pdf_path,out],check=True)
    return open(out).read()

ENTRY_START = re.compile(r"\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s+(\d{1,2})\b(?=\s*\.)")

def parse_ref_field(field):
    """One dot-leader-delimited field like 'Genesis 1-2' -> [(book, ch), ...]"""
    field = field.strip().rstrip(".").strip()
    if not field:
        return []
    if field == "2 John -3JO":          # chart typesetting artifact (Jun 19, Dec 19)
        return [("2 John",1),("3 John",1)]
    m = re.match(r"^([1-3]?\s?[A-Za-z][A-Za-z ]*?)\s+(\d.*)$", field)
    if not m:
        raise ValueError(f"unparseable ref field: {field!r}")
    book = BOOK_FIX.get(m.group(1).strip(), m.group(1).strip())
    chs = m.group(2).strip()
    if ":" in chs:                       # Psalm 119 verse part -> chapter 119
        ch = int(chs.split(":")[0])
        return [(book, ch)]
    if "-" in chs:
        lo, hi = (int(x) for x in chs.split("-"))
        return [(book, c) for c in range(lo, hi+1)]
    return [(book, int(chs))]

def parse(text):
    days = {}
    for line in text.splitlines():
        starts = list(ENTRY_START.finditer(line))
        for i, mt in enumerate(starts):
            seg_end = starts[i+1].start() if i+1 < len(starts) else len(line)
            month, day = MONTHS[mt.group(1)], int(mt.group(2))
            if (month, day) == (2, 29):  # D1: no Feb 29 entry
                continue
            seg = line[mt.end():seg_end]
            fields = [f for f in re.split(r"\s*\.{3,}\s*", seg) if f.strip()]
            if len(fields) != 3:
                raise ValueError(f"{mt.group(1)} {day}: expected 3 fields, got {fields!r}")
            portions = []
            for stream, f in enumerate(fields, start=1):
                refs = parse_ref_field(f)
                portions.append({"stream": stream,
                                 "refs": [{"book": b, "chapter": c} for b, c in refs]})
            key = (month, day)
            if key in days:
                raise ValueError(f"duplicate day {key}")
            days[key] = {"month": month, "day": day, "portions": portions}
    return days

# S1-T6 reconciliation overrides — proven PRIMARY-source defects, corrected to the
# reconciled value (evidence in docs/data/README.md reconciliation log):
#  (1,12)/(1,13): chart "Psalms 26-29"/"30" vs antipas "26-28"/"29-30";
#                 third witness dailyreadings.org.uk agrees with antipas.
#  (4,26): chart "Deuteronomy 12-13" double-reads ch 13 (both sources put 13 in Apr 27).
#  (12,18): chart "Haggia 1" omits Haggai 2 (never read otherwise; antipas "Hag 1,2").
#  (12,28): chart "Zechariah 13" omits ch 14 (never read otherwise; antipas "13,14").
OVERRIDES = {
    (1,12):  (2, [("Psalms",26),("Psalms",27),("Psalms",28)]),
    (1,13):  (2, [("Psalms",29),("Psalms",30)]),
    (4,26):  (1, [("Deuteronomy",12)]),
    (12,18): (2, [("Haggai",1),("Haggai",2)]),
    (12,28): (2, [("Zechariah",13),("Zechariah",14)]),
}

def apply_overrides(days):
    for (m,d),(stream,refs) in OVERRIDES.items():
        days[(m,d)]["portions"][stream-1]["refs"] = [
            {"book":b,"chapter":c} for b,c in refs]

def main():
    if len(sys.argv) > 1:
        pdf = sys.argv[1]
    else:
        pdf = tempfile.mktemp(suffix=".pdf")
        urllib.request.urlretrieve(URL, pdf)
    days = parse(pdf_text(pdf))
    apply_overrides(days)
    # structural sanity before writing
    assert len(days) == 365, f"expected 365 days, got {len(days)}"
    for m, n in enumerate(DAYS_PER_MONTH, start=1):
        got = sum(1 for (mm, _) in days if mm == m)
        assert got == n, f"month {m}: expected {n} days, got {got}"
    ordered = [days[k] for k in sorted(days)]
    doc = {"schemaVersion": 1,
           "source": "christadelphia.org chart.pdf (Bible Companion, Robert Roberts); "
                     "verified against antipas.org Bible Companion booklet",
           "days": ordered}
    with open(OUT, "w") as f:
        json.dump(doc, f, indent=1)
        f.write("\n")
    print(f"wrote {OUT}: {len(ordered)} days")

if __name__ == "__main__":
    main()
