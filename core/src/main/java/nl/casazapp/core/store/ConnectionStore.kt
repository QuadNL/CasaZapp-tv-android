package nl.casazapp.core.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import nl.casazapp.core.local.LocalPlaylist

/** The server this device is paired with. */
data class Connection(val serverUrl: String, val token: String)

private val Context.dataStore by preferencesDataStore("connection")
private val SERVER = stringPreferencesKey("server_url")
private val TOKEN = stringPreferencesKey("device_token")
private val LAST_CHANNEL = intPreferencesKey("last_channel")
private val UI_MODE = stringPreferencesKey("ui_mode")
private val SOURCE_MODE = stringPreferencesKey("source_mode")
private val LOCALE = stringPreferencesKey("locale")
private val LOCAL_PLAYLIST = stringPreferencesKey("local_playlist")
private val LOCAL_PASSWORD = stringPreferencesKey("local_password")
private val PIP = booleanPreferencesKey("pip")

/** Where channels come from. Null until the user chose at first launch. */
enum class SourceMode { SERVER, LOCAL }

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

    /** "nl" or "en"; null follows the device language. Wrapped so "not loaded" differs from null. */
    /** Picture-in-picture when leaving the player on a phone or tablet (#62); on unless switched off. */
    val pip: Flow<Boolean> = context.dataStore.data.map { it[PIP] ?: true }

    suspend fun savePip(on: Boolean) {
        context.dataStore.edit { it[PIP] = on }
    }

    val locale: Flow<List<String?>> = context.dataStore.data.map { listOf(it[LOCALE]) }

    suspend fun saveLocale(tag: String?) {
        context.dataStore.edit { if (tag == null) it.remove(LOCALE) else it[LOCALE] = tag }
    }

    val sourceMode: Flow<SourceMode?> = context.dataStore.data.map { prefs ->
        prefs[SOURCE_MODE]?.let { runCatching { SourceMode.valueOf(it) }.getOrNull() }
    }

    suspend fun saveSourceMode(mode: SourceMode?) {
        context.dataStore.edit { if (mode == null) it.remove(SOURCE_MODE) else it[SOURCE_MODE] = mode.name }
    }

    /** The playlist of local mode; the password is kept encrypted with the Keystore. */
    val localPlaylist: Flow<LocalPlaylist?> = context.dataStore.data.map { prefs ->
        prefs[LOCAL_PLAYLIST]?.let { stored ->
            runCatching { Json.decodeFromString<LocalPlaylist>(stored) }.getOrNull()
                ?.copy(password = prefs[LOCAL_PASSWORD]?.let(Secrets::decrypt))
        }
    }

    suspend fun saveLocalPlaylist(playlist: LocalPlaylist) {
        context.dataStore.edit {
            it[LOCAL_PLAYLIST] = Json.encodeToString(LocalPlaylist.serializer(), playlist.copy(password = null))
            val password = playlist.password
            if (password.isNullOrEmpty()) it.remove(LOCAL_PASSWORD) else it[LOCAL_PASSWORD] = Secrets.encrypt(password)
        }
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
