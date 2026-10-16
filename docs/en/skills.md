# Skills

Skills are reusable Markdown instruction files stored locally by Agora.

**Allow skill access** makes the skill tools available. It does not add a filename catalog or skill
contents to ordinary requests. A skill is read when you explicitly request it or run a saved template
that names it. Supporting articles are read only when selected for that task.

For example, opening the OSCE menu is local. Pressing Run names the examiner skill and the selected
condition; it does not advertise all the other condition files. The original teaching instructions
remain available through that named skill.

There is no active-skill toggle. Advanced custom system prompts may explicitly include the
`{skill_catalog}` variable to opt into the full index; the default prompt does not. Existing
unmodified built-in defaults are migrated without changing their IDs or your selected prompt.
Custom prompts are preserved.

Skills are included in native exports and automatic backups. Import can merge them or replace the local skill database according to the selected import mode.

## Create and edit custom Skills

Open **Settings → Skills**, choose **Add Skill**, or reopen a saved row. The editor contains the
name, description, shortcut, Markdown instructions, required tool names, call examples, folders and
other Skills. Save publishes the changes; Back discards the editor draft. The overflow menu supports
duplication, Markdown export and deletion. Import Markdown retains saved Agora configuration.

**Create with AI** adds a request to the current chat draft. Close Settings, edit that request and
explicitly Send when ready. No model request is made while browsing or saving Settings.

## Link reference material

Paste an absolute directory path and choose its exact configured server name. **Local Sandbox**
uses `/home/agora`; permitted shared Android storage is mounted at `/mnt/shared`. The F-Droid
sandbox must be installed and enabled. Play builds do not provide a local Linux sandbox.

Alternatively, choose an Android folder or link an individual source file. Give the location a
short alias such as `clinical-notes`, then add it and Save. Android document links use their
persisted read grants and work without a Linux sandbox. Check previews filenames without asking
a model. If a grant is revoked or a folder moves, edit the Skill and reconnect it.

The file tools list metadata and read selected source text. Android folders return direct entries;
filesystem listings search at most four levels and return relative filenames. Results report
pagination/truncation. Binary documents need extraction rather than being treated as Markdown.
Folder grants and remote paths may need reconnecting after import on another device.

## Run and compose Skills

Type `/skills` in chat to choose a saved Skill. Selection prepares an editable `/skill` command;
add your inputs and explicitly Send. A configured shortcut works directly when it does not conflict
with a saved template. Existing template shortcuts keep their original behavior.

A Skill can call another declared Skill through `invoke_skill`. Required children must exist;
optional children can be omitted according to the parent instructions. Children run in the same
conversation and tool loop. The returned invocation token identifies a parent for nested calls.
Cycles, excessive nesting and per-Run call/context budgets fail explicitly. Existing user tool
settings remain authoritative; declaring a tool does not install it or grant permissions.

Configuration checks are local. A model test is a normal chat send and uses your selected provider.
The examples under `examples/skills` demonstrate an examiner calling a folder source-reader.
Agora's configuration comment, local URI grants and tool names are application-specific; importing
these Markdown files into another agent may require adapting the bindings.
