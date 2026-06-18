package com.ekam.baton.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ekam.baton.core.data.db.entity.ConversationEntity
import com.ekam.baton.core.data.db.entity.AgentEntity
import com.ekam.baton.core.data.db.entity.MessageEntity
import com.ekam.baton.core.data.db.dao.AgentDao
import com.ekam.baton.core.data.db.dao.ConversationDao
import com.ekam.baton.core.data.db.dao.MessageDao
import com.ekam.baton.core.data.db.dao.MemoryDao

import com.ekam.baton.core.data.db.entity.MemoryEntity

/**
 * BATON Room database.
 *
 * Add new [Entity][androidx.room.Entity] classes to [entities] and bump [version]
 * with a matching [Migration][androidx.room.migration.Migration] in [DataModule].
 *
 * Schema export directory is configured in core/data/build.gradle.kts via KSP
 * argument `room.schemaLocation` — schemas are version-controlled for audit.
 */
@Database(
    entities    = [ConversationEntity::class, AgentEntity::class, MessageEntity::class, MemoryEntity::class],
    version     = 5,
    exportSchema = true,
)
abstract class BatonDatabase : RoomDatabase() {

    abstract fun agentDao(): AgentDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao

    companion object {
        const val DATABASE_NAME = "baton.db"
    }
}
