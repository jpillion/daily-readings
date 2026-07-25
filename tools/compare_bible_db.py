#!/usr/bin/env python3
"""Diagnose a bible.db byte-diff failure: did the CONTENT drift, or only the encoding?

WHY THIS EXISTS
---------------
The `data-rebuild` CI job asserts a strict byte-diff of zero between the committed
`app/src/main/assets/bible/bible.db` and a fresh re-derivation from the pinned sources. When
that assertion fails, `cmp` alone says only "byte N differs", which is not enough to act on --
it took five weeks of a red build to establish, by hand, that a month of failures were an
encoding artefact and not a data problem. This script answers that question automatically.

It distinguishes two very different failures:

  * CONTENT DRIFT (real) -- the schema or any row differs. A hand-edited asset, a mis-derived
    asset, or an upstream source change that actually altered the text. Exit 1.
  * ENCODING DRIFT (toolchain) -- every row identical, bytes differ. SQLite stamps its own
    version into the header (offsets 96..99) and its page-slack handling depends on build flags,
    so a different SQLite library writes different bytes for identical data. Exit 0; the CI job
    still fails on the `cmp`, but the log now names the cause.

CI pins the writing library (SQLite 3.43.2 built with SQLITE_SECURE_DELETE, LD_PRELOADed) so the
byte-diff is genuinely deterministic -- see docs/data/README.md, "Toolchain pinning". If this
script ever reports ENCODING DRIFT in CI, that pin has slipped.

Python stdlib only. Also useful locally, where your system SQLite almost certainly differs from
the pinned one:
  python3 tools/compare_bible_db.py --committed <old.db> --rebuilt <new.db>
"""
import argparse
import hashlib
import sqlite3
import struct
import sys
from pathlib import Path

# Tables compared row-for-row, with the ORDER BY that makes the comparison canonical.
TABLES = (
    ("translation", "id, code, name, language, is_public_domain, copyright", "id"),
    ("book", "book_no, name, usfm_code, testament, chapter_count", "book_no"),
    ("room_master_table", "id, identity_hash", "id"),
    (
        "verse",
        "translation_id, verse_id, book_no, chapter, verse, native_label, is_title, text_markup",
        "translation_id, verse_id",
    ),
)


def sqlite_writer_version(path: Path) -> str:
    """The SQLITE_VERSION_NUMBER the writing library stamped at header offsets 96..99."""
    with path.open("rb") as f:
        header = f.read(100)
    if header[:16] != b"SQLite format 3\x00":
        sys.exit(f"FATAL: {path} is not a SQLite database")
    n = struct.unpack(">I", header[96:100])[0]
    return f"{n // 1_000_000}.{(n // 1000) % 1000}.{n % 1000}"


def canonical_rows(path: Path):
    """Schema + every row, as a flat list of strings, in a fixed order."""
    con = sqlite3.connect(f"file:{path}?mode=ro", uri=True)
    try:
        out = [
            f"SCHEMA|{name}|{sql}"
            for name, sql in con.execute(
                "SELECT name, sql FROM sqlite_master WHERE sql IS NOT NULL ORDER BY name"
            )
        ]
        for table, cols, order in TABLES:
            for row in con.execute(f"SELECT {cols} FROM {table} ORDER BY {order}"):
                out.append(table + "|" + "|".join("" if c is None else str(c) for c in row))
        return out
    finally:
        con.close()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--committed", required=True, type=Path)
    ap.add_argument("--rebuilt", required=True, type=Path)
    args = ap.parse_args()

    old_bytes = args.committed.read_bytes()
    new_bytes = args.rebuilt.read_bytes()
    old_writer = sqlite_writer_version(args.committed)
    new_writer = sqlite_writer_version(args.rebuilt)

    print(f"committed : {args.committed}")
    print(f"            {len(old_bytes)} bytes  sha256 {hashlib.sha256(old_bytes).hexdigest()}")
    print(f"            written by SQLite {old_writer}")
    print(f"rebuilt   : {args.rebuilt}")
    print(f"            {len(new_bytes)} bytes  sha256 {hashlib.sha256(new_bytes).hexdigest()}")
    print(f"            written by SQLite {new_writer} (this interpreter: {sqlite3.sqlite_version})")
    print()

    old_rows = canonical_rows(args.committed)
    new_rows = canonical_rows(args.rebuilt)
    if old_rows != new_rows:
        print("::error::CONTENT DRIFT - the committed bible.db does not match a fresh rebuild.")
        print("  This is a DATA problem, not a toolchain one. Do not regenerate the asset to make")
        print("  it green: find out why the derivation changed.")
        print(f"  committed rows: {len(old_rows)}   rebuilt rows: {len(new_rows)}")
        for i, (a, b) in enumerate(zip(old_rows, new_rows)):
            if a != b:
                print(f"  first difference at canonical row {i}:")
                print(f"    committed: {a[:300]}")
                print(f"    rebuilt  : {b[:300]}")
                break
        else:
            longer, label = (
                (old_rows, "committed") if len(old_rows) > len(new_rows) else (new_rows, "rebuilt")
            )
            extra = longer[min(len(old_rows), len(new_rows)):][:5]
            print(f"  rows present only in the {label} file (first 5): {extra}")
        return 1

    print(f"CONTENT: identical ({len(old_rows)} canonical rows - schema + every table row).")

    if old_bytes == new_bytes:
        print("BYTES:   identical (strict byte-diff of zero).")
        return 0

    differing = sum(1 for a, b in zip(old_bytes, new_bytes) if a != b)
    print(
        f"BYTES:   differ ({differing} bytes; sizes {len(old_bytes)} vs {len(new_bytes)}) - but "
        "every row is identical, so this is ENCODING DRIFT, not a data problem."
    )
    if old_writer != new_writer:
        print(
            f"         Cause: the asset was written by SQLite {old_writer} and this rebuild used "
            f"{new_writer}. In CI that means the pinned-SQLite preload slipped - fix the pin "
            "(see docs/data/README.md, 'Toolchain pinning'). Locally this is expected."
        )
    else:
        print(
            f"         Both files claim SQLite {old_writer}, so the version stamp does not explain "
            "this. Check the build flags - SQLITE_SECURE_DELETE changes freed-page slack."
        )
    return 0


if __name__ == "__main__":
    sys.exit(main())
