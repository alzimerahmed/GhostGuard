# Research & ADRs

## Filter list signing (Phase 4 security audit, finding H3)

**Decision:** All built-in/curated filter-list and scriptlet downloads are verified
(Ed25519 signature + SHA-256 digest) in Kotlin at the download boundary, BEFORE the
bytes are persisted to disk/Room or handed to the Go engine (`setTries`) or injected
as JS in the in-app browser WebView. No Go-side changes were needed.

### Threat model

A compromised or MITM'd filter-list mirror/CDN must not be able to push arbitrary
block rules (censorship / supply-chain attack) or arbitrary JavaScript (scriptlets
execute in the app's WebView). Untrusted content fails closed.

### Signature scheme

- **Algorithm:** Ed25519 (RFC 8032) over the artifact bytes, plus a SHA-256 digest.
- **Format:** detached `<artifact>.sig` text file:
  ```
  GG-SIG1
  <sha256-hex of artifact bytes>
  <ed25519-hex signature over the ASCII line "GG-SIG1\n<sha256-hex>">
  ```
  SHA-256 gives integrity (truncated/corrupted downloads); Ed25519 gives
  authenticity. Both must pass.
- **Why sign the digest line rather than raw bytes:** makes the SHA-256 check an
  explicit, independently testable step and keeps the signature file small and
  human-inspectable.

### Key management

- The app embeds ONLY the Ed25519 **public** key
  (`app/src/main/java/app/ghostguard/data/security/SigningKeys.kt`). Private keys
  are never committed.
- **Release signing:** the maintainer holds the 64-byte Ed25519 private key as a CI
  secret (or local keystore) and signs each release artifact with
  `tunnel/cmd/signfilter`:
  ```bash
  go run ./tunnel/cmd/signfilter -generate            # one-time keypair (dev only)
  go run ./tunnel/cmd/signfilter -key "$GG_SIGNING_KEY" -in easylist.trie
  # -> writes easylist.trie.sig next to the artifact on the CDN
  ```
- **Key rotation:** publish the new key in `SigningKeys.kt`, keep the old key
  accepted during a transition window, then remove it.
- The key committed in `SigningKeys.kt` was generated for development/testing of
  this feature; replace it with the production release key before shipping.

### Custom (user-added) list policy

User-added custom filter URLs may remain **unsigned** — the user explicitly chose
that source and it is their own trust decision. Built-in/curated sources (anything
with `isBuiltIn = true`, including the remote `filter_lists.json` catalog) are
**required** to be signed; verification happens before persistence and before the
files are pushed to the Go engine via `setTries` or compiled into
`scriptlets_combined.txt` for WebView JS injection.

### Failure policy (fail closed)

On any verification failure (missing `.sig`, malformed sig, wrong key, tampered
bytes, SHA-256 mismatch):

1. The temp download is deleted; the previously-cached artifact is kept untouched.
2. The update is rejected (`downloadFilterList` returns failure).
3. `FilterListRepository.updateStatusFlow` emits
   `FilterUpdateStatus.VerificationFailed(filterName, reason, timestamp)` for the
   filter-list UI, and the failure is logged locally (Timber).

### Gotchas

- Android's `java.security` only gained Ed25519 on API 33; minSdk is 24, so
  verification uses a small pure-Kotlin implementation (`data/security/Ed25519.kt`)
  validated against RFC 8032 test vectors.
- Verification happens on the `.tmp` download, before `renameTo(dest)` — a failed
  update can never clobber the good on-disk version.
- Zip bundles are verified as a whole (`.zip.sig`) before extraction.
