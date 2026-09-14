package app.ghostguard.data.security

/**
 * Embedded GhostGuard filter-list signing PUBLIC keys.
 *
 * Only public keys may live here — private keys are held by the maintainer
 * (CI secret / local keystore) and are NEVER committed to this repository.
 * See docs/research.md ("Filter list signing", H3) for the release signing
 * process and the `tunnel/cmd/signfilter` tool used to produce signatures.
 *
 * The key below is the current GhostGuard filter-signing public key
 * (Ed25519, 32 bytes, hex-encoded). Rotate by adding the new key and keeping
 * the old one until all mirrors have updated.
 */
object SigningKeys {
    /** Ed25519 public key that signs built-in filter/scriptlet artifacts. */
    const val FILTER_SIGNING_PUBLIC_KEY_HEX: String =
        "f601b1e528e7e4b5c8c8fc015a39bc7d3767bfdf25e6b87b2c153aa17059995e"
}
