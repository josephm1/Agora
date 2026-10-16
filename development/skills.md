# Skills Architecture Contract

Status: authoritative; simplified by owner request and extended with template sequencing, 17 October 2026.

## Product and editing

A Skill is a content-descriptive Markdown filename, a concise description (aim for 5–10 words,
maximum ten), an optional shortcut and Markdown instructions. Settings → Skills uses the established
System Prompt list/detail editor with those four main fields, optional Before/After/continuation
choices (see below), and explicit Save/Back. Back
cancels unsaved edits. Saved rows can be reopened or deleted; there are no standalone Markdown
import/export or duplication actions. Native application archives and backups retain Skills.

All tool names, call examples, Alpine filesystem paths and other Skill filenames belong in the
instructions. No tool/folder/child dependency forms, Android file/folder grants, Skill UUIDs or hidden
rename aliases. Renaming a referenced Skill or directory requires updating the referring text.
The name should describe its contents. Filenames containing spaces are supported in quoted commands.

## Persistence

SkillManager owns app-private skill_db Markdown and skill_meta.json descriptions/shortcut index.
Shortcut and native template-sequencing settings travel in a leading agora-skill/v1 comment;
instructions remain Markdown.
Read previous draft comments while discarding removed configuration on an explicit edit. No Skill
body enters Room. Keep path containment, normalized .md names, no-overwrite creation, expected-content
comparison on editor saves, atomic body replacement/recovery and existing rollback behavior.
File operations run off Main. Failures retain user drafts and report errors.

Catalog revision invalidates only consumers. Settings list and /skills discovery read filenames and
small metadata, never all Skill bodies. Shortcut resolution uses the small index then reads only the
selected file. Descriptions are visible in local Settings; model discovery lists filenames only.
The selected description and full instructions are returned by read_skill_file or invoke_skill.
Ordinary default prompts remain catalog-free. Explicit {skill_catalog} uses filenames only and no
bodies/descriptions. Opening Settings or a local picker makes no model request.

## Runtime and paths

Existing accessSkills controls discovery/read/modify tools. Reuse ordinary capability loading,
GenerationToolExecutor, authorized batch identity, durable tool segments, timeouts and error handling.
There is no Active Skill, separate agent, Provider, queue, Run or context builder.

The existing list/read/create/edit/delete tools remain available to authorized requests.
invoke_skill loads one named Skill and returns a snapshot and actual tool-call token. Instructions
can call another Skill by filename with that parent token. Completed selected-path tool records own
ancestry. Reject unknown parents, cycles, depth beyond six, more than 24 calls per Run or retained
instruction snapshots beyond 240 KB. Edits affect new roots; an already used child snapshot stays
pinned within its root invocation. Required dependency forms and tool preflight lists are removed.

Paths are plain text for the existing Alpine Linux terminal integration. Use the established shell
file_glob/file_read/shell tools and existing permissions; no Skill-specific file adapter, private
reference collection, SAF linking or automatic path repair. Only list/read selected reference material
when needed. Disabled/missing terminal access or stale filenames fail through ordinary tools.
The local Linux sandbox remains F-Droid-only and needs setup/enabling; shared storage uses its
existing /mnt/shared mount. Pi-native session discovery is outside this draft.

/skills shows filenames and optional shortcuts; selection prepares a quoted /skill filename command
without sending. Explicit /skill and unambiguous shortcuts use the existing composer freeze/admission/
clearing path. Template shortcuts retain precedence and editor conflicts are rejected.

## Create from a discussion

create-skill.md is packaged authoring guidance, loaded on explicit invocation only, with a user-saved
file of that name taking precedence. It does not seed files or read conversation owners at startup.
It formalizes the current discussion into a reusable workflow, without gathering unrelated chats.
Create with AI appends an unsent request to invoke it in the exact originating draft, preserving
existing text and attachments through the admitted composer owner and cancellation-safe release.

propose_skill returns an ordinary completed InteractiveAnswer artifact with a SkillDocument draft.
It performs no library mutation. Its deterministic escaped HTML presents name, short description,
shortcut and complete instructions, with a readable fallback. It uses the existing inline/expanded
viewer, independent of the generic AI-generated interactive-answers permission: its markup is
app-owned and has no generated script. Skill modify access remains required.

Expanded Review and save Skill opens the same four-field native editor. A deliberate Save verifies
the exact originating artifact on the selected conversation path, checks template shortcut conflicts,
and uses SkillManager's no-overwrite save. Editing the draft cannot target another chat or silently
overwrite an existing file. Cancel leaves the library untouched. Only completed propose_skill
artifacts with a valid draft obtain this action; ordinary render_interactive_answer rejects drafts.
No arbitrary interface JavaScript receives storage authority. Archive/fork transport uses existing
structured tool result serialization; no extra draft store, table or generation lifecycle.

## Verification

Cover name-based save/rename/missing references, stale-save rejection, shortcut conflicts, atomic
body recovery, instruction-only edits, filename-only discovery, selected description/body reads,
quoted commands, nested call provenance/cycles/budgets/pinning, four-field editor save/cancel,
review artifacts and disabled/oversized/malformed drafting. Existing composer race/cancellation and
unrelated-owner preservation checks remain mandatory. Run both flavor suites, build-logic and Kotlin
size gates and both preview APK builds. Physical/e-ink acceptance belongs to the owner.

## Slash discovery presentation

Typing `/` now offers local templates and Skills in one composer picker; `/skills` retains a
Skills-only filter. Rows show a command/name and approximately five words of description from
small metadata, without reading instruction files or putting descriptions into model context.
Template name/shortcut conflicts take precedence and duplicate Skill commands/names are omitted.
Skill selection fills the explicit quoted filename command, preserving the draft until Send.
The same row component serves connected Remote native Skills (see remote-sessions.md), using
separate session-owned sources and no remote-to-local persistence. Normal theme styling and
static high-contrast e-ink styling share input, accessibility and bounded-list behavior; e-ink
adds the existing list page controls rather than another scrolling owner.

## Skill-owned template workflow (17 October 2026)

Owner revision supersedes the four-fields-only restriction: an optional Workflow section selects
Before template, After template and Continue with Skill (after the latter's Continue gesture).
References are template IDs internally and Skill filenames. No During phase. Before opens locally
at composer admission; Start invokes the originating Skill. complete_skill is the explicit end
signal and presents its configured After template as an ordinary completed tool artifact. It never
infers completion from an assistant reply, Stop or Run settlement. Continue starts the configured
next Skill through normal foreground admission, including its Before template if present. Opening,
selecting and closing UI never sends. Actual parent invocation tokens and ancestry bound cycles and
chain depth. Missing references fail without sending or silently skipping steps.

Routing is native snapshot metadata on the opened card, not template HTML or model prompt settings.
Templates remain reusable input/output interfaces. Legacy template-owned linkedSkill is read-only
compatibility; the metadata editor no longer offers it. New/edited Skill and template descriptions
are limited to ten words; old template catalogues remain readable.

A template can specify a local Alpine sourceDirectory. Opening lists bounded Markdown paths through
the existing sandbox, injects filenames locally into the interface, and snapshots the allowed
selection. Start validates exactly one selected relative path, canonical containment and bounded
readability, then includes only that file's text in the normal Skill request. No corpus or HTML is
sent to a model. No Android SAF bindings, file UUIDs, new provider loop or workflow execution engine.
