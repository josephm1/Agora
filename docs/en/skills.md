# Skills

A Skill is a named Markdown workflow you can reuse in chat. Open **Settings → Skills → Add Skill**.
Enter a meaningful name, a short description (aim for 5–10 words), an optional shortcut and the
instructions. Put tool names, necessary call examples, exact Alpine terminal paths and other Skill
filenames directly in the instructions. Save explicitly; Back discards unsaved changes. Reopen any
saved Skill to edit it, or use its menu to delete it.

Use your existing Alpine terminal integration to list/read a path when needed. There is no separate
folder attachment or dependency form. Paths and filenames are literal references: if you rename a
file or folder, update the instructions that refer to it. The F-Droid sandbox needs setup/enabling;
shared Android storage uses its existing `/mnt/shared` mount. Play has no local Linux sandbox.

Type **/skills** to browse filenames and select one. Add your task to the prepared command and Send.
A configured shortcut also works unless a template already owns it. Discovery does not load every
Skill's content. Only the selected Skill's short description and instructions are loaded. Other
Skills can be called by filename through `invoke_skill` with the returned parent token, in the same
conversation. Existing tool permissions still apply.

At the end of a useful discussion, ask to **create a Skill from this discussion**, or use
**Create with AI** in Settings. That action adds an unsent request to the current draft; close
Settings, review and Send. The packaged `create-skill.md` authoring Skill asks the model to formalize
the workflow and present it using `propose_skill`.

The draft card shows its name, short description, shortcut and complete instructions. Expand it and
choose **Review and save Skill** to open the same native editor. Edit if needed, then Save. No Skill
is saved by merely displaying the card. Existing filenames cannot be overwritten by this action.
You can later edit the saved Skill in Settings. A user-saved `create-skill.md` overrides the packaged
authoring instructions.

There are no separate import or duplicate actions. Skills remain included in normal Agora backups
and native archives. Ordinary prompts do not automatically include the library; an advanced custom
`{skill_catalog}` variable explicitly opts into filenames only.
