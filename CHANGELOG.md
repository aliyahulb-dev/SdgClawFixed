# Changelog

All notable changes to **SDG Claw** are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [Unreleased]

### Added
- `scripts/push_changes.sh` — utility script to stage, commit, and push all local
  changes to the remote repository in a single command. Includes:
  - Guard warning when operating on `main`.
  - Pre-commit summary of staged files (`git diff --cached --name-status`).
  - Automatic upstream tracking setup when the remote branch does not yet exist.
  - Post-push SHA verification with a non-zero exit code on mismatch.
- `CHANGELOG.md` — this file; tracks changes going forward.

### Architecture additions (described in project context)
- `AgentLoop.kt` — core LLM ↔ tool orchestration loop (`MAX_ITERATIONS = 10`).
- `LLMClient.kt` — multi-provider HTTP client (OpenAI, Anthropic, Google Gemini, Custom).
- `ToolRegistry.kt` — local + remote (Termux) tool registration and dispatch.
- `TermuxBridge.kt` — OkHttp WebSocket client with auto-reconnect and ping keepalive.
- `TermuxDeepLinkHelper.kt` — fires deep-link intents into Termux for bridge recovery.
- `BridgePollingStateMachine.kt` — FSM that monitors bridge health and triggers recovery.
- `StabilityDiagnostic.kt` — blackbox agent trajectory analysis (drift + Shannon entropy).
- `ui/StabilityDashboardView.kt` — live in-chat overlay for stability metrics.
- `BridgeSetupActivity.kt` — step-by-step guided Termux bridge setup wizard.
- `assets/setup.sh` — bundled shell script for automated Termux bridge installation.

---

## [0.1.0] — Initial release

### Added
- Android AI agent application skeleton targeting SDK 34.
- `MainActivity`, `ChatActivity`, `SettingsActivity` with Material Design 3 theming.
- WebSocket bridge protocol (`ws://127.0.0.1:8765`) connecting the Android app to a
  Termux Node.js server (`termux-bridge/server.js`).
- Multi-provider LLM support via SharedPreferences-backed API key storage.
- Dark-theme color palette and rounded-rectangle drawable resources.
