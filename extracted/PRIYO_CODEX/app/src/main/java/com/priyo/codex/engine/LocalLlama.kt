package com.priyo.codex.engine

object LocalLlama {
    init { System.loadLibrary("priyo_llama") }
    external fun generate(modelPath: String, prompt: String, maxTokens: Int): String
}
