# Folder-backed OSCE workflow

The Skill owns the clinical conversation; templates collect inputs locally. This example contains
OSCE setup, OSCE review, an examiner Skill and a targeted-revision Skill. It includes no clinical corpus.

1. Put your Markdown references in an Alpine-accessible directory, for example
   `/mnt/shared/bmj/Cardiology/Heart failure.md`. Preserve meaningful real filenames.
2. Enable/setup the F-Droid Alpine sandbox and its shared-storage mount.
3. Run `python3 examples/interactive-templates/osce/create_workflow_pack.py /path/to/osce-workflow.agora`
   to package only the Skills and interfaces. Import its Memories/Skills and Templates categories
   using the normal native archive importer. Merge preserves existing conflicts. The folder setup
   has its own template identity so it can coexist with the old Zero to Finals example. If a Skill
   filename already exists, review the reported conflict before choosing which version to keep.
4. In Templates, edit OSCE setup's Alpine source folder to your actual directory if different.
5. In Settings → Skills, OSCE has Before = OSCE setup, After = OSCE review and Continue with =
   targeted-revision.md. Its shortcut is `osce`; clear an older template's conflicting `/osce`
   shortcut first if one was assigned. No user library items are overwritten automatically.

Invoke `/osce`. Opening lists Markdown paths locally and displays directories as specialties.
Choose Cardiology → Heart failure, select a question count and press Start. Browsing and selection
make no model request. Start validates the source and includes only its content and the chosen
options in the normal Skill request. The examiner asks one question at a time, then calls
complete_skill after the selected exercise is finished. The app shows the review interface;
Continue starts targeted revision, preserving the ordinary conversation and its reference.

The interface has no examiner instructions, Skill names or embedded medical catalogue. Its only
submission is selected input data. Renames need a fresh setup listing; missing/moved/unreadable
files reject Start. Another Skill can reuse these interfaces with different continuation settings.
No live model or physical e-ink acceptance is claimed.

The earlier Zero to Finals download script/catalogue remain legacy preparation/compatibility
resources. They are not the active folder-backed workflow and are not downloaded or executed by
Agora. The new pack generator never downloads or packages reference articles.
