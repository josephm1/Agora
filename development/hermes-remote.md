# Hermes Remote backend

Hermes is an optional backend for Remote. `remote/RemoteBackend.kt` defines the small
presentation-facing API; `remote/hermes/` owns dashboard authentication, JSON-RPC,
session bindings and native-message projection. FiloClient remains the default for
existing saved connections and retains its encrypted Conch protocol.

## Connection

Run an authenticated Hermes dashboard reachable through your private Tailscale network
or an HTTPS endpoint. The messaging gateway's OpenAI-compatible API on port 8642 is a
different surface; existing Desktop/TUI chats need dashboard `/api/ws` and native
`session.resume`. Do not supply the provider API key as a dashboard password.

In Remote → Add Device, choose Hermes, enter the dashboard URL (with a trailing slash),
dashboard username/password and optionally a named Hermes profile. The first version
supports the dashboard's `basic` password provider. OAuth-only dashboards and legacy
token-only gateways are unsupported. Do not disable TLS certificate validation or the
Hermes Host/Origin guards. A successful public `/api/status` is not authenticated readiness.

Passwords use the existing atomic, Keystore-encrypted, no-backup connection store.
Cookies and single-use WebSocket tickets are transient. Connection identity includes
backend, endpoint, account and profile. Old entries default to Filo; changing backend,
account or profile cannot inherit the previous entry's viewed-turn state.

## Ownership and delivery

Hermes owns native sessions, tool execution and transcripts. The app attaches to native
sessions and retains bounded transient presentation. It never writes Hermes databases,
starts a competing agent, changes global model defaults or imports native transcripts
into Room. Session creation requests `close_on_disconnect=false` and includes a stable
creation key, but prompt submission is never retried automatically.

Stored session IDs and live runtime IDs are distinct. A dropped socket invalidates its
runtime binding. Recovery obtains a fresh authenticated ticket, reattaches the selected
stored session with `lazy=true` and reads its native history. This watch attachment
avoids Hermes' cold-resume auto-continuation path; merely browsing must not submit
crash-recovery work. A genuine live owner is reused by native session attachment.
An unbuilt watch session does not claim a confirmed current model; explicit Send or
model selection lets the native runtime build/upgrade it. The existing Remote read-recovery loop
owns reconnect timing. A transport failure cannot establish idle, successful delivery
or completion. Unknown outcomes require explicit user reconciliation.

Prompt acknowledgement is separate from completion. A native `user_row_id` confirms
input delivery. Where canonical admission initially omits that row, its admission ID
can be matched to the terminal persisted-turn submission and user-row evidence. Text
comparison never proves delivery. Terminal unread/completion identity is taken only
from Hermes' committed final assistant-row evidence, not an invented turn ID.

Legacy `session.interrupt` is session-scoped: Agora verifies its current local binding
and active presentation token before calling it, but Hermes does not accept a compare-and-stop
turn ID in this method. This is weaker than Filo's exact-turn Stop contract and needs
real concurrent-turn qualification before claiming equivalent guarantees.

## First-version capabilities and limits

Supported surfaces: paged native session list/history, create/continue, text streaming,
tool activity, session-local model selection, rename/soft archive, native session interrupt, approve once/deny
and single-choice/free-text clarification. Native approval replies use exact JSON-RPC
request IDs. No permanent or session-wide grant is offered. Unsupported secret, sudo,
multi-select clarification, browser and desktop requests receive an explicit JSON-RPC
unsupported-method response instead of waiting invisibly.

The existing Remote picker, draft previews and import owner also serve Hermes documents
and images. Original bytes stream through the authenticated multipart upload endpoint
without base64 expansion or automatic POST retry. A checked upload is bound to the exact
native runtime using `file.attach` and canonical references for both documents and images. Hermes
chooses its own extraction/vision tools; Agora does not force image content into a
model that cannot accept it. The installed DeepSeek Flash provider rejected direct
image payloads during qualification, so automatic image-queue injection is not used. Native limits are 100 MiB per file and 25 MiB per image, within
Agora's existing 16-attachment draft limit. Drafts clear only after native delivery
confirmation. Uploaded files are retained on the host under its managed `agora-uploads`
directory; failed/abandoned uploads may leave files there. Model image understanding and
document extraction still depend on Hermes/model capabilities.

Native catalog/history use bounded REST pages (100 sessions, 64 messages) and a 1 MiB
response/frame ceiling. Native offset pagination is not an immutable history snapshot;
concurrent edits/compression may require reopening the chat. Known rows retain native
identity and old reads are fenced by the selected owner. Oversized content fails visibly;
it is not silently truncated. Live presentation is bounded to 256 records and 512 KiB
per text segment. Native user image/file references render as authenticated lazy image slots or file labels. Tool-image payloads beyond native user references are not yet mapped.

Interactive HTML artifacts, account usage, busy-session
steering, reasoning-effort selection and dashboard administration are not advertised.
They must be added only with verified native protocol mappings. Existing Filo features
remain available on Filo connections. Normal Agora voice typing still edits the draft;
Hermes does not supply Agora's transcription service.

`rusty4444/hermes-android` is the owner's selected behavioral reference. Its source was
read at revision `370ff9786e5e0c28b590df6773273963019a5f77`; this implementation was
written against installed Hermes contracts rather than copying its Dart code. Its README
says MIT but its NOTICE disclaims an upstream grant; no source from it is vendored here.
Herald was also consulted as a native Kotlin protocol reference. Experimental mobile
`turn_recovery` is not assumed or advertised by this implementation.

## Verification

Local MockWebServer fixtures cover authentication and tickets, native session binding,
streaming, exact-request approval replies, durable-row receipts and a lost submit reply
followed by reconnect without a second submission. Storage tests cover mixed Filo/Hermes
entries and encrypted credential preservation. Compose tests verify gesture-only approval
and native clarification keys. Existing Remote/Filo tests exercise the extracted seam.

`HermesLiveTest` is skipped by default. Explicitly providing `AGORA_HERMES_LIVE_ENV` and
`AGORA_HERMES_LIVE_URL` allows one new native chat and one provider turn; the env file
contains dashboard basic-auth fields and must stay private. Never enable it in CI.
Record actual run and device results in the PR; successful fixture tests are not proof
of HiBreak sleep/network behavior or survival of a Hermes process restart.

## Official references

- https://hermes-agent.nousresearch.com/docs/developer-guide/programmatic-integration/
- https://hermes-agent.nousresearch.com/docs/user-guide/features/web-dashboard
- https://github.com/rusty4444/hermes-android
