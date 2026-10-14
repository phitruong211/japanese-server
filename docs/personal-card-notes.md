# Personal card notes

Migration V13 creates `personal_card_notes` and adds private-by-default sharing options to decks and groups. Deploy the backend migration before the frontend feature.

Authenticated endpoints:

- `GET /api/v1/card-notes/{sha256-key}`: returns text and revision for the current account; missing notes return version zero. Response uses no-store.
- `PUT /api/v1/card-notes/{sha256-key}` with `{ "text": "...", "version": 0 }`: maximum 5,000 characters. An owner lock serializes creation and updates; stale different text returns 409. Repeated identical text is safe after a lost acknowledgement.

Empty text is a versioned tombstone; resetting a review schedule or removing a bookmark does not delete notes. Keys are opaque and scoped to the authenticated owner. Personal card keys match the frontend SHA-256 of `PERSONAL:{UUID}`; built-in keys use the permanent source note_id.

PUT deck/group sharing accepts optional `{ "includePersonalNotes": true }`. Existing clients remain compatible. Defaults are false and revoking a link resets consent. Group consent is independent of individual deck consent. Explicitly included personal notes are copied into separate recipient note rows linked to the new card UUIDs, preserving source notes and independent progress. Existing copies are never updated or overwritten.

Tests: PersonalCardNoteIntegrationTest, PersonalCardNoteMigrationTest, DeckShareIntegrationTest and DeckGroupShareIntegrationTest. Database integration tests use isolated H2 databases; no production data is modified.
