# Custom Skills draft delivery

This draft implements the local Agora portion of the approved custom-Skills plan, on top of
`codex/share-files-from-apps`. The broader design is in `custom-skills-plan.md`.

Implemented:

- Existing Skills Settings expanded into a shared list/detail Markdown editor with explicit save,
  reopen/edit/cancel, search, import/export, duplicate and delete.
- Saved tool dependencies and call examples; pasted sandbox/remote paths, Android folder/file
  linking, metadata checks, relative source listing and bounded reads.
- Child-Skill selection with required/optional dependencies; invocation snapshots, parent-token
  validation, cycles/depth/call/context limits and ordinary tool-loop execution.
- `/skills` selection into an editable composer command; explicit `/skill` and configured shortcuts
  use existing composer admission and draft clearing. Create with AI prepares an unsent request.
- Stable configured identities and retained legacy references across rename; stale-save rejection,
  atomic body replacement/recovery, normal Markdown/native archive transport and composed examples.

Still requires separate integration or acceptance:

- Native Pi agent-session transport, `/skills` discovery from that session and remote Skill authoring.
  This checkout's Remote service contract is Codex-specific; no companion Filo service source is
  present here. Configured remote shell folder access is implemented and is a distinct capability.
- Dynamic filesystem discovery inside the existing OSCE HTML launcher. The new composed Markdown
  example works through chat without replacing the existing user template or its shortcut.
- Dedicated file-copy upload collections; this draft links user-picked source files/folders and
  retains existing chat attachment upload behavior.
- Rich binary document extraction, arbitrary executable tool creation, fixed automatic start-call
  workflows and persistent generated-editor state. The new source wrapper rejects unsupported
  binary paths instead of claiming extraction.
- Human translation of the newly added English resource copy in each supported locale.
- Live model evaluation of the composed exercise and owner-owned Android/e-ink physical acceptance.

These are visible draft boundaries, not claimed completed features. No application was installed
on the user's device during this work.

Validation of the draft:

- F-Droid unit suite: 3,275 tests, zero failures/errors, five skipped.
- Play unit suite: 3,258 tests, zero failures/errors, five skipped.
- Build-logic suite: ten tests passed.
- `verifyKotlinFileSize` and `git diff --check` passed.
- Both arm64 preview APKs assembled successfully with the separate `.skillspreview` application ID.
- Automated coverage includes editor save/reopen/cancel, folder bindings, provider file access,
  invocation provenance and nesting limits, persistence recovery, and composer ownership/draft
  preservation. Physical-device and live-model behavior have not been accepted.
