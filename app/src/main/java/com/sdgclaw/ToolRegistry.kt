package com.sdgclaw

import android.util.Log
import com.sdgclaw.bridge.TermuxBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * ToolRegistry - Manages available tools for the agent
 * Tools can be local (Kotlin functions) or remote (via Termux bridge)
 */
class ToolRegistry(
    private val termuxBridge: TermuxBridge
) {
    
    companion object {
        private const val TAG = "ToolRegistry"
    }
    
    // Tool definition data class
    data class ToolDefinition(
        val name: String,
        val description: String,
        val parameters: JSONObject, // JSON Schema
        val isLocal: Boolean = false
    )
    
    // Tool execution result
    data class ToolResult(
        val success: Boolean,
        val output: String,
        val error: String? = null
    )
    
    private val localTools = mutableMapOf<String, (JSONObject) -> ToolResult>()
    private val remoteTools = mutableSetOf<String>()
    private val toolDefinitions = mutableMapOf<String, ToolDefinition>()
    
    init {
        registerBuiltinTools()
    }
    
    /**
     * Register a local tool (executed in Kotlin)
     */
    fun registerLocalTool(
        name: String,
        description: String,
        parameters: JSONObject,
        executor: (JSONObject) -> ToolResult
    ) {
        localTools[name] = executor
        toolDefinitions[name] = ToolDefinition(name, description, parameters, true)
        Log.d(TAG, "Registered local tool: $name")
    }
    
    /**
     * Register a remote tool (executed via Termux bridge)
     */
    fun registerRemoteTool(
        name: String,
        description: String,
        parameters: JSONObject
    ) {
        remoteTools.add(name)
        toolDefinitions[name] = ToolDefinition(name, description, parameters, false)
        Log.d(TAG, "Registered remote tool: $name")
    }
    
    /**
     * Execute a tool by name
     */
    suspend fun executeTool(name: String, arguments: JSONObject): String {
        Log.d(TAG, "Executing tool: $name with args: $arguments")
        
        return if (localTools.containsKey(name)) {
            // Execute local tool
            try {
                val result = withContext(Dispatchers.IO) {
                    localTools[name]!!(arguments)
                }
                if (result.success) result.output else "Error: ${result.error}"
            } catch (e: Exception) {
                Log.e(TAG, "Local tool $name failed", e)
                "Error executing $name: ${e.message}"
            }
        } else if (remoteTools.contains(name)) {
            // Execute remote tool via Termux bridge
            executeRemoteTool(name, arguments)
        } else {
            "Error: Tool '$name' not found"
        }
    }
    
    /**
     * Execute tool via Termux bridge (async, returns immediately)
     * For synchronous result, the bridge would need request/response correlation
     */
    private fun executeRemoteTool(name: String, arguments: JSONObject): String {
        val requestId = java.util.UUID.randomUUID().toString()
        
        val request = JSONObject().apply {
            put("type", "tool_call")
            put("request_id", requestId)
            put("tool", name)
            put("arguments", arguments)
        }
        
        termuxBridge.sendJson(request)
        
        // For now, return immediately - in production you'd wait for response
        return "Tool '$name' dispatched to Termux backend (request: $requestId)"
    }
    
    /**
     * Get all tool definitions for LLM function calling
     */
    fun getToolDefinitions(): List<JSONObject> {
        return toolDefinitions.values.map { def ->
            JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", def.name)
                    put("description", def.description)
                    put("parameters", def.parameters)
                })
            }
        }
    }
    
    /**
     * Get tool definition by name
     */
    fun getToolDefinition(name: String): ToolDefinition? = toolDefinitions[name]
    
    /**
     * List all available tools
     */
    fun getAvailableTools(): List<String> = toolDefinitions.keys.toList()
    
    /**
     * Register built-in tools
     */
    private fun registerBuiltinTools() {
        // Local tool: Execute shell command via Termux bridge
        registerRemoteTool(
            name = "execute_command",
            description = "Execute a shell command in Termux environment",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("command", JSONObject().apply {
                        put("type", "string")
                        put("description", "The shell command to execute")
                    })
                    put("working_dir", JSONObject().apply {
                        put("type", "string")
                        put("description", "Working directory (optional)")
                    })
                    put("timeout", JSONObject().apply {
                        put("type", "integer")
                        put("description", "Timeout in seconds (default 30)")
                    })
                })
                put("required", org.json.JSONArray().apply { put("command") })
            }
        )
        
        // Local tool: Read file
        registerRemoteTool(
            name = "read_file",
            description = "Read contents of a file",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("path", JSONObject().apply {
                        put("type", "string")
                        put("description", "Path to the file")
                    })
                    put("encoding", JSONObject().apply {
                        put("type", "string")
                        put("description", "File encoding (default utf-8)")
                    })
                })
                put("required", org.json.JSONArray().apply { put("path") })
            }
        )
        
        // Local tool: Write file
        registerRemoteTool(
            name = "write_file",
            description = "Write content to a file",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("path", JSONObject().apply {
                        put("type", "string")
                        put("description", "Path to the file")
                    })
                    put("content", JSONObject().apply {
                        put("type", "string")
                        put("description", "Content to write")
                    })
                    put("append", JSONObject().apply {
                        put("type", "boolean")
                        put("description", "Append to file (default false)")
                    })
                })
                put("required", org.json.JSONArray().apply { put("path"); put("content") })
            }
        )
        
        // Local tool: List directory
        registerRemoteTool(
            name = "list_directory",
            description = "List contents of a directory",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("path", JSONObject().apply {
                        put("type", "string")
                        put("description", "Path to the directory")
                    })
                    put("show_hidden", JSONObject().apply {
                        put("type", "boolean")
                        put("description", "Show hidden files (default false)")
                    })
                })
                put("required", org.json.JSONArray().apply { put("path") })
            }
        )
        
        // Local tool: Get system info
        registerLocalTool(
            name = "get_system_info",
            description = "Get Android device system information",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject())
            }
        ) { _ ->
            ToolResult(
                success = true,
                output = """
                    Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}
                    Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})
                    SDK: ${android.os.Build.VERSION.CODENAME}
                    ABI: ${android.os.Build.SUPPORTED_ABIS.joinToString(", ")}
                    """.trimIndent()
            )
        }
        
        // Local tool: Get app info
        registerLocalTool(
            name = "get_app_info",
            description = "Get SDG Claw app information",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject())
            }
        ) { _ ->
            ToolResult(
                success = true,
                output = "SDG Claw v1.0\nPackage: com.sdgclaw\nBridge: ${termuxBridge.isConnected()}"
            )
        }
        
        Log.d(TAG, "Registered ${toolDefinitions.size} built-in tools")
    }
    
    /**
     * Handle tool result from Termux bridge (call this when bridge receives tool response)
     */
    fun handleToolResult(requestId: String, success: Boolean, output: String, error: String?) {
        // In a full implementation, you'd correlate requestId with pending requests
        // and deliver the result to the waiting coroutine
        Log.d(TAG, "Tool result for $requestId: success=$success")
    }
}