package com.priyo.codex.engine

import android.content.Context
import java.io.File

object ModelFiles {
    const val MODEL_NAME = "qwen2.5-coder-0.5b-instruct-q4_k_m.gguf"
    fun ensureModel(context: Context): File {
        val target = File(context.filesDir, MODEL_NAME)
        if (target.exists() && target.length() > 100_000_000L) return target
        val asset = "models/$MODEL_NAME"
        context.assets.open(asset).use { input ->
            target.outputStream().buffered().use { output -> input.copyTo(output, 1024 * 1024) }
        }
        require(target.length() > 100_000_000L) { "GGUF model asset missing or incomplete" }
        return target
    }
}
