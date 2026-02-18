package com.obelus.data.local.model

/**
 * User-facing interpretation of a signal value.
 */
data class Interpretation(
    val status: Status,
    val color: Int, // Color resource or hex
    val message: String,
    val action: String? = null
)
