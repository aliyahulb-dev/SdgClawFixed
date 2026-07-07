# SDG Claw - Android AI Agent App

Open source Android AI agent app like Operit. Text chat interface, Termux WebSocket as execution backend, supports multiple AI providers (user brings own API key).

## Features

- 🤖 **Multi-provider LLM Support**: OpenAI, Anthropic, Google Gemini, Custom OpenAI-compatible endpoints
- 🔌 **Termux Bridge**: WebSocket connection to Node.js backend running in Termux
- 🛠️ **Tool System**: Execute commands, read/write files, list directories via Termux
- 💬 **Chat Interface**: Modern Material Design 3 chat UI with message bubbles
- ⚙️ **Settings**: Configure API keys, models, and active provider
- 🔒 **Privacy**: Your API keys stored locally, no cloud sync

## Architecture

```
┌─────────────────┐     WebSocket      ┌──────────────────┐
│  Android App    │ ◄─────────────────► │  Termux Bridge   │
│  (SDG Claw)     │   ws://127.0.0.1:8765│  (Node.js/ws)    │
└─────────────────┘                    └──────────────────┘
        ▲                                       ▲
        │                                       │
        ▼                                       ▼
┌─────────────────┐                    ┌──────────────────┐
│  LLM Providers  │                    │  Termux Tools    │
│  (API Keys)     │                    │  (Shell, Files)  │
└─────────────────┘                    └──────────────────┘
```

## Project Structure

```
app/src/main/java/com/sdgclaw/
├── bridge/
│   └── TermuxBridge.kt      # WebSocket client for Termux
├── SDGClawApplication.kt    # Application class
├── MainActivity.kt          # Home screen with connection test
├── ChatActivity.kt                  # Multi-provider LLM client
├── ToolRegistry.kt      # Tool management & execution
├── AgentLoop.kt         # Core agent execution loop
├── ChatActivity.kt      # Chat UI with RecyclerView
└── SettingsActivity.kt  # API keys & provider settings
```

## Building

### Prerequisites
- Android Studio / A-IDE
- Gradle 8.13
- Android SDK 34
- Kotlin 1.9.22

### Build Commands
```bash
# In A-IDE or terminal
./gradlew assembleDebug
```

### Termux Bridge Setup

1. Install Termux from F-Droid
2. Install Node.js: `pkg install nodejs`
3. Create bridge directory:
   ```bash
   mkdir -p ~/sdgclaw-bridge
   cd ~/sdgclaw-bridge
   npm init -y
   npm install ws
   ```
4. Save `server.js` (see below) to `~/sdgclaw-bridge/server.js`
5. Start server: `node ~/sdgclaw-bridge/server.js`

## Termux Bridge Server (server.js)

```javascript
const WebSocket = require('ws');
const { spawn } = require('child_process');

const wss = new WebSocket.Server({ port: 8765 });
console.log('SDGClaw bridge listening on ws://127.0.0.1:8765');

const clients = new Set();
const pendingRequests = new Map();

wss.on('connection', (ws) => {
    console.log('SDGClaw app connected');
    clients.add(ws);
    
    ws.on('message', (data) => {
        try {
            const msg = JSON.parse(data.toString());
            handleMessage(ws, msg);
        } catch (e) {
            console.error('Invalid JSON:', e);
        }
    });
    
    ws.on('close', () => {
        clients.delete(ws);
        console.log('SDGClaw app disconnected');
    });
});

function handleMessage(ws, msg) {
    switch (msg.type) {
        case 'test':
            ws.send(JSON.stringify({ type: 'test_response', ok: true }));
            break;
            
        case 'tool_call':
            executeTool(ws, msg);
            break;
            
        default:
            console.log('Unknown message type:', msg.type);
    }
}

function executeTool(ws, msg) {
    const { request_id, tool, arguments: args } = msg;
    
    switch (tool) {
        case 'execute_command':
            execCommand(args.command, args.working_dir, args.timeout || 30000)
                .then(result => sendResult(ws, request_id, true, result.stdout, result.stderr))
                .catch(err => sendResult(ws, request_id, false, '', err.message));
            break;
            
        case 'read_file':
            const fs = require('fs');
            fs.readFile(args.path, args.encoding || 'utf-8', (err, data) => {
                if (err) sendResult(ws, request_id, false, '', err.message);
                else sendResult(ws, request_id, true, data, '');
            });
            break;
            
        case 'write_file':
            const fs2 = require('fs');
            fs2.writeFile(args.path, args.content, { flag: args.append ? 'a' : 'w' }, (err) => {
                if (err) sendResult(ws, request_id, false, '', err.message);
                else sendResult(ws, request_id, true, 'OK', '');
            });
            break;
            
        case 'list_directory':
            const fs3 = require('fs');
            fs3.readdir(args.path, { withFileTypes: true }, (err, files) => {
                if (err) sendResult(ws, request_id, false, '', err.message);
                else {
                    const list = files
                        .filter(f => args.show_hidden || !f.name.startsWith('.'))
                        .map(f => ({ name: f.name, type: f.isDirectory() ? 'dir' : 'file' }));
                    sendResult(ws, request_id, true, JSON.stringify(list), '');
                }
            });
            break;
    }
}

function execCommand(command, cwd, timeout) {
    return new Promise((resolve, reject) => {
        const child = spawn('sh', ['-c', command], { cwd, shell: true });
        let stdout = '', stderr = '';
        const timer = setTimeout(() => {
            child.kill('SIGKILL');
            reject(new Error('Command timeout'));
        }, timeout);
        
        child.stdout.on('data', d => stdout += d.toString());
        child.stderr.on('data', d => stderr += d.toString());
        child.on('close', code => {
            clearTimeout(timer);
            resolve({ stdout, stderr, code });
        });
        child.on('error', err => {
            clearTimeout(timer);
            reject(err);
        });
    });
}

function sendResult(ws, requestId, success, output, error) {
    ws.send(JSON.stringify({
        type: 'tool_result',
        request_id: requestId,
        success,
        output,
        error: error || null
    }));
}
```

## Configuration

### LLM Providers

Add your API keys in Settings:
- **OpenAI**: Get key from https://platform.openai.com/api-keys
- **Anthropic**: Get key from https://console.anthropic.com/
- **Google Gemini**: Get key from https://makersuite.google.com/app/apikey
- **Custom**: Any OpenAI-compatible endpoint (Ollama, LM Studio, etc.)

### Default Models
- OpenAI: `gpt-4o-mini`
- Anthropic: `claude-3-5-sonnet-20241022`
- Google: `gemini-1.5-flash`

## Permissions

- `INTERNET` - WebSocket connection to Termux, LLM APIs
- `FOREGROUND_SERVICE` - Keep bridge alive

## License

MIT License - Open source for GitHub release

## Contributing

PRs welcome! Please follow Kotlin/Android conventions.