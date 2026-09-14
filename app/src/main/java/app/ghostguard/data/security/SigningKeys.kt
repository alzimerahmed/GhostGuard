package app.ghostguard.data.security

/**
 * Embedded GhostGuard filter-list signing PUBLIC keys.
 *
 * Only public keys may live here — private keys are held by the maintainer
 * (CI secret / local keystore) and are NEVER committed to this repository.
 * See docs/research.md ("Filter list signing", H3) for the release signing
 * process and the `tunnel/cmd/signfilter` tool used to produce signatures.
 *
 * Key rotation: add the new key to [ACCEPTED_FILTER_SIGNING_PUBLIC_KEYS_HEX]
 * and keep the old one until all mirrors have updated, then remove the old one.
 */
object SigningKeys {
    /**
     * Ed25519 public keys accepted for signing built-in filter/scriptlet artifacts.
     * A signature verifies against ANY key in this set, so old+new both work
     * during a rotation window.
     *
     * DEV KEY — the single key below is a development placeholder and MUST be
     * replaced with the production release key before publishing a release
     * build. See docs/research.md and `tunnel/cmd/signfilter -generate`.
     */
    val ACCEPTED_FILTER_SIGNING_PUBLIC_KEYS_HEX: Set<String> =
        setOf(
            // DEV — replace before release
            "f601b1e528e7e4b5c8c8fc015a39bc7d3767bfdf25e6b87b2c153aa17059995e",
        )

    /** Primary key (first in the accepted set) — used for new signatures. */
    const val FILTER_SIGNING_PUBLIC_KEY_HEX: String =
        "f601b1e528e7e4b5c8c8fc015a39bc7d3767bfdf25e6b87b2c153aa17059995e"
}
