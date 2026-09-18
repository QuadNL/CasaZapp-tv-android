@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package nl.casazapp.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import nl.casazapp.core.api.CasaZappApi
import nl.casazapp.core.api.Channel
import nl.casazapp.core.store.ConnectionStore
import nl.casazapp.core.store.UiMode
import androidx.compose.foundation.layout.systemBarsPadding
import nl.casazapp.tv.R

/** Everything the screens need to talk to the server and load logos. */
data class Session(val api: CasaZappApi, val serverUrl: String, val token: String) {
    fun logo(path: String?) = path?.let { serverUrl + it }
}

/** Which list the user watches, and where in it; the player zaps through the same list. */
data class Watching(val channels: List<Channel>, val index: Int, val label: String)

private enum class Route { Home, Live, Settings }

@Composable
fun App(store: ConnectionStore) {
    val connection by store.connection.collectAsState(initial = null)
    val lastChannel by store.lastChannel.collectAsState(initial = null)
    val uiMode by store.uiMode.collectAsState(initial = UiMode.AUTO)
    val form = LocalForm.current
    val scope = rememberCoroutineScope()
    var watching by remember { mutableStateOf<Watching?>(null) }
    var route by remember { mutableStateOf(Route.Home) }

    Box(Modifier.fillMaxSize().background(Casa.bg)) {
        val current = connection
        if (current == null) {
            ConnectScreen(onConnected = { scope.launch { store.save(it) } })
            return@Box
        }
        val session = remember(current) {
            Session(CasaZappApi(current.serverUrl, current.token), current.serverUrl, current.token)
        }
        val playing = watching
        if (playing != null) {
            PlayerScreen(
                session = session,
                watching = playing,
                onZap = { watching = playing.copy(index = it) },
                onPlaying = { scope.launch { store.saveLastChannel(it) } },
                onBack = { watching = null },
            )
            return@Box
        }

        val screen: @Composable () -> Unit = {
            when (route) {
                Route.Home -> HomeScreen(session, lastChannel, onWatch = { watching = it })
                Route.Live -> LiveScreen(session, onWatch = { watching = it })
                Route.Settings -> SettingsScreen(
                    serverUrl = current.serverUrl,
                    uiMode = uiMode,
                    onUiMode = { scope.launch { store.saveUiMode(it) } },
                    onUnpair = { scope.launch { store.clear() } },
                )
            }
        }
        if (form.compact) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) { screen() }
                TabBar(route) { route = it }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                NavRail(route) { route = it }
                val pad = if (form.tv) Modifier.padding(horizontal = 48.dp, vertical = 36.dp) else Modifier.padding(24.dp)
                Box(Modifier.weight(1f).fillMaxHeight().systemBarsPadding().then(pad)) { screen() }
            }
        }
    }
}

private val NAV = listOf(
    Triple(Route.Home, Icons.home, R.string.nav_home),
    Triple(Route.Live, Icons.tv, R.string.nav_live),
    Triple(Route.Settings, Icons.settings, R.string.nav_settings),
)

/** The web app's mobile tab bar: icons with a small label, at the bottom. */
@Composable
private fun TabBar(route: Route, onRoute: (Route) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Casa.surface).padding(vertical = 6.dp)) {
        NAV.forEach { (target, icon, label) ->
            val active = route == target
            Column(
                Modifier.weight(1f).focusRing { onRoute(target) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(icon, tint = if (active) Casa.accent else Casa.muted, size = 22.dp)
                Text(stringResource(label), color = if (active) Casa.text else Casa.muted, fontSize = 11.sp)
            }
        }
    }
}

/** The web app's desktop sidebar: brand on top, then the sections. */
@Composable
private fun NavRail(route: Route, onRoute: (Route) -> Unit) {
    val items: List<Triple<Route, ImageVector, Int>> = NAV
    Column(
        Modifier.width(240.dp).fillMaxHeight().background(Casa.surface).padding(horizontal = 16.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.padding(start = 8.dp, bottom = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            androidx.compose.foundation.Image(painterResource(R.drawable.icon), null, Modifier.size(36.dp))
            Text("CasaZapp TV", color = Casa.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        items.forEach { (target, icon, label) ->
            val active = route == target
            Row(
                Modifier
                    .fillMaxWidth()
                    .focusRing(onFocus = { if (it) onRoute(target) }) { onRoute(target) }
                    .background(if (active) Casa.raised else androidx.compose.ui.graphics.Color.Transparent)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(icon, tint = if (active) Casa.accent else Casa.muted)
                Text(stringResource(label), color = if (active) Casa.text else Casa.muted, fontSize = 17.sp)
            }
        }
    }
}
