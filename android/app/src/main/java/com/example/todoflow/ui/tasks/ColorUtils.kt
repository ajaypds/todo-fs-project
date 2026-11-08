package com.example.todoflow.ui.tasks

import androidx.compose.ui.graphics.Color

fun parseHexColor(hex: String?): Color {
    if (hex.isNullOrBlank()) return Color(0xFF3B82F6)
    return try {
        val clean = hex.removePrefix("#")
        val colorInt = when (clean.length) {
            6 -> ("FF" + clean).toLong(16)
            8 -> clean.toLong(16)
            else -> 0xFF3B82F6
        }
        Color(colorInt)
    } catch (_: Exception) {
        Color(0xFF3B82F6)
    }
}
