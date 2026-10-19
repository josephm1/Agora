# Templates

Templates let you reuse an interactive interface without asking a model to rebuild it.
Open **Settings → Templates** to browse and search saved interfaces, just like Skills.
Select a template to edit its name, short description, source folder or interface code.

## Preview and edit code

Choose **Preview template** to try the current draft in the interactive viewer. Preview does not
add anything to a chat or run a Skill; chat submission buttons are disabled. Folder-backed templates use Android to list reference filenames locally. No model request is made.
If preparation fails, the editor displays the reason.

**Interface code (JSON)** contains the complete interface, including HTML, CSS and JavaScript.
Use **Copy code**, paste it into a chat, and ask for the changes you want. Ask for the complete
revised JSON object, then copy it back using **Paste code**. Paste the object itself without
Markdown code fences. You can also edit the field directly. Preview checks the current draft.

Press **Save** to apply changes. Invalid code cannot be saved. Back discards unsaved edits.
The template keeps its identity, so existing Skills still refer to it. Existing chat cards retain
their original snapshots. Editing here never modifies a Skill's instructions or workflow.

## Reference folders and dynamic menus

In **Reference folder**, choose any folder through Android's picker. This is read-only access to
that selected folder and its subfolders; no all-files permission, Alpine installation or Shell
tools are needed. Access is remembered on this device. Imported templates need their folder
connected on each device.

**Find new Markdown files when opening** is enabled by default. Each fresh setup lists `.md` and
`.MD` filenames and subfolders in the background, then builds its menu locally. Adding, moving or
renaming files is reflected the next time you open the setup. An already-open menu retains its
snapshot. Only **Start** reads the selected file and attaches it before the Skill's API request.
A missing, unreadable or oversized file stops submission. Original files are never renamed.

Labels omit the Markdown extension and replace hyphens/underscores with spaces. **Hide filename
prefix** removes only the exact optional prefix you specify, preserving meaningful numbers.
**Refresh filenames** updates the editor's catalogue. Turn automatic discovery off to maintain
an explicit list of relative paths instead. No separate file check is needed: Start reads and validates
the selected file automatically before the request.
The current limit is 500 Markdown files, eight folder levels and 128 KB per selected reference.

**Copy for AI** includes the interface, catalogue configuration and its supported bridge contract;
it excludes document bodies, credentials and Android permission URIs. Ask for a complete
`agora.template-edit/v1` JSON object and use **Paste code**. Ordinary answer-only JSON remains
supported. Identity, folder connection and Skill instructions are preserved by interface edits.

Template JavaScript can use `window.agoraReferences` (`path`, `label`, `categories`) or await
`Websandbox.connection.remote.listReferenceFiles()` for the same native catalogue snapshot.
`window.agoraSourceFiles` remains available as exact relative filenames. The host provides these
before running the menu code. Browsing needs no AI; submit `sourceFiles: [selected.path]` through
the existing `sendPrompt` bridge only on Start. File paths, folder grants and reading remain native.

Old templates using **Alpine source folder** remain compatible. Choosing an Android folder switches
that template to native access. Shell tools can be disabled for OSCE without disabling native
folder access; templates do not change global tool settings automatically.

## Manage templates

In an expanded interactive answer, **Save as template** saves the reusable interface.
In Settings, **Import** loads a portable template JSON file. Each saved row's Actions menu offers
Duplicate, Replace from file, Export and Delete. Duplicate creates a new identity; replacement
preserves the selected template's identity and updates only its interface.

Templates collect or display data; Skills provide instructions and own the sequence. Configure
Before/After templates and the next Skill in **Settings → Skills**. Type `/` in chat to browse
Skills. Standalone templates and legacy template shortcuts do not start chat workflows.

## Backup and examples

The **Interactive templates** native export/import and automatic-backup category carries the library.
Merge preserves conflicting IDs and reports conflicts; Replace affects only the selected category.
Archives without templates remain compatible. Skill files use their existing backup category.

The repository includes a folder-backed OSCE example under `examples/interactive-templates/osce`.
It contains two Skills and two interfaces, with no medical articles or downloads. Point its source
folder at your own Markdown references. It is an example, not a built-in command.
