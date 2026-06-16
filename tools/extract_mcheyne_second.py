#!/usr/bin/env python3
"""SA-T9 helper: extract the INDEPENDENT M'Cheyne second-source fixture from the Carson/TGC PDF
(The Gospel Coalition RtB_Reading-Plan_2020.pdf) into the committed gate fixture
app/src/test/resources/plans/mcheyne/plan_verify.json (schemaVersion 3).

This is a GENUINELY INDEPENDENT lineage from the canonical edginet/Haslam source the asset is built
from (docs/data/README.md: Carson/TGC vs Edgington/Haslam — checksum-distinct, two parsers). The
day-by-day equality of this fixture vs the shipped asset is McheynePlanVerificationTest's central
invariant. A SEPARATE parser from build_mcheyne_plan.py (different book tokens, different layout).

Usage: extract_mcheyne_second.py [path-to-tgc.pdf]   (fetches the URL if no path given)
Requires: pdftotext (poppler).
"""
import json, os, re, subprocess, sys, tempfile, urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "test", "resources", "plans", "mcheyne", "plan_verify.json")
TGC_URL = "https://media.thegospelcoalition.org/wp-content/uploads/2020/01/09151141/RtB_Reading-Plan_2020.pdf"

DAYS_PER_MONTH = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
MONTHS = {"Jan": 1, "Feb": 2, "Mar": 3, "Apr": 4, "May": 5, "June": 6,
          "July": 7, "Aug": 8, "Sept": 9, "Oct": 10, "Nov": 11, "Dec": 12}

# Carson/TGC abbreviations (DIFFERENT lineage's tokens — 'Gen.'/'Matt.'/'Ezek.' vs edginet's
# 'Gn'/'Mt'/'Eze'). Mapped to the canonical BookCatalog names.
TGCBOOK = {
    "Gen.": "Genesis", "Ex.": "Exodus", "Lev.": "Leviticus", "Num.": "Numbers",
    "Deut.": "Deuteronomy", "Josh.": "Joshua", "Judg.": "Judges", "Ruth": "Ruth",
    "1 Sam.": "1 Samuel", "2 Sam.": "2 Samuel", "1 Kings": "1 Kings", "2 Kings": "2 Kings",
    "1 Chron.": "1 Chronicles", "2 Chron.": "2 Chronicles", "Ezra": "Ezra", "Neh.": "Nehemiah",
    "Est.": "Esther", "Job": "Job", "Ps.": "Psalms", "Prov.": "Proverbs", "Eccles.": "Ecclesiastes",
    "Song": "Song of Solomon", "Isa.": "Isaiah", "Jer.": "Jeremiah", "Lam.": "Lamentations",
    "Ezek.": "Ezekiel", "Dan.": "Daniel", "Hos.": "Hosea", "Joel": "Joel", "Amos": "Amos",
    "Obad.": "Obadiah", "Jonah": "Jonah", "Mic.": "Micah", "Nah.": "Nahum", "Hab.": "Habakkuk",
    "Zeph.": "Zephaniah", "Hag.": "Haggai", "Zech.": "Zechariah", "Mal.": "Malachi",
    "Matt.": "Matthew", "Mark": "Mark", "Luke": "Luke", "John": "John", "Acts": "Acts",
    "Rom.": "Romans", "1 Cor.": "1 Corinthians", "2 Cor.": "2 Corinthians", "Gal.": "Galatians",
    "Eph.": "Ephesians", "Phil.": "Philippians", "Col.": "Colossians", "1 Thess.": "1 Thessalonians",
    "2 Thess.": "2 Thessalonians", "1 Tim.": "1 Timothy", "2 Tim.": "2 Timothy", "Titus": "Titus",
    "Philem.": "Philemon", "Heb.": "Hebrews", "James": "James", "1 Pet.": "1 Peter",
    "2 Pet.": "2 Peter", "1 John": "1 John", "2 John": "2 John", "3 John": "3 John",
    "Jude": "Jude", "Rev.": "Revelation",
}


def load_counts():
    path = os.path.join(ROOT, "app", "src", "test", "resources", "bible", "kjv_verse_counts.csv")
    counts = {}
    with open(path) as f:
        next(f)
        for line in f:
            line = line.strip()
            if line:
                b, c, n = line.split(",")
                counts[(b, int(c))] = int(n)
    return counts


def pdf_text(pdf_path):
    out = tempfile.mktemp(suffix=".txt")
    subprocess.run(["pdftotext", "-layout", pdf_path, out], check=True)
    return open(out).read()


def window(book, ch, vs, ve, counts):
    total = counts[(book, ch)]
    if vs == 1 and ve == total:
        return {"book": book, "chapter": ch}
    return {"book": book, "chapter": ch, "verseStart": vs, "verseEnd": ve}


def expand_spec(book, spec, counts):
    m = re.match(r"^(\d+):(\d+)-(\d+):(\d+)$", spec)
    if m:
        c1, v1, c2, v2 = (int(x) for x in m.groups())
        out = [window(book, c1, v1, counts[(book, c1)], counts)]
        for c in range(c1 + 1, c2):
            out.append({"book": book, "chapter": c})
        out.append(window(book, c2, 1, v2, counts))
        return out
    m = re.match(r"^(\d+):(\d+)-(\d+)$", spec)
    if m:
        c, v1, v2 = (int(x) for x in m.groups())
        return [window(book, c, v1, v2, counts)]
    m = re.match(r"^(\d+):(\d+)$", spec)
    if m:
        c, v = int(m.group(1)), int(m.group(2))
        return [window(book, c, v, v, counts)]
    m = re.match(r"^(\d+)-(\d+)$", spec)
    if m:
        return [{"book": book, "chapter": c} for c in range(int(m.group(1)), int(m.group(2)) + 1)]
    m = re.match(r"^(\d+)$", spec)
    if m:
        return [{"book": book, "chapter": int(m.group(1))}]
    raise ValueError(f"unparseable spec {spec!r} for {book}")


def norm_slot(field, counts):
    field = re.sub(r"\s*:\s*", ":", field.strip().rstrip(".")).strip()
    refs = []
    cur = None
    for part in re.split(r"\s*,\s*", field):
        part = part.strip()
        book = None
        spec = part
        for tok, full in sorted(TGCBOOK.items(), key=lambda x: -len(x[0])):
            if part == tok or part.startswith(tok + " ") or part.startswith(tok + "."):
                book = full
                spec = part[len(tok):].lstrip(". ").strip()
                break
        if book:
            cur = book
        if spec == "":
            refs.append({"book": cur, "chapter": 1})
            continue
        refs.extend(expand_spec(cur, spec, counts))
    return refs


def parse_tgc(text, counts):
    text = text.replace("–", "-").replace("—", "-")
    day_re = re.compile(
        r"(Jan|Feb|Mar|Apr|May|June|July|Aug|Sept|Oct|Nov|Dec)\.?\s+(\d{1,2})\s+(.+?)"
        r"(?=(?:Jan|Feb|Mar|Apr|May|June|July|Aug|Sept|Oct|Nov|Dec)\.?\s+\d{1,2}\s|$)")
    raw = {}
    for line in text.split("\n"):
        for m in day_re.finditer(line):
            mon = MONTHS[m.group(1)]
            d = int(m.group(2))
            body = re.sub(r"\s*:\s*", ":", m.group(3).strip())
            raw.setdefault((mon, d), body)
    days = []
    for key in sorted(raw):
        mon, d = key
        slots = [s.strip() for s in raw[key].split(";") if s.strip()]
        # A bare-number slot (TGC 'Jer. 36; 45' splits to '...36','45') is a SAME-book 2nd chapter.
        merged = []
        for sl in slots:
            if re.match(r"^\d+$", sl) and merged:
                merged[-1] = merged[-1] + "," + sl
            else:
                merged.append(sl)
        slots = merged[:4]
        portions = [{"stream": i + 1, "refs": norm_slot(s, counts)} for i, s in enumerate(slots)]
        days.append({"month": mon, "day": d, "portions": portions})
    return days


def main():
    pdf = sys.argv[1] if len(sys.argv) > 1 else None
    if pdf is None:
        pdf = tempfile.mktemp(suffix=".pdf")
        req = urllib.request.Request(TGC_URL, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req) as r, open(pdf, "wb") as f:
            f.write(r.read())
    counts = load_counts()
    days = parse_tgc(pdf_text(pdf), counts)
    assert len(days) == 365, f"expected 365 TGC days, got {len(days)}"
    for m, n in enumerate(DAYS_PER_MONTH, start=1):
        got = sum(1 for x in days if x["month"] == m)
        assert got == n, f"month {m}: expected {n}, got {got}"
    doc = {
        "schemaVersion": 3,
        "planId": "mcheyne",
        "name": "M'Cheyne (Carson/TGC second source)",
        "source": "The Gospel Coalition / D.A. Carson RtB_Reading-Plan_2020.pdf "
                  "(independent witness to edginet/Haslam)",
        "anchoring": "DATE",
        "dayCount": 365,
        "streams": [
            {"number": 1, "title": "Family — Old Testament"},
            {"number": 2, "title": "Family — Gospels"},
            {"number": 3, "title": "Secret — Psalms & Prophets"},
            {"number": 4, "title": "Secret — Epistles"},
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
