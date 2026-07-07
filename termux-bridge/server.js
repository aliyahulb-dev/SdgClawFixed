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