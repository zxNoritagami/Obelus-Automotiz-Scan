package com.obelus.domain.model

data class DdtEcu(
    val name: String,
    val protocol: String,
    val group: String,
    val parameters: List<DdtParameter> = emptyList(),
    val commands: List<DdtCommand> = emptyList()
)

data class DdtParameter(
    val name: String,
    val description: String,
    val byteOffset: Int,
    val bitOffset: Int,
    val length: Int,
    val minValue: Float,
    val maxValue: Float,
    val unit: String,
    val step: Float,
    val offset: Float = 0f,
    val isSigned: Boolean = false,
    val isLittleEndian: Boolean = false,
    val valueMap: Map<Int, String> = emptyMap()
)

data class DdtCommand(
    val name: String,
    val description: String,
    val hexRequest: String
)
