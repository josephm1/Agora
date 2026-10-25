# Reverting the food-planner delivery

The PR separates three responsibilities into commits:

1. `8b47987c`: Deepgram settings visibility/localization fixes discovered by the full gate.
2. `ed87b4b5`: generic interface state, schema 40, archive/fork/deletion and migration tests.
3. Food-planner Skill/template/helpers/tests/docs: the following feature commit.

To withdraw only the food planner, revert the feature commit identified in the PR.
Keep the generic schema/state commit; no app-owned food screen or built-in shortcut
was registered. Already imported user templates/Skills are independent library
resources: disable/remove their `/food` Skill and template through normal Settings
if the user wants them removed. Do not delete unrelated chat state or library files.

Do NOT deploy a plain revert of the entire merge to devices that have opened schema
40: pre-change builds understand schema 39 and intentionally refuse a newer DB.
A full product rollback must retain schema 40, its nullable interactiveStateJson
column, migration registration and exported Room schema. Alternatively supply a
forward migration/new version that preserves user data. Never remove the database,
add a destructive fallback or install an older build over schema 40 as a shortcut.
The generic state payload can remain dormant safely while its UI bridge is disabled.

Native conversation backups/forks include interface state. The external prepared
recipe/catalogue folder is separate; retain its ZIP and on-device folder. Favourites
contain titles only. Schema 40 migration adds a nullable column without rewriting
messages, Runs, Todo, titles, pinning or attachments. Incremental baseline revision
2 requires one complete rebuild of older backups before reuse.

Owner-deferred items are not rollback failures: Linux Lidl fetcher connection,
live AI-provider check, device checks and the official OL550UK PDF import.
