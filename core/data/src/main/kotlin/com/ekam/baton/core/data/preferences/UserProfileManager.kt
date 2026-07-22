package com.ekam.baton.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.userProfileDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_profile_preferences")

class UserProfileManager(private val context: Context) {

    companion object {
        val BATON_ID = stringPreferencesKey("baton_id")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
    }

    suspend fun getBatonId(): String {
        var currentId = context.userProfileDataStore.data.map { it[BATON_ID] }.firstOrNull()
        if (currentId == null) {
            currentId = UUID.randomUUID().toString()
            context.userProfileDataStore.edit { preferences ->
                preferences[BATON_ID] = currentId
            }
        }
        return currentId
    }
    
    val batonIdFlow: Flow<String> = context.userProfileDataStore.data.map { preferences ->
        preferences[BATON_ID] ?: ""
    }

    val displayName: Flow<String?> = context.userProfileDataStore.data.map { preferences ->
        preferences[DISPLAY_NAME]
    }

    suspend fun setDisplayName(name: String) {
        context.userProfileDataStore.edit { preferences ->
            preferences[DISPLAY_NAME] = name
        }
    }
}
