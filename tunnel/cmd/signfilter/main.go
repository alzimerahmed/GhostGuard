// Command signfilter produces detached Ed25519 signatures for GhostGuard
// filter-list / scriptlet artifacts (audit finding H3).
//
// Signature file format (written as <input>.sig, plain text):
//
//	GG-SIG1
//	<sha256-hex of the artifact bytes>
//	<ed25519-hex signature over the ASCII line "GG-SIG1\n<sha256-hex>">
//
// The Android app embeds the matching Ed25519 PUBLIC key
// (app/src/main/java/app/ghostguard/data/security/SigningKeys.kt) and refuses
// to persist or use any built-in artifact whose signature does not verify.
//
// Usage:
//
//	signfilter -generate                     # print a fresh keypair (dev only)
//	signfilter -key <privkey-hex> -in file   # write file.sig
//
// Key management: the private key is held by the maintainer (CI secret or
// local keystore) and must NEVER be committed to the repository. The public
// key lives in app/.../data/security/SigningKeys.kt.
package main

import (
	"crypto/ed25519"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"flag"
	"fmt"
	"os"
	"strings"
)

const formatID = "GG-SIG1"

func main() {
	generate := flag.Bool("generate", false, "generate a new Ed25519 keypair and exit")
	privHex := flag.String("key", "", "Ed25519 private key (64-byte hex, seed+pub)")
	in := flag.String("in", "", "file to sign")
	flag.Parse()

	if *generate {
		pub, priv, err := ed25519.GenerateKey(rand.Reader)
		if err != nil {
			fatal("generate key: %v", err)
		}
		fmt.Printf("public  (embed in SigningKeys.kt): %s\n", hex.EncodeToString(pub))
		fmt.Printf("private (KEEP SECRET, never commit): %s\n", hex.EncodeToString(priv))
		return
	}

	if *privHex == "" || *in == "" {
		flag.Usage()
		os.Exit(2)
	}

	priv, err := hex.DecodeString(strings.TrimSpace(*privHex))
	if err != nil || len(priv) != ed25519.PrivateKeySize {
		fatal("invalid private key (need %d-byte hex)", ed25519.PrivateKeySize)
	}

	content, err := os.ReadFile(*in)
	if err != nil {
		fatal("read %s: %v", *in, err)
	}

	digest := sha256.Sum256(content)
	digestHex := hex.EncodeToString(digest[:])
	payload := []byte(formatID + "\n" + digestHex)
	sig := ed25519.Sign(ed25519.PrivateKey(priv), payload)

	sigPath := *in + ".sig"
	sigText := fmt.Sprintf("%s\n%s\n%s\n", formatID, digestHex, hex.EncodeToString(sig))
	if err := os.WriteFile(sigPath, []byte(sigText), 0o644); err != nil {
		fatal("write %s: %v", sigPath, err)
	}
	fmt.Printf("signed %s -> %s (sha256 %s)\n", *in, sigPath, digestHex)
}

func fatal(format string, args ...any) {
	fmt.Fprintf(os.Stderr, "signfilter: "+format+"\n", args...)
	os.Exit(1)
}
