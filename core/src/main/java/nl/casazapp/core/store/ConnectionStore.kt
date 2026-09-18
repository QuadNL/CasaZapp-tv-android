package nl.casazapp.core.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The server this device is paired with. Local mode (no server) comes later. */
data class Connection(val serverUrl: String, val token: String)

private val Context.dataStore by preferencesDataStore("connection")
private val SERVER = stringPreferencesKey("server_url")
private val TOKEN = stringPreferencesKey("device_token")
private val LAST_CHANNEL = intPreferencesKey("last_channel")
private val UI_MODE = stringPreferencesKey("ui_mode")

/** How the app lays itself out; `AUTO` asks the system whether this is a television. */
enum class UiMode { AUTO, TV, MOBILE }

class ConnectionStore(private val context: Context) {
    val connection: Flow<Connection?> = context.dataStore.data.map { prefs ->
        val server = prefs[SERVER]
        val token = prefs[TOKEN]
        if (server != null && token != null) Connection(server, token) else null
    }

    suspend fun save(connection: Connection) {
        context.dataStore.edit {
            it[SERVER] = connection.serverUrl
            it[TOKEN] = connection.token
        }
    }

    val lastChannel: Flow<Int?> = context.dataStore.data.map { it[LAST_CHANNEL] }

    suspend fun saveLastChannel(id: Int) {
        context.dataStore.edit { it[LAST_CHANNEL] = id }
    }

    val uiMode: Flow<UiMode> = context.dataStore.data.map { prefs ->
        prefs[UI_MODE]?.let { runCatching { UiMode.valueOf(it) }.getOrNull() } ?: UiMode.AUTO
    }

    suspend fun saveUiMode(mode: UiMode) {
        context.dataStore.edit { it[UI_MODE] = mode.name }
    }

    /** Forgets the server; device preferences such as the display mode stay. */
    suspend fun clear() {
        context.dataStore.edit {
            it.remove(SERVER)
            it.remove(TOKEN)
            it.remove(LAST_CHANNEL)
        }
    }
}
