# ChatGPT account and plan usage

During setup, choose OpenAI → ChatGPT Account → Continue with ChatGPT. Complete sign-in and
OpenAI's consent in your browser, return to Agora, choose a model and press Continue. This
finishes setup. No API key or separate Use ChatGPT plan checkbox is needed.

You can also connect later in Settings → Providers → OpenAI → ChatGPT → Continue with ChatGPT.
The connection screen shows your account, selected model and Continue. Manage Account, available
from Settings after connection, contains account/workspace switching, adding an account, signing
out, reloading models, usage and connection details. Use an API Key returns to API billing. Signing out preserves local conversations
and recipes. You can also choose the API-key method during setup.

Choosing ChatGPT authorizes Agora to use that account after OpenAI grants the required access.
Sign-in cannot bypass eligibility, required scopes or workspace policy. A missing model catalogue
can be retried; connection and plan errors never silently switch to an API key.

Plan requests use the official public Responses endpoint, stream text, set `store:false` and use
app-local encrypted OAuth credentials. PKCE, state, nonce and signed OIDC identity checks bind
sign-in to the account registration. Credentials are excluded from Android backups.

The account connection implementation is adapted from LearnHarness-Mobile, with its AGPL license
retained in the app's assets/licenses. Each app has separate registration, vault and callbacks.
Physical UI acceptance and real account sign-in remain owner checks.

From a conversation, open the composer’s three-dot menu → Settings to reach the app settings,
even with the top toolbar collapsed. Choose Thinking in that same menu to change the current
conversation’s effort level. GPT-6.1 Sol offers Low, Medium, High, Extra High and Max; the selector
does not offer Off or Minimal for this model. Global thinking defaults remain in Settings → Generation.

Thinking choices follow the selected account/model catalogue when it supplies supported effort
metadata. Otherwise Agora uses its documented model defaults. Opening ChatGPT connection settings
refreshes older cached catalogues once; Reload Models refreshes them manually later.
