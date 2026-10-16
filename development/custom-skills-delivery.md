# Custom Skills draft delivery

This draft is stacked on codex/share-files-from-apps. The owner's simplified four-field model
supersedes the earlier custom-skills-plan.md design.

Implemented:
- Shared Settings list/detail editor: name, concise description, shortcut and instructions.
- Reopen/edit/Save/Back and delete. Removed import/export/duplicate actions and tool/folder/child forms.
- Filename references instead of Skill UUIDs/rename maps. Tools, examples, Alpine paths and child
  filenames live directly in Markdown. Reuses existing terminal tools; removed Skill-specific SAF
  and file-wrapper providers.
- Filename-only discovery; selected description/body loading, indexed shortcut lookup and quoted
  filenames. Existing template/composer admission and draft preservation retained.
- Nested invocation snapshots, parent-token validation and cycles/depth/context limits through the
  ordinary tool loop. No required-dependency configuration.
- Packaged create-skill.md and propose_skill: discussion → review card → same native editor → Save.
  No automatic file creation or overwrites from a displayed draft.
- Existing atomic body recovery, stale editor-save rejection and native archive/backup transport.

Still pending: native Pi session discovery/transport/editing (external companion source), dynamic
filesystem discovery in the old OSCE HTML launcher, human translations and live-model/physical
Android/e-ink acceptance. Those are not prerequisites for the simplified local editor contract.
No device installation, paid model request or physical acceptance has been performed.

Validation of this revision is recorded in the PR after the final checks.
