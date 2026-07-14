package com.sdgclaw

import android.util.Log
import com.sdgclaw.bridge.TermuxBridge
import org.json.JSONObject

/**
 * Shared data models used by both [LLMClient] and [ToolRegistry].
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: JSONObject,
    val isLocal: Boolean = false
)

data class ToolResult(
    val success: Boolean,
    val output: String,
    val error: String? = null
)

/**
 * ToolRegistry — registers and dispatches local (Kotlin lambda) and remote
 * (Termux WebSocket) tools.
 */
class ToolRegistry(private val termuxBridge: TermuxBridge) {

    companion object {
        private const val TAG = "ToolRegistry"
    }

    private val localTools = mutableMapOf<String, suspend (JSONObject) -> ToolResult>()
    private val remoteToolNames = mutableSetOf<String>()

    init {
        registerBuiltinTools()
    }

    // ── Registration ───────────────────────────────────────────────────────────

    fun registerLocalTool(
        name: String,
        handler: suspend (JSONObject) -> ToolResult
    ) {
        localTools[name] = handler
        Log.d(TAG, "Registered local tool: $name")
    }

    fun registerRemoteTool(name: String) {
        remoteToolNames.add(name)
        Log.d(TAG, "Registered remote tool: $name")
    }

    // ── Dispatch ───────────────────────────────────────────────────────────────

    suspend fun executeTool(name: String, argsJson: String): ToolResult {
        return try {
            val args = JSONObject(argsJson)
            when {
                localTools.containsKey(name) -> {
                    Log.d(TAG, "Executing local tool: $name")
                    localTools[name]!!.invoke(args)
                }
                remoteToolNames.contains(name) -> {
                    Log.d(TAG, "Executing remote tool via bridge: $name")
                    val result = termuxBridge.executeCommand(name, args)
                    ToolResult(success = true, output = result)
                }
                else -> {
                    Log.w(TAG, "Unknown tool: $name")
                    ToolResult(success = false, output = "", error = "Unknown tool: $name")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tool execution error: $name", e)
            ToolResult(success = false, output = "", error = e.message ?: "Execution error")
        }
    }

    // ── Tool definitions (for LLM function-calling schema) ─────────────────────

    fun getToolDefinitions(): List<ToolDefinition> {
        val defs = mutableListOf<ToolDefinition>()

        // Local tools
        defs.addAll(builtinDefinitions)

        // Remote tools registered by name — provide a generic schema
        remoteToolNames.forEach { name ->
            defs.add(
                ToolDefinition(
                    name        = name,
                    description = "Remote Termux tool: $name",
                    parameters  = JSONObject("""{"type":"object","properties":{"command":{"type":"string"}},"required":["command"]}"""),
                    isLocal     = false
                )
            )
        }

        return defs
    }

    // ── Built-in local tools ───────────────────────────────────────────────────

    private val builtinDefinitions = mutableListOf<ToolDefinition>()

    private fun registerBuiltinTools() {
        // ── shell_exec ──────────────────────────────────────────────────────────
        val shellExecDef = ToolDefinition(
            name        = "shell_exec",
            description = "Execute a shell command in the Termux environment and return its stdout/stderr.",
            parameters  = JSONObject("""
                {
                  "type": "object",
                  "properties": {
                    "command": {
                      "type": "string",
                      "description": "The shell command to run."
                    },
                    "timeout_seconds": {
                      "type": "integer",
                      "description": "Optional execution timeout in seconds (default 30).",
                      "default": 30
                    }
                  },
                  "required": ["command"]
                }
            """.trimIndent()),
            isLocal = false   // delegated to bridge
        )
        builtinDefinitions.add(shellExecDef)
        remoteToolNames.add("shell_exec")

        // ── read_file ───────────────────────────────────────────────────────────
        val readFileDef = ToolDefinition(
            name        = "read_file",
            description = "Read the contents of a file at the given path.",
            parameters  = JSONObject("""
                {
                  "type": "object",
                  "properties": {
                    "path": {
                      "type": "string",
                      "description": "Absolute path to the file."
                    }
                  },
                  "required": ["path"]
                }
            """.trimIndent()),
            isLocal = false
        )
        builtinDefinitions.add(readFileDef)
        remoteToolNames.add("read_file")

        // ── write_file ──────────────────────────────────────────────────────────
        val writeFileDef = ToolDefinition(
            name        = "write_file",
            description = "Write content to a file at the given path, creating it if necessary.",
            parameters  = JSONObject("""
                {
                  "type": "object",
                  "properties": {
                    "path": {
                      "type": "string",
                      "description": "Absolute path to the file."
                    },
                    "content": {
                      "type": "string",
                      "description": "Text content to write."
                    }
                  },
                  "required": ["path", "content"]
                }
            """.trimIndent()),
            isLocal = false
        )
        builtinDefinitions.add(writeFileDef)
        remoteToolNames.add("write_file")

        // ── get_time ────────────────────────────────────────────────────────────
        val getTimeDef = ToolDefinition(
            name        = "get_time",
            description = "Return the current date and time on the device.",
            parameters  = JSONObject("""{"type":"object","properties":{}}"""),
            isLocal     = true
        )
        builtinDefinitions.add(getTimeDef)
        registerLocalTool("get_time") {
            ToolResult(success = true, output = java.util.Date().toString())
        }
    }
}
