package com.ekam.baton.core.data.db.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityTest {

    // ── AgentEntity ─────────────────────────────────────────────────────

    @Test
    fun `AgentEntity has correct default values`() {
        val agent = AgentEntity(
            name = "TestAgent",
            description = "A test agent",
            mcpEndpointUrl = "https://example.com/mcp",
            authType = "bearer",
            authConfig = "{}",
            colorAccent = "#FF0000"
        )

        assertNotNull(agent.id)
        assertTrue(agent.isActive)
        assertFalse(agent.isAuthenticated)
        assertNull(agent.avatarUri)
        assertNull(agent.lastUsedAt)
        assertNull(agent.lastAuthAt)
        assertTrue(agent.createdAt > 0)
        assertEquals("standard", agent.securityMode)
        assertEquals("{}", agent.securityConfig)
    }

    @Test
    fun `AgentEntity copy works`() {
        val agent = AgentEntity(
            name = "Original",
            description = "Desc",
            mcpEndpointUrl = "https://example.com/mcp",
            authType = "bearer",
            authConfig = "{}",
            colorAccent = "#00FF00"
        )
        val copy = agent.copy(name = "Copied", isActive = false)

        assertEquals("Copied", copy.name)
        assertFalse(copy.isActive)
        // Unchanged fields carry over
        assertEquals(agent.id, copy.id)
        assertEquals(agent.description, copy.description)
        assertEquals(agent.mcpEndpointUrl, copy.mcpEndpointUrl)
        assertEquals(agent.colorAccent, copy.colorAccent)
    }

    @Test
    fun `AgentEntity equality is based on all fields`() {
        val a = AgentEntity(
            id = "fixed-id",
            name = "Agent",
            description = "D",
            mcpEndpointUrl = "https://mcp.test",
            authType = "none",
            authConfig = "{}",
            colorAccent = "#000000",
            createdAt = 1000L
        )
        val b = a.copy()
        assertEquals(a, b)

        val c = a.copy(name = "Different")
        assertNotEquals(a, c)
    }

    // ── ConversationEntity ──────────────────────────────────────────────

    @Test
    fun `ConversationEntity has correct default values`() {
        val conv = ConversationEntity(
            agentId = "agent-1",
            title = "Hello"
        )

        assertNotNull(conv.id)
        assertFalse(conv.isPinned)
        assertEquals(0, conv.messageCount)
        assertTrue(conv.createdAt > 0)
        assertTrue(conv.updatedAt > 0)
    }

    @Test
    fun `ConversationEntity copy works`() {
        val conv = ConversationEntity(
            agentId = "agent-1",
            title = "Original"
        )
        val copy = conv.copy(title = "Updated", isPinned = true, messageCount = 5)

        assertEquals("Updated", copy.title)
        assertTrue(copy.isPinned)
        assertEquals(5, copy.messageCount)
        // Unchanged fields carry over
        assertEquals(conv.id, copy.id)
        assertEquals(conv.agentId, copy.agentId)
    }

    @Test
    fun `ConversationEntity equality is based on all fields`() {
        val a = ConversationEntity(
            id = "conv-1",
            agentId = "agent-1",
            title = "T",
            createdAt = 2000L,
            updatedAt = 3000L
        )
        val b = a.copy()
        assertEquals(a, b)

        val c = a.copy(isPinned = true)
        assertNotEquals(a, c)
    }

    // ── MessageEntity ───────────────────────────────────────────────────

    @Test
    fun `MessageEntity has correct default values`() {
        val msg = MessageEntity(
            conversationId = "conv-1",
            role = "user",
            content = "Hi there"
        )

        assertNotNull(msg.id)
        assertFalse(msg.isStreaming)
        assertNull(msg.attachments)
        assertNull(msg.toolCallJson)
        assertNull(msg.tokenCount)
        assertTrue(msg.timestamp > 0)
    }

    @Test
    fun `MessageEntity copy works`() {
        val msg = MessageEntity(
            conversationId = "conv-1",
            role = "user",
            content = "Original"
        )
        val copy = msg.copy(content = "Edited", isStreaming = true, tokenCount = 42)

        assertEquals("Edited", copy.content)
        assertTrue(copy.isStreaming)
        assertEquals(42, copy.tokenCount)
        // Unchanged fields carry over
        assertEquals(msg.id, copy.id)
        assertEquals(msg.conversationId, copy.conversationId)
        assertEquals(msg.role, copy.role)
    }

    @Test
    fun `MessageEntity equality is based on all fields`() {
        val a = MessageEntity(
            id = "msg-1",
            conversationId = "conv-1",
            role = "assistant",
            content = "Reply",
            timestamp = 5000L
        )
        val b = a.copy()
        assertEquals(a, b)

        val c = a.copy(isStreaming = true)
        assertNotEquals(a, c)
    }

    // ── MemoryEntity ────────────────────────────────────────────────────

    @Test
    fun `MemoryEntity has correct default isActive value`() {
        val memory = MemoryEntity(
            id = "mem-1",
            layer = "episodic",
            agentId = "agent-1",
            conversationId = "conv-1",
            title = "Remembered",
            content = "Something important",
            createdAt = 1000L,
            lastAccessedAt = 2000L,
            relevanceScore = 0.95f,
            tags = "tag1,tag2"
        )

        assertTrue(memory.isActive)
    }

    @Test
    fun `MemoryEntity fields are accessible`() {
        val memory = MemoryEntity(
            id = "mem-2",
            layer = "semantic",
            agentId = null,
            conversationId = null,
            title = "General Knowledge",
            content = "The sky is blue",
            createdAt = 3000L,
            lastAccessedAt = 4000L,
            relevanceScore = 0.75f,
            tags = "facts",
            isActive = false
        )

        assertEquals("mem-2", memory.id)
        assertEquals("semantic", memory.layer)
        assertNull(memory.agentId)
        assertNull(memory.conversationId)
        assertEquals("General Knowledge", memory.title)
        assertEquals("The sky is blue", memory.content)
        assertEquals(3000L, memory.createdAt)
        assertEquals(4000L, memory.lastAccessedAt)
        assertEquals(0.75f, memory.relevanceScore, 0.001f)
        assertEquals("facts", memory.tags)
        assertFalse(memory.isActive)
    }

    @Test
    fun `MemoryEntity copy works`() {
        val memory = MemoryEntity(
            id = "mem-3",
            layer = "episodic",
            agentId = "agent-1",
            conversationId = "conv-1",
            title = "Title",
            content = "Content",
            createdAt = 1000L,
            lastAccessedAt = 2000L,
            relevanceScore = 0.5f,
            tags = "a,b"
        )
        val copy = memory.copy(relevanceScore = 0.9f, isActive = false)

        assertEquals(0.9f, copy.relevanceScore, 0.001f)
        assertFalse(copy.isActive)
        // Unchanged fields carry over
        assertEquals(memory.id, copy.id)
        assertEquals(memory.layer, copy.layer)
        assertEquals(memory.title, copy.title)
    }
}
