#!/usr/bin/env python3
"""VA-T3 — tools/build_bible_db.py — the KJV bible.db import pipeline (ESpec-v3 §7.1).

Deterministic importer:
  PRIMARY corpus  : open-bibles eng-kjv.osis.xml (OSIS; carries transChange added words +
                    separate <title type="psalm"> superscriptions).
  book table      : populated ONLY from book_catalog_export.json (D-S9-1; never authored).
  output          : app/src/main/assets/bible/bible.db — three tables translation/book/verse
                    (D-V3-2: audio/versification/user tables cut), byte-deterministic.

It also emits the second-source witnesses from the INDEPENDENT corpus
  SECOND corpus   : scrollmapper formats/csv/KJV.csv (different upstream/lineage)
  -> kjv_verse_counts.csv (1,189 rows) and kjv_superscriptions.csv (the titled set).

Determinism: fixed PRAGMAs, rows inserted in verse_id order, no timestamps, VACUUM at end.
Re-running from the pinned sources reproduces a byte-identical bible.db (the data-rebuild
CI job asserts a byte-diff of zero).

Decisions: D-V3-2/5/6/7/11. Markup contract (§4.4): V3.0 P0 emits ONLY <a>. <l>/<w> are
available in the primary (recorded in docs/data/README.md) but are NOT emitted in V3.0.

Usage:
  python3 tools/build_bible_db.py --osis <eng-kjv.osis.xml> --second <scrollmapper_KJV.csv>
"""
import argparse
import csv
import hashlib
import json
import re
import sqlite3
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
EXPORT = ROOT / "app/src/test/resources/bible/book_catalog_export.json"
DB_OUT = ROOT / "app/src/main/assets/bible/bible.db"
COUNTS_OUT = ROOT / "app/src/test/resources/bible/kjv_verse_counts.csv"
SUPER_OUT = ROOT / "app/src/test/resources/bible/kjv_superscriptions.csv"

# Room schema identity hash for BibleDatabase v1 (entities=[VerseEntity], exportSchema=false).
# Source of truth: BibleDatabase_Impl.createOpenDelegate() -> RoomOpenDelegate(1, "<hash>", ...).
# Re-derive after any VerseEntity change (see the verse-DDL comment in main()).
ROOM_IDENTITY_HASH = "8144e1bc57f05006d1a15856ac762552"

OSIS_NS = "{http://www.bibletechnologies.net/2003/OSIS/namespace}"

# OSIS osisID -> BookCatalog order, for the 66 canonical books only (Apocrypha excluded).
OSIS_TO_ORDER = {
    "Gen": 1, "Exod": 2, "Lev": 3, "Num": 4, "Deut": 5, "Josh": 6, "Judg": 7, "Ruth": 8,
    "1Sam": 9, "2Sam": 10, "1Kgs": 11, "2Kgs": 12, "1Chr": 13, "2Chr": 14, "Ezra": 15,
    "Neh": 16, "Esth": 17, "Job": 18, "Ps": 19, "Prov": 20, "Eccl": 21, "Song": 22,
    "Isa": 23, "Jer": 24, "Lam": 25, "Ezek": 26, "Dan": 27, "Hos": 28, "Joel": 29,
    "Amos": 30, "Obad": 31, "Jonah": 32, "Mic": 33, "Nah": 34, "Hab": 35, "Zeph": 36,
    "Hag": 37, "Zech": 38, "Mal": 39, "Matt": 40, "Mark": 41, "Luke": 42, "John": 43,
    "Acts": 44, "Rom": 45, "1Cor": 46, "2Cor": 47, "Gal": 48, "Eph": 49, "Phil": 50,
    "Col": 51, "1Thess": 52, "2Thess": 53, "1Tim": 54, "2Tim": 55, "Titus": 56, "Phlm": 57,
    "Heb": 58, "Jas": 59, "1Pet": 60, "2Pet": 61, "1John": 62, "2John": 63, "3John": 64,
    "Jude": 65, "Rev": 66,
}
# scrollmapper Book name -> order (its names match BookCatalog canonicalName).
NAME_TO_ORDER = None  # filled from export

# scrollmapper uses Roman-numeral prefixes and "Revelation of John"; map to canonical names.
SECOND_NAME_FIX = {
    "I Samuel": "1 Samuel", "II Samuel": "2 Samuel", "I Kings": "1 Kings", "II Kings": "2 Kings",
    "I Chronicles": "1 Chronicles", "II Chronicles": "2 Chronicles",
    "I Corinthians": "1 Corinthians", "II Corinthians": "2 Corinthians",
    "I Thessalonians": "1 Thessalonians", "II Thessalonians": "2 Thessalonians",
    "I Timothy": "1 Timothy", "II Timothy": "2 Timothy",
    "I Peter": "1 Peter", "II Peter": "2 Peter",
    "I John": "1 John", "II John": "2 John", "III John": "3 John",
    "Revelation of John": "Revelation",
}


def verse_id(book_no, chapter, verse):
    return book_no * 1_000_000 + chapter * 1_000 + verse


def normalize_ws(s):
    return re.sub(r"\s+", " ", s).strip()


# Documented text reconciliations (Sprint-1 OVERRIDES discipline). The primary OSIS corpus
# carries 4 verse defects vs the INDEPENDENT second-source witness (scrollmapper) AND
# authentic KJV; each corrected here and logged in docs/data/README.md (the V3 reconciliation
# table). key = (book_no, chapter, verse) -> corrected text_markup. We only override where the
# independent source AND the known KJV reading agree against the primary; genuine inter-edition
# variants (1 Sam 7:1 fetched/brought, 2 Kgs 2:20 bring/brought) are NOT overridden.
TEXT_OVERRIDES = {
    # 1 Chr 11:2 — primary DOUBLES the closing clause "and thou shalt be ruler over my people
    # Israel" (second source + authentic KJV have it once).
    (13, 11, 2): "And moreover in time past, even when Saul <a>was</a> king, thou <a>wast</a> "
                 "he that leddest out and broughtest in Israel: and the LORD thy God said unto "
                 "thee, Thou shalt feed my people Israel, and thou shalt be ruler over my people "
                 "Israel.",
    # Ezek 17:24 — primary DOUBLES "I the LORD have brought down the high tree ... to flourish".
    (26, 17, 24): "And all the trees of the field shall know that I the LORD have brought down "
                  "the high tree, have exalted the low tree, have dried up the green tree, and "
                  "have made the dry tree to flourish: I the LORD have spoken and have done "
                  "<a>it.</a>",
    # Lev 17:8 — primary reads "burnt offering OF sacrifice"; KJV reads "burnt offering OR
    # sacrifice".
    (3, 17, 8): "And thou shalt say unto them, Whatsoever man <a>there be</a> of the house of "
                "Israel, or of the strangers which sojourn among you, that offereth a burnt "
                "offering or sacrifice,",
    # Isa 47:11 — primary typo "put IF off"; KJV reads "put IT off".
    (23, 47, 11): "Therefore shall evil come upon thee; thou shalt not know from whence it "
                  "riseth: and mischief shall fall upon thee; thou shalt not be able to put it "
                  "off: and desolation shall come upon thee suddenly, <a>which</a> thou shalt "
                  "not know.",
    # Matt 5:30 — primary typo "cut IF off"; KJV reads "cut IT off".
    (40, 5, 30): "And if thy right hand offend thee, cut it off, and cast it from thee: for it "
                 "is profitable for thee that one of thy members should perish, and not "
                 "<a>that</a> thy whole body should be cast into hell.",
}


def local(tag):
    return tag.split("}", 1)[-1] if "}" in tag else tag


def sha256(path):
    h = hashlib.sha256()
    h.update(Path(path).read_bytes())
    return h.hexdigest()


# ---------------------------------------------------------------------------
# PRIMARY: parse the OSIS into {(order,chapter,verse): markup} + titled set.
# OSIS uses milestone verses: <verse sID=.. n=..> text ... <verse eID=../>. Text and
# inline <transChange type="added"> live as tail/text between the sID and eID milestones.
# Psalm/Hab titles are <title type="psalm"> elements sitting between chapter open and v1.
# ---------------------------------------------------------------------------
def parse_osis(osis_path):
    """Robust raw-XML-segment parser.

    OSIS milestone verses are flat: <verse osisID="Gen.1.1" sID="..."/>BODY<verse eID="..."/>.
    The BODY (including inline <transChange type="added">added</transChange>) lives as raw XML
    *between* the sID milestone and the matching eID milestone. We slice the raw book XML on
    those milestones and convert each BODY segment by: transChange-added -> <a>..</a>, then
    strip every remaining tag keeping inner text. This avoids ElementTree text/tail
    double-counting (which duplicated clauses around nested inline elements).

    Psalm/Hab superscriptions are <title type="psalm">..</title> elements between a chapter
    open milestone and verse 1; we capture them as verse 0.
    """
    raw = Path(osis_path).read_text(encoding="utf-8")
    verses = {}
    titles = {}

    # isolate each canonical book's raw XML span
    book_open = re.compile(r'<div type="book" osisID="([A-Za-z0-9]+)"[^>]*>')
    opens = [(m.group(1), m.start()) for m in book_open.finditer(raw)]
    bounds = []
    for i, (osis_book, pos) in enumerate(opens):
        nxt = opens[i + 1][1] if i + 1 < len(opens) else len(raw)
        bounds.append((osis_book, pos, nxt))

    verse_milestone = re.compile(
        r'<verse osisID="([^"]+)" sID="[^"]*"[^>]*/>(.*?)<verse eID="[^"]*"\s*/>', re.DOTALL)
    # A chapter superscription is a <title type="psalm"> that appears AFTER the chapter open
    # and BEFORE that chapter's verse 1 (intervening <p>/<lb/> dividers are allowed, e.g. the
    # "Book II" rubric before Ps 42). We slice each chapter's preamble (open -> first verse sID)
    # and look for the title there.
    chapter_open_re = re.compile(r'<chapter osisRef="[^"]*\.(\d+)" sID="[^"]*"[^>]*/>')
    title_in_preamble_re = re.compile(r'<title type="psalm"[^>]*>(.*?)</title>', re.DOTALL)
    first_verse_re = re.compile(r'<verse osisID="[^"]*" sID=')

    def to_markup(seg):
        # convert transChange added -> <a>..</a>; keep inner text of everything else
        seg = re.sub(
            r'<transChange type="added"[^>]*>(.*?)</transChange>',
            lambda m: " <a>" + re.sub(r"<[^>]+>", "", m.group(1)).strip() + "</a> ",
            seg, flags=re.DOTALL)
        seg = re.sub(r"<[^>]+>", "", seg)   # strip every remaining tag, keep inner text
        seg = (seg.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
               .replace("&quot;", '"').replace("&apos;", "'"))
        # re-protect the <a> we just emitted (they were plain text after &lt; restore? no:
        # we emitted literal <a>; the strip above already ran before; guard order below)
        return normalize_ws(seg)

    for osis_book, lo, hi in bounds:
        order = OSIS_TO_ORDER.get(osis_book)
        if order is None:
            continue  # Apocrypha
        chunk = raw[lo:hi]
        for m in verse_milestone.finditer(chunk):
            osis_id, body = m.group(1), m.group(2)
            parts = osis_id.split(".")
            v = int(parts[-1]); ch = int(parts[-2])
            # convert transChange first, then strip remaining tags (order matters: do the
            # <a> insertion, then strip non-<a> tags but keep our <a>)
            body2 = re.sub(
                r'<transChange type="added"[^>]*>(.*?)</transChange>',
                lambda mm: "\x01" + re.sub(r"<[^>]+>", "", mm.group(1)).strip() + "\x02",
                body, flags=re.DOTALL)
            body2 = re.sub(r"<[^>]+>", "", body2)
            body2 = (body2.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                     .replace("&quot;", '"').replace("&apos;", "'"))
            body2 = body2.replace("\x01", "<a>").replace("\x02", "</a>")
            verses[(order, ch, v)] = normalize_ws(body2)
        opens = [(int(m.group(1)), m.end()) for m in chapter_open_re.finditer(chunk)]
        for idx, (ch, start) in enumerate(opens):
            nxt = opens[idx + 1][1] if idx + 1 < len(opens) else len(chunk)
            preamble_end = chunk.find('<verse osisID=', start, nxt)
            if preamble_end == -1:
                continue
            preamble = chunk[start:preamble_end]
            tm = title_in_preamble_re.search(preamble)
            if not tm:
                continue
            ttxt = re.sub(r"<[^>]+>", "", tm.group(1))
            ttxt = (ttxt.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                    .replace("&quot;", '"').replace("&apos;", "'"))
            ttxt = normalize_ws(ttxt)
            if ttxt:
                titles[(order, ch)] = ttxt
    return verses, titles


# ---------------------------------------------------------------------------
# SECOND: scrollmapper CSV -> verse counts + (independent) plain text for the gate diff.
# ---------------------------------------------------------------------------
def parse_second(csv_path):
    counts = {}            # (order, ch) -> max verse
    plain = {}             # (order, ch, v) -> plain text
    with open(csv_path, newline="", encoding="utf-8") as f:
        r = csv.DictReader(f)
        for row in r:
            name = row["Book"].strip()
            name = SECOND_NAME_FIX.get(name, name)
            order = NAME_TO_ORDER.get(name)
            if order is None:
                sys.exit(f"FATAL: second source book '{name}' not in catalog")
            ch = int(row["Chapter"]); v = int(row["Verse"])
            counts[(order, ch)] = max(counts.get((order, ch), 0), v)
            plain[(order, ch, v)] = normalize_ws(row["Text"])
    return counts, plain


def main():
    global NAME_TO_ORDER
    ap = argparse.ArgumentParser()
    ap.add_argument("--osis", required=True)
    ap.add_argument("--second", required=True)
    args = ap.parse_args()

    books = json.loads(EXPORT.read_text(encoding="utf-8"))
    NAME_TO_ORDER = {b["name"]: b["book_no"] for b in books}
    by_order = {b["book_no"]: b for b in books}

    print("parsing primary OSIS ...")
    verses, titles = parse_osis(args.osis)
    print(f"  primary verses: {len(verses)}, titles: {len(titles)}")

    print("parsing second source ...")
    counts, plain = parse_second(args.second)
    print(f"  second chapters: {len(counts)}, second verses: {len(plain)}")

    # --- emit witnesses (deterministic order) ---
    with open(COUNTS_OUT, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["book", "chapter", "verse_count"])
        for (order, ch) in sorted(counts):
            w.writerow([by_order[order]["name"], ch, counts[(order, ch)]])

    with open(SUPER_OUT, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["book", "chapter"])
        for (order, ch) in sorted(titles):
            w.writerow([by_order[order]["name"], ch])

    # --- build verse rows: body verses from primary + superscriptions at v0 ---
    rows = []  # (verse_id, book_no, chapter, verse, native_label, is_title, text_markup)
    for (order, ch), title_markup in titles.items():
        vid = verse_id(order, ch, 0)
        rows.append((vid, order, ch, 0, "", 1, title_markup))
    for (order, ch, v), markup in verses.items():
        if v == 0:
            continue
        markup = TEXT_OVERRIDES.get((order, ch, v), markup)
        vid = verse_id(order, ch, v)
        rows.append((vid, order, ch, v, str(v), 0, markup))
    rows.sort(key=lambda r: r[0])

    if DB_OUT.exists():
        DB_OUT.unlink()
    con = sqlite3.connect(DB_OUT)
    con.execute("PRAGMA page_size=4096")
    con.execute("PRAGMA encoding='UTF-8'")
    # ---- VERSE TABLE DDL MUST MATCH ROOM EXACTLY (KJV-load-fix) ----
    # The `verse` table is the ONLY table Room maps (VerseEntity). Room's createFromAsset
    # validates the bundled DB against the structure Room generates for the entity, so this
    # DDL is byte-for-byte the CREATE TABLE statement emitted by BibleDatabase_Impl
    # (translation_id/verse_id are the composite PK; NO foreign keys, NO secondary index —
    # VerseEntity declares neither, and an unexpected FK or index makes Room's onValidateSchema
    # reject the asset). `translation` and `book` are NOT Room entities (Room never reads them),
    # so their DDL is free-form and unchanged. Room also requires room_master_table carrying the
    # identity hash for schema version 1; the hash below is the one in the generated
    # BibleDatabase_Impl.createOpenDelegate() (RoomOpenDelegate(1, "<hash>", ...)). To re-derive
    # it after any VerseEntity change: build the app, read app/build/.../BibleDatabase_Impl.kt
    # (or set exportSchema=true once and read app/schemas/.../BibleDatabase/1.json identityHash),
    # and update ROOM_IDENTITY_HASH below. The Robolectric Room-open test (BibleDatabaseRoomOpenTest)
    # is the durable guard: if this hash or DDL drifts from the entity, that test goes red.
    con.executescript("""
        CREATE TABLE translation (
          id INTEGER PRIMARY KEY, code TEXT NOT NULL UNIQUE, name TEXT NOT NULL,
          language TEXT NOT NULL DEFAULT 'en', is_public_domain INTEGER NOT NULL DEFAULT 1,
          copyright TEXT);
        CREATE TABLE book (
          book_no INTEGER PRIMARY KEY, name TEXT NOT NULL, usfm_code TEXT NOT NULL UNIQUE,
          testament TEXT NOT NULL, chapter_count INTEGER NOT NULL);
        CREATE TABLE `verse` (`translation_id` INTEGER NOT NULL, `verse_id` INTEGER NOT NULL, `book_no` INTEGER NOT NULL, `chapter` INTEGER NOT NULL, `verse` INTEGER NOT NULL, `native_label` TEXT NOT NULL, `is_title` INTEGER NOT NULL, `text_markup` TEXT NOT NULL, PRIMARY KEY(`translation_id`, `verse_id`));
        CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT);
    """)
    con.execute(
        "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)",
        (ROOM_IDENTITY_HASH,))
    con.execute(
        "INSERT INTO translation (id,code,name,language,is_public_domain,copyright) "
        "VALUES (1,'KJV','King James Version','en',1,NULL)")
    for b in books:
        con.execute("INSERT INTO book VALUES (?,?,?,?,?)",
                    (b["book_no"], b["name"], b["usfm_code"], b["testament"], b["chapter_count"]))
    for (vid, order, ch, v, label, is_title, markup) in rows:
        con.execute("INSERT INTO verse VALUES (1,?,?,?,?,?,?,?)",
                    (vid, order, ch, v, label, is_title, markup))
    con.commit()
    con.execute("VACUUM")
    con.commit()
    con.close()

    body = sum(1 for r in rows if r[5] == 0)
    titlec = sum(1 for r in rows if r[5] == 1)
    print(f"wrote {DB_OUT}: {body} body verses + {titlec} superscriptions = {len(rows)} rows")
    print(f"primary OSIS sha256: {sha256(args.osis)}")
    print(f"second CSV  sha256: {sha256(args.second)}")


if __name__ == "__main__":
    main()
