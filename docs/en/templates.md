# Interactive templates

Templates let you reuse an interactive interface without asking a model to rebuild it.
Open **Templates** from the conversation drawer.

## Add and manage

Expand a completed interactive answer, then choose **Save as template** in its toolbar, or choose **Import** in the library
and select an Agora template JSON file. Imports store the interface without running it.

Search by name or description. Tap a row to open it in the current conversation. **Actions** offers
Edit template, Duplicate, Replace interface from file, Export and Delete. Edit changes the name,
description (at most ten words), optional shortcut and optional Alpine source folder; it does not
edit HTML or JavaScript. For a custom interface, edit its HTML/CSS/JavaScript in a template JSON file
and import it, or ask for an interactive answer and save that interface.
Replace changes the interface while retaining the template identity and metadata. Failed changes
preserve the previous version. Duplicate creates a separate template without a shortcut.

## User-defined shortcuts

Assign `study` in the Shortcut field to open that template by sending `/study`. Use lowercase
letters, digits and hyphens, starting with a letter; do not enter the slash. Shortcuts must be unique.
There are no built-in medical commands. Without an assigned shortcut, a slash command that
uniquely matches a saved template name also opens it locally. If several templates match, choose
from the slash menu; no AI request is made. Commands matching no saved template report that a template must be saved/imported; they do not
start an AI request.

Opening from the library or a shortcut runs locally and makes no AI request. Remove attachments
and wait for the current reply/submission before opening. Existing cards keep their saved interface
when you later edit or delete the library template. Reopening resets controls as with other
interactive answers. Swipe inside the interface to scroll its content; swipe outside it to scroll
the chat. At an embedded template’s top or bottom, continuing in that direction hands scrolling to the chat.
The default chat card offers only Expand. Save as template is available in the expanded viewer’s
toolbar; after a successful save it is disabled for that viewer visit to prevent duplicate taps.

## Start an AI workflow

A saved template submits its selected inputs immediately when you press its Run/follow-up button.
There is no second Send dialog. It uses the same conversation and preserves your Composer draft.
Generated interactive answers that have not been saved as templates retain prompt confirmation.
Templates collect or display data; Skills provide the instructions and own the sequence. In
**Settings → Skills**, select the template to run Before or After that Skill and optionally choose
the next Skill after the After interface. The interface submits choices through `sendPrompt`;
Agora applies the configured routing. The interface does not need to contain Skill instructions
or commands. The user must press Start or Continue for each transition. See [Skills](skills.md).

A folder-backed template can set **Alpine source folder**, for example `/mnt/shared/bmj`.
Opening it lists Markdown filenames locally through the existing Alpine sandbox. Its JavaScript
can use `globalThis.agoraSourceFiles` to build a menu and submit one exact relative filename in
`sourceFiles`. Start checks that selection and reads only that file. Paths remain literal; update
the folder setting when renaming the root and reopen the menu after renaming a file. Shared storage
must be enabled. Play has no Alpine sandbox. The HTML has no direct filesystem access.

Older templates with a linked Skill remain readable for compatibility; editing their metadata
clears that legacy link. Configure new workflows from the Skill editor instead.

With **Interactive answers** enabled, models can also list saved templates and open one when asked.
This uses a normal AI/tool turn; opening by shortcut or from the library needs no model call.
The existing sandbox and interface-size limits apply to all templates.

## Backup and examples

The **Interactive templates** native export/import and automatic-backup category carries the library.
Merge preserves conflicting template IDs/shortcuts and reports conflicts; Replace affects only the
selected library category. Archives without templates remain compatible. Skill files are backed up
through their existing category.

Type `/` in the chat composer to see your saved templates. Keep typing to filter by name or
shortcut; tap a template to open its local interface immediately. A shortcut is optional for menu
selection, and no AI request is made until the interface submits a prompt.

The repository includes a folder-backed OSCE example under `examples/interactive-templates/osce`.
Its setup chooses specialty, condition and question count; Start passes only the selected reference
to the examiner Skill. When the exercise finishes, a review interface leads to a revision Skill.
The portable example contains two Skills and two interfaces, with no medical articles or downloads.
Point its source folder at your own Markdown references. It is an example, not a built-in command.
