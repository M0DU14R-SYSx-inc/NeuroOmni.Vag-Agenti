package com.horizons.provider

import com.horizons.orchestrator.Tool
import com.horizons.termux.TermuxBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * CLI-as-provider Tool. Substitutes the user prompt into [commandTemplate]
 * (where `{prompt}` is the placeholder, POSIX-shell-escaped) and dispatches
 * the result through Termux's RUN_COMMAND intent via [TermuxBridge].
 *
 * stdout is emitted progressively, line by line, when the command completes.
 * stderr (when non-empty alongside a non-zero exit code) is surfaced as a
 * final `[error] ...` token. Bridge-level failures emit a single
 * `[error] <message>` token.
 */
class TermuxCliClient(
    override val id: String,
    override val displayName: String,
    private val commandTemplate: String,
    private val bridge: TermuxBridge,
) : Tool {

    override fun run(prompt: String, imagePath: String?): Flow<String> = flow {
        val escaped = shellEscape(prompt)
        val command = if (commandTemplate.contains(PROMPT_PLACEHOLDER)) {
            commandTemplate.replace(PROMPT_PLACEHOLDER, escaped)
        } else {
            // No placeholder — append the escaped prompt as the final arg.
            "$commandTemplate $escaped"
        }

        val result = bridge.run(command)
        result.fold(
            onSuccess = { out ->
                if (out.stdout.isNotEmpty()) {
                    for (line in out.stdout.split('\n')) {
                        if (line.isNotEmpty()) emit(line + "\n")
                    }
                }
                if (out.exitCode != 0 && out.stderr.isNotBlank()) {
                    emit("[error] " + out.stderr.trim())
                }
            },
            onFailure = { e ->
                emit("[error] " + (e.message ?: e.javaClass.simpleName))
            }
        )
    }.flowOn(Dispatchers.IO)

    /**
     * POSIX shell-escape: wrap in single quotes, replacing any internal `'`
     * with the canonical `'\''` sequence. The result is always safe to splice
     * as a single argument inside a `bash -c '...'`-style command line.
     */
    private fun shellEscape(s: String): String =
        "'" + s.replace("'", "'\\''") + "'"

    companion object {
        const val PROMPT_PLACEHOLDER = "{prompt}"
    }
}
