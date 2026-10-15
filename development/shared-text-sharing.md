# Incoming Android shared text

Implemented 15 October 2026. `ShareTextActivity` is an exported `ACTION_SEND` target for text/plain
and text/html. `EXTRA_TEXT` is copied before the receiver finishes. `EXTRA_TITLE` or `EXTRA_SUBJECT`
provides a fallback attachment name. Text is limited to 512 KiB; oversize/empty input fails visibly
without truncation. It remains an attachment; the user's composer text is not overwritten.

The destination chooser follows the existing Notes inbox UI: choose a new-chat workspace or an
existing conversation. `ConversationComposerController.attachPreparedMany` owns the ordinary CAS
transaction. A generated request/local attachment ID and a persisted selected owner make rotation,
process recreation and retry idempotent; a failed/frozen draft commit remains retryable. A completed
import selects the target chat without submitting a message or starting model generation.

Optional `org.koreader.extra.EXCERPT_METADATA` is JSON with `schema=org.koreader.excerpt/v1`, `title`,
`bookTitle`, `authors` and `thumbnailBase64`. KOReader TOC-share v0.4.0 emits it through stock Android
JNI and a normal platform chooser. It still emits ordinary text/title/subject for other receivers.
No package-specific launch intent or sibling-repository dependency is introduced.

Metadata is untrusted. Labels are bounded; the JSON is bounded to 140,000 characters, cover Base64
to 120,000 and decoded bytes to 90,000. JPEG/PNG signatures and dimensions (at most 2048×2048) are
checked before decode/re-encode to an owned JPEG. Missing/malformed covers do not lose the text.
The thumbnail is purely display metadata, with no provider image index. Sender covers fit
160×220 pixels. Standard Android content-URI previews in ClipData (or Intent.data) are copied through the granted content resolver with the same byte/signature/dimension limits. URI failures preserve the text; remote URL covers are never fetched. KOReader also supplies this granted URI for the system Sharesheet preview.

`SharedTextInboxTest` verifies ordinary shares, provenance/cover persistence and model payload,
invalid/oversize input, stable retries and manifest registration. Native backup policy tests cover
draft/message thumbnail transport. Ordinary Composer prepared-attachment tests retain CAS, frozen
submission and existing-text behavior. Physical chooser/receiver interaction remains user testing.

User-facing share labels use Android resources with English fallback entries in the maintained locales. No new translated copy is claimed.

Android URI preview guidance: https://developer.android.com/training/sharing/send. The receiver copies granted bytes before the sender exits; the durable draft owns its private copy afterward.
