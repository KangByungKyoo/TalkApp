package com.minipapa.englishtalk.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.conversationDataStore by preferencesDataStore(name = "conversation_settings")

interface ConversationSettingsRepository {
    val settings: Flow<ConversationSettings>
    suspend fun selectCharacter(character: ConversationCharacter)
    suspend fun selectResponseLength(length: ResponseLength)
}

class DataStoreConversationSettingsRepository(private val store: DataStore<Preferences>) : ConversationSettingsRepository {
    constructor(context: Context) : this(context.applicationContext.conversationDataStore)

    private val characterKey = stringPreferencesKey("character_id")
    private val lengthKey = stringPreferencesKey("response_length")

    override val settings: Flow<ConversationSettings> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> ConversationSettings(
            ConversationCharacter.fromId(preferences[characterKey]),
            ResponseLength.fromId(preferences[lengthKey])
        ) }

    override suspend fun selectCharacter(character: ConversationCharacter) {
        store.edit { it[characterKey] = character.id }
    }

    override suspend fun selectResponseLength(length: ResponseLength) {
        store.edit { it[lengthKey] = length.id }
    }
}
