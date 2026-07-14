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
| **Application** | `SDGClawApplication` — singleton Application class; owns `TermuxBridge` lifecycle |
| **Assets** | `setup.sh` — bundled shell script asset for automated Termux bridge installation |

---

## Key Files

### Android App (`app/src/main/java/com/sdgclaw/`)

| File | Role |
|---|---|
| `SDGClawApplication.kt` | Application singleton; initializes and auto-connects `TermuxBridge`; exposes bridge and `CoroutineScope` to activities; starts `BridgePollingStateMachine` on app launch |
| `MainActivity.kt` | Home screen; shows connection status; buttons for Test Connection, Settings, Chat, and Setup Bridge; observes polling state machine status |
| `ChatActivity.kt` | Chat UI using `RecyclerView`; initializes `AgentLoop`, `LLMClient`, `ToolRegistry`; wires agent callbacks to UI; injects system prompt from SharedPreferences into `AgentLoop` |
| `AgentLoop.kt` | Core agent loop; manages `conversationHistory`, iterates LLM↔tool calls up to `MAX_ITERATIONS=10`; emits state via callbacks (`onAgentResponse`, `onToolCall`, `onToolResult`, `onError`, `onStateChange`); accepts system prompt as constructor parameter |
| `LLMClient.kt` | Multi-provider LLM HTTP client using OkHttp; handles OpenAI, Anthropic, Google Gemini, and custom OpenAI-compatible endpoints; reads API keys from SharedPreferences |
| `ToolRegistry.kt` | Registers local tools (Kotlin lambdas) and remote tools (via Termux bridge); `executeTool()` dispatches by tool type |
| `SettingsActivity.kt` | UI for configuring API keys, model names per provider, active provider selection, and system prompt; saves to SharedPreferences |
| `BridgeSetupActivity.kt` | Step-by-step guided setup wizard for the Termux WebSocket bridge; walks the user through installing Termux, Node.js, copying bridge files, and starting the server; copies bundled `setup.sh` from assets and provides a way to deploy it; verifies connection at the end |
| `StabilityDiagnostic.kt` | Pure Kotlin implementation of blackbox agent stability analysis; classifies trajectory as converging/diverging/limit-cycle/chaotic using drift and Shannon entropy metrics |
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

### Resources (`app/src/main/res/`)

| Path | Role |
|---|---|
| `layout/activity_chat.xml` | Chat screen layout with `RecyclerView`, input bar, typing indicator |
| `layout/activity_main.xml` | Home screen layout; includes Setup Bridge button entry point and polling status indicator |
| `layout/activity_settings.xml` | Settings screen layout with TextInputEditText fields per provider, plus system prompt multi-line editor |
| `layout/activity_bridge_setup.xml` | Bridge setup wizard layout; contains step indicator, step content area (ScrollView with per-step instructions and code blocks), navigation buttons (Back/Next/Finish), and a connection status indicator on the final step |
| `layout/item_message.xml` | Individual chat message bubble layout |
| `drawable/bg_*.xml` | Rounded rectangle backgrounds for message bubbles (assistant, user, tool) and inputs |
| `values/colors.xml` | Dark-theme color palette (`surface_dark`, `card_bg`, `gray_dark`, `gray_medium`, etc.) |
| `values/themes.xml` | `Theme.SDGClaw` and `Theme.SDGClaw.NoActionBar` (Material Design 3) |
| `values/strings.xml` | App strings including all bridge setup step titles, instructions, and code snippet strings |

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
| **UI** | ViewBinding / DataBinding, RecyclerView, AppCompat, Material Components |
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

### Termux Deep-Link Pattern (`TermuxDeepLinkHelper`)
- Termux exposes a `termux://` URI scheme and `com.termux.RUN_COMMAND` intent that allow external apps to run commands inside Termux without user interaction (requires the `com.termux.permission.RUN_COMMAND` permission and Termux:API or allow-external-apps setting)
- `TermuxDeepLinkHelper` encapsulates intent construction; callers pass a shell command string and the helper fires it via `startActivity` or `sendBroadcast` depending on availability
- Deep links are used as a recovery mechanism by `BridgePollingStateMachine`: when the bridge is found to be unreachable, the state machine fires a deep link to start/restart the Node.js server in Termux automatically
- The `QUERY_ALL_PACKAGES` or explicit package check is used to verify Termux is installed before attempting the deep link; if not installed, the state machine surfaces a `DISCONNECTED` status and prompts the user to complete setup
- Deep-link firing is always performed on `Dispatchers.Main` (as `startActivity` must run on the main thread); the surrounding polling coroutine switches context accordingly

### Bridge Polling State Machine Pattern (`BridgePollingStateMachine`)
- Implemented as a pure state machine class (no Android framework dependencies except `Context` for deep links); injectable and testable
- Polling runs on a `CoroutineScope` provided by the caller (typically `SDGClawApplication`'s scope); uses `delay()` between poll cycles rather than a `Timer` or `Handler`
- Configurable poll interval (default: 10 seconds when `CONNECTED`, 3 seconds when `RECOVERING` or `CONNECTING`)
- Health check is a lightweight WebSocket ping or HTTP probe to `ws://127.0.0.1:8765`; success transitions to `CONNECTED`, failure to `DISCONNECTED` / `RECOVERING`
- Recovery strategy: on first disconnect, immediately attempt deep-link restart; subsequent failures increment `recoveryAttempts`; after a configurable max (e.g., 3), the machine stays in `DISCONNECTED` and requires manual user action
- `StateFlow<PollingStatus>` exposed publicly; activities and `MainActivity` collect this flow inside `lifecycleScope` using `repeatOnLifecycle(Lifecycle.State.STARTED)` to update status badges/icons reactively
- State transitions are serialized (only one coroutine drives the machine); no additional mutex needed because the polling loop is a single sequential coroutine

### Asset Bundling Pattern (`setup.sh`)
- Shell scripts and other static deployment files are bundled in `app/src/main/assets/` so they are included in the APK and accessible at runtime via `Context.assets`
- At runtime, the activity (typically `BridgeSetupActivity`) opens the asset with `assets.open("setup.sh")` and copies it to an accessible path (e.g., `/sdcard/sdgclaw-setup/setup.sh`) using standard `InputStream`/`FileOutputStream` with a byte-buffer copy loop; this runs on `Dispatchers.IO`
- After copying, the UI displays the target path and instructs the user to run the script in Termux; a "Copy Path" or "Copy Command" button provides the exact command (e.g., `bash /sdcard/sdgclaw-setup/setup.sh`) to the