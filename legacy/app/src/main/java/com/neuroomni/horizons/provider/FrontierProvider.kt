package com.neuroomni.horizons.provider

/**
 * The provider toggles from Architecture §5. The toggle IS the routing decision:
 * Derek picks one, then talks directly to whatever's behind it. OmniNeural does not
 * intercept or re-route — it only does STT and mechanical glue.
 *
 * [ProviderId] is the stable, serializable key (used for persistence and the Router
 * selector). The richer [FrontierProvider] sealed type carries the resolved auth/config
 * needed to actually make a call, and is built from an [EndpointConfig] at send time.
 */
enum class ProviderId(
    val displayName: String,
    val billingPool: BillingPool,
    /** Which [Transport] reaches this provider — HTTP now, Termux shell in Session 6. */
    val transport: Transport,
    /** False for toggles whose transport isn't wired yet (shown but disabled in Router). */
    val implemented: Boolean,
) {
    Edge("Edge (offline)", BillingPool.Free, Transport.OnDevice, implemented = true),
    AnthropicDirect("Anthropic Direct", BillingPool.AnthropicConsole, Transport.Http, implemented = true),
    AIStudioGemini("AI Studio Gemini", BillingPool.GcpCredits, Transport.Http, implemented = true),
    OllamaCompatible("Ollama (self-hosted)", BillingPool.Free, Transport.Http, implemented = true),
    OpenAICompatible("OpenAI-compatible", BillingPool.External, Transport.Http, implemented = true),
    VertexClaude("Vertex Claude", BillingPool.GcpCredits, Transport.Http, implemented = false),
    VertexGemini("Vertex Gemini", BillingPool.GcpCredits, Transport.Http, implemented = false),
    ClaudeCodeCli("Claude Code CLI", BillingPool.GcpCredits, Transport.TermuxShell, implemented = false),
    GeminiCli("Gemini CLI", BillingPool.WorkspaceAllowance, Transport.TermuxShell, implemented = false);

    val isEdge: Boolean get() = this == Edge

    companion object {
        /** Frontier (non-edge) providers, in Router display order. */
        val frontier: List<ProviderId> get() = entries.filterNot { it.isEdge }

        fun fromName(name: String?): ProviderId =
            entries.firstOrNull { it.name == name } ?: Edge
    }
}

/** How the app physically reaches a provider. */
enum class Transport { OnDevice, Http, TermuxShell }

/**
 * The credit pool a toggle burns (Architecture §5 "Billing Pools"). Surfaced in the
 * Router so Derek can see which pool the active toggle draws from before he sends.
 */
enum class BillingPool(val label: String, val monitorHint: String) {
    Free("Free (battery)", "On-device — no metered cost"),
    AnthropicConsole("Anthropic Console", "console.anthropic.com → Billing"),
    GcpCredits("GCP credits", "GCP Console → Billing"),
    WorkspaceAllowance("Workspace allowance", "Google Workspace admin"),
    External("External account", "Provider's own dashboard"),
}
