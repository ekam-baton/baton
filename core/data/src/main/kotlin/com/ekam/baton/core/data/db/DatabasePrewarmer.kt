package com.ekam.baton.core.data.db

/**
 * Interface to trigger asynchronous background pre-warming of the encrypted SQLCipher database.
 * Encapsulated to prevent the :app module from requiring compile-time classpath access to RoomDatabase.
 */
interface DatabasePrewarmer {
    fun prewarm()
}
