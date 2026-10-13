# Dictionary MVP

The dictionary reads the Japanese/English component of JMdict. Attribution: James William Breen and EDRDG, [CC BY-SA 4.0](https://www.edrdg.org/edrdg/licence.html). [Documentation](https://www.edrdg.org/jmdict/edict_doc.html). The UI identifies English definitions. Vietnamese data and handwriting are not bundled. No source dumps belong in Git.

## Install and update

1. Build Java 21: `./mvnw.cmd package`. Migration V11 creates independent dictionary tables and two nullable server-owned provenance fields on cards.
2. Set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` for the intended database. Use MySQL 8 and utf8mb4. For a large import, append `rewriteBatchedStatements=true` to the JDBC URL (use `?` or `&` according to existing parameters).
3. From the backend root run `./scripts/import-dictionary.ps1 -Java <java21-path>`. It downloads the official English gzip, prints SHA-256, imports with no HTTP server and activates only after complete validation. Default minimum is 150,000 entries. Never set the small fixture minimum in production.
4. Start the normal API. `GET /api/v1/dictionary/sources` reports ready/count/version/import time. Check exact search, romaji via client, reading, English gloss and a private add-to-deck.
5. Deploy the frontend. Public reads are explicitly enumerated in SecurityConfig. Writes and deck picker always require JWT.

Keep the old gzip. Re-running the script with `-UseExistingSnapshot -Snapshot <old-gzip>` activates its READY version without reimporting, so it can also roll back. A failed/staging version never becomes active. Simultaneous imports of the same checksum are rejected. Do not delete an active version. A killed importer leaves STAGING: inspect it and delete only that inactive version's rows before retrying, or fetch a fresh snapshot. Existing cards retain their snapshots and attribution independently of dictionary tables.

The operator must run an update at least monthly under EDRDG's web dictionary terms. Add this script to the deployment host's existing scheduler after confirming its database credentials and log destination; this change does not create a machine-wide scheduled task. Alert on nonzero exit or an import older than a month. Keep at least the previous READY version, plus any version in use by drafts/cursors. No automatic destructive cleanup is installed.

## APIs

- Public GET `/api/v1/dictionary/search?q=...&kana=...&limit=20&cursor=...`: literal exact/prefix forms, selected deinflection and tokenized English meanings. `kana` is an optional kana alternative supplied by WanaKana; original Latin query stays searchable. Cursor binds query, kana, limit and active version. Changing version requires restarting search. Candidate pool is bounded to 250 ranked entries; this is not an exhaustive corpus browse API.
- Public GET `/api/v1/dictionary/words/{jmdict:ID}?version=...` and `/dictionary/sources`.
- Auth GET `/api/v1/decks/options?q=...&page=0`: 20 light deck options including grouped decks; no study progress aggregation. Owner-only; SYSTEM decks excluded.
- Auth POST `/api/v1/dictionary/words/{key}/cards` with `Idempotency-Key` (16–100 ASCII letters/digits/underscore/hyphen). Body: targetDeckId, datasetVersion, writtenForm, reading, senseIds, front, back, notes. The server validates source selections, active owner deck, text limits and creates a VOCABULARY card with no review history. Response has cardId/deckId/outcome/cardCount/available. `alreadyExists` preserves edits to the existing card.
- Auth POST `/api/v1/dictionary/decks` with name and Idempotency-Key creates a private MANUAL deck through the existing DeckService. This separate wrapper keeps existing deck-create clients compatible.

Request replay is scoped to owner + operation. A reused key with changed payload is a conflict. A completed operation whose card was moved/deleted returns available=false; a deliberate new add uses a new key. Request records are currently retained indefinitely; a future retention job must keep at least the documented client retry window (7 days). Cards have server-only dictionary identity/provenance; user-editable extraData is not trusted for deduplication. Sense identities hash source meaning, restrictions, POS and notes rather than import ordinal. A changed/ambiguous meaning becomes a different identity instead of silently merging.

## Transactions and limitations

Search caches public ranked results for 60 seconds, keyed by active dataset version and normalized query/alternatives/limit. It coalesces simultaneous loads, bounds the cache to 32 entries and approximately 16 MB, and never caches errors or account writes. Pagination reads the same ranked candidate list; a catalog switch produces a different cache key. This is not a replacement for gateway rate limiting.

Mutations use an owner lock shared by dictionary, existing card add/edit/delete/reorder/move and deck writes; deck/card reads for mutation lock current rows. This deliberately trades parallel writes within one account for simpler correctness with existing move/delete paths. Independent users do not share that lock. Dictionary identity stays with a moved card; moving into an existing identical entry/reading/sense is rejected atomically. Deletion frees identity for a new deliberate add. Sharing/copy preserves server provenance and identity; public preview has an attribution marker, without exposing private metadata.

The first schema uses server-owned fields on `cards` and a matching composite index rather than separate link/active-key tables. All participating writes are serialized by owner. Do not introduce a new direct card writer without participating in that protocol. Generic imported/manual cards are not automatically treated as dictionary duplicates just because their text happens to match.

Typed search supports one conjugation layer: polite, negative, past/te forms, selected godan/ichidan/する/来る, i-adjectives; every result must match dictionary POS. Compound/progressive/passive/causative forms are not claimed supported. There is no fuzzy typo search or substring scan for the main dictionary. Empty datasets return 503. `app.dictionary.enabled=false` disables dictionary controllers; `app.dictionary.cards-enabled=false` temporarily stops adds and deck creation while preserving reads.

## Validation

`DictionaryIntegrationTest` covers source parsing/internal DTD, external entity rejection, atomic failure, Unicode/kana, meaning tokens, ambiguity/cursors, grammar/POS, owner rights, retry keys, simultaneous normal/dictionary writes, moves/deletes and shared copies. Default tests use H2. To validate migration/collation/locking against an **isolated** MySQL database: `./mvnw.cmd '-Dtest=DictionaryIntegrationTest' '-Ddictionary.mysql.url=jdbc:mysql://127.0.0.1:13307/dictionary_checks' test` (local root/empty password test server only). Do not use production for tests.

Frontend browser flow: `PLAYWRIGHT_CHANNEL=msedge npm run test:dictionary` (PowerShell sets the env variable separately). The fixture browser tests complement real API/MySQL tests; they do not prove full-corpus performance.
