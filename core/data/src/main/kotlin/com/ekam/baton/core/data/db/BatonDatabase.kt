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
import com.ekam.baton.core.data.db.entity.AuditLogEntity
import com.ekam.baton.core.data.db.dao.AuditDao
import com.ekam.baton.core.data.db.entity.AgentActionLogEntity
import com.ekam.baton.core.data.db.dao.AgentActionLogDao
import com.ekam.baton.core.data.db.entity.WorldRoomEntity
import com.ekam.baton.core.data.db.entity.WorldRoomPropEntity
import com.ekam.baton.core.data.db.dao.WorldRoomDao
import com.ekam.baton.core.data.db.dao.WorldRoomPropDao

/**
 * BATON Room database.
 *
 * Version 11: Adds World Room + Agent Card identity system.
 *   - New tables: world_rooms, world_room_props
 *   - New agents columns: role, card_did, card_fingerprint, card_issued_at,
 *     card_avatar_seed, world_room_id
 *
 * Add new [Entity][androidx.room.Entity] classes to [entities] and bump [version]
 * with a matching [Migration][androidx.room.migration.Migration] in [DataModule].
 */
@Database(
    entities = [
        ConversationEntity::class,
        AgentEntity::class,
        MessageEntity::class,
        MemoryEntity::class,
        AuditLogEntity::class,
        AgentActionLogEntity::class,
        WorldRoomEntity::class,
        WorldRoomPropEntity::class,
    ],
    version = 11,
    exportSchema = true,
)
abstract class BatonDatabase : RoomDatabase() {

    abstract fun agentDao(): AgentDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun auditDao(): AuditDao
    abstract fun agentActionLogDao(): AgentActionLogDao
    abstract fun worldRoomDao(): WorldRoomDao
    abstract fun worldRoomPropDao(): WorldRoomPropDao

    companion object {
        const val DATABASE_NAME = "baton.db"
    }
}
