#!/usr/bin/env python3
"""VA-T2 — exportBookCatalog.

Emits app/src/test/resources/bible/book_catalog_export.json from the SINGLE source of
truth, BookCatalog.kt. No book rows are ever hand-authored (D-S9-1 anti-drift): this
script parses the catalog's Book(...) rows and derives testament from order. The importer
(build_bible_db.py) and the gate (BibleTextVerificationTest) both read this fixture.

Deterministic: rows ordered by book_no, sorted keys, trailing newline. USFM codes (D-V3-5),
never OSIS.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CATALOG = ROOT / "app/src/main/kotlin/com/jpillion/dailyreadingplanner/data/reference/BookCatalog.kt"
OUT = ROOT / "app/src/test/resources/bible/book_catalog_export.json"

ROW = re.compile(
    r'Book\(\s*order\s*=\s*(\d+),\s*canonicalName\s*=\s*"([^"]+)",\s*'
    r'chapterCount\s*=\s*(\d+),\s*blbAbbrev\s*=\s*"([^"]+)",\s*usfmCode\s*=\s*"([^"]+)"\s*\)'
)


def parse_catalog():
    text = CATALOG.read_text(encoding="utf-8")
    books = []
    for m in ROW.finditer(text):
        order = int(m.group(1))
        books.append({
            "book_no": order,
            "name": m.group(2),
            "usfm_code": m.group(5),
            "chapter_count": int(m.group(3)),
            "testament": "OT" if order <= 39 else "NT",
        })
    books.sort(key=lambda b: b["book_no"])
    return books


def main():
    books = parse_catalog()
    if len(books) != 66:
        sys.exit(f"FATAL: parsed {len(books)} books from BookCatalog.kt, expected 66")
    if [b["book_no"] for b in books] != list(range(1, 67)):
        sys.exit("FATAL: book_no sequence is not 1..66")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(books, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"wrote {OUT} ({len(books)} books)")


if __name__ == "__main__":
    main()
