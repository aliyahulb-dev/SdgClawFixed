#!/data/data/com.termux/files/usr/bin/bash
# =============================================================================
# SDG Claw — Termux Bridge Setup Script
# =============================================================================
# This script installs and starts the SDG Claw WebSocket bridge inside Termux.
# Run it once after copying it into Termux:
#
#   bash ~/sdgclaw-setup.sh
#
# After the first run, start the bridge any time with:
#   node ~/sdgclaw-bridge/server.js
# =============================================================================

set -euo pipefail

# ── Colour helpers ────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Colour

info()    { echo -e "${CYAN}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*" >&2; }
die()     { error "$*"; exit 1; }

# ── Constants ─────────────────────────────────────────────────────────────────
BRIDGE_DIR="$HOME/sdgclaw-bridge"
SERVER_FILE="$BRIDGE_DIR/server.js"
PKG_FILE="$BRIDGE_DIR/package.json"
PORT=8765
MIN_NODE_MAJOR=16

# ── Step 1: Check that we are inside Termux ───────────────────────────────────
echo ""
echo -e "${BOLD}═══════════════════════════════════════════${NC}"
echo -e "${BOLD}   SDG Claw — Termux Bridge Installer      ${NC}"
echo -e "${BOLD}═══════════════════════════════════════════${NC}"
echo ""

if [[ ! -d "/data/data/com.termux" ]]; then
    die "This script must be run inside Termux."
fi
success "Running inside Termux."

# ── Step 2: Update package lists ─────────────────────────────────────────────
info "Updating Termux package lists (this may take a moment)…"
if ! pkg update -y 2>&1 | tail -5; then
    warn "pkg update encountered warnings; continuing anyway."
fi

# ── Step 3: Install Node.js if missing ───────────────────────────────────────
install_nodejs() {
    info "Installing Node.js via pkg…"
    pkg install -y nodejs || die "Failed to install nodejs. Check your internet connection and try again."
}

if command -v node &>/dev/null; then
    NODE_VER=$(node --version | sed 's/v//')
    NODE_MAJOR=$(echo "$NODE_VER" | cut -d. -f1)
    if (( NODE_MAJOR >= MIN_NODE_MAJOR )); then
        success "Node.js $NODE_VER is already installed."
    else
        warn "Node.js $NODE_VER is too old (need ≥ $MIN_NODE_MAJOR). Upgrading…"
        install_nodejs
    fi
else
    install_nodejs
fi

# Verify npm is available
if ! command -v npm &>/dev/null; then
    die "npm not found after Node.js install. Run 'pkg install nodejs' manually."
fi
success "npm $(npm --version) is available."

# ── Step 4: Create bridge directory ──────────────────────────────────────────
info "Creating bridge directory at $BRIDGE_DIR…"
mkdir -p "$BRIDGE_DIR"
success "Directory ready: $BRIDGE_DIR"

# ── Step 5: Write package.json ────────────────────────────────────────────────
info "Writing package.json…"
cat > "$PKG_FILE" << 'PKGJSON'
{
  "name": "sdgclaw-bridge",
  "version": "1.0.0",
  "description": "SDG Claw Termux WebSocket bridge",
  "main": "server.js",
  "scripts": {
    "start": "node server.js"
  },
  "dependencies": {
    "ws": "^8.16.0"
  },
  "license": "MIT"
}
PKGJSON
success "package.json written."

# ── Step 6: Write server.js ───────────────────────────────────────────────────
info "Writing server.js…"
cat > "$SERVER_FILE" << 'SERVERJS'
/**
 * SDG Claw — Termux WebSocket Bridge  (server.js)
 *
 * Listens on ws://127.0.0.1:8765
 * Receives JSON tool-execution requests from the Android app and returns results.
 *
 * Message format (App → Bridge):
 *   { "type": "execute_command"|"read_file"|"write_file"|"list_dir"|"ping",
 *     "id": "<request-id>",
 *     "payload": { ... } }
 *
 * Message format (Bridge → App):
 *   { "type": "result"|"error"|"pong",
 *     "id": "<request-id>",
 *     "output": "...",
 *     "error": "..." }
 */

'use strict';

const { WebSocketServer } = require('ws');
const { spawn }            = require('child_process');
const fs                   = require('fs');
const path                 = require('path');

const PORT    = 8765;
const HOST    = '127.0.0.1';
const TIMEOUT = 30_000; // ms — max time allowed per command

// ── Helpers ──────────────────────────────────────────────────────────────────

function send(ws, obj) {
    if (ws.readyState === ws.OPEN) {
        ws.send(JSON.stringify(obj));
    }
}

function reply(ws, id, output, errorMsg) {
    const msg = { id };
    if (errorMsg) {
        msg.type  = 'error';
        msg.error = errorMsg;
    } else {
        msg.type   = 'result';
        msg.output = output ?? '';
    }
    send(ws, msg);
}

// ── Handlers ─────────────────────────────────────────────────────────────────

function handleExecuteCommand(ws, id, payload) {
    const cmd = payload.command;
    if (!cmd || typeof cmd !== 'string') {
        return reply(ws, id, null, 'Missing or invalid "command" in payload');
    }

    let stdout = '';
    let stderr = '';
    let done   = false;

    // Run the command through bash so pipes, redirects, etc. work.
    const child = spawn('/data/data/com.termux/files/usr/bin/bash', ['-c', cmd], {
        env: { ...process.env },
        timeout: TIMEOUT,
    });

    const timer = setTimeout(() => {
        if (!done) {
            child.kill('SIGKILL');
            reply(ws, id, stdout || null, `Command timed out after ${TIMEOUT / 1000}s`);
            done = true;
        }
    }, TIMEOUT);

    child.stdout.on('data', d => { stdout += d.toString(); });
    child.stderr.on('data', d => { stderr += d.toString(); });

    child.on('close', code => {
        if (done) return;
        done = true;
        clearTimeout(timer);
        const output = stdout + (stderr ? `\n[stderr]\n${stderr}` : '');
        if (code === 0) {
            reply(ws, id, output);
        } else {
            reply(ws, id, output, `Process exited with code ${code}${stderr ? ': ' + stderr.trim() : ''}`);
        }
    });

    child.on('error', err => {
        if (done) return;
        done = true;
        clearTimeout(timer);
        reply(ws, id, null, `spawn error: ${err.message}`);
    });
}

function handleReadFile(ws, id, payload) {
    const filePath = payload.path;
    if (!filePath) return reply(ws, id, null, 'Missing "path" in payload');
    try {
        const content = fs.readFileSync(path.resolve(filePath), 'utf8');
        reply(ws, id, content);
    } catch (err) {
        reply(ws, id, null, `read_file error: ${err.message}`);
    }
}

function handleWriteFile(ws, id, payload) {
    const filePath = payload.path;
    const content  = payload.content ?? '';
    if (!filePath) return reply(ws, id, null, 'Missing "path" in payload');
    try {
        const resolved = path.resolve(filePath);
        fs.mkdirSync(path.dirname(resolved), { recursive: true });
        fs.writeFileSync(resolved, content, 'utf8');
        reply(ws, id, `Written ${content.length} bytes to ${resolved}`);
    } catch (err) {
        reply(ws, id, null, `write_file error: ${err.message}`);
    }
}

function handleListDir(ws, id, payload) {
    const dirPath = payload.path ?? '.';
    try {
        const entries = fs.readdirSync(path.resolve(dirPath), { withFileTypes: true });
        const lines   = entries.map(e => `${e.isDirectory() ? 'd' : 'f'} ${e.name}`);
        reply(ws, id, lines.join('\n'));
    } catch (err) {
        reply(ws, id, null, `list_dir error: ${err.message}`);
    }
}

// ── WebSocket server ──────────────────────────────────────────────────────────

const wss = new WebSocketServer({ host: HOST, port: PORT });

wss.on('listening', () => {
    console.log(`[SDG Claw Bridge] Listening on ws://${HOST}:${PORT}`);
});

wss.on('connection', (ws, req) => {
    console.log(`[Bridge] Client connected from ${req.socket.remoteAddress}`);

    ws.on('message', raw => {
        let msg;
        try {
            msg = JSON.parse(raw.toString());
        } catch {
            send(ws, { type: 'error', id: null, error: 'Invalid JSON' });
            return;
        }

        const { type, id, payload = {} } = msg;

        switch (type) {
            case 'ping':
                send(ws, { type: 'pong', id });
                break;
            case 'execute_command':
                handleExecuteCommand(ws, id, payload);
                break;
            case 'read_file':
                handleReadFile(ws, id, payload);
                break;
            case 'write_file':
                handleWriteFile(ws, id, payload);
                break;
            case 'list_dir':
                handleListDir(ws, id, payload);
                break;
            default:
                reply(ws, id, null, `Unknown message type: "${type}"`);
        }
    });

    ws.on('close', () => console.log('[Bridge] Client disconnected.'));
    ws.on('error', err => console.error('[Bridge] WS error:', err.message));
});

wss.on('error', err => {
    console.error(`[Bridge] Server error: ${err.message}`);
    process.exit(1);
});

// Graceful shutdown
process.on('SIGTERM', () => { wss.close(() => process.exit(0)); });
process.on('SIGINT',  () => { wss.close(() => process.exit(0)); });
SERVERJS
success "server.js written."

# ── Step 7: Install npm dependencies ─────────────────────────────────────────
info "Installing npm dependencies (ws)…"
cd "$BRIDGE_DIR"
if ! npm install --omit=dev 2>&1; then
    die "npm install failed. Check your internet connection."
fi
success "Dependencies installed."

# ── Step 8: Make the script executable ───────────────────────────────────────
chmod +x "$SERVER_FILE"
success "server.js marked executable."

# ── Step 9: Sanity-check the installation ────────────────────────────────────
info "Verifying installation…"

[[ -f "$SERVER_FILE" ]] || die "server.js not found at expected location."
[[ -f "$BRIDGE_DIR/node_modules/ws/package.json" ]] || \
    die "ws module not found in node_modules."

# Quick syntax check
node --check "$SERVER_FILE" || die "server.js has syntax errors."
success "All checks passed."

# ── Done ──────────────────────────────────────────────────────────────────────
echo ""
echo -e "${BOLD}${GREEN}✓ SDG Claw Bridge installed successfully!${NC}"
echo ""
echo -e "  Bridge directory : ${CYAN}$BRIDGE_DIR${NC}"
echo -e "  Start server     : ${CYAN}node $SERVER_FILE${NC}"
echo -e "  WebSocket URL    : ${CYAN}ws://127.0.0.1:$PORT${NC}"
echo ""
echo -e "${YELLOW}Starting the bridge now…${NC}"
echo ""

exec node "$SERVER_FILE"
