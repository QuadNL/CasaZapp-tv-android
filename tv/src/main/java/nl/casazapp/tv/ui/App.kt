package nl.casazapp.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import nl.casazapp.core.api.CasaZappApi
import nl.casazapp.core.api.Channel
import nl.casazapp.core.store.ConnectionStore

/** Which list the user watches, and where in it; the player zaps through the same list. */
data class Watching(val channels: List<Channel>, val index: Int)

@Composable
fun App(store: ConnectionStore) {
    val connection by store.connection.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var watching by remember { mutableStateOf<Watching?>(null) }

    Box(Modifier.fillMaxSize().background(Casa.bg)) {
        val current = connection
        if (current == null) {
            ConnectScreen(onConnected = { scope.launch { store.save(it) } })
        } else {
            val api = remember(current) { CasaZappApi(current.serverUrl, current.token) }
            val playing = watching
            if (playing == null) {
                ChannelsScreen(
                    api = api,
                    serverUrl = current.serverUrl,
                    token = current.token,
                    onWatch = { channels, index -> watching = Watching(channels, index) },
                    onUnpair = { scope.launch { store.clear() } },
                )
            } else {
                PlayerScreen(
                    api = api,
                    watching = playing,
                    onZap = { watching = playing.copy(index = it) },
                    onBack = { watching = null },
                )
            }
        }
    }
}
