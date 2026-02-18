package com.obelus.protocol

sealed class OBD2Response {
    data class Success(
        val data: ByteArray,
        val raw: String
    ) : OBD2Response() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Success

            if (!data.contentEquals(other.data)) return false
            if (raw != other.raw) return false

            return true
        }

        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + raw.hashCode()
            return result
        }
    }

    data class Error(
        val message: String,
        val raw: String? = null
    ) : OBD2Response()

    object NoData : OBD2Response()
}
