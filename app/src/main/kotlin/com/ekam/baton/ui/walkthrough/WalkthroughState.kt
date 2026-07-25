package com.ekam.baton.ui.walkthrough

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect

@Stable
class WalkthroughState {
    // Stores the bounds of specific elements we want to highlight.
    // Keyed by string identifier (e.g. "Chats", "Agents", "A2A", "Settings")
    val highlightedElements = mutableStateMapOf<String, Rect>()

    fun updateElementBounds(key: String, bounds: Rect) {
        highlightedElements[key] = bounds
    }
}

@Composable
fun rememberWalkthroughState(): WalkthroughState {
    return remember { WalkthroughState() }
}
