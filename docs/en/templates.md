# Interactive templates

Templates let you reuse an interactive interface without asking a model to rebuild it.
Open **Templates** from the conversation drawer.

## Add and manage

Choose **Save as template** on a completed interactive answer, or choose **Import** in the library
and select an Agora template JSON file. Imports store the interface without running it.

Search by name or description. Tap a row to open it in the current conversation. **Actions** offers
Edit template, Duplicate, Replace interface from file, Export and Delete. Edit changes the name,
description, optional shortcut and linked Skill filename; it does not edit HTML or JavaScript.
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
interactive answers.

## Start an AI workflow

An interface can submit its selected inputs using a follow-up button. Agora shows the exact prompt
for confirmation, then sends it to the same conversation while preserving your Composer draft.
A linked Skill adds an instruction to read that saved Skill; import/manage Skills separately.
Missing Skills or disabled Skill access prevent Start until corrected.

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

The source repository includes a separate offline Zero to Finals OSCE example under
`examples/interactive-templates/osce`. Import its Skill and template, then assign `osce` yourself.
It is an example of the generic feature, not a built-in command or a connected medical library.
