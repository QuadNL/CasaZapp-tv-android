package nl.casazapp.core.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The server this device is paired with. Local mode (no server) comes later. */
data class Connection(val serverUrl: String, val token: String)

private val Context.dataStore by preferencesDataStore("connection")
private val SERVER = stringPreferencesKey("server_url")
private val TOKEN = stringPreferencesKey("device_token")

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

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
