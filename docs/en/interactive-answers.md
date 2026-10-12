# Interactive answers

Enable **Settings → Interaction → Interactive answers** to let your model create calculators,
charts, diagrams and other interactive answers. Ask, for example, “Make a bill splitter with
controls for people and tip” or “Show these values in an interactive bar chart.” Your selected
model must support tool calls. Open the completed answer using **Open interactive view**.

This feature uses Open Intelligent UI's styles and Websandbox. Calculations can run locally;
external visualization libraries and supported images require internet access. Update Android
System WebView if the viewer reports an unsupported browser. A readable answer remains available
when rendering fails. Follow-up buttons show their text for confirmation before sending to the
same conversation; they preserve your Composer draft. Links open in your external browser.

Closing and reopening resets controls. Saved answers travel with native conversation backups and
forks. Public Markdown sharing contains readable fallback text without executable code. Very large
interfaces are refused before saving; keep the complete generated payload under 80,000 UTF-8 bytes.
Agora's existing history-size protections can make old interactive content unavailable. There is
no progressive preview, template library, device profile or editable cross-turn artifact in v1.
