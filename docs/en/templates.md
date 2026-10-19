# Templates

Templates let you reuse an interactive interface without asking a model to rebuild it.
Open **Settings → Templates** to browse and search saved interfaces, just like Skills.
Select a template to edit its name, short description, source folder or interface code.

## Preview and edit code

Choose **Preview template** to try the current draft in the interactive viewer. Preview does not
add anything to a chat or run a Skill; chat submission buttons are disabled. Folder-backed templates
use Alpine to list reference filenames. If preparation fails, the editor displays the reason.

**Interface code (JSON)** contains the complete interface, including HTML, CSS and JavaScript.
Use **Copy code**, paste it into a chat, and ask for the changes you want. Ask for the complete
revised JSON object, then copy it back using **Paste code**. Paste the object itself without
Markdown code fences. You can also edit the field directly. Preview checks the current draft.

Press **Save** to apply changes. Invalid code cannot be saved. Back discards unsaved edits.
The template keeps its identity, so existing Skills still refer to it. Existing chat cards retain
their original snapshots. Editing here never modifies a Skill's instructions or workflow.

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
