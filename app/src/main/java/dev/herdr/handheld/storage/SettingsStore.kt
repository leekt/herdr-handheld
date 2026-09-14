package dev.herdr.handheld.storage

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dev.herdr.handheld.herdr.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.handheldData by preferencesDataStore("handheld")

class SettingsStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    suspend fun codexBinary() = context.handheldData.data.first()[stringPreferencesKey("codex_binary")] ?: "codex"
    suspend fun saveCodexBinary(value: String) { context.handheldData.edit { it[stringPreferencesKey("codex_binary")] = value } }
    suspend fun speechLanguage() = context.handheldData.data.first()[stringPreferencesKey("speech_language")] ?: ""
    suspend fun saveSpeechLanguage(value: String) { context.handheldData.edit { it[stringPreferencesKey("speech_language")] = value } }
    suspend fun profile(): HostProfile = context.handheldData.data.first()[stringPreferencesKey("profile")]?.let {
        runCatching { json.decodeFromString<HostProfile>(it) }.getOrNull()
    } ?: HostProfile()
    suspend fun saveProfile(profile: HostProfile) { context.handheldData.edit { it[stringPreferencesKey("profile")] = json.encodeToString(profile) } }
    suspend fun fontSize() = context.handheldData.data.first()[intPreferencesKey("font")] ?: 17
    suspend fun saveFontSize(size: Int) { context.handheldData.edit { it[intPreferencesKey("font")] = size.coerceIn(12,26) } }
    suspend fun demo() = context.handheldData.data.first()[booleanPreferencesKey("demo")] ?: false
    suspend fun saveDemo(value: Boolean) { context.handheldData.edit { it[booleanPreferencesKey("demo")] = value } }
    suspend fun applicationCursor() = context.handheldData.data.first()[booleanPreferencesKey("application_cursor")] ?: false
    suspend fun saveApplicationCursor(value: Boolean) { context.handheldData.edit { it[booleanPreferencesKey("application_cursor")] = value } }
    suspend fun lastTarget() = context.handheldData.data.first()[stringPreferencesKey("last_target")]
    suspend fun saveLastTarget(key: String) { context.handheldData.edit { it[stringPreferencesKey("last_target")] = key } }
    suspend fun mappings(): Map<String,Int> = context.handheldData.data.first()[stringPreferencesKey("buttons")]?.let {
        runCatching { json.decodeFromString<Map<String,Int>>(it) }.getOrNull()
    } ?: emptyMap()
    suspend fun saveMappings(value: Map<String,Int>) { context.handheldData.edit { it[stringPreferencesKey("buttons")] = json.encodeToString(value) } }
    suspend fun cachedAgents(): List<AgentTarget> = context.handheldData.data.first()[stringPreferencesKey("agents")]?.let {
        runCatching { json.decodeFromString<List<AgentTarget>>(it) }.getOrNull()
    } ?: emptyList()
    suspend fun cachedAt() = context.handheldData.data.first()[longPreferencesKey("agents_at")] ?: 0L
    suspend fun cacheAgents(agents: List<AgentTarget>, time: Long) { context.handheldData.edit {
        it[stringPreferencesKey("agents")] = json.encodeToString(agents); it[longPreferencesKey("agents_at")] = time
    } }
}
