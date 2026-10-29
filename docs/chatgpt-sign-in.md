# Server-owner ChatGPT sign-in

One server owner connects one ChatGPT account for the server's agent requests. Players do not sign in. This uses OpenAI's documented [open-source/self-hosted ChatGPT plan flow](https://developers.openai.com/siwc/token-sharing-open-source), not ChatGPT private backend endpoints. Eligibility, app-specific allowances and account/workspace policies still apply. Paid or centrally hosted products may require separate OpenAI onboarding; authorizing an account does not override its terms.

## Requirements

- Java 25 or newer on the owner's local computer.
- The shaded VibeMine `*-all.jar` on that computer.
- SSH/SFTP access to the Minecraft server.
- POSIX file permissions on both machines (Linux/macOS). This first version fails closed on non-POSIX credential storage; API-key-only operation still works on other systems. Windows owners can use a Linux environment such as WSL and open the printed URL in a browser that can reach that environment's loopback listener.

The callback listener runs only on `127.0.0.1` on the owner's computer. No public callback domain, server HTTP port or pasted Minecraft-chat tokens are needed.

## 1. Get the server's host ID

Start VibeMine and run this in the **local server console**:

```text
vibe auth login
```

`vibe auth host` prints the same persistent `urn:uuid:...` host ID. Keep `plugins/vibemine/auth/host-id` across restarts. Use the actual plugin data-directory capitalization on your server.

## 2. Sign in locally

Create an output directory on your computer. Use the server's host ID, not a newly invented one:

```bash
java --add-modules jdk.httpserver \
  -cp ./vibemine-1.0-SNAPSHOT-all.jar \
  io.rcw.vibemine.ai.auth.ChatGptLogin \
  --host-id 'urn:uuid:THE-SERVER-HOST-ID' \
  --output "$HOME/vibemine-owner.json"
```

Choose **Continue with ChatGPT** in the browser and authorize ChatGPT plan usage. If a browser cannot be opened automatically, the helper prints an authorization URL to open on the same computer. It contains no access/refresh tokens. The attempt expires after five minutes.

The helper uses fresh state, nonce and PKCE, validates the signed ID token (issuer, audience, expiry, signature and nonce), and saves an owner-only `0600` credential export. It also saves a token-free `vibemine-owner.json.registration.json` sidecar. Keep the sidecar at that path to reuse the issued client registration on subsequent sign-ins. Returning sign-in checks the account identity before replacing the export. To deliberately register a different account/workspace, use another output filename and keep its sidecar separate.

Do not run the helper against server-owned live credential files. The local helper does not refresh the exported session; after import, only the server should refresh it.

## 3. Transfer and import securely

Transfer over SSH/SFTP, never through Minecraft chat, email, a public upload or source control:

```bash
scp "$HOME/vibemine-owner.json" minecraft@SERVER:/path/to/server/plugins/vibemine/auth/import.json
ssh minecraft@SERVER 'chmod 600 /path/to/server/plugins/vibemine/auth/import.json'
```

Import promptly: the signed ID token must still be valid. In the local server console:

```text
vibe auth import
vibe auth status
vibe auth models
```

Import checks the server host ID and verifies the signed account identity before atomically replacing existing credentials. Successful import removes `import.json`. The new account becomes active only after validation; a failed validation leaves the previous account in use. Delete the local token export after successful import, retaining only its token-free sidecar.

Choose a model slug shown by `vibe auth models`. Put it in `ai.subscription.model`, then restart to apply config changes. Importing refreshed credentials does not require a restart. A signed-in identity without `chatgpt.tokens.use.direct` permission cannot make subscription requests; reauthorize with plan permission or use the configured API path.

## API fallback and billing

```yaml
ai:
  api_key: "YOUR_OPTIONAL_API_KEY"
  model: "gpt-5.4-mini"       # API-key generation model
  subscription:
    enabled: true
    model: "ACCOUNT_MODEL_SLUG"
    api_fallback: true        # WARNING: normal API charges can apply
```

With subscription enabled, VibeMine tries ChatGPT plan inference first, including conversation summaries. If subscription credentials are missing, refreshing fails, transport is unavailable, or the service reports an authentication/usage-limit/availability failure, it can use the configured API key **only when `api_fallback` is true**. Startup and each switch to the API emit billing warnings to the server log. API summaries use the existing `gpt-5.4-nano` summary model.

The remaining tool loop stays on the API after a switch. Tool calls are executed only from a completed response, not from partial streaming events. Completed tool results are included in the API history; they are not executed again simply because billing paths changed.

Policy/region restrictions, unsupported subscription capabilities and incomplete responses do not silently switch to API billing. Fix the reported issue, or explicitly disable subscription inference if you want API-only operation. Set `api_fallback: false` to prohibit automatic API fallback. Set `subscription.enabled: false` to use the API key directly. Without usable credentials on an allowed path, requests fail with a setup error.

Subscription requests use `POST https://api.openai.com/v1/responses`, `store:false`, `stream:true`, developer instructions, namespaced function tools, and full per-turn context including returned encrypted reasoning. No ChatGPT conversation history is imported.

## Owner-only management

All auth commands are restricted to the local server console, not OP players, generated plugin commands or remote in-game chat:

- `vibe auth login` / `host`: local-helper instructions and server host ID.
- `vibe auth import`: import the fixed `auth/import.json` file.
- `vibe auth status`: account label, plan permission and configured billing fallback.
- `vibe auth models`: current account-specific model catalog.
- `vibe auth logout`: attempt remote refresh-session revocation, then clear local tokens while retaining account/client metadata and host identity. If revocation cannot be confirmed, disconnect VibeMine in [ChatGPT settings](https://chatgpt.com/settings/usage). **API fallback remains independently configured after logout.**

Existing requests may finish during logout. Tokens are never printed by these commands. Anyone who can read the server's credential files can use the connected session: only authorize servers and administrators you trust.

## Storage and recovery

- `auth/host-id`: stable server identity, not an access credential.
- `auth/credentials.json`: protected account registration and rotating credentials.
- `auth/import.json`: temporary secure-transfer input, deleted after successful import.

Credential writes are atomic and owner-only. Refreshes are serialized in the server process and persist replacement tokens together. Do not run two servers sharing this auth directory, manually copy rotating sessions between active runtimes, or restore an obsolete credential backup: refresh-token reuse can invalidate the session. A confirmed invalid refresh token clears the unusable local token set and requires sign-in again. Temporary failures preserve it.

If permission checks fail, fix the credential files with `chmod 600` and restart if storage could not be initialized at startup. Never commit credentials. Revoke the app in ChatGPT settings if a credential export or server backup leaks.

The OAuth flow, live subscription model/tool compatibility and account eligibility must be verified with a real owner account before production use. Unit tests do not establish eligibility or guarantee a subscription's availability.
