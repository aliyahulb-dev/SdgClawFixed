# SDG Claw — Project Context Document

## Project Overview

**SDG Claw** is an open-source Android AI agent application (similar to Operit) that provides a text chat interface powered by multiple LLM providers. The app uses a Termux WebSocket bridge as its execution backend, enabling the AI agent to run shell commands, read/write files, and perform system operations directly on the Android device.

- **Package**: `com.sdgclaw`
- **Repo**: `aliyahulb-dev/SdgClawFixed`
- **Target SDK**: Android 34
- **Min/Build Tools**: Gradle 8.13, Kotlin 1.9.22, Android SDK 34

---

## Architecture

```
┌─────────────────────┐     WebSocket (ws://127.0.0.1:8765)     ┌──────────────────────┐
│   Android App       │ ◄──────────────────────────────────────► │   Termux Bridge      │
│   (SDG Claw)        │                                          │   (Node.js / ws)     │
└─────────────────────┘                                          └──────────────────────┘
         ▲                                                                  ▲
         │                                                                  │
         ▼                                                                  ▼
┌─────────────────────┐                                          ┌──────────────────────┐
│   LLM Providers     │                                          │   Termux Tools       │
│   (API Keys)        │                                          │   (Shell, Files)     │
└─────────────────────┘                                          └──────────────────────┘
```

### Layer Breakdown

| Layer | Responsibility |
|---|---|
| **UI Layer** | `MainActivity`, `ChatActivity`, `SettingsActivity`, `BridgeSetupActivity` — Android Activities with Material Design 3 |
| **Agent Layer** | `AgentLoop` — orchestrates the conversation loop between user, LLM, and tools |
| **LLM Layer** | `LLMClient` — multi-provider HTTP client (OpenAI, Anthropic, Google, Custom) |
| **Tool Layer** | `ToolRegistry` — registers and dispatches local (Kotlin) and remote (Termux) tools |
| **Bridge Layer** | `TermuxBridge` — WebSocket client managing persistent connection to Termux Node.js server |
| **Deep-Link Layer** | `TermuxDeepLinkHelper` — fires `Intent.ACTION_VIEW` deep links into Termux to auto-start the bridge server |
| **Polling Layer** | `BridgePollingStateMachine` — state machine that monitors bridge connectivity and triggers recovery actions |
| **Diagnostics** | `StabilityDiagnostic` — pure-Kotlin agent trajectory analysis (no external deps) |
| **Dashboard Layer** | `StabilityDashboardView` — live in-chat overlay displaying real-time `StabilityDiagnostic` metrics |
| **Application** | `SDGClawApplication` — singleton Application class; owns `TermuxBridge` lifecycle |
| **Assets** | `setup.sh` — bundled shell script asset for automated Termux bridge installation |

---

## Key Files

### Android App (`app/src/main/java/com/sdgclaw/`)

| File | Role |
|---|---|
| `SDGClawApplication.kt` | Application singleton; initializes and auto-connects `TermuxBridge`; exposes bridge and `CoroutineScope` to activities; starts `BridgePollingStateMachine` on app launch |
| `MainActivity.kt` | Home screen; shows connection status; buttons for Test Connection, Settings, Chat, and Setup Bridge; observes polling state machine status |
| `ChatActivity.kt` | Chat UI using `RecyclerView`; initializes `AgentLoop`, `LLMClient`, `ToolRegistry`; wires agent callbacks to UI; injects system prompt from SharedPreferences into `AgentLoop`; hosts and updates `StabilityDashboardView` overlay; feeds agent trajectory snapshots to `StabilityDiagnostic` after each agent step and reflects results in the dashboard |
| `AgentLoop.kt` | Core agent loop; manages `conversationHistory`, iterates LLM↔tool calls up to `MAX_ITERATIONS=10`; emits state via callbacks (`onAgentResponse`, `onToolCall`, `onToolResult`, `onError`, `onStateChange`); accepts system prompt as constructor parameter |
| `LLMClient.kt` | Multi-provider LLM HTTP client using OkHttp; handles OpenAI, Anthropic, Google Gemini, and custom OpenAI-compatible endpoints; reads API keys from SharedPreferences |
| `ToolRegistry.kt` | Registers local tools (Kotlin lambdas) and remote tools (via Termux bridge); `executeTool()` dispatches by tool type |
| `SettingsActivity.kt` | UI for configuring API keys, model names per provider, active provider selection, and system prompt; saves to SharedPreferences |
| `BridgeSetupActivity.kt` | Step-by-step guided setup wizard for the Termux WebSocket bridge; walks the user through installing Termux, Node.js, copying bridge files, and starting the server; copies bundled `setup.sh` from assets and provides a way to deploy it; verifies connection at the end |
| `StabilityDiagnostic.kt` | Pure Kotlin implementation of blackbox agent stability analysis; classifies trajectory as converging/diverging/limit-cycle/chaotic using drift and Shannon entropy metrics |
| `ui/StabilityDashboardView.kt` | Custom `View` (or `ViewGroup`) that renders a live mini-dashboard inside `ChatActivity`; displays regime badge, drift sparkline, entropy sparkline, S-score, step counter, and last-updated timestamp; updated by calling `updateReport(report: StabilityDiagnostic.Report)` from the UI thread; collapsed/expanded toggle via a chevron button |
| `bridge/TermuxBridge.kt` | OkHttp WebSocket client; connects to `ws://127.0.0.1:8765`; auto-reconnect (3s delay); ping interval 30s; queues messages when disconnected |
| `bridge/TermuxDeepLinkHelper.kt` | Fires `termux://` or `Intent.ACTION_VIEW` deep-link intents to launch Termux and run a command (e.g., start the bridge server); used by `BridgePollingStateMachine` during recovery |
| `bridge/BridgePollingStateMachine.kt` | Finite state machine that polls bridge health on a configurable interval; transitions through states (`DISCONNECTED`, `CONNECTING`, `CONNECTED`, `RECOVERING`); triggers deep-link recovery when disconnected; exposes `StateFlow<PollingState>` for UI observation |

### Termux Bridge (`termux-bridge/`)

| File | Role |
|---|---|
| `package.json` | Node.js package; depends on `ws ^8.16.0`; entry point `server.js` |
| `server.js` (described in README) | WebSocket server on port 8765; receives tool execution requests from the Android app; runs shell commands via `child_process.spawn` |

### Bundled Assets (`app/src/main/assets/`)

| File | Role |
|---|---|
| `setup.sh` | Shell script bundled as an Android asset; automates Termux bridge setup (creates directory, writes `server.js`, runs `npm install ws`, and starts the server); copied to a user-accessible location (e.g., `/sdcard/sdgclaw-setup/`) at runtime and displayed for the user to run in Termux |

### Scripts (repo root)

| File | Role |
|---|---|
| `push_changes.sh` (or similar) | Utility shell script added to automate pushing local changes to the Git repository; committed to the `feat/add-git-push-script-rndjb-queued` branch and merged via `main_queued` |

### Resources (`app/src/main/res/`)

| Path | Role |
|---|---|
| `layout/activity_chat.xml` | Chat screen layout with `RecyclerView`, input bar, typing indicator, and `StabilityDashboardView` overlay anchored at top of chat area |
| `layout/activity_main.xml` | Home screen layout; includes Setup Bridge button entry point and polling status indicator |
| `layout/activity_settings.xml` | Settings screen layout with TextInputEditText fields per provider, plus system prompt multi-line editor |
| `layout/activity_bridge_setup.xml` | Bridge setup wizard layout; contains step indicator, step content area (ScrollView with per-step instructions and code blocks), navigation buttons (Back/Next/Finish), and a connection status indicator on the final step |
| `layout/item_message.xml` | Individual chat message bubble layout |
| `layout/view_stability_dashboard.xml` | Layout for `StabilityDashboardView`; contains regime badge (`TextView`), S-score label, drift sparkline (`View` / canvas-drawn), entropy sparkline, step counter, last-updated label, and a collapse/expand chevron `ImageButton` |
| `drawable/bg_*.xml` | Rounded rectangle backgrounds for message bubbles (assistant, user, tool) and inputs |
| `drawable/bg_regime_*.xml` | Tinted pill/badge backgrounds for each regime type (`converging` = green, `diverging` = red, `limit-cycle` = yellow, `chaotic` = orange); used by the stability dashboard regime badge |
| `values/colors.xml` | Dark-theme color palette (`surface_dark`, `card_bg`, `gray_dark`, `gray_medium`, etc.); extended with regime colors (`regime_converging`, `regime_diverging`, `regime_limit_cycle`, `regime_chaotic`) |
| `values/themes.xml` | `Theme.SDGClaw` and `Theme.SDGClaw.NoActionBar` (Material Design 3) |
| `values/strings.xml` | App strings including all bridge setup step titles, instructions, and code snippet strings; extended with stability dashboard labels (`str_regime`, `str_s_score`, `str_drift`, `str_entropy`, `str_steps`) |

### Build Files

| File | Role |
|---|---|
| `build.gradle` (root) | Root-level Gradle config |
| `app/build.gradle` | App module build config; sets `compileSdk 34`, Kotlin 1.9.22 |

---

## Tech Stack

| Category | Technology |
|---|---|
| **Language** | Kotlin (Android app), JavaScript/Node.js (Termux bridge), Shell (setup script) |
| **Android SDK** | API 34 (target), Material Design 3 |
| **UI** | ViewBinding / DataBinding, RecyclerView, AppCompat, Material Components, custom `View` subclasses with `Canvas` drawing |
| **Networking** | OkHttp (HTTP + WebSocket client) |
| **Serialization** | `kotlinx.serialization` (JSON) |
| **Async** | Kotlin Coroutines (`CoroutineScope`, `Channel`, `StateFlow`, `Dispatchers.IO/Main`) |
| **Bridge Protocol** | WebSocket + JSON messages |
| **Build System** | Gradle 8.13 |
| **Node.js (Bridge)** | Node.js with `ws` library (`^8.16.0`) |

---

## Data Models

### `AgentLoop`
```kotlin
data class ChatMessage(
    val role: String,          // "user" | "assistant" | "tool" | "system"
    val content: String,
    val toolCallId: String? = null,
    val toolName: String? = null
)

enum class AgentState { IDLE, THINKING, CALLING_TOOL, WAITING_FOR_TOOL, RESPONDING, ERROR }
```

### `LLMClient`
```kotlin
enum class ProviderType { OPENAI, ANTHROPIC, GOOGLE, CUSTOM }

data class ProviderConfig(
    val type: ProviderType,
    val name: String,
    val apiKey: String,
    val baseUrl: String,
    val defaultModel: String,
    val enabled: Boolean,
    val customHeaders: Map<String, String>
)

// Wire format models (kotlinx.serialization):
// ChatMessage, ToolCall, FunctionCall, ToolDefinition,
// FunctionDefinition, ChatRequest, ChatResponse, Choice
```

### `ToolRegistry`
```kotlin
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: JSONObject,  // JSON Schema
    val isLocal: Boolean
)

data class ToolResult(
    val success: Boolean,
    val output: String,
    val error: String? = null
)
```

### `StabilityDiagnostic`
```kotlin
data class Report(
    val steps: Int, val dimensions: Int,
    val drift: List<Double>, val entropy: List<Double>,
    val finalDrift: Double, val meanEntropy: Double,
    val sScore: Double,
    val regime: String,     // "converging" | "diverging" | "limit-cycle" | "chaotic"
    val regimeDetail: String
)
```

### `BridgePollingStateMachine`
```kotlin
enum class PollingState {
    DISCONNECTED,   // No connection; recovery not yet attempted
    CONNECTING,     // WebSocket connect attempt in progress
    CONNECTED,      // Bridge is reachable and responding
    RECOVERING      // Deep-link fired; waiting for bridge to come up
}

data class PollingStatus(
    val state: PollingState,
    val lastCheckedMs: Long,
    val recoveryAttempts: Int
)
// Exposed as StateFlow<PollingStatus> for UI observation
```

### `StabilityDashboardView`
```kotlin
// Custom View located in ui/StabilityDashboardView.kt
// State is held internally; updated via:
fun updateReport(report: StabilityDiagnostic.Report)
// Sparkline history is accumulated internally (ring buffer, max ~50 points)
// Collapsed state toggled by user tap on chevron; persists for the Activity lifetime
var isCollapsed: Boolean  // backed by instance field; no SharedPreferences persistence
```

---

## Git Workflow

- **Default integration branch**: `main`
- **Work branches**: feature branches named `feat/<description>-<id>-queued` (e.g., `feat/add-git-push-script-rndjb-queued`)
- **Queued merge branch**: `main_queued` — `main` is synced into `main_queued` before feature branches are created from it; feature branches are committed to `main_queued` before final merge
- **Commit granularity**: one commit per logical file addition or change set
- **Utility/automation scripts** (e.g., Git push helpers) are committed to the repo root and tracked in version control like any other source file

---

## Coding Conventions

### Kotlin Style
- Standard Kotlin idioms; `data class` for value types, `companion object` for constants/tags
- `TAG` constant in each class used with `android.util.Log`
- Coroutines used throughout for async operations; `withContext(Dispatchers.IO)` for blocking I/O
- Callbacks set via `setOn*` methods (e.g., `setOnConnected`, `setOnAgentResponse`)
- Nullability: bridge references typed as nullable (`TermuxBridge?`); forced with `!!` where lifecycle guarantees non-null
- `Channel<String>(Channel.UNLIMITED)` used for inter-component message passing
- `StateFlow` used for observable state that UI components collect (e.g., `BridgePollingStateMachine.status`)

### Architecture Conventions
- `SDGClawApplication` is the single owner of `TermuxBridge` and `BridgePollingStateMachine`; activities retrieve them via `application as SDGClawApplication`
- Activities use `lifecycleScope` for coroutines; Application uses a manual `CoroutineScope(SupervisorJob() + Dispatchers.Main)`
- `AgentLoop` accepts all dependencies via constructor (LLMClient, ToolRegistry, TermuxBridge, system prompt string)
- Maximum 10 agent iterations per turn (`MAX_ITERATIONS = 10`)
- System prompt is stored in SharedPreferences under a dedicated key and read in `ChatActivity` before constructing `AgentLoop`; it is injected into the conversation history as a `ChatMessage("system", ...)` prepended to every turn

### Stability Dashboard Pattern (`StabilityDashboardView` + `ChatActivity`)
- `StabilityDashboardView` is a