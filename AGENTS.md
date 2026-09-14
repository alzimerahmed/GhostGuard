# BlockAds Android — Project Rules for AI Agents

## Project

BlockAds (`blockads-android`, upstream: https://github.com/pass-with-high-score/blockads-android) — a free, open-source, privacy-first ad blocker for Android. Blocks ads, trackers, and malware system-wide via local VPN-based DNS filtering (no root required, no data collection). GPL-3.0.

**Stack:** Kotlin, Jetpack Compose + Material 3 (dynamic theming), Koin (DI), Compose Destinations (navigation), Go tunnel core via gomobile (`tunnel/` → AAR in `app/libs/`, build with `./scripts/build_tunnel.sh` or `./gradlew buildGoTunnel`). Min SDK 24, target SDK 36, JDK 17.

**Key subsystems:** VpnService DNS filtering + Root Proxy (iptables) mode, Trie-based filter-list matching, HTTPS filtering via userspace TCP/IP stack (gVisor netstack / tun2socks, per-app MITM), DoH support, DNS query logs, filter-list auto-update, per-app bypass, custom block/allow rules, WireGuard import, multi-language (en, vi, ja, ko, zh, th, es).

**Build/verify:** `./gradlew assembleDebug` · `./gradlew testDebugUnitTest` · `./gradlew bundleRelease` (needs signing key). Requires JDK 17 + Android SDK 36; Go 1.21+ + gomobile only for rebuilding the tunnel AAR (pre-built copy ships in `app/libs/`).

## Entry Point

This file is auto-loaded by Devin at every session start. It is the entry point to the full prompt system in `.devin/prompt/`. Read `.devin/prompt/map.md` before starting any task — it is the system map.

## Resource Discipline (mandatory for non-trivial tasks)

Before starting any non-trivial task:
1. Read `docs/toolset.md` intent-map (one table, task type → resources)
2. Identify the task type row; invoke every skill and sub-agent listed there
3. Read every rule listed for that task type (from `.devin/rules/`)
4. At task end: `code-reviewer` sub-agent on the final diff (non-negotiable)
5. Append learnings via `/ce-compound` if a durable lesson was learned

For phase implementations (any task completing a row in docs/plan.md), /ce-work is mandatory.

Skip this for single-line edits, pure Q&A, or reading files.

## Project-Type Filter (Android native app)

This is a native Android app, not a website. Per the intent-map in `docs/toolset.md`:
- **Skip web-only sub-agents/skills:** frontend-designer, css-architect, pwa-engineer, seo-specialist, search-optimization, playwright-design-clone (except when auditing Compose UI against design references — use tastemaker/pixel-analyst instead).
- **Keep universal ones:** code-reviewer, debugger, test-engineer, security-auditor, performance-engineer, git-master, migration-specialist, docs-writer, i18n-specialist (7 languages via Compose string resources), build-optimizer, caveman-compressor, pixel-analyst, vibe-coding-auditor, type-safety-engineer (Kotlin), database-engineer (Room/DataStore if applicable).
- **Android-specific quality gates:** `./gradlew lint` (Android Lint), unit tests via JUnit/Robolectric, Compose UI tests, and a11y via Compose semantics — not axe-core/browser tooling.

## Communication Style

Default to **caveman-lite** compression (lightly compressed, still readable, full technical accuracy). Use the `/caveman` skill for full / ultra / wenyan modes when tighter compression is needed.

## Quick Task Flow

For quick tasks, follow `.devin/prompt/quick.md` (commandments) and `.devin/prompt/rules.md` (scoping, verification, escalation). For phased work, follow `.devin/prompt/phase.md`.

## Key References

- `docs/toolset.md` — intent map (task type → skills, sub-agents, rules) — create per phase.md §4 if absent
- `docs/plan.md` — phased plan and current status — create per phase.md §8 if absent
- `docs/project.md` — project state and structure — create per phase.md §3 if absent
- `docs/agent.md` — past implementations and decisions — create if absent
- `docs/research.md` — technical research, ADRs, gotchas — create per phase.md §7 if absent
- Upstream repo — README (features, build steps, architecture) and `GEMINI.md` + `.agents/rules/` for upstream contributor rules
