# Focused Markdown sharing — 19 October 2026

Owner approved an additional answer action while retaining the existing Share icon unchanged.
The existing icon still shares its generation transcript through Android. The additional Checklist
icon is labelled **Choose content** and uses the same answer source as Copy (including citations).
It adds no Notes-specific destination, tool, settings, automatic return or background processing.

The preview is black on white with fixed Previous/Next navigation and two blocks per page. All
blocks are selected initially; the common case is deselecting the opening preface and closing
commentary. A heading offers Remove section/Keep section through the next peer/ancestor heading.
A block can be edited in the shared copy without changing the original answer. The original GFM
source offsets preserve code fences, lists, tables, escaping, links and line endings. Top-level
list items are separately selectable; code and tables remain one block. Long block previews are
truncated visually and Edit text exposes the complete source. No content is silently truncated in
the shared payload. All/None, page and block edits survive ordinary recreation through saved UI
state; parsing is demand-driven on a background dispatcher. Android sharing is launched only after
Share Markdown is explicitly pressed. Its existing inline/file fallback remains the only outgoing
transport. Closing/cancelling the preview changes no conversation or draft.

Notes in Kalamos-final independently registers a generic text/Markdown SEND receiver. It copies
incoming text/granted files, saves the current note through its ordinary close path, and uses the
existing Markdown importer. Reverse sharing continues through Share Markdown → Agora → chat picker.
Standard shares create independent copies; they do not replace old chat responses or maintain a
live link. User performs physical Android/e-ink acceptance.

Verification evidence is kept in Kalamos-final/artifacts/agora-markdown-selection-2026-10-19.
