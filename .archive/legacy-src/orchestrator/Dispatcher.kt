package com.horizons.orchestrator

class Dispatcher {
    private val tools = linkedMapOf<String, Tool>()

    fun register(tool: Tool) { tools[tool.id] = tool }
    fun unregister(id: String) { tools.remove(id) }
    fun list(): List<Tool> = tools.values.toList()
    fun get(id: String): Tool? = tools[id]

    suspend fun pickTool(prompt: String, hint: String? = null): Tool {
        TODO("Phase 5: VLM-as-manager picks a tool id from list() based on the prompt. Default = first registered.")
    }
}
