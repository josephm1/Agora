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
