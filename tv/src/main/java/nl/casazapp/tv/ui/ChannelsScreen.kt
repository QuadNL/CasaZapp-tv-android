@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package nl.casazapp.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import nl.casazapp.core.api.CasaZappApi
import nl.casazapp.core.api.Channel
import nl.casazapp.core.api.ChannelList
import nl.casazapp.core.api.NowNext
import nl.casazapp.tv.R

/** Channels below each other, like the guide in the web player; your own lists on top. */
@Composable
fun ChannelsScreen(
    api: CasaZappApi,
    serverUrl: String,
    token: String,
    onWatch: (List<Channel>, Int) -> Unit,
    onUnpair: () -> Unit,
) {
    var playlistId by remember { mutableStateOf<Int?>(null) }
    var lists by remember { mutableStateOf<List<ChannelList>>(emptyList()) }
    var listId by remember { mutableStateOf<Int?>(null) }
    var channels by remember { mutableStateOf<List<Channel>?>(null) }
    var guide by remember { mutableStateOf<Map<String, NowNext>>(emptyMap()) }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            playlistId = api.playlists().firstOrNull()?.id
            lists = api.lists()
        } catch (e: Exception) {
            error = true
        }
    }
    LaunchedEffect(playlistId, listId) {
        val id = playlistId ?: return@LaunchedEffect
        channels = null
        try {
            val page = api.channels(id, listId)
            channels = page.items
            guide = api.nowNext(page.items.map { it.id })
        } catch (e: Exception) {
            error = true
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CasaZapp TV", color = Casa.accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Button(onClick = onUnpair) { Text(stringResource(R.string.unpair)) }
        }

        LazyRow(
            Modifier.padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Chip(stringResource(R.string.all_channels), listId == null) { listId = null }
            }
            items(lists, key = { it.id }) { list ->
                Chip("★ ${list.name}", listId == list.id) { listId = list.id }
            }
        }

        val current = channels
        when {
            error -> Text(stringResource(R.string.connect_failed), color = Casa.live)
            current == null -> Text(stringResource(R.string.loading), color = Casa.muted)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(current, key = { _, c -> c.id }) { index, channel ->
                    ChannelRow(
                        number = index + 1,
                        channel = channel,
                        now = guide[channel.id.toString()]?.now?.title,
                        logoUrl = channel.logo?.let { serverUrl + it },
                        token = token,
                        onClick = { onWatch(current, index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Casa.text else Casa.surface)
            .border(2.dp, if (focused) Casa.accent else Color.Transparent, RoundedCornerShape(50))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Text(label, color = if (selected) Casa.bg else Casa.muted, fontSize = 16.sp)
    }
}

@Composable
private fun ChannelRow(
    number: Int,
    channel: Channel,
    now: String?,
    logoUrl: String?,
    token: String,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Casa.raised else Color.Transparent)
            .border(2.dp, if (focused) Casa.accent else Color.Transparent, RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            number.toString(),
            color = Casa.muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 16.sp,
            modifier = Modifier.width(44.dp),
        )
        Logo(logoUrl, token)
        Column(Modifier.weight(1f)) {
            Text(channel.name, color = Casa.text, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (now != null) {
                Text(now, color = Casa.muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (channel.favorite) Text("★", color = Casa.accent, fontSize = 18.sp)
    }
}

/** Logos come from the server's cache and need the device token. */
@Composable
fun Logo(url: String?, token: String, size: Int = 48) {
    val context = LocalContext.current
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(10.dp)).background(Casa.surface),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .httpHeaders(NetworkHeaders.Builder().set("Authorization", "Bearer $token").build())
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(4.dp),
            )
        }
    }
}
