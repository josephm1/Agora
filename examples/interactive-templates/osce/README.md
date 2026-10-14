# Zero to Finals offline OSCE pack

This proof of concept uses the complete public Medicine and Surgery topic indexes: 165 Medicine
and 135 Surgery entries. Publisher headings include some investigations, physiology and care topics
alongside conditions; their labels and grouping are retained rather than inventing a new syllabus.
Paediatrics, obstetrics/gynaecology, other sources and MCP are outside this pack.

The template is a normal user-owned interface. It contains labels and local source filenames,
not the clinical articles. `/osce` remains a user-assigned shortcut, not application logic.

## Prepare the personal pack

Install `beautifulsoup4` if needed, then run:

```sh
python3 examples/interactive-templates/osce/build_pack.py --output /path/to/private-output
```

This explicit preparation step downloads each article once using curl. It preserves headings,
bold emphasis, paragraph/list text and table rows as Markdown, with the source URL and download
time. Publisher navigation and the final member-site promotion are omitted. This text-only proof
of concept does not download illustrations or embedded videos. Original website content is not
edited. Clinical content is saved in the private output/cache, never added to this repository.

Rebuild with `--offline` to use only cached downloads. `--refresh` explicitly fetches updated pages.
A missing/unreadable article fails the build rather than producing an incomplete import pack.
The output includes `zero-to-finals-osce.agora`, the standalone template JSON and a source audit.

## Import on the device

1. Use a build containing the generic template-library feature.
2. Transfer `zero-to-finals-osce.agora` to the device.
3. In Settings → Data Control, import that native archive. Select **Merge** for the two categories:
   Memories/Skills and Interactive templates. The pack contains no API keys or conversation data.
   Merge preserves existing conflicting entries; do not use Replace on your existing library merely
   to import this pack. On a clean test installation it adds one examiner and 300 source files.
4. In Templates, edit Zero to Finals OSCE and assign `osce` (without `/`) if desired.
5. Enable Skill read access. The selected model must support Agora's local file tools.

Open from Templates, or type `/` in chat and tap Zero to Finals OSCE (typing `/os` filters the
list). Sending `/osce` directly also opens the interface when OSCE uniquely matches its name. An
assigned exact shortcut takes priority if more than one template matches. Choose a specialty under Medicine or Surgery, then a condition. Random chooses from the
current specialty, or the whole pack on the initial menu. The slider sets 1–40 questions, default 12. Browsing/selection/slider changes
are local and call no model. Run submits immediately without another confirmation, including the
examiner instruction, and attaches exactly the selected local article before the first model request. Neither input
requires a model tool call. The other 299 article names and contents are absent.
The examiner reads no other condition and conducts the original progressive one-question-per-turn
exercise. Further questions can be requested after the selected total.

**There is no website API, web fetch/search or MCP during case execution.** The ordinary AI provider
still receives the selected article and conversation to generate questions and feedback; source
retrieval is local. Missing source files stop the session rather than falling back to web or
unsupported claims. A download timestamp is provenance, not a claim that the guidance is current.

## Verification

The browser test uses the actual packaged Websandbox at mobile and desktop sizes. It verifies
Medicine/Surgery menu selection, all 300 labels, slider updates, local randomisation, reopening,
and one Run request containing only the selected filename. Native tests validate the import pack
and exercise the existing Skill tool against its downloaded files without model or network calls.
Physical Android import and an actual AI teaching session remain user acceptance checks.
