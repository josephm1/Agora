# Interactive template library

Templates are user-owned reusable interfaces. The library reuses InteractiveAnswer and its
sandbox; it does not introduce another generator, renderer or provider pipeline.

## Storage and transport

InteractiveTemplate v1 contains a UUID, name, description, optional user-defined shortcut,
optional Skill filename, and a validated InteractiveAnswer payload. No application-specific
shortcut or example is registered automatically. The interface retains the 80,000 UTF-8-byte
persistence limit; a single portable JSON file is bounded to 100,000 bytes. UTF-8 imports are
strictly decoded. Library catalogues are capped at 16 MiB and atomically replaced under one
process-wide mutation fence. Corruption is reported; it never silently clears stored resources.

Save as template in the expanded viewer snapshots a completed generated answer or saved template
card. It revalidates the originating artifact and retains its linked Skill. The inline card offers
only Expand. The expanded Save action blocks repeat taps during saving and after success until the
viewer is reopened. Duplicating
assigns a new identity and clears the shortcut. Metadata edits and interface replacement preserve
identity. Shortcut names are unique, start with a lowercase letter, and contain only lowercase
letters, digits and hyphens (maximum 64 characters). Skill filenames are references, not bundled
Skill bodies. A missing or disabled linked Skill blocks submission but not local browsing.

The native `templates` category stores `templates/catalog.json`. Older archives without it leave
the library unchanged. Merge preserves ID/shortcut conflicts and reports them; Replace validates
the entire incoming catalogue before replacing only this library. Native automatic backups expose
the category alongside other resources. Existing explicit backup selections are not rewritten.

## Opening and provider boundaries

Typing `/` in an otherwise empty composer offers all saved templates, including those without a
shortcut. Further typing filters their names and assigned shortcuts. Tapping a row passes its
immutable template ID through the composer submission controller and opens the local interface;
there is no provider fallback even if the template was removed. Busy/frozen composers or attachments
disable menu selection. This menu keeps keyboard focus and appears above the input.

The composer freezes the input through its existing controller, then resolves exact registered
slash commands before provider admission. When no exact shortcut is registered, it uses the same
name/shortcut matches as the slash picker: one match opens locally; several matches preserve the
draft and ask for menu selection. An unmatched slash command also stays local and reports the
missing template instead of sending the command to the AI. Messages containing a command as
ordinary text follow normal Send. Attachments or a busy conversation prevent local opening and
preserve the input. Library Open and slash commands share InteractiveTemplateController.

Local opening takes the existing conversation execution lock, then commits one immutable
`interactive_template` segment in a completed local content Run. LOCAL_TEMPLATE_OPENED identifies
its origin; it does not fabricate a model completion, provider tool call or active generation slot.
The DAO rejects malformed local content and concurrent live Runs in the same transaction. New-chat
preferences/settings and selected branches use the existing atomic graph insertion boundary.
No provider, Compact or title-generation call is made.

Generated `open_interactive_template` tool results remain real tool results. list/open tools obey
the existing Interactive answers permission. Both local cards and tool-opened cards use the same
artifact decoder and renderer. Provider projection excludes wholly local cards and strips template
structured code from model-facing tool segments; readable tool results remain available.

Template cards snapshot their content and linked Skill reference. Library edits/deletion do not
change old cards. Existing renderer control reset, owner revocation, Websandbox restrictions and
e-ink presentation still apply. The native browser touch host retains vertical gestures begun
inside an interface while its scroll chain can move in the finger’s direction. A sandbox helper
reports root and touched nested-scroll limits through the validated message bridge, independent of
e-ink eligibility. At a boundary the native host allows chat interception, including mid-drag.
Pointer cancellation during Chromium touch panning retains the active target until touch end.
Sliders keep ownership; horizontal non-control gestures can yield to the drawer.
Only Expand appears below an inline interface; Save lives in the expanded toolbar. A saved template Run gesture sends immediately without a second
confirmation. Native artifact provenance selects this behavior for both inline and expanded views;
interface JavaScript cannot select it. Generated answers retain confirmation. Start includes any
Skill instruction, then rechecks card ownership, linked Skill availability and exact prompt before
normal Send. The existing host still requires an active user gesture and permits only one pending
follow-up; loading or changing controls cannot submit automatically.
Composer drafts remain separate. No arbitrary interface code obtains Android storage access.

## Ownership and checks

- templates/: format, validation, repository and shortcut resolution.
- ui/templates/: user CRUD and file pickers; no provider calls.
- InteractiveTemplateController: local graph placement and existing execution-lock coordination.
- InteractiveTemplateToolProvider: normal model list/open tools; no library mutations.
- Existing interactive owners: sandbox, inline/expanded presentation, verification and confirmation.
- Existing archive owners: additive template category and normal backup selection.

Tests cover storage, conflict/failed replacement preservation, local/provider boundaries, real
Room graph admission, composer bypass, library metadata editing, archive/fork preservation and
browser interaction. Both flavor tests, Kotlin size/build-logic checks and APK builds remain required.
Host tests do not establish Android physical-device acceptance.

## Nested-scroll references

Android documents the WebView/Compose nested-scroll limitation in
[Wrap a WebView in Compose](https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/wrap-webview-in-compose#nested-scrolling).
[Compose nested-scroll interoperability](https://developer.android.com/develop/ui/compose/touch-input/scroll/nested-scroll-modifiers)
requires cooperating views; an opaque iframe needs its own geometry report. This implementation uses
that report to select touch interception rather than trusting the outer WebView scroll range.
Chrome describes boundary propagation as
[scroll chaining](https://developer.chrome.com/blog/overscroll-behavior); CSS alone cannot transfer
gestures into Agora’s native chat container.
