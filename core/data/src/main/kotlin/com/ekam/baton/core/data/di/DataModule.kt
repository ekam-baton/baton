package com.ekam.baton.core.data.di

import android.content.Context
import androidx.room.Room
import com.ekam.baton.core.data.db.BatonDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

import com.ekam.baton.core.data.db.dao.AgentDao
import com.ekam.baton.core.data.db.dao.ConversationDao
import com.ekam.baton.core.data.db.dao.MessageDao
import com.ekam.baton.core.data.db.dao.MemoryDao
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import net.sqlcipher.database.SupportFactory
import java.util.UUID

/**
 * - [BatonDatabase] — the single Room instance for the entire app
 *
 * DAOs should be provided in this module (or a companion module) once
 * they are declared in [BatonDatabase]. Example:
 * ```kotlin
 * @Provides @Singleton
 * fun provideConversationDao(db: BatonDatabase): ConversationDao = db.conversationDao()
 * ```
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideBatonDatabase(
        @ApplicationContext context: Context
    ): BatonDatabase {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val sharedPreferences = EncryptedSharedPreferences.create(
            context,
            "secret_shared_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        var dbPassphrase = sharedPreferences.getString("db_passphrase", null)
        if (dbPassphrase == null) {
            dbPassphrase = UUID.randomUUID().toString()
            sharedPreferences.edit().putString("db_passphrase", dbPassphrase).apply()
        }

        val factory = SupportFactory(dbPassphrase.toByteArray())

        return Room.databaseBuilder(
            context,
            BatonDatabase::class.java,
            BatonDatabase.DATABASE_NAME,
        )
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideAgentDao(db: BatonDatabase): AgentDao = db.agentDao()

    @Provides
    @Singleton
    fun provideConversationDao(db: BatonDatabase): ConversationDao = db.conversationDao()

    @Provides
    @Singleton
    fun provideMessageDao(db: BatonDatabase): MessageDao = db.messageDao()

    @Provides
    @Singleton
    fun provideMemoryDao(db: BatonDatabase): MemoryDao = db.memoryDao()

    @Provides
    @Singleton
    fun provideAgentSecurityConfigProvider(
        agentDao: AgentDao
    ): com.ekam.baton.core.network.security.AgentSecurityConfigProvider {
        return com.ekam.baton.core.data.repository.AgentSecurityConfigProviderImpl(agentDao)
    }
}
