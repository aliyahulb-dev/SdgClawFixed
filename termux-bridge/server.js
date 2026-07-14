/**
 * SDG Claw — Termux WebSocket Bridge  (server.js)
 *
 * Listens on ws://127.0.0.1:8765
 * Receives JSON tool-execution requests from the Android app and returns results.
 *
 * Message format (App → Bridge):
 *   {
 *     "type": "execute_command" | "read_file" | "write_file" | "list_dir" | "ping",
 *     "id": "<uuid>",
 *     "payload": { ... }
 *   }
 *
 * Message format (Bridge → App):
 *   {
 *     "type": "result" | "error" | "pong",
 *     "id": "<uuid>",
 *     "output": "...",   // present on "result"
 *     "error": "..."     // present on "error"
 *   }
 *
 * Usage:
 *   node ~/sdgclaw-bridge/server.js
 */

'use strict';

const { WebSocketServer } = require('ws');
const { spawn }            = require('child_process');
const fs                   = require('fs');
const path                 = require('path');

// ── Configuration ─────────────────────────────────────────────────────────────
const PORT    = 8765;
const HOST    = '127.0.0.1';
const TIMEOUT = 30_000; // ms — maximum time a single command may run

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Send a JSON object to a WebSocket client, silently ignoring non-OPEN sockets.
 * @param {import('ws').WebSocket} ws
 * @param {object} obj
 */
function send(ws, obj) {
    if (ws.readyState === ws.OPEN) {
        ws.send(JSON.stringify(obj));
    }
}

/**
 * Build and send a result / error reply.
 * @param {import('ws').WebSocket} ws
 * @param {string|null} id        Request id (may be null for parse errors).
 * @param {string|null} output    Command / file output on success.
 * @param {string|null} errorMsg  Error message; if truthy the reply type is "error".
 */
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

// ── Tool handlers ─────────────────────────────────────────────────────────────

/**
 * execute_command — run an arbitrary shell command via bash.
 * stdout + stderr are captured and returned; non-zero exit codes are errors.
 *
 * payload: { command: string }
 */
function handleExecuteCommand(ws, id, payload) {
    const cmd = payload.command;
    if (!cmd || typeof cmd !== 'string') {
        return reply(ws, id, null, 'Missing or invalid "command" in payload');
    }

    let stdout = '';
    let stderr = '';
    let done   = false;

    const child = spawn(
        '/data/data/com.termux/files/usr/bin/bash',
        ['-c', cmd],
        { env: { ...process.env } }
    );

    // Safety timeout — kill if the command runs too long
    const timer = setTimeout(() => {
        if (done) return;
        done = true;
        child.kill('SIGKILL');
        reply(ws, id, stdout || null,
            `Command timed out after ${TIMEOUT / 1000}s`);
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
            reply(ws, id, output,
                `Process exited with code ${code}${stderr ? ': ' + stderr.trim() : ''}`);
        }
    });

    child.on('error', err => {
        if (done) return;
        done = true;
        clearTimeout(timer);
        reply(ws, id, null, `spawn error: ${err.message}`);
    });
}

/**
 * read_file — read a file from the filesystem and return its UTF-8 contents.
 *
 * payload: { path: string }
 */
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

/**
 * write_file — write UTF-8 content to a file, creating parent dirs as needed.
 *
 * payload: { path: string, content: string }
 */
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

/**
 * list_dir — list directory entries, prefixed with 'd' (dir) or 'f' (file).
 *
 * payload: { path?: string }  (defaults to '.')
 */
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
    console.log('[SDG Claw Bridge] Waiting for connections from the Android app…');
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
        console.log(`[Bridge] → type="${type}" id="${id}"`);

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
    if (err.code === 'EADDRINUSE') {
        console.error(`[Bridge] Port ${PORT} is already in use. Is the bridge already running?`);
    }
    process.exit(1);
});

// ── Graceful shutdown ─────────────────────────────────────────────────────────
function shutdown(signal) {
    console.log(`\n[Bridge] Received ${signal}. Shutting down…`);
    wss.close(() => {
        console.log('[Bridge] Server closed.');
        process.exit(0);
    });
}

process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT',  () => shutdown('SIGINT'));
