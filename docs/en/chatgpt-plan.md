# ChatGPT account and plan usage

Open Settings → Providers → OpenAI → ChatGPT account. Continue with ChatGPT in the system browser, grant plan permission, refresh available models, choose a model, then select Use ChatGPT plan. Saved accounts and workspaces can be reconnected or selected independently. Use API-key billing explicitly to return to existing OpenAI API configuration.

Plan requests use the official public Responses endpoint, stream text, set `store:false` and use app-local encrypted OAuth credentials. PKCE, state, nonce and signed OIDC identity checks bind sign-in to the account registration. Plan errors stop the request without selecting an API key. Manage usage opens ChatGPT usage controls. Sign-in alone does not grant plan usage; eligibility and workspace policy are enforced by OpenAI. Credentials are excluded from Android backups.

The account connection implementation is adapted from LearnHarness-Mobile, with its AGPL license retained in the app's assets/licenses. Each app is standalone and has separate registration, vault and callbacks.

Validation on 25 October 2026: 47 focused JVM tests pass (OAuth validation, loopback binding/cancellation, provider config projection, Deepgram transcription and e-ink bottom pin); the F-Droid device preview APK builds. The preview uses verified unchanged native project libraries from v2026.10.19-preview.1; native code was not rebuilt. Physical UI and real account sign-in are owner checks. The latest candidate has not been installed because ADB had no connected device.
