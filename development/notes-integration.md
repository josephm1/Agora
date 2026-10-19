# Kalamos Notes documents

Notes owns the mutable document, editable handwriting, semantic text/task bindings, originals,
working revisions and proposal acceptance. Agora owns immutable revision attachments and ordinary
completed tool proposals. This integration does not introduce another document editor, generation
pipeline or conversation graph.

The attachment menu opens the real Notes editor to write or pick a document. `NotesExchangeActivity`
is internal and records the exact Composer owner and request UUID before launch. The exported
`NotesInboxActivity` accepts only `com.kalamos.notes.action.ATTACH_DOCUMENT` with a granted content URI
and `application/vnd.kalamos.notes-document+zip`; external draft-owner hints never choose a destination.
Incoming unsolicited handoffs show the existing lightweight conversation list plus New Chat. A linked
owner is a label only. Neither path sends automatically. Cancellation preserves the draft.

`NotesExchangeStore` owns only durable handoff receipts and immutable imported bytes. It reads a
specific request after explicit entry, never scans inboxes or conversations at startup. Result UUIDs
must match the recorded request. The copied ZIP is validated before publication: 100 MiB compressed
and expanded limits, 1 MiB manifest, at most 32 pages, unique safe paths, exact reference/checksum sets,
SHA-256 verification, and bounded typed blocks. Files are never extracted under caller-supplied paths.
Notes revisions are opaque SHA-256 identities, not timestamps. Model-facing projection includes only
document/revision/block identities, semantic content and explicit user instructions; origin metadata,
private paths and vector sidecars are not sent. Supplied JPEG previews join ordinary image attachment
and transcription handling. Users control preview export in Notes.

`ConversationComposerController.attachPrepared` uses its existing session mutex and draft CAS.
Stable attachment IDs make repeated acceptance idempotent, reject identity collisions, preserve
newer draft text and refuse frozen submissions. The incoming request persists until the draft write
succeeds. Deleting the target chat is an error, never authority to retarget another draft. Opening an
attachment supplies its original immutable bundle so Notes can offer recovery if its local document
is gone. Historical attachments never update when Notes changes.

`propose_notes_document` uses the ordinary completed tool pipeline. Its bounded typed JSON becomes an
existing structured tool result in Room; there is no executable HTML and no automatic Notes mutation.
New proposal/block identities come from Agora. Existing IDs are retained. Updates carry documentId
and baseRevisionId; Notes owns final existence, binding and compare-and-set checks at review. A native
card offers Review in Notes. Ordinary completed assistant text also offers Create note in Notes,
converting supported paragraph/heading/list/task syntax for review. Existing interactive-answer HTML
remains unrelated and unchanged.

Native archives retain both the document ZIP and readable projection. Existing attachment reference
cleanup and fork cloning own the files. Message metadata adds optional document and revision fields,
with backward-compatible defaults. The backup format version and Room schema stay unchanged.

The installed preview is built from this checkout using `delivery/preview-build.gradle`; the regular
flavors consume the same integration. New dialogs use the established e-ink presentation helper.
Physical writing, inter-app navigation and panel acceptance remain user testing.

Verification covers corrupt/traversal bundles, task-state validation, host IDs, draft isolation,
idempotence/collisions, frozen/write-failure retries, archive ZIP retention, provider projection,
regular flavor tests and the existing arm64 e-ink preview build. A local build script absent from this
checkout (`build.ps1` in older prose) is not claimed as run; use the maintained Flatpak delivery script.

## Settings and non-tool models

Interaction settings owns Notes availability and the default inclusion of page images. Both defaults
are true; portable export/restore and legacy import preserve them. Generation admission freezes Notes
availability in the existing GenerationContext. The settings repository remains the authority, with
integration setters kept in its responsibility-specific extension file to respect the Kotlin size gate.

Completed fenced JSON answers may produce the same validated proposal when a model does not call
tools; IDs are supplied by the host and incomplete/invalid data never becomes applicable. Arbitrary
text still creates a new reviewed note, never a guessed update. Notes recovery from an immutable bundle
requires explicit user acceptance and is returned with a recovery marker for the recorded edit request.
New resource strings use English fallback text in the maintained locale files; no translated copy is
claimed. Physical model/pen/result delivery acceptance remains pending user testing.

Qualification: Play3071/F-Droid3088 tests pass; preview build/install and installed hash verify on Bigme12October2026. See kalamos-workspace/artifacts/notes-agora-2026-10-12/VERIFICATION.md for evidence. No physical pen/model acceptance claimed.

## 12 October 2026 navigation correction

The previous installed UI is rejected, not accepted. The external editor runs inside one temporary
exchange task with its own affinity; ordinary Agora MainActivity launch mode is unchanged. Explicit
entry reserves an active request pointer under the exchange store's launch lock. It reads one exact
receipt, never scans conversation owners. Another entry resumes that same live request and its original
draft; it cannot silently assign a new owner. A fresh admitted request starts a fresh temporary task,
avoiding platform reuse of an old root whose launch intent carries another UUID. Activity-result Notes
launches remain standard inside that task. Cancellation/receipt completion is durable. A recreated old
launch without its result registry cancels instead of launching again. Failed received data leaves an
error surface with Retry/Back; e-ink window treatment reuses the existing shared helper. MainActivity
removes consumed Notes extras from its intent as well as its UI state.

The Notes editor has a visible Attach to chat action. Back saves locally and returns without attachment;
review/options Back dismisses the sheet first. Note selection previews before attachment. Nothing is
sent automatically. Notes-origin toolbar integration closes its mutable editor after preparing the
revision and reloads the saved note on return. Notes enforces one live mutable editor per notebook.

Focused lifecycle tests cover normal/external WebView isolation, actual shell startup/router, awaited
close transitions, canceled/terminal/old-registry requests, durable pre-launch identity, return failure,
and a second draft attempting to enter an unfinished session. These supplement document and Composer
CAS tests. Mock activities do not prove firmware task behavior or physical pen acceptance.

## Dispatch lifetime — 13 October 2026

NotesLaunchGate protects only the launch coroutine, releasing in finally on success/error/cancel.
Do not keep an opening flag latched until an eventual activity resume event: canceled/reused tasks
and package updates can bypass the expected lifecycle. Durable live-session admission remains owned
by NotesExchangeStore. Launch-gate3 and exchange-activity6 tests pass; e-ink preview installed.
Physical silent-tap recovery is pending user verification. Logs record accepted operation/failure
without note/draft contents. Guidance review: Android Activity Result, Compose side-effects and UI
state/events documentation; see Kalamos apps/notes/docs/ANDROID-INTEGRATION-GUIDANCE.md.

## Multiple selected notes — 13 October 2026

Attach from Notes may return a single activity result with `documentCount` and ClipData containing
1–32 immutable Notes content URIs. The original request ID and captured owner remain authoritative;
only pick requests admit groups. The store validates/stages every document, rejects duplicate document
identities and more than 100 MiB total, then atomically publishes the parent receipt. Deterministic
child receipt IDs derive from parent and URI, preserving retry identity without confusing a changed
selection. ComposerPreparedAttachment commits the whole group under the ordinary session mutex/CAS
in one durable write; conflicts or failures expose none of the new group. Draft text, other attachments
and other owners are retained. No automatic Send. Existing single-result/recovered-edit paths remain.
Store2, exchange6, document5 and Composer attachment5 tests pass. Physical multi-select/return QA is
performed by the user. Notes picker reuses Kalamos Notes' existing shelf/search/selection/footer UI.

Handler/startup audit13October: incoming Notes UI returns immediately when no request exists and
collects conversation choices only while the destination chooser is visible. Compose incoming effects
and Activity lifecycle coroutines propagate cancellation rather than showing an import error/cancelling
receipts on rotation/disposal. The existing atomic composer commit intentionally remains NonCancellable.
Notes selection state is back in its already-ordered model; no new deferred startup handler or separate
hold recognizer. Detailed evidence: Kalamos apps/notes/docs/HANDLER-STARTUP-AUDIT.md. No startup speedup
or physical ink acceptance claimed without measurement/user testing.
