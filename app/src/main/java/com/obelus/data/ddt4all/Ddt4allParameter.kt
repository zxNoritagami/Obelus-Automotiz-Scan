package com.obelus.data.ddt4all

data class Ddt4allParameter(
    val name: String,
    val desc: String,
    val byte: Int,
    val bit: Int,
    val length: Int,
    val min: Double,
    val max: Double,
    val unit: String,
    val step: Double,
    val targetId: String,
    val values: List<Ddt4allValue>
)
