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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.ui.focus.focusRequester
import nl.casazapp.tv.update.Updater
import androidx.compose.runtime.LaunchedEffect
import nl.casazapp.core.store.SourceMode
import nl.casazapp.core.api.TvSource
import nl.casazapp.core.local.LocalSource
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.systemBarsPadding
import nl.casazapp.tv.R

/**
 * Everything the screens need: where channels come from, and how to load logos. In local mode
 * there is no server: logos are the provider's own URLs and need no token.
 */
data class Session(val api: TvSource, val serverUrl: String?, val token: String?) {
    fun logo(path: String?) = path?.let { if (it.startsWith("http") || serverUrl == null) it else serverUrl + it }
}

/** Which list the user watches, and where in it; the player zaps through the same list. */
data class Watching(
    val channels: List<Channel>,
    val index: Int,
    val label: String,
    /** What is being zapped through, as the web app names it: category id, "favorites" or "list:<id>". */
    val context: String? = null,
)

private enum class Route { Home, Live, Playlists, Settings }

@Composable
fun App(store: ConnectionStore) {
    val connection by store.connection.collectAsState(initial = null)
    val lastChannel by store.lastChannel.collectAsState(initial = null)
    val uiMode by store.uiMode.collectAsState(initial = UiMode.AUTO)
    val pip by store.pip.collectAsState(initial = true)
    // Wrapped, so "not loaded yet" differs from "not chosen yet".
    val sourceMode by remember { store.sourceMode.map { listOf(it) } }.collectAsState(initial = null)
    val localPlaylist by store.localPlaylist.collectAsState(initial = null)
    val locale by store.locale.collectAsState(initial = null)
    var editLocal by remember { mutableStateOf(false) }
    var backFromEdit by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<Updater.Release?>(null) }
    LaunchedEffect(Unit) { update = Updater.newer() }
    val context = LocalContext.current
    val form = LocalForm.current
    val scope = rememberCoroutineScope()
    var watching by remember { mutableStateOf<Watching?>(null) }
    var route by remember { mutableStateOf(Route.Home) }

    Box(Modifier.fillMaxSize().background(Casa.bg)) {
        val loaded = sourceMode ?: return@Box
        // Devices paired before local mode existed have a server but no choice stored.
        val mode = loaded.first() ?: if (connection != null) SourceMode.SERVER else null
        val current = connection
        val local = localPlaylist
        when {
            mode == null -> {
                WelcomeScreen(onChoose = { scope.launch { store.saveSourceMode(it) } })
                // The first screen: Back asks before closing, like on Home (#78).
                var askExit by remember { mutableStateOf(false) }
                BackHandler { askExit = true }
                if (askExit) ExitPrompt(onCancel = { askExit = false }, onExit = { context.findActivity()?.finish() })
                return@Box
            }
            mode == SourceMode.SERVER && current == null -> {
                ConnectScreen(
                    onConnected = { scope.launch { store.save(it) } },
                    onBack = { scope.launch { store.saveSourceMode(null) } },
                )
                return@Box
            }
            mode == SourceMode.LOCAL && (local == null || editLocal) -> {
                LocalSetupScreen(
                    initial = local,
                    onSaved = {
                        scope.launch { store.saveLocalPlaylist(it) }
                        backFromEdit = editLocal
                        editLocal = false
                    },
                    onBack = {
                        if (editLocal) {
                            backFromEdit = true
                            editLocal = false
                        } else {
                            scope.launch { store.saveSourceMode(null) }
                        }
                    },
                )
                return@Box
            }
        }
        val session = remember(mode, current, local) {
            if (mode == SourceMode.LOCAL) {
                Session(LocalSource(context.filesDir, local!!), null, null)
            } else {
                Session(CasaZappApi(current!!.serverUrl, current.token), current.serverUrl, current.token)
            }
        }
        DisposableEffect(session) { onDispose { session.api.close() } }
        val playing = watching
        if (playing != null) {
            PlayerScreen(
                session = session,
                watching = playing,
                onZap = { watching = playing.copy(index = it) },
                onPlaying = {
                    scope.launch { store.saveLastChannel(it) }
                    // Shared with the web and the other devices, so Home continues here everywhere.
                    scope.launch { runCatching { session.api.setRecent(it, playing.context) } }
                },
                onBack = { watching = null },
                onSwitch = { watching = it },
            )
            return@Box
        }

        val screen: @Composable () -> Unit = { Column {
            update?.let { UpdateBanner(it, Modifier.padding(bottom = 16.dp)) }
            when (route) {
                Route.Home -> HomeScreen(session, lastChannel, onWatch = { watching = it })
                Route.Live -> LiveScreen(session, onWatch = { watching = it })
                Route.Playlists -> PlaylistsScreen(session, local, onEditLocal = { backFromEdit = false; editLocal = true }, focusChange = backFromEdit)
                Route.Settings -> SettingsScreen(
                    mode = mode!!,
                    serverUrl = current?.serverUrl,
                    locale = locale?.firstOrNull(),
                    onLocale = { scope.launch { store.saveLocale(it) } },
                    uiMode = uiMode,
                    onUiMode = { scope.launch { store.saveUiMode(it) } },
                    pip = pip,
                    onPip = { scope.launch { store.savePip(it) } },
                    onUnpair = { scope.launch { store.clear() } },
                    onSwitchMode = { scope.launch { store.saveSourceMode(it) } },
                    onCheckUpdate = { Updater.newer().also { update = it } != null },
                )
            }
        } }
        if (form.compact) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) { screen() }
                TabBar(route) { route = it }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                NavRail(route, iconsOnly = form.phone) { route = it }
                val pad = if (form.tv) Modifier.padding(horizontal = 48.dp, vertical = 36.dp) else if (form.phone) Modifier.padding(16.dp) else Modifier.padding(24.dp)
                Box(Modifier.weight(1f).fillMaxHeight().systemBarsPadding().then(pad)) { screen() }
            }
        }

        // Back goes to Home first; on Home it asks before closing the app.
        var askExit by remember { mutableStateOf(false) }
        BackHandler { if (route != Route.Home) route = Route.Home else askExit = true }
        if (askExit) ExitPrompt(onCancel = { askExit = false }, onExit = { context.findActivity()?.finish() })
    }
}

@Composable
private fun ExitPrompt(onCancel: () -> Unit, onExit: () -> Unit) {
    val stay = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { stay.requestFocus() } }
    BackHandler(onBack = onCancel)
    Box(
        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f)).clickable(onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(24.dp).background(Casa.surface, androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.exit_title), color = Casa.text, fontSize = 20.sp, fontFamily = CasaFonts.display, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CasaButton(stringResource(R.string.exit_stay), Modifier.focusRequester(stay), primary = false, onClick = onCancel)
                CasaButton(stringResource(R.string.exit_close), onClick = onExit)
            }
        }
    }
}

private val NAV = listOf(
    Triple(Route.Home, Icons.home, R.string.nav_home),
    Triple(Route.Live, Icons.tv, R.string.nav_live),
    Triple(Route.Playlists, Icons.list, R.string.nav_playlists),
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
private fun NavRail(route: Route, iconsOnly: Boolean = false, onRoute: (Route) -> Unit) {
    val items: List<Triple<Route, ImageVector, Int>> = NAV
    if (iconsOnly) {
        // A phone on its side: the web's tablet rail, icons with a small label.
        Column(
            Modifier.width(76.dp).fillMaxHeight().background(Casa.surface).systemBarsPadding().padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            androidx.compose.foundation.Image(painterResource(R.drawable.icon), null, Modifier.padding(bottom = 12.dp).size(32.dp))
            items.forEach { (target, icon, label) ->
                val active = route == target
                Column(
                    Modifier.fillMaxWidth().focusRing { onRoute(target) }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(icon, tint = if (active) Casa.accent else Casa.muted, size = 22.dp)
                    Text(stringResource(label), color = if (active) Casa.text else Casa.muted, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
        return
    }
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
