@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package nl.casazapp.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import nl.casazapp.core.api.ChannelList
import nl.casazapp.core.api.Playlist
import nl.casazapp.core.local.LocalPlaylist
import nl.casazapp.core.local.LocalSource
import nl.casazapp.tv.R

/**
 * Your playlists and own lists. With a server you manage them in the web app; this shows what the
 * app gets. In local mode the playlist lives here, so refreshing and changing it are here too.
 */
@Composable
fun PlaylistsScreen(session: Session, localPlaylist: LocalPlaylist?, onEditLocal: () -> Unit) {
    val scope = rememberCoroutineScope()
    val compact = LocalForm.current.compact
    var playlists by remember { mutableStateOf<List<Playlist>?>(null) }
    var lists by remember { mutableStateOf<List<ChannelList>>(emptyList()) }
    var counts by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val refreshedText = stringResource(R.string.local_refreshed)
    val failedText = stringResource(R.string.local_failed)
    val local = session.api as? LocalSource

    suspend fun load() {
        runCatching {
            val all = session.api.playlists()
            counts = all.associate { it.id to session.api.channels(it.id, limit = 1).total }
            lists = session.api.lists()
            playlists = all
        }
    }
    LaunchedEffect(session) { load() }

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.nav_playlists), color = Casa.text, fontSize = if (compact) 24.sp else 32.sp, fontFamily = CasaFonts.display, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(if (local != null) R.string.playlists_local_hint else R.string.playlists_server_hint),
            color = Casa.muted,
            fontSize = 14.sp,
        )

        playlists?.forEach { p ->
            Section {
                Text(p.name, color = Casa.text, fontSize = 18.sp, fontFamily = CasaFonts.display, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.channel_count, counts[p.id] ?: 0), color = Casa.muted, fontSize = 14.sp)
                if (local != null) {
                    localPlaylist?.let { Text(it.url, color = Casa.muted, fontSize = 13.sp) }
                    message?.let { Text(it, color = Casa.muted, fontSize = 13.sp) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CasaButton(stringResource(if (busy) R.string.loading else R.string.refresh), enabled = !busy) {
                            busy = true
                            scope.launch {
                                val count = runCatching { local.refresh() }.getOrNull()
                                message = if (count != null) refreshedText.format(count) else failedText
                                busy = false
                                load()
                            }
                        }
                        CasaButton(stringResource(R.string.change_playlist), primary = false, onClick = onEditLocal)
                    }
                }
            }
        }

        if (lists.isNotEmpty()) {
            Text(stringResource(R.string.own_lists), color = Casa.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                lists.forEach { l ->
                    Row(
                        Modifier.border(1.dp, Casa.line, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(l.name, color = Casa.text, fontSize = 14.sp)
                        Text(l.channels.toString(), color = Casa.muted, fontSize = 14.sp)
                        if (l.primary) {
                            Text(
                                stringResource(R.string.primary),
                                color = Casa.accentInk,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.background(Casa.accent, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
