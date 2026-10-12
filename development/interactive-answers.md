# Interactive answers

Owner request: optional interactive answers inside native Agora, reusing Open Intelligent
UI's browser renderer patterns. Device profiles and template management are outside v1.

## Ownership and flow

Settings DataStore owns `interactive_answers_enabled` (default false); portable settings
export/import/reset includes it. `GenerationRequestBuilder` freezes the value in the existing
`GenerationContext`. The built-in `render_interactive_answer` ToolProvider validates content
and returns ordinary model-facing fallback text plus an explicitly structured artifact.
Existing mailbox, tool-round and Room owners remain authoritative. No separate generation
backend, conversation graph, AG-UI client, Python agent or visualization router is introduced.

The tool accepts title, description, fallback, CSS, HTML, JavaScript functions and ordered
JavaScript expressions. `InteractiveAnswer` is the versioned persisted artifact; it lives in
existing structured segment JSON. Failed, stopped, incomplete, foreign and malformed results
never become executable. Ordinary Markdown HTML stays literal. No Room migration is required.
The transport parser rejects inputs over 1 MiB. The tool additionally rejects argument and
artifact JSON above 80,000 UTF-8 bytes before completion: Agora's generic result capture clips
at 100,000 characters and its aggregate segment budget is 600,000 bytes. These limits are not
bypassed. Existing aggregate-history trimming can make old interactive content unavailable;
the viewer must never execute partial code, and readable tool output remains the fallback.

## Presentation and bridge

A completed artifact has a native card with title, description and Open interactive view.
One native full-screen Dialog contains the trusted local host and the packaged Websandbox
opaque-origin iframe. Open/load is demand driven. Closing destroys ports, callbacks and WebView;
reopening reconstructs initial content and resets local controls. Selection/branch removal
revokes the viewer. No progressive preview, cross-turn DOM patching or durable control state.

Reuse upstream theme/form/SVG styles, import map, separated code fields and the final-frame
sandbox pattern. Use Kotlin for Android and plain JavaScript for the host; no maintained
TypeScript application. External libraries/images use upstream's explicit HTTPS domains.
Private files/content URIs, downloads, extra windows and arbitrary frame navigation are blocked.
Generated code has no direct Android interface, credentials, storage access or native port.
Import-map support is required; unsupported/hung/failed renderers show a readable fallback.

The only functional bridge methods are upstream-compatible `sendPrompt({text})` and
`openLink({url})`. Follow-ups require a user gesture, valid 1–4000-character text and native
confirmation of the exact text. Duplicate pending submissions are refused. Native submission
revalidates one saved artifact in the selected originating conversation and uses ordinary
foreground send/queue admission, preserving Composer text and attachments. Requests never
retarget another chat. HTTPS links open in the external browser after validation.

Native archives and forks preserve artifacts through existing segment serialization. The
public Markdown formatter includes only the readable fallback of this explicitly declared
interactive-answer artifact; generic tool payloads, reasoning and executable code stay private.
This is the artifact-public-content exception to the generic tool omission rule.

## Verification

Cover opt-in/snapshot behavior, malformed/oversized payloads, completed-only recognition,
ordinary persistence and native archive/fork behavior, public fallback-only sharing, owner
revocation, cancellation and existing tool execution identity gates. Browser tests cover a
calculator, SVG, sandbox isolation, validated actions, repeat open and offline failure, plus
an optional live Chart.js import. Run both flavor unit suites, build-logic tests, Kotlin size
gate and affected APK builds. Browser/JVM checks do not establish physical-device acceptance.
