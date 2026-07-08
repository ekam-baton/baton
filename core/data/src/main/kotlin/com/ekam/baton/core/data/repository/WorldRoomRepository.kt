package com.ekam.baton.core.data.repository

import com.ekam.baton.core.data.db.dao.WorldRoomDao
import com.ekam.baton.core.data.db.dao.WorldRoomPropDao
import com.ekam.baton.core.data.db.entity.WorldRoomPropEntity
import com.ekam.baton.core.data.model.AgentRole
import com.ekam.baton.core.data.model.WorldRoom
import com.ekam.baton.core.data.model.WorldRoomProp
import com.ekam.baton.core.data.model.toDomainModel
import com.ekam.baton.core.data.model.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class WorldRoomRepository(
    private val roomDao: WorldRoomDao,
    private val propDao: WorldRoomPropDao
) {
    fun observeAllRooms(): Flow<List<WorldRoom>> =
        roomDao.observeAll().map { list -> list.map { it.toDomainModel() } }

    suspend fun getRoomForAgent(agentId: String): WorldRoom? =
        roomDao.getByAgentId(agentId)?.toDomainModel()

    suspend fun upsertRoom(room: WorldRoom) = roomDao.upsert(room.toEntity())

    suspend fun deleteRoomForAgent(agentId: String) {
        roomDao.getByAgentId(agentId)?.let { entity ->
            propDao.deleteByRoomId(entity.id)
            roomDao.delete(entity)
        }
    }

    fun observePropsForRoom(roomId: String): Flow<List<WorldRoomProp>> =
        propDao.observeByRoom(roomId).map { list -> list.map { it.toDomainModel() } }

    suspend fun getPropsForRoom(roomId: String): List<WorldRoomProp> =
        propDao.getByRoomId(roomId).map { it.toDomainModel() }

    /** Place the default props for this room's role. Called once on room creation. */
    suspend fun setDefaultPropsForRoom(room: WorldRoom) {
        propDao.deleteByRoomId(room.id) // clear any prior props
        val props = DefaultPropLayouts.forRole(room.role).mapIndexed { i, layout ->
            WorldRoomPropEntity(
                id = UUID.randomUUID().toString(),
                roomId = room.id,
                propType = layout.type,
                gridX = layout.x,
                gridY = layout.y,
                gridZ = layout.z,
                // Golden-angle spread (137 * i) so props never animate in sync
                animSeed = i * 137
            )
        }
        propDao.insertAll(props)
    }

    suspend fun upsertProp(prop: WorldRoomProp) = propDao.upsert(prop.toEntity())
}

// ── Default prop layout tables ─────────────────────────────────────────────────

data class PropLayout(val type: String, val x: Float, val y: Float, val z: Float = 1f)

object DefaultPropLayouts {
    fun forRole(role: AgentRole): List<PropLayout> = when (role) {
        AgentRole.CODER -> listOf(
            PropLayout("DUAL_MONITOR_DESK",   2f, 2f,   1f),
            PropLayout("SERVER_TOWER",         5f, 1f,   1f),
            PropLayout("COFFEE_CUP",           2.5f, 2.5f, 2f),
            PropLayout("MECH_KEYBOARD",        2f, 2.8f, 2f),
            PropLayout("DESK_SUCCULENT",       5f, 3f,   2f),
            PropLayout("FLOATING_DATA_CUBE",   3f, 1f,   3f),
        )
        AgentRole.RESEARCHER -> listOf(
            PropLayout("BOOKSHELF",            1f, 1f,   1f),
            PropLayout("SINGLE_MONITOR_DESK",  3f, 2f,   1f),
            PropLayout("MAGNIFYING_GLASS",     5f, 2f,   1f),
            PropLayout("FLOATING_DOCUMENTS",   3f, 1f,   3f),
            PropLayout("TEA_KETTLE",           3.5f, 2.5f, 2f),
            PropLayout("GLOBE",                1.5f, 3.5f, 2f),
        )
        AgentRole.SECURITY -> listOf(
            PropLayout("HEX_MONITOR_ARRAY",    2f, 1f,   1f),
            PropLayout("RADAR_SCREEN",         5f, 2f,   1f),
            PropLayout("SERVER_RACK",          1f, 3f,   1f),
            PropLayout("FIREWALL_GATE",        3f, 4f,   1f),
            PropLayout("ENCRYPTION_KEY",       4f, 1f,   3f),
            PropLayout("ALERT_BEACON",         6f, 4f,   2f),
        )
        AgentRole.CREATIVE -> listOf(
            PropLayout("DRAWING_TABLET",       2f, 2f,   1f),
            PropLayout("EASEL_CANVAS",         5f, 1f,   1f),
            PropLayout("COLOR_PALETTE",        2f, 4f,   2f),
            PropLayout("LIGHTBULB",            3f, 1f,   4f),
            PropLayout("PAINT_BRUSH_CUP",      3.5f, 2.5f, 2f),
            PropLayout("HANGING_PLANT",        1f, 1f,   3f),
            PropLayout("NEON_SIGN",            5f, 4f,   3f),
        )
        AgentRole.MEMORY -> listOf(
            PropLayout("BRAIN_ORB",            3f, 2f,   3f),
            PropLayout("NEURAL_CLOUD",         2f, 1f,   4f),
            PropLayout("MEMORY_SHARD",         5f, 2f,   2f),
            PropLayout("ARCHIVE_CYLINDER",     1f, 3f,   1f),
            PropLayout("TIMESTAMP_RINGS",      3f, 4f,   0f),
            PropLayout("SYNAPSE_WEB",          4f, 1f,   5f),
        )
        AgentRole.ANALYST -> listOf(
            PropLayout("STATS_DASHBOARD",      2f, 2f,   1f),
            PropLayout("WALL_CHARTS",          1f, 1f,   1f),
            PropLayout("DATA_STREAM",          5f, 1f,   1f),
            PropLayout("FUNNEL_PROP",          4f, 3f,   2f),
            PropLayout("PIE_CHART_FLOOR",      3f, 3f,   0f),
        )
        AgentRole.COORDINATOR -> listOf(
            PropLayout("NETWORK_GLOBE",        3f, 2f,   3f),
            PropLayout("CONFERENCE_TABLE",     2f, 2f,   1f),
            PropLayout("AGENDA_BOARD",         1f, 1f,   1f),
            PropLayout("RADIO_TOWER",          5f, 4f,   1f),
            PropLayout("STAR_BADGE",           3f, 1f,   5f),
        )
    }
}
