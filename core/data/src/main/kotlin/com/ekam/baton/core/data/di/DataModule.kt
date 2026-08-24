package com.ekam.baton.core.data.di

import android.content.Context
import androidx.room.Room
import com.ekam.baton.core.data.db.BatonDatabase
import com.ekam.baton.core.data.db.dao.AgentDao
import com.ekam.baton.core.data.db.dao.ConversationDao
import com.ekam.baton.core.data.db.dao.MessageDao
import com.ekam.baton.core.data.db.dao.MemoryDao
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.util.UUID
import kotlinx.coroutines.flow.firstOrNull

val dataModule = module {

    single {
        val context = androidContext()
        val sharedPreferences = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "secret_shared_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            e.printStackTrace()
            // 1. Delete the shared preferences using system APIs
            try {
                context.deleteSharedPreferences("secret_shared_prefs")
            } catch (ignored: Exception) {}
            try {
                val file = java.io.File(context.filesDir.parent, "shared_prefs/secret_shared_prefs.xml")
                if (file.exists()) file.delete()
                val bakFile = java.io.File(context.filesDir.parent, "shared_prefs/secret_shared_prefs.xml.bak")
                if (bakFile.exists()) bakFile.delete()
                val bakFile2 = java.io.File(context.filesDir.parent, "shared_prefs/secret_shared_prefs.bak")
                if (bakFile2.exists()) bakFile2.delete()
            } catch (ignored: Exception) {}

            // 2. Clear corrupted key from Android Keystore
            try {
                val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            } catch (ignored: Exception) {}

            // 3. Retry creating preference store with a fresh master key, or fall back to standard preferences if it fails again
            try {
                val freshMasterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    "secret_shared_prefs",
                    freshMasterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (fallbackEx: Exception) {
                fallbackEx.printStackTrace()
                // Security Fix: Fail-secure rather than silently storing the SQLCipher passphrase in plaintext.
                throw SecurityException("CRITICAL: Failed to initialize EncryptedSharedPreferences. Keystore is compromised.", fallbackEx)
            }
        }

        var dbPassphrase = sharedPreferences.getString("db_passphrase", null)
        if (dbPassphrase == null) {
            dbPassphrase = UUID.randomUUID().toString()
            sharedPreferences.edit().putString("db_passphrase", dbPassphrase).apply()
        }

        System.loadLibrary("sqlcipher")
        val factory = SupportOpenHelperFactory(dbPassphrase.toByteArray())

        // ---------------------------------------------------------------
        // Missing migrations 1→6 — prevent data loss on legacy upgrades
        // ---------------------------------------------------------------

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `agents` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `avatar_uri` TEXT, `mcp_endpoint_url` TEXT NOT NULL, `auth_type` TEXT NOT NULL, `auth_config` TEXT NOT NULL, `is_active` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `last_used_at` INTEGER, `color_accent` TEXT NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Recreate conversations: PK type change (INTEGER AUTOINCREMENT → TEXT), add is_pinned, message_count
                db.execSQL("CREATE TABLE IF NOT EXISTS `conversations_new` (`id` TEXT NOT NULL, `agent_id` TEXT NOT NULL, `title` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `is_pinned` INTEGER NOT NULL DEFAULT 0, `message_count` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))")
                db.execSQL("INSERT INTO `conversations_new` (`id`, `agent_id`, `title`, `created_at`, `updated_at`, `is_pinned`, `message_count`) SELECT CAST(`id` AS TEXT), `agent_id`, `title`, `created_at`, `updated_at`, 0, 0 FROM `conversations`")
                db.execSQL("DROP TABLE `conversations`")
                db.execSQL("ALTER TABLE `conversations_new` RENAME TO `conversations`")
                // Add messages table
                db.execSQL("CREATE TABLE IF NOT EXISTS `messages` (`id` TEXT NOT NULL, `conversation_id` TEXT NOT NULL, `role` TEXT NOT NULL, `content` TEXT NOT NULL, `attachments` TEXT, `timestamp` INTEGER NOT NULL, `is_streaming` INTEGER NOT NULL, `tool_call_json` TEXT, `token_count` INTEGER, PRIMARY KEY(`id`))")
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `memories` (`id` TEXT NOT NULL, `layer` TEXT NOT NULL, `agentId` TEXT, `conversationId` TEXT, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `lastAccessedAt` INTEGER NOT NULL, `relevanceScore` REAL NOT NULL, `tags` TEXT NOT NULL, `isActive` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE agents ADD COLUMN `is_authenticated` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE agents ADD COLUMN `last_auth_at` INTEGER")
                db.execSQL("ALTER TABLE agents ADD COLUMN `security_mode` TEXT NOT NULL DEFAULT 'standard'")
                db.execSQL("ALTER TABLE agents ADD COLUMN `security_config` TEXT NOT NULL DEFAULT '{}'")
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE agents ADD COLUMN `provider_type` TEXT NOT NULL DEFAULT 'local_mcp'")
            }
        }

        // ---------------------------------------------------------------
        // Existing migrations 6→14
        // ---------------------------------------------------------------

        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add columns to agents
                db.execSQL("ALTER TABLE agents ADD COLUMN previous_hash TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE agents ADD COLUMN hash TEXT NOT NULL DEFAULT ''")
                // Add columns to conversations
                db.execSQL("ALTER TABLE conversations ADD COLUMN previous_hash TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE conversations ADD COLUMN hash TEXT NOT NULL DEFAULT ''")
                // Add columns to messages
                db.execSQL("ALTER TABLE messages ADD COLUMN previous_hash TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN hash TEXT NOT NULL DEFAULT ''")
                // Add columns to memories
                db.execSQL("ALTER TABLE memories ADD COLUMN previous_hash TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE memories ADD COLUMN hash TEXT NOT NULL DEFAULT ''")
                // Create audit_logs table
                db.execSQL("CREATE TABLE IF NOT EXISTS `audit_logs` (`id` TEXT NOT NULL, `entity_name` TEXT NOT NULL, `entity_id` TEXT NOT NULL, `action` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `device_id` TEXT NOT NULL, `payload_json` TEXT NOT NULL, `previous_hash` TEXT NOT NULL, `hash` TEXT NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Create agent_action_logs table for AI audit logging
                db.execSQL("CREATE TABLE IF NOT EXISTS `agent_action_logs` (`id` TEXT NOT NULL, `prompt_id` TEXT NOT NULL, `agent_id` TEXT NOT NULL, `action_type` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `payload_json` TEXT NOT NULL, `previous_hash` TEXT NOT NULL, `hash` TEXT NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add relay_url column for remote fallback routing
                db.execSQL("ALTER TABLE agents ADD COLUMN relay_url TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add relay_token column for remote fallback authentication
                db.execSQL("ALTER TABLE agents ADD COLUMN relay_token TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // New agent card + avatar identity columns
                db.execSQL("ALTER TABLE agents ADD COLUMN role TEXT NOT NULL DEFAULT 'COORDINATOR'")
                db.execSQL("ALTER TABLE agents ADD COLUMN card_did TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE agents ADD COLUMN card_fingerprint TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE agents ADD COLUMN card_issued_at INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE agents ADD COLUMN card_avatar_seed TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE agents ADD COLUMN world_room_id TEXT DEFAULT NULL")
                // World room tables
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `world_rooms` (
                        `id` TEXT NOT NULL,
                        `agent_id` TEXT NOT NULL,
                        `role_key` TEXT NOT NULL DEFAULT 'COORDINATOR',
                        `display_name` TEXT NOT NULL,
                        `color_hex` TEXT NOT NULL DEFAULT '#3D8EFF',
                        `world_x` INTEGER NOT NULL DEFAULT 0,
                        `world_y` INTEGER NOT NULL DEFAULT 0,
                        `width_tiles` INTEGER NOT NULL DEFAULT 8,
                        `height_tiles` INTEGER NOT NULL DEFAULT 6,
                        `created_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`))
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `world_room_props` (
                        `id` TEXT NOT NULL,
                        `room_id` TEXT NOT NULL,
                        `prop_type` TEXT NOT NULL,
                        `grid_x` REAL NOT NULL,
                        `grid_y` REAL NOT NULL,
                        `grid_z` REAL NOT NULL DEFAULT 0.0,
                        `anim_seed` INTEGER NOT NULL DEFAULT 0,
                        `custom_color_hex` TEXT,
                        PRIMARY KEY(`id`))
                """.trimIndent())
            }
        }

        val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE agents ADD COLUMN owner_id TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE agents ADD COLUMN owner_name TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `groups` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `creatorId` TEXT NOT NULL,
                        `memberIds` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`))
                """.trimIndent())
            }
        }

        val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN is_failed INTEGER NOT NULL DEFAULT 0")
            }
        }

        val builder = Room.databaseBuilder(
            context,
            BatonDatabase::class.java,
            BatonDatabase.DATABASE_NAME,
        )
            .openHelperFactory(factory)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)

        try {
            val db = builder.build()
            // Force open the database to trigger key validation
            db.openHelper.writableDatabase
            db
        } catch (e: Exception) {
            e.printStackTrace()
            // Key is incorrect or file is corrupted. Delete the database files and rebuild.
            try {
                context.deleteDatabase(BatonDatabase.DATABASE_NAME)
            } catch (ignored: Exception) {}

            val freshBuilder = Room.databaseBuilder(
                context,
                BatonDatabase::class.java,
                BatonDatabase.DATABASE_NAME,
            )
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
            freshBuilder.build()
        }
    }

    single<AgentDao> { get<BatonDatabase>().agentDao() }
    single<ConversationDao> { get<BatonDatabase>().conversationDao() }
    single<MessageDao> { get<BatonDatabase>().messageDao() }
    single<MemoryDao> { get<BatonDatabase>().memoryDao() }
    single<com.ekam.baton.core.data.db.dao.AuditDao> { get<BatonDatabase>().auditDao() }
    single<com.ekam.baton.core.data.db.dao.AgentActionLogDao> { get<BatonDatabase>().agentActionLogDao() }
    single<com.ekam.baton.core.data.db.dao.WorldRoomDao> { get<BatonDatabase>().worldRoomDao() }
    single<com.ekam.baton.core.data.db.dao.WorldRoomPropDao> { get<BatonDatabase>().worldRoomPropDao() }
    single<com.ekam.baton.core.data.db.dao.GroupDao> { get<BatonDatabase>().groupDao() }
    single { com.ekam.baton.core.data.repository.WorldRoomRepository(get(), get()) }

    single { com.ekam.baton.core.data.preferences.AppPreferences(androidContext()) }
    single { com.ekam.baton.core.data.preferences.UserProfileManager(androidContext()) }
    single<com.ekam.baton.core.network.mcp.McpProfileProvider> {
        val userProfileManager: com.ekam.baton.core.data.preferences.UserProfileManager = get()
        object : com.ekam.baton.core.network.mcp.McpProfileProvider {
            override suspend fun getBatonId(): String = userProfileManager.getBatonId()
            override suspend fun getDisplayName(): String? = userProfileManager.displayName.firstOrNull()
        }
    }
    single { com.ekam.baton.core.data.preferences.SessionManager(get()) }
    single { com.ekam.baton.core.data.preferences.SubscriptionManager() }
    
    single(createdAtStart = true) { com.ekam.baton.core.data.billing.BillingManager(androidContext(), get(), get()) }

    single { com.ekam.baton.core.data.repository.AgentRepository(get()) }
    single { com.ekam.baton.core.data.repository.MemoryRepository(get(), get()) }
    single { com.ekam.baton.core.data.repository.ChatRepository(get(), get(), get(), get(), get(), get(), get(), get()) }
    single { com.ekam.baton.core.data.repository.AgentActionAuditRepository(get()) }
    single { com.ekam.baton.core.data.repository.WipeDataManager(get()) }

    single { com.ekam.baton.core.data.memory.MemoryInjectionEngine(get()) }
    single { com.ekam.baton.core.data.memory.WorkingMemoryManager(get()) }

    single<com.ekam.baton.core.network.security.AgentSecurityConfigProvider> {
        com.ekam.baton.core.data.repository.AgentSecurityConfigProviderImpl(get())
    }

    single<com.ekam.baton.core.network.security.LocalNetworkPolicyProvider> {
        com.ekam.baton.core.data.repository.LocalNetworkPolicyProviderImpl(get(), get())
    }

    // Forensic Auditing & eIDAS QES Compliance
    single { com.ekam.baton.core.data.forensic.TrustedTimeProvider() }
    single { com.ekam.baton.core.data.forensic.EnterpriseCertificateManager(androidContext(), get()) }
    single { com.ekam.baton.core.data.forensic.ForensicCryptoManager(get()) }
    single { com.ekam.baton.core.data.forensic.ForensicScreenshotManager(androidContext(), get(), get()) }
    single { com.ekam.baton.core.data.forensic.EvidenceExportManager(androidContext(), get(), get()) }
    single { com.ekam.baton.core.data.repository.AuditRepository(get(), get(), get(), androidContext()) }

    single<com.ekam.baton.core.network.BackendUrlProvider> {
        com.ekam.baton.core.data.preferences.AppPreferencesBackendUrlProvider(get())
    }
}
