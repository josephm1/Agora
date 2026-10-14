# OSCE acceptance example

This is an abbreviated UI test catalogue, not a complete syllabus or source library.
Its topic structure is inspired by the [GMC MLA content map](https://www.gmc-uk.org/education/medical-licensing-assessment/mla-content-map.).
The reusable template feature contains no medical-specific registration or defaults.

1. Import `medical-osce-examiner.md` in Settings → Skills.
2. Import `osce.agora-template.json` in Templates.
3. Edit the template and assign the optional shortcut `osce` (without `/`).
4. Send `/osce`, or open the template from the library. Selection and randomisation run locally.
5. Set the question count and press Start. The existing native confirmation shows the exact
   prompt, including the linked skill instruction, before provider submission.

This example does not retrieve BMJ documents or ECGs. No MCP service is installed.
Replace the interface by importing another template file; edit the teaching skill separately.
