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
| **Diagnostics** | `StabilityDiagnostic` — pure-Kotlin agent trajectory analysis (no external deps) |
| **Application** | `SDGClawApplication` — singleton Application class; owns `TermuxBridge` lifecycle |

---

## Key Files

### Android App (`app/src/main/java/com/sdgclaw/`)

| File | Role |
|---|---|
| `SDGClawApplication.kt` | Application singleton; initializes and auto-connects `TermuxBridge`; exposes bridge and `CoroutineScope` to activities |
| `MainActivity.kt` | Home screen; shows connection status; buttons for Test Connection, Settings, Chat, and Setup Bridge |
| `ChatActivity.kt` | Chat UI using `RecyclerView`; initializes `AgentLoop`, `LLMClient`, `ToolRegistry`; wires agent callbacks to UI; injects system prompt from SharedPreferences into `AgentLoop` |
| `AgentLoop.kt` | Core agent loop; manages `conversationHistory`, iterates LLM↔tool calls up to `MAX_ITERATIONS=10`; emits state via callbacks (`onAgentResponse`, `onToolCall`, `onToolResult`, `onError`, `onStateChange`); accepts system prompt as constructor parameter |
| `LLMClient.kt` | Multi-provider LLM HTTP client using OkHttp; handles OpenAI, Anthropic, Google Gemini, and custom OpenAI-compatible endpoints; reads API keys from SharedPreferences |
| `ToolRegistry.kt` | Registers local tools (Kotlin lambdas) and remote tools (via Termux bridge); `executeTool()` dispatches by tool type |
| `SettingsActivity.kt` | UI for configuring API keys, model names per provider, active provider selection, and system prompt; saves to SharedPreferences |
| `BridgeSetupActivity.kt` | Step-by-step guided setup wizard for the Termux WebSocket bridge; walks the user through installing Termux, Node.js, copying bridge files, and starting the server; verifies connection at the end |
| `StabilityDiagnostic.kt` | Pure Kotlin implementation of blackbox agent stability analysis; classifies trajectory as converging/diverging/limit-cycle/chaotic using drift and Shannon entropy metrics |
| `bridge/TermuxBridge.kt` | OkHttp WebSocket client; connects to `ws://127.0.0.1:8765`; auto-reconnect (3s delay); ping interval 30s; queues messages when disconnected |

### Termux Bridge (`termux-bridge/`)

| File | Role |
|---|---|
| `package.json` | Node.js package; depends on `ws ^8.16.0`; entry point `server.js` |
| `server.js` (described in README) | WebSocket server on port 8765; receives tool execution requests from the Android app; runs shell commands via `child_process.spawn` |

### Resources (`app/src/main/res/`)

| Path | Role |
|---|---|
| `layout/activity_chat.xml` | Chat screen layout with `RecyclerView`, input bar, typing indicator |
| `layout/activity_main.xml` | Home screen layout; includes Setup Bridge button entry point |
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
| **Language** | Kotlin (Android app), JavaScript/Node.js (Termux bridge) |
| **Android SDK** | API 34 (target), Material Design 3 |
| **UI** | ViewBinding / DataBinding, RecyclerView, AppCompat, Material Components |
| **Networking** | OkHttp (HTTP + WebSocket client) |
| **Serialization** | `kotlinx.serialization` (JSON) |
| **Async** | Kotlin Coroutines (`CoroutineScope`, `Channel`, `Dispatchers.IO/Main`) |
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

---

## Coding Conventions

### Kotlin Style
- Standard Kotlin idioms; `data class` for value types, `companion object` for constants/tags
- `TAG` constant in each class used with `android.util.Log`
- Coroutines used throughout for async operations; `withContext(Dispatchers.IO)` for blocking I/O
- Callbacks set via `setOn*` methods (e.g., `setOnConnected`, `setOnAgentResponse`)
- Nullability: bridge references typed as nullable (`TermuxBridge?`); forced with `!!` where lifecycle guarantees non-null
- `Channel<String>(Channel.UNLIMITED)` used for inter-component message passing

### Architecture Conventions
- `SDGClawApplication` is the single owner of `TermuxBridge`; activities retrieve it via `application as SDGClawApplication`
- Activities use `lifecycleScope` for coroutines; Application uses a manual `CoroutineScope(SupervisorJob() + Dispatchers.Main)`
- `AgentLoop` accepts all dependencies via constructor (LLMClient, ToolRegistry, TermuxBridge, system prompt string)
- Maximum 10 agent iterations per turn (`MAX_ITERATIONS = 10`)
- System prompt is stored in SharedPreferences under a dedicated key and read in `ChatActivity` before constructing `AgentLoop`; it is injected into the conversation history as a `ChatMessage("system", ...)` prepended to every turn

### Step-Based Wizard UI Pattern (`BridgeSetupActivity`)
- Multi-step setup flows are implemented as a single `Activity` with a `currentStep: Int` state variable
- Step content (title, body text, code snippets) is driven by a `steps: List<SetupStep>` data structure defined in the activity; each `SetupStep` is a local data class holding `title`, `description`, `codeSnippet` (nullable), and any per-step action flags
- Navigation uses Back/Next buttons; the final step's Next becomes a Finish/Verify button that triggers a live connection test against `TermuxBridge`
- A horizontal step indicator (e.g., dots or numbered chips) reflects `currentStep` visually
- Code snippets displayed in a monospace `TextView` inside a visually distinct card (dark background, rounded corners) so users can read commands to type into Termux
- Copy-to-clipboard button accompanies each code snippet block for convenience
- Connection verification on the final step runs in `lifecycleScope` on `Dispatchers.IO`; result updates a status `TextView` and icon on the main thread via `withContext(Dispatchers.Main)`
- `BridgeSetupActivity` is launched from `MainActivity` via a dedicated "Setup Bridge" button; declared in `AndroidManifest.xml` with `Theme.SDGClaw.NoActionBar`

### SharedPreferences Keys
- API keys, model names, and active provider are stored in SharedPreferences (existing pattern)
- System prompt stored under its own dedicated key (e.g., `"system_prompt"`) in the same SharedPreferences file
- All sensitive values (API keys) are never synced; SharedPreferences file is app-private

### Settings UI Conventions
- `SettingsActivity` groups related settings visually (per-provider sections, then global agent settings)
- System prompt field uses a multi-line `TextInputEditText` (`inputType="textMultiLine"`) within a `TextInputLayout`, consistent with other text fields in the settings screen
- Placeholder/hint text describes the field's effect on agent behavior
- Save action persists all fields (including system prompt) in a single pass

### Resource Conventions
- Dark-theme focused color palette defined in `colors.xml`
- All drawables are XML shape drawables with rounded corners
- Message bubble styles: different corner radii for assistant (bottom-left flat), user (bottom-right flat), tool messages
- No action bar style (`Theme.SDGClaw.NoActionBar`) used on all activities
- String resources for wizard step content (titles, instructions, code commands) are defined in `strings.xml` rather than hardcoded in the activity, following existing resource conventions

### Bridge Protocol
- JSON message passing over WebSocket
- Messages parsed with `org.json.JSONObject`
- Pending messages queued when WebSocket is disconnected, flushed on reconnect
- Reconnect delay: 3000ms; ping interval: 30000ms

---

## Agent Execution Flow

```
User input
    │
    ▼
AgentLoop.sendUserMessage()
    │
    ▼
conversationHistory.add(ChatMessage("system", systemPrompt))  ← prepended if set
conversationHistory.add(ChatMessage("user", ...))
    │
    ▼
runAgentTurn()  [up to MAX_ITERATIONS=10]
    │
    ├─► LLMClient.chat(history, tools) ──► LLM API (HTTP)
    │         │
    │         ├─ text response ──► onAgentResponse callback ──► ChatActivity UI
    │         │
    │         └─ tool_call ──► ToolRegistry.executeTool()
    │                   │
    │                   ├─ local tool: Kotlin lambda
    │                   └─ remote tool: TermuxBridge WebSocket ──► Node.js ──► shell
    │                             │
    │                             └─ result ──► conversationHistory.add("tool", result)
    │                                          └─ next LLM iteration
    │
    ▼
AgentState transitions: IDLE → THINKING → CALLING_TOOL → WAITING_FOR_TOOL → RESPONDING → IDLE
```

---

## WebSocket Bridge Protocol

The Termux bridge (Node.js `server.js`) listens on `ws://127.0.0.1:8765`.

**App → Bridge** (JSON):
```json
{
  "type": "execute_command" | "read_file" | "write_file" | "list_dir" | "ping",
  "id": "<request-id>",
  "payload": { ... }
}
```

**Bridge → App** (JSON):
```json
{
  "type": "result" | "error" | "pong",
  "id": "<request-id>",
  "output": "...",
  "error": "..."
}
```

---

## Permissions

Declared in `AndroidManifest.xml`:
- `INTERNET` — LLM API calls and WebSocket connection
- `FOREGROUND_SERVICE` — background agent operation
- `FOREGROUND_SERVICE_DATA_SYNC` — API 34 foreground service type

---

## Development Workflow

### Prerequisites
- Android Studio or A-IDE
- Gradle 8.13
- Android SDK 34
- Kotlin 1.9.22
- Termux (from F-Droid) with Node.js (`pkg install nodejs`)

### Build
```bash
./gradlew assembleDebug
```

### Termux Bridge Setup
```bash
mkdir -p ~/sdgclaw-bridge && cd ~/sdgclaw-bridge
npm init -y
npm install ws
# Copy server.js to this directory
node ~/sdgclaw-bridge/server.js
```

The in-app `Bridge