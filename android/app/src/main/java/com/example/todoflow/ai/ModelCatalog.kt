package com.example.todoflow.ai

/** A model the app offers to download. Sizes are approximate (shown to the user, used for the free-space check). */
data class ModelSpec(
    val id: String,
    val name: String,
    val description: String,
    val fileName: String,
    val url: String,
    val approxBytes: Long,
    /** Phones below this much RAM will struggle to run it. */
    val minRamGb: Int,
    /** Needs a Hugging Face token (and an accepted licence) to download. */
    val gated: Boolean
)

object ModelCatalog {

    private const val HF = "https://huggingface.co/litert-community"

    val models: List<ModelSpec> = listOf(
        ModelSpec(
            id = "qwen3-0.6b",
            name = "Qwen3 0.6B",
            description = "Smallest and fastest. Good for simple requests; may slip on long or messy ones.",
            fileName = "Qwen3-0.6B.litertlm",
            url = "$HF/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
            approxBytes = 620_000_000L,
            minRamGb = 4,
            gated = false
        ),
        ModelSpec(
            id = "gemma-4-e2b",
            name = "Gemma 4 E2B",
            description = "Best balance of quality and speed on phones with 8 GB of RAM or more.",
            fileName = "gemma-4-E2B-it.litertlm",
            url = "$HF/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            approxBytes = 2_580_000_000L,
            minRamGb = 8,
            gated = true
        ),
        ModelSpec(
            id = "gemma-4-e4b",
            name = "Gemma 4 E4B",
            description = "Highest quality, slower and larger. Needs a high-end phone (12 GB of RAM or more).",
            fileName = "gemma-4-E4B-it.litertlm",
            url = "$HF/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
            approxBytes = 3_650_000_000L,
            minRamGb = 12,
            gated = true
        )
    )

    /** The model to highlight for a phone with [ramGb] of RAM. */
    fun recommended(ramGb: Int): ModelSpec = when {
        ramGb >= 8 -> models.first { it.id == "gemma-4-e2b" }
        else -> models.first { it.id == "qwen3-0.6b" }
    }

    fun byFileName(fileName: String): ModelSpec? = models.firstOrNull { it.fileName == fileName }

    /** A readable name for any model file, catalogue or imported. */
    fun displayName(fileName: String): String =
        byFileName(fileName)?.name ?: fileName.removeSuffix(".litertlm")
}
