@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package nl.casazapp.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import java.time.LocalTime
import nl.casazapp.core.api.Channel
import nl.casazapp.core.api.ChannelDetail
import nl.casazapp.core.api.NowNext
import nl.casazapp.tv.R

/** Home like the web app: continue watching, your favourite channels, then Live TV. */
@Composable
fun HomeScreen(session: Session, lastChannelId: Int?, onWatch: (Watching) -> Unit) {
    val api = session.api
    var last by remember { mutableStateOf<ChannelDetail?>(null) }
    var lastList by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var favorites by remember { mutableStateOf<List<Channel>?>(null) }
    var row by remember { mutableStateOf<Pair<String, List<Channel>>?>(null) }
    var guide by remember { mutableStateOf<Map<String, NowNext>>(emptyMap()) }
    val favoritesLabel = stringResource(R.string.favorites)

    LaunchedEffect(lastChannelId) {
        runCatching {
            val playlistId = api.playlists().firstOrNull()?.id ?: return@runCatching
            favorites = api.channels(playlistId, favorites = true, limit = 40).items
            lastChannelId?.let { id ->
                val detail = api.channel(id)
                last = detail
                lastList = api.channels(playlistId, categoryId = detail.categoryId).items
            }
            // Like the web: the primary list, otherwise the first category with more than one channel.
            val primary = api.lists().firstOrNull { it.primary }
            row = if (primary != null) {
                primary.name to api.channels(playlistId, listId = primary.id, limit = 24).items
            } else {
                api.categories(playlistId).firstOrNull { it.enabledCount > 1 }?.let {
                    it.name to api.channels(playlistId, categoryId = it.id, limit = 24).items
                }
            }
            guide = api.nowNext(
                (listOfNotNull(last?.id) + favorites.orEmpty().map { it.id } + row?.second.orEmpty().map { it.id }).distinct(),
            )
        }
    }

    val hour = LocalTime.now().hour
    val greeting = when {
        hour < 6 -> R.string.greeting_night
        hour < 12 -> R.string.greeting_morning
        hour < 18 -> R.string.greeting_afternoon
        else -> R.string.greeting_evening
    }

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Text(stringResource(greeting), color = Casa.text, fontSize = 32.sp, fontFamily = CasaFonts.display, fontWeight = FontWeight.SemiBold)

        // Continue watching
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
            val current = last
            Box(
                Modifier
                    .width(420.dp)
                    .aspectRatio(16f / 9f)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF1E3A5F), Color(0xFF05070A))),
                        RoundedCornerShape(16.dp),
                    )
                    .focusRing(RoundedCornerShape(16.dp)) {
                        if (current != null) {
                            val index = lastList.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
                            if (lastList.isNotEmpty()) onWatch(Watching(lastList, index, current.categoryName ?: ""))
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (current != null) ChannelLogo(current.name, session.logo(current.logo), session.token, 96.dp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.continue_watching).uppercase(), color = Casa.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                if (current == null) {
                    Text(stringResource(R.string.nothing_yet), color = Casa.muted, fontSize = 18.sp)
                } else {
                    Text(current.name, color = Casa.text, fontSize = 30.sp, fontFamily = CasaFonts.display, fontWeight = FontWeight.SemiBold)
                    current.categoryName?.let { Text(it, color = Casa.muted, fontSize = 16.sp) }
                    guide[current.id.toString()]?.now?.let { now ->
                        Text("${now.title} · ${time(now.start)}–${time(now.stop)}", color = Casa.text, fontSize = 17.sp)
                        ProgressBar(progressOf(now), Modifier.width(320.dp))
                    }
                }
            }
        }

        // Favourite channels
        Text(stringResource(R.string.favorite_channels), color = Casa.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        val favs = favorites
        if (favs != null && favs.isEmpty()) {
            Text(stringResource(R.string.no_favorites), color = Casa.muted, fontSize = 15.sp)
        } else if (favs != null) {
            ChannelRow(session, favs, guide) { index -> onWatch(Watching(favs, index, favoritesLabel)) }
        }

        row?.let { (label, channels) ->
            if (channels.isNotEmpty()) {
                Text(label, color = Casa.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                ChannelRow(session, channels, guide) { index -> onWatch(Watching(channels, index, label)) }
            }
        }
    }
}

/** A row of channel tiles with what is on now, as on the web Home. */
@Composable
private fun ChannelRow(session: Session, channels: List<Channel>, guide: Map<String, NowNext>, onPick: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        itemsIndexed(channels, key = { _, c -> c.id }) { index, channel ->
            Column(
                Modifier.width(220.dp).focusRing { onPick(index) }.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier.fillMaxSize().aspectRatio(16f / 9f).background(Casa.surface, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) { ChannelLogo(channel.name, session.logo(channel.logo), session.token, 64.dp) }
                Text(channel.name, color = Casa.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                guide[channel.id.toString()]?.now?.let { now ->
                    Text(now.title, color = Casa.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ProgressBar(progressOf(now), Modifier.fillMaxWidth())
                }
            }
        }
    }
}
