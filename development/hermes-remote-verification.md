# Hermes add-on qualification

Date: 2026-10-21. This records the scope of the draft feature PR, not a claim
that every Hermes feature or phone lifecycle case is qualified.

## Implementation boundary

The optional Kotlin backend lives in `remote/hermes/`. Shared Remote owners consume
`RemoteBackend`; existing Filo connections retain their protocol. No third-party
mobile source is vendored. Local host credentials and the machine-specific systemd
unit are outside this repository; the service example contains no credentials.

## Native endpoint checks

Hermes checkout revision: `4d04303818` (health version reports `0.16.0`).
The separately supervised dashboard uses basic password auth and listens only on the
PC's Tailscale interface. The API server, messaging gateway and existing Pi gateway
remain separate. Both PC and HiBreak are present on the tailnet.

Read-only checks passed: public status reports authentication required; password login;
single-use WebSocket ticket; greeting; client capability negotiation; native session
list and attachment; native model inventory. The greeting does not advertise the
experimental mobile `turn_recovery` contract.

Explicit live Kotlin test passed against that endpoint: upload a text document and
PNG as original multipart bytes, create a native chat, attach both files to that
runtime, submit one prompt, stream the response, confirm delivery using a durable
native user row and confirm completion using a committed final assistant row. A
unique explicit title was then stored. The completed fixture runtime was closed, a
fresh client resumed the stored chat, a second explicit turn completed with native
delivery/completion evidence, and the fixture was soft-archived. No existing user chat was used for model work.
Native files are retained in the host's managed uploads/attachments areas.

The initial direct image-queue path reached the gateway but the configured DeepSeek
Flash provider returned `format_error`. The final implementation uses `file.attach`
references for both files and images, as the selected mobile reference does. Hermes
owns subsequent extraction/vision; Agora does not assume that the main model accepts
raw image messages. The successful attachment test verifies transport and persistence,
not recognition accuracy or auxiliary-vision-provider availability.

## Automated checks

New fixtures cover streamed multipart bytes and upload receipts, ordered document/image
binding to the exact runtime, invalid uploads/unknown attachment IDs, endpoint checks,
native history identity, account/profile identity separation, lost prompt replies with
no replay, native delivery receipts, streaming, approval and clarification validation,
and authenticated image slots. Compose checks cover gesture-only approval and exact
clarification keys. Mixed-connection storage tests exercise encrypted credentials and
legacy Filo compatibility. Existing strict mock fixtures now provide backend metadata
and close behavior explicitly.

The cumulative ARM64 F-Droid preview suite completed **4,097 tests, zero failures,
zero errors and five existing skips**, with the opt-in native create/resume test enabled.
The source-size gate and cumulative APK assembly also passed. The final focused run
covered **18 Hermes/protocol/attachment/UI checks, all passing with no skips**, including
the live two-turn cold-resume check after the last native-resume/settings guard changes. Locale-key parity is maintained across all supported locales.

## Remaining acceptance and limitations

- HiBreak installation/sign-in, real photo/PDF picking, sleep/background/network changes,
  and native question/Stop interaction still need physical-device acceptance.
- Secret/sudo/browser/desktop control and multi-select clarification are explicitly
  unsupported. The client offers approve-once/deny, not persistent permission grants.
- Busy-session steering, account usage, reasoning-effort controls and structured
  interactive figures need additional native mappings and are not advertised.
- Legacy Hermes interrupt is session-scoped, without Filo's compare-and-stop turn ID.
- Offset history paging is not an immutable snapshot across concurrent edits/compression.
- Standard native resume may recover interrupted tasks under Hermes’ existing auto-continue configuration; the app does not change that global policy.
- A gateway/host process restart may interrupt accepted work; no execution-survival or
  exactly-once WebSocket guarantee is claimed.
- Native attachment limits are 100 MiB/file and 25 MiB/image. The shared image preview
  store has its existing 20 MiB bound; larger image originals may send successfully
  while a preview is unavailable.

Run live provider tests only through their explicit opt-in; they remain skipped in CI.
