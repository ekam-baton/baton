package com.ekam.baton.core.data.preferences

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardShortcutSerializationTest {

    @Test
    fun testSerializationAndDeserialization() {
        val shortcuts = listOf(
            KeyboardShortcut(label = "Test1", textToInsert = "Inserted Text 1", isImmediate = false),
            KeyboardShortcut(label = "Test2", textToInsert = "Inserted Text 2", isImmediate = true)
        )

        val jsonString = Json.encodeToString(shortcuts)
        val deserialized = Json.decodeFromString<List<KeyboardShortcut>>(jsonString)

        assertEquals(2, deserialized.size)
        assertEquals("Test1", deserialized[0].label)
        assertEquals("Inserted Text 1", deserialized[0].textToInsert)
        assertFalse(deserialized[0].isImmediate)

        assertEquals("Test2", deserialized[1].label)
        assertEquals("Inserted Text 2", deserialized[1].textToInsert)
        assertTrue(deserialized[1].isImmediate)
    }

    @Test
    fun testDefaultValues() {
        val shortcut = KeyboardShortcut(label = "Default", textToInsert = "hello")
        assertFalse(shortcut.isImmediate)
    }
}
