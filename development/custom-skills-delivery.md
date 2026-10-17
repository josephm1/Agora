# Custom Skills draft delivery

This draft is stacked on codex/share-files-from-apps. The owner's simplified four-field model
supersedes the earlier custom-skills-plan.md design. The later Before/After workflow group
extends those four main fields without reintroducing dependency/tool forms.

Implemented:
- Shared Settings list/detail editor: name, concise description, shortcut and instructions.
- Reopen/edit/Save/Back and delete. Removed import/export/duplicate actions and tool/folder/child forms.
- Filename references instead of Skill UUIDs/rename maps. Tools, examples, Alpine paths and child
  filenames live directly in Markdown. Reuses existing terminal tools; removed Skill-specific SAF
  and file-wrapper providers.
- Filename-only discovery; selected description/body loading, indexed shortcut lookup and quoted
  filenames. Existing template/composer admission and draft preservation retained.
- Nested invocation snapshots, parent-token validation and cycles/depth/context limits through the
  ordinary tool loop. No required-dependency configuration.
- Packaged create-skill.md and propose_skill: discussion → review card → same native editor → Save.
  No automatic file creation or overwrites from a displayed draft.
- Existing atomic body recovery, stale editor-save rejection and native archive/backup transport.

Still pending: native Pi session discovery/transport/editing (external companion source), human translations and live-model/physical
Android/e-ink acceptance. Those are not prerequisites for the simplified local editor contract.
No device installation, paid model request or physical acceptance has been performed.

Validation of this revision is recorded in the PR after the final checks.

Final validation of the simplified revision:
- F-Droid: 3,276 tests; Play: 3,259 tests; zero failures/errors, five skipped per flavor.
- Build-logic: ten tests passed; Kotlin source-size gate and diff whitespace checks passed.
- Packaged browser viewer checks passed, including sandbox isolation, gesture gates, reopen and
  conditional e-ink presentation. This is computer-side evidence, not device acceptance.
- Both arm64 Skills Preview APKs assembled successfully with the separate .skillspreview ID.
- Automated coverage includes four-field edit/save/cancel, native review of a chat-authored draft,
  completed-only artifact recognition and exact proposal identity, names-only discovery without
  decoding unrelated bodies, filename/rename behavior, invocation limits and composer ownership.

Slash-picker extension:
- `/` combines local templates/Skills with short metadata descriptions and duplicate suppression;
  `/skills` remains a Skills-only filter. Shared themed rows include the e-ink high-contrast variant.
- Optional native `runtime.skills` metadata supports connected Remote suggestions through the same
  UI and existing send path. It follows live-runtime revocation and is never saved to the library.
- The Android receiver is implemented; computer-side discovery/adapters must supply this optional
  metadata. Existing bridges without it remain unchanged. Codex-only connection qualification is
  retained; no claim of native Pi execution is made.

Slash-picker validation:
- Full F-Droid suite: 3,285 tests; Play: 3,268 tests; zero failures/errors, five skipped per flavor.
- Both arm64 preview APKs, build-logic tests and Kotlin size gate passed; diff whitespace clean.
- Inspected regular and e-ink component previews under app/build/reports/custom-skills/.
- Tests cover concise descriptions, typing/filtering, duplicate suppression, unsent selection,
  malformed unrelated Skill bodies, native command transport, optional protocol compatibility,
  incomplete native entries, session/hide/disconnect revocation and read-only fallback exclusion.
- Native agent discovery/dispatch has not been tested against a live computer bridge with this
  extension. No device installation, paid model request or physical/e-ink acceptance was performed.


Template workflow revision:
- Skills own optional Before/After interfaces and a next Skill filename; there is no During phase.
- Before opens locally; explicit complete_skill presents After using the real invocation snapshot.
  User Continue uses normal send admission and provenance; cycles, replay and missing inputs block.
- Folder-backed interfaces list real Markdown filenames through the existing Alpine sandbox;
  only the selected contained file is read at Start. Templates submit data, not routing commands.
- OSCE example packages setup/review HTML and examiner/revision Skills, no clinical corpus.
- Settings reuse the existing editor/dropdowns for regular and e-ink modes. Descriptions cap at
  ten words on new writes; legacy long descriptions and linked-Skill templates remain readable.
- Physical Android/e-ink, live-model quality and computer-side Pi adapters remain unverified.


Final template-workflow validation (17 October 2026):
- Full F-Droid suite: 3,298 tests; Play: 3,281 tests; zero failures/errors, five skipped per flavor.
- Both arm64 Skills Preview APKs built; build-logic's ten tests and Kotlin source-size gate passed.
- Packaged browser checks passed, including folder setup/review, exact selected source submission,
  regular selected-state contrast and black/white e-ink selection. Preview images are generated in
  app/build/reports/interactive-templates/; they do not establish device acceptance.
- Settings tests cover save/reopen of Before/After/continuation choices. Runtime tests cover explicit
  completion snapshots, compact native receipts, provenance, local-only setup, direct and setup-first
  continuation, cycle rejection, stale/repeated gestures, missing sources and directory containment.
- Generated the reference-free OSCE archive and checked that its template/Skill references resolve.
  Its setup identity is separate from the legacy example; Merge still preserves user conflicts.
- Expanded-view metadata uses demand hydration of its exact origin, preserving source folders on
  Save. Library opening captures its conversation before listing to prevent cross-chat retargeting.
- Diff whitespace checks passed. No device installation, paid model calls or physical acceptance.

## Combined device installation

The owner wants one installed app containing the cumulative changes, rather than separate feature
preview apps. Build from this cumulative branch with
`./gradlew -I scripts/combined-device-preview.gradle :app:assembleFdroidEinkPreview`. This opt-in init script keeps the existing
`com.newoether.agora.einkpreview` package identity and debug signature for data-preserving updates,
sets the launcher name to Agora, and includes the F-Droid Alpine integration. The retained package
suffix is an upgrade identity; this build includes Skills, templates, e-ink and all ancestor changes.
Install with `adb install -r` when requested. Do not introduce another per-feature package on the
owner's device. Normal production build configuration is unchanged.


Zero to Finals device migration (17 October 2026): moved all 300 exported article references into
`/sdcard/Zero to Finals/{Medicine,Surgery}/<specialty>/<condition>.md`, with SHA-256 verification
before and after replacing the old article Skills. Alpine uses `/mnt/shared/Zero to Finals`.
Only examiner/revision workflows remain in Skills. The existing setup identity now navigates real
folder levels locally and uses native Before/After/continuation settings. Fresh pre/post native
exports verified no intervening edits and the final reduced library. Recovery backups remain.
Eight Python tests and the nested-menu browser suite pass. Owner confirms Alpine installed/working;
no model exercise or physical panel acceptance is claimed. See zero-to-finals-folder-handover.md.


Restored OSCE layout delivery: compact Medicine/Surgery sections with 18 specialty buttons and
filename-derived counts, followed by condition selection. Applied through template-only native
import after fixing its importability check; installed the cumulative APK in place and inspected
the fresh device menu. Final checks: 3,300 F-Droid / 3,283 Play tests, zero failures/errors; five
skipped per flavor; both combined APKs, size/build-logic, eight Python tests and browser suite pass.
Offline initial tool audit reproduces 1,008 estimated tokens (916 raw + 92 buffer); see
initial-tool-context-audit.md. No paid model requests or exercise-quality acceptance claimed.
