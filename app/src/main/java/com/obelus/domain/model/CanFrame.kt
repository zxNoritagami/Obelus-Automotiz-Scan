package com.obelus.domain.model

data class CanFrame(
    val id: String,
    val data: ByteArray,
    val timestamp: Long = System.currentTimeMillis(),
    val lastChanged: Long = System.currentTimeMillis(),
    val changedBytesMask: Int = 0,
    val frequencyHz: Float = 0f,
    val frameCount: Int = 1,
    val heuristicTag: String? = null,
    val isFromBaseline: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CanFrame) return false
        if (id != other.id) return false
        if (!data.contentEquals(other.data)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}
