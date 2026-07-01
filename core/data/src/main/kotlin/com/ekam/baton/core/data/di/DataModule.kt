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
                // Bulletproof Fallback: Use standard SharedPreferences to avoid crashing the app
                context.getSharedPreferences("secret_shared_prefs_fallback", Context.MODE_PRIVATE)
            }
        }

        var dbPassphrase = sharedPreferences.getString("db_passphrase", null)
        if (dbPassphrase == null) {
            dbPassphrase = UUID.randomUUID().toString()
            sharedPreferences.edit().putString("db_passphrase", dbPassphrase).apply()
        }

        System.loadLibrary("sqlcipher")
        val factory = SupportOpenHelperFactory(dbPassphrase.toByteArray())

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

        Room.databaseBuilder(
            context,
            BatonDatabase::class.java,
            BatonDatabase.DATABASE_NAME,
        )
            .openHelperFactory(factory)
            .addMigrations(MIGRATION_6_7, MIGRATION_7_8)
            .fallbackToDestructiveMigration()
            .build()
    }

    single<AgentDao> { get<BatonDatabase>().agentDao() }
    single<ConversationDao> { get<BatonDatabase>().conversationDao() }
    single<MessageDao> { get<BatonDatabase>().messageDao() }
    single<MemoryDao> { get<BatonDatabase>().memoryDao() }
    single<com.ekam.baton.core.data.db.dao.AuditDao> { get<BatonDatabase>().auditDao() }
    single<com.ekam.baton.core.data.db.dao.AgentActionLogDao> { get<BatonDatabase>().agentActionLogDao() }

    single { com.ekam.baton.core.data.preferences.AppPreferences(androidContext()) }
    single { com.ekam.baton.core.data.preferences.SessionManager(get()) }
    single { com.ekam.baton.core.data.preferences.SubscriptionManager() }

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

    single<com.ekam.baton.core.network.BackendUrlProvider> {
        com.ekam.baton.core.data.preferences.AppPreferencesBackendUrlProvider(get())
    }
}
