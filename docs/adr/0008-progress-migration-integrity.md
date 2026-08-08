# ADR-0008 — `ProgressDatabase` migration integrity for already-shipped users

**Status:** Draft, pending owner sign-off · **Date:** 2026-08-08 · **Author:** Staff / Port Architect

## Context

Daily Bible Reading Planner is **live in production on Google Play** (1.8.1 / 10801, 100% rollout
in 177 countries since 2026-07-23). Real users have real reading history.

That history lives in one Room table:

```
reading_progress(plan_id TEXT NOT NULL, dateEpochDay INTEGER NOT NULL,
                 stream INTEGER NOT NULL, readAtEpochMillis INTEGER NOT NULL,
                 PRIMARY KEY(plan_id, dateEpochDay, stream))
```

`ProgressDatabase` is at **version 2**, with `exportSchema = true` and schemas checked in at
`app/schemas/…/ProgressDatabase/{1,2}.json`. The v1→v2 migration
(`data/progress/ProgressMigrations.kt`) is hand-written recreate-and-copy — SQLite cannot alter a
primary key in place — stamping every existing row with `'bible_companion'`. It is proven lossless
by `ProgressMigrationTest` (`MigrationTestHelper`, real exported schemas) and
`ProgressMigrationNoPerceptibleChangeTest`. **`fallbackToDestructiveMigration` is deliberately
OFF** (D-V3-15): a failed migration must be a loud crash, never silent data loss.

There is a second store of shipped user state: the DataStore preferences file `settings`
(`settings.preferences_pb`), holding theme, font scale, tracking-start date, selected plan,
reminder time and enablement, persistent-notification enablement, external Bible app, and every
first-run marker.

**The asymmetry that matters:** iOS starts empty. Every migration risk in this port is an
**Android** risk. The port cannot make iOS worse; it can absolutely brick Android.

## Decision

**Preserve both stores bit-for-bit, and prove it with fixtures rather than reasoning.**

### Hard rules

1. **`ProgressDatabase` stays at version 2.** The port introduces no schema change. If a schema
   change is wanted, it ships in a separate release *after* the port, never bundled with it.
2. **`exportSchema = true` stays on, and `app/schemas/…/ProgressDatabase/2.json` must remain
   byte-identical** through the port. That file is the tripwire. **If it changes at all, stop and
   escalate — do not regenerate the baseline.** A changed `2.json` means Room's computed identity
   hash changed, which means every existing user's database will be rejected on open.
3. **`fallbackToDestructiveMigration` stays off.** Not negotiable, not "temporarily during
   development".
4. `MIGRATION_1_2` is preserved verbatim in behaviour. Its SQL is rewritten only insofar as
   Room KMP's migration callback takes `SQLiteConnection` rather than `SupportSQLiteDatabase`;
   **the four statements and the `'bible_companion'` literal do not change.** There may still be
   devices in the wild at v1 (a user who has not updated since before 1.5.0), and the path from
   v1 must keep working forever.
5. **The DataStore file name (`settings`) and every key string are unchanged.** A renamed key is a
   silently reset user: their theme reverts, their tracking-start date is lost, their plan
   selection reverts to Bible Companion, and their first-run dialogs re-fire. Enumerate the keys
   from `data/prefs/SettingsRepositoryImpl.kt` and pin them in a test that asserts the literal
   strings — the same discipline the project already applies to plan-data literals.

### Acceptance criteria (these belong in the persistence task brief verbatim)

> **PG-1.** A `progress.db` produced by the shipped 1.8.1 Android build, containing marks across
> multiple years and at least two plans, opens on the ported Android build and returns identical
> results from every `ReadingProgressDao` query. Prove it with a **fixture database committed to
> the repo**, not with reasoning about hashes.
>
> **PG-2.** A `settings.preferences_pb` written by 1.8.1 is read back with identical values for
> every key by the ported Android build. Fixture-based, committed.
>
> **PG-3.** `app/schemas/…/ProgressDatabase/2.json` is byte-identical before and after the port.
> Assert it in CI with a checksum, the same way the plan and bible assets are guarded.
>
> **PG-4.** `ProgressMigrationTest` and `ProgressMigrationNoPerceptibleChangeTest` continue to
> pass. If `MigrationTestHelper` has no multiplatform equivalent (⟦VERIFY⟧ V4), **they stay as
> Android-only tests.** An Android-only migration test is entirely correct — migration is an
> Android-only concern.

### Sequencing

**The persistence port ships to Android first, alone, as a normal Play release, before any iOS
build exists.** Structural change to a live database is verified by Android users on the proven
Play pipeline, not co-mingled with a new-platform launch where a crash-on-open would be
ambiguous.

That means the port's Android-visible releases are ordered:
`1.8.x (today) → 1.9.0 "internal restructure, no user-visible change" → iOS 1.0.0`.

## Alternatives rejected

**Ship the restructure and the iOS launch together.** Rejected. If existing Android users start
crashing on open, you want exactly one variable to have changed. This also matches the lesson
the project already learned the hard way in 1.7.0 (a crash that only R8 exposed) — reduce the
number of things a release changes at once.

**Take the opportunity to clean up the schema** (e.g. drop `readAtEpochMillis`, add an index for
`getChapter`, normalise `plan_id`). Rejected, firmly. Refactor-during-port is how ports fail. File
the improvements; ship them later.

**Add `fallbackToDestructiveMigration` "just for the port" as a safety net.** Rejected, and worth
naming as an anti-pattern: it converts a loud, diagnosable crash into silent deletion of every
user's reading history. That is the opposite of a safety net.

**Migrate users' data to a new store as part of the port** (e.g. SQLDelight, or a fresh
schema). Rejected — all the risk, none of the reward, on the one table that holds irreplaceable
user data.

## Consequences accepted

- **The port is constrained by a schema we may not touch.** Any improvement to the progress
  schema is deferred behind the port. Accepted; the schema is fine.
- **An extra Android release** (the restructure-only 1.9.0) with no user-visible change, which
  costs a release cycle and a whatsnew entry that says essentially nothing. Worth it.
- **`ProgressMigrationTest` may remain Android-only forever**, meaning the migration is proven on
  one platform. Correct — it is a one-platform concern.
- Two committed fixture files (a `progress.db` and a `settings.preferences_pb`) enter the repo.
  Small, and they are the only honest way to prove PG-1/PG-2.
- If ⟦VERIFY⟧ V2 comes back saying Room KMP's exported `2.json` differs, **this ADR blocks the
  whole persistence phase** until we know why and whether the identity hash is affected. That is
  the intended behaviour of a tripwire.

## Revisit when

- V2 resolves — this ADR is not accepted until then.
- A genuine schema change is wanted (V4 features, per-verse marks, notes). At that point the
  normal migration discipline applies, and this ADR's constraints lift.
- Play crash vitals after the 1.9.0 restructure release confirm zero migration-related crashes —
  at which point the iOS launch is unblocked from this direction.
