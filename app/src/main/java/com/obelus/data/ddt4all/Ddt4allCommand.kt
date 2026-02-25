package com.obelus.data.ddt4all

data class Ddt4allCommand(
    val name: String,
    val desc: String,
    val code: String,
    val parameters: List<String>
)
