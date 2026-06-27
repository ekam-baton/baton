package com.ekam.baton.core.data.preferences

import kotlinx.serialization.Serializable

@Serializable
data class KeyboardShortcut(
    val label: String,
    val textToInsert: String,
    val isImmediate: Boolean = false
)
