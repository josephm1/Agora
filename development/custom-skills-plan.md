# Agora custom Skills — consolidated implementation plan

16 October 2026. This supersedes the earlier proposal where they differ. It is an implementation
plan; no application implementation or physical-device testing is claimed.

## 1. Product model

A Skill contains reusable instructions, a name/description, optional shortcut, linked folders/files,
required existing tools, optional child-Skill dependencies and optional launcher template. It can
be created, edited, tested and reused through Settings or invoked through `/skills` and shortcuts.

Reference material stays in user-linked folders or uploaded collections. Only files selected/read
for a task enter model context. Supporting documents are not individually registered as Skills.
Ordinary chat continues to avoid loading the entire Skill catalogue.

Skills can invoke other Skills. Local composition uses the existing tool loop, model and conversation;
it does not spawn another agent, make an independent provider request or introduce another Run owner.
Remote-agent composition executes on that agent under its supported native semantics.

## 2. Ownership and compatibility

Extend SkillManager and the existing Skills Settings destination. Keep template storage/rendering,
the file importer, tool registry/executor, generation admission and Remote transport in their current
owners. Add implementation boundaries only for actual new invariants, such as persisted Android
folder grants and validated Skill invocation.

Retain existing Markdown Skills and their descriptions. Add stable identity and versioned optional
configuration without rewriting all existing bodies or treating filenames as permanent identity.
Perform a tested idempotent migration with backups/recovery and old-archive fixtures. Existing
read/create/edit tools remain compatible; updating an existing Skill preserves its identity.

Portable representation: SKILL.md with supported metadata, instructions and optional supporting
files. Use conventional name/description metadata where possible; namespace Agora-only configuration.
Do not claim arbitrary Agora tool configurations work unchanged in Pi or OpenClaw. Export reports
unsupported fields and folder links that need reconnection.

OpenClaw's useful reference is editable Markdown instructions teaching registered tool use,
relative resources and optional slash-to-tool dispatch. Pi provides a concrete remote discovery/
invocation protocol. Neither requires copying its complete runtime into Agora.

## 3. Settings → Skills

Use the existing System Prompt list/detail editor as the UI reference. Extend the current Skills
page rather than creating a second library. The list supports search, Add, Import, Edit, Duplicate,
Export and Delete, and identifies local versus connected-agent ownership.

The editor has:

| Group | Editable content |
|---|---|
| Identity | Name, description, shortcut and execution location. |
| Instructions | Full Markdown, with the System Prompt editor's typography and navigation. |
| Tools | Registered tool dependencies, availability and parameter documentation. |
| Folders/files | Linked device/remote folders, individual files and uploaded collections. |
| Other Skills | Child Skill links, required/optional status and usage guidance. |
| Call examples | Editable example arguments for tools and child Skills. |
| Optional launcher | Existing template linked to the Skill. |
| Test | Sample input, actual tool/Skill activity and inspectable errors. |

Save explicitly commits; Cancel/Back discards the unsaved draft using existing editor conventions.
Preserve the prior saved revision and reject stale overwrites. Saved entries can be reopened and
edited repeatedly. Editing dependencies should show missing or unavailable targets before testing.

Call examples are guidance, not automatic execution. An optional deterministic start call can be
added when a concrete workflow requires one, with its behavior explicitly labeled. Avoid a general
workflow graph or scripting language in the first implementation.

## 4. File access and uploads

Offer **Paste directory path**, **Choose folder**, and **Upload files**. A pasted path is paired
with its execution location: Local Sandbox or a named remote computer. Add a Check folder action
to verify availability and list a small metadata preview without a model request. Paths remain
editable and files stay in place.

Reuse the existing F-Droid Alpine/PRoot sandbox first for path-backed local Skills. It already
implements shell commands and structured file read/glob/grep/edit. Its default home is
`/home/agora`; permitted shared Android storage is exposed at `/mnt/shared`. Support copying
the checked sandbox path. If a user pastes an Android storage path, translate it only when the
configured mount mapping can be verified, and show the resulting sandbox path. Do not silently
interpret an Android path as a rootfs path or a remote path.

Sandbox support is F-Droid-flavor only, is disabled by default, and requires installing the rootfs.
Play builds have an unavailable implementation. Shared storage additionally requires its existing
enablement and Android access grant. Show actual readiness rather than implying a terminal exists
and is enabled on every installation. The Settings terminal display currently reports environment
management output; command execution is exposed through shell tools.

For folders outside this accessible filesystem or builds without sandbox support, link a folder
through the Storage Access Framework and retain its URI grant. Persist a
friendly alias and opaque binding identity; document-provider URIs are not shell paths. Support a
chosen existing folder without copying its contents. Uploaded documents can instead be copied into
an explicitly selected app-managed collection.

Reuse current file tools for sandbox/remote paths. For SAF-backed folders, provide the equivalent
bounded list/read operations and filename search first, then supported content search:

```text
list_files(folder="clinical-notes", path="Cardiology", cursor=...)
search_files(folder="clinical-notes", query="acute coronary", glob="*.md")
read_file(folder="clinical-notes", path="Cardiology/acs.md", offset=0, limit=...)
```

These are proposed API shapes, not currently available Android tools. Lists return metadata and
pagination. Reads report truncation and source identity. Search states its coverage. Validate
containment within the selected tree at the runtime boundary. Expose missing/revoked grants,
unavailable providers and unsupported formats with useful repair actions.

Reuse existing document/vision extraction where supported. Do not read a PDF or image as plain
text or describe incomplete extraction as complete. Only content actually read is sent to a model.
Folder listings and user browsing remain local until submitted as model context.

For configured computers/sandboxes, reuse existing file_glob/file_grep/file_read or the remote
agent's native tools. A literal ls can be used where shell execution is intended; structured listing
is the default for device browsing. Always bind reads to the selected host and folder.

Uploads show their destination: this device or a named computer/workspace. Remote delivery uses
verified transfer receipts and actual remote paths, not phone paths inserted into a remote prompt.
Linking does not copy files; importing/uploading does. Export can optionally bundle sources.

## 5. Tool configuration

The editor selects existing native, MCP or supported remote tools. Show their actual identities,
schemas, required arguments and availability. Skill instructions can explain when and how to use
them; editable examples can supply common argument shapes.

Declaring a tool does not implement it or grant new authority. At invocation, resolve declared
dependencies through existing admission and capability owners. Check the current user's enabled
access, selected execution location and applicable limits. Show missing dependencies rather than
inventing a fallback. Distinguish model guidance from any restrictions actually enforced by runtime.

Reuse on-demand capability loading without sending every tool schema or every Skill body on
ordinary requests. Keep Skill-file modification access separate from file-reading needs.

## 6. Skills calling Skills

Add a validated invocation operation, provisionally:

```text
invoke_skill(skill="clinical-source-reader", input="Find the requested ACS article")
```

The displayed name resolves to a stable Skill identity and execution location. The editor's child
selector saves that identity, so renaming a Skill does not break its callers. Free-form imported
references resolve explicitly; ambiguity is an error rather than first-match selection.

For a local call, the runtime validates the dependency and returns the selected saved instructions,
input and resolved dependency information through the normal tool result. The current model
continues with that Skill's procedure and tools. This gives executable composition through the
existing loop, rather than only pasting a child's name into a prompt. A child does not have a
separate hidden model, transcript, queue or Stop lifecycle.

Two forms of dependency are supported:

- Required: a missing/disabled child blocks the invocation with its exact missing dependency.
- Optional: a missing child is reported, and the caller may continue only as its instructions allow.

Do not preload every child. A selected invocation loads a child when actually called, unless the
parent explicitly requires its instructions before work begins. Inspect dependency metadata for
validation without injecting all bodies into the provider request.

Keep an invocation trace with parent/child identities, revisions and status under the existing
Run/tool-result ownership. Validate the same call-chain state during execution and recovery.
Detect A → B → A and direct self-calls; impose a bounded nesting depth and shared total call/context
budget. Repeated sequential uses with different inputs remain possible. Enforce limits in code,
not by asking the model to count. Begin with a small documented depth limit and revise only when
real workflows need it.

Children inherit the current execution authority and share Stop/cancellation. They cannot widen
permissions, change host/model or modify their parent's saved definition merely by declaring it.
Tool effects remain validated by the normal executor. Failure returns to the parent with an
inspectable error; it does not silently fall through to another Skill or environment.

Instructions from the current user and system retain precedence. Linked source files are data.
Edits to saved Skills affect future invocations; a current invocation retains the revisions it has
resolved. Persist the actual resolved instruction revisions with ordinary invocation records so
recovery cannot quietly replace them with new content. Do not create an application-global Active
Skill or append all used Skills to every future conversation.

Multi-turn exercises need branch-scoped continuation: preserve the selected exercise instructions,
resolved dependencies and relevant source results through ordinary conversation context/compaction.
If required source evidence no longer fits, retrieve the exact retained source or report that it
must be reloaded; do not claim a summary still contains the full article. Cover this in acceptance.

## 7. OSCE as the first composed workflow

Keep /osce user-configurable. A useful composition is:

```text
OSCE examiner
  → clinical-source-reader: list/search/read linked articles
  → feedback-format: consistent feedback and end-of-session summary, when needed
```

The source reader accepts the condition and folder binding, locates and reads the relevant source,
and reports missing material. The examiner owns the fictional scenario, one question per turn and
question count. Extract a feedback Skill only if its reuse is useful; do not split a clear examiner
instruction into many tiny Skills merely to demonstrate nesting.

Both chat-only invocation and the optional existing OSCE template use the same Skill and folder
bindings. A template selection can provide the exact condition directly, avoiding model-driven
listing when the user has already selected it. Adapt its hard-coded resource index to live folder
discovery deliberately; Markdown alone cannot change that interface.

Test factual grounding and educational quality separately from file retrieval and valid tool calls.
Support requesting additional questions and changing topics without losing source provenance.

## 8. Creating Skills with AI

Add Create with AI, Create from files and Save this workflow as a Skill entry points that lead to
the same editor. The normal conversation/tool pipeline drafts the instructions and configuration.

Flow: describe the goal → choose/link/upload material → inspect selected files → draft instructions,
tools and child dependencies → review/edit → save → test. Saving or browsing alone does not invoke
a model. AI creation never silently rewrites an existing Skill; updates identify the target and
show the proposed changes. Testing exercises the saved or explicitly labeled draft version.

## 9. Local shortcuts and remote /skills

Route slash commands by explicit target identity. Reuse the existing picker and composer ownership;
resolve collisions between template shortcuts and Skill shortcuts visibly. Existing registered
shortcuts retain their behavior through migration. Do not silently change an existing /osce owner.

In ordinary chat, /skills opens local Skills. In a Remote session it discovers the current agent's
Skills. Show host, project/user scope and availability. Pi uses get_commands and its native
/skill:name invocation; use the installed protocol's returned command rather than guessing names.

The reviewed Remote client is Codex-specific. Pi requires an intentional adapter/transport extension,
not removing the agent check. Qualify discovery, session identity, sends, busy behavior, streaming,
Stop and uploads while preserving existing Codex ownership and no-blind-retry guarantees.

Remote nested invocation uses the agent's native support. If it lacks a dedicated invoke operation,
its Skills may compose by reading referenced Skill instructions with its own tools; expose the
available behavior accurately. Do not claim Agora's local cycle/budget enforcement automatically
governs an independent remote agent. Do not implement nested remote steps as repeated synthetic
phone sends or attach a second writer to an existing session.

Remote edits save through a supported authoring path on the selected computer. If editing is
unsupported, provide an explicit export/copy path. Keep local copies and remote originals distinct.
Cross-host Skill calls are outside the first release; a deliberate remote tool action can be added
later without pretending local composition is remote execution.

## 10. Runtime presentation and persistence

Show a compact actual trace: Running OSCE → Reading clinical source → Reading selected article.
Distinguish waiting, completed, failed and cancelled operations. Allow inspection of loaded Skill
revisions, tool inputs/results and source files. Reuse existing tool presentation and errors.

Use crash-safe Skill saves, stale-edit checks, revision recovery and tested import/export conflict
handling. Keep credentials in existing secure settings, not the instruction body. Immutable test
records identify the instructions and source versions actually used; linked files remain live for
future reads unless explicitly snapshotted.

Interactive apps continue to use the existing isolated renderer. Save as template saves interface
code, not live user edits. Keep durable notebook work in Notes. Add durable generated-editor state
only for a concrete editor use case, with explicit save/checkpoint/restore and conflict semantics.

## 11. Delivery sequence

1. Record baseline and update Agora module contracts to the accepted scope. Capture existing Skill,
   template, slash, backup and Remote behavior before changing storage or routing.
2. Add backward-compatible Skill metadata, stable references, atomic revisions and migrations.
3. Expand Settings Skills with the System Prompt editor reference, including tools, folders and
   child Skills. Verify save/reopen/edit/cancel and import/export.
4. Implement Android folder bindings and bounded list/read/search; reuse remote file tools.
5. Add validated local invocation and nested composition through ordinary admission/tool owners.
6. Integrate the OSCE workflow and optional launcher, then add AI-assisted authoring and testing.
7. Add Pi Remote discovery/invocation, followed by supported remote editing/uploads and composition.
8. Run regression/build gates and deliver affected APKs for user-owned physical acceptance.

Each phase delivers a usable result; remote-agent work does not block local custom Skills.

## 12. Acceptance criteria

- Existing Skills, prompts, templates, shortcuts and archives remain usable.
- A user can create a Skill, link a real folder, select tools and child Skills, save, reopen and edit.
- A workflow reads selected files without preloading the corpus or unrelated Skills.
- Required/optional dependencies behave as declared; rename works, deletion is explicit, cycles and
  depth/context exhaustion stop with useful errors, and sequential reuse works.
- Stop, retry, stale results, process recovery and context compaction retain correct ownership.
- Tool declarations never create unavailable capabilities or expand execution authority.
- OSCE passes end-to-end multi-turn source, count, feedback and missing-source checks.
- Remote discovery, uploads and invocations stay with the chosen host/session, including reconnect
  and ambiguous delivery, with current Codex behavior preserved.
- Browser/JVM evidence, model-quality evidence and physical-device acceptance remain separate.

References checked during planning:

- https://docs.openclaw.ai/tools/skills
- https://docs.openclaw.ai/tools/creating-skills
- https://github.com/earendil-works/pi/blob/main/packages/coding-agent/docs/rpc-commands.md
- https://github.com/earendil-works/pi/blob/main/packages/coding-agent/docs/skills.md
