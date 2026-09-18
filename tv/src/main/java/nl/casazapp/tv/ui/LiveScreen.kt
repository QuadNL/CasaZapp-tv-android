@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package nl.casazapp.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import nl.casazapp.core.api.Category
import nl.casazapp.core.api.Channel
import nl.casazapp.core.api.ChannelList
import nl.casazapp.core.api.NowNext
import nl.casazapp.tv.R

/** What the chips at the top can select; the player zaps through the same selection. */
sealed interface Filter {
    data object All : Filter
    data object Favorites : Filter
    data class OwnList(val list: ChannelList) : Filter
    data class InCategory(val category: Category) : Filter
}

/** Live TV like the web app: chips for your lists and categories, channels with now/next. */
@Composable
fun LiveScreen(session: Session, onWatch: (Watching) -> Unit) {
    val api = session.api
    var playlistId by remember { mutableStateOf<Int?>(null) }
    var lists by remember { mutableStateOf<List<ChannelList>>(emptyList()) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var filter by remember { mutableStateOf<Filter>(Filter.All) }
    var channels by remember { mutableStateOf<List<Channel>?>(null) }
    var guide by remember { mutableStateOf<Map<String, NowNext>>(emptyMap()) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching {
            val id = api.playlists().firstOrNull()?.id
            lists = api.lists()
            // With a primary list the app shows own lists instead of the categories, like the web.
            lists.firstOrNull { it.primary }?.let { filter = Filter.OwnList(it) }
            if (id != null && lists.none { it.primary }) categories = api.categories(id)
            playlistId = id
        }.onFailure { failed = true }
    }
    LaunchedEffect(playlistId, filter) {
        val id = playlistId ?: return@LaunchedEffect
        channels = null
        runCatching {
            val f = filter
            val page = api.channels(
                id,
                listId = (f as? Filter.OwnList)?.list?.id,
                categoryId = (f as? Filter.InCategory)?.category?.id,
                favorites = f is Filter.Favorites,
            )
            channels = page.items
            guide = api.nowNext(page.items.map { it.id })
        }.onFailure { failed = true }
    }

    val label = when (val f = filter) {
        Filter.All -> stringResource(R.string.all)
        Filter.Favorites -> stringResource(R.string.favorites)
        is Filter.OwnList -> f.list.name
        is Filter.InCategory -> f.category.name
    }

    Column {
        Text(stringResource(R.string.nav_live), color = Casa.text, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
        channels?.let {
            Text(stringResource(R.string.channel_count, it.size), color = Casa.muted, fontSize = 15.sp)
        }

        LazyRow(Modifier.padding(vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (lists.none { it.primary }) {
                item { Chip(stringResource(R.string.all), filter == Filter.All) { filter = Filter.All } }
            }
            items(lists, key = { "l${it.id}" }) { l ->
                Chip("★ ${l.name}", (filter as? Filter.OwnList)?.list?.id == l.id) { filter = Filter.OwnList(l) }
            }
            item { Chip(stringResource(R.string.favorites), filter == Filter.Favorites) { filter = Filter.Favorites } }
            items(categories, key = { "c${it.id}" }) { c ->
                Chip(c.name.ifEmpty { "—" }, (filter as? Filter.InCategory)?.category?.id == c.id) {
                    filter = Filter.InCategory(c)
                }
            }
        }

        val current = channels
        when {
            failed -> Text(stringResource(R.string.connect_failed), color = Casa.live)
            current == null -> Text(stringResource(R.string.loading), color = Casa.muted)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(current, key = { _, c -> c.id }) { index, channel ->
                    ChannelRow(index + 1, channel, guide[channel.id.toString()], session) {
                        onWatch(Watching(current, index, label))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(number: Int, channel: Channel, info: NowNext?, session: Session, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().focusRing(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(number.toString(), color = Casa.muted, fontFamily = FontFamily.Monospace, fontSize = 15.sp, modifier = Modifier.width(40.dp))
        ChannelLogo(channel.name, session.logo(channel.logo), session.token, 52.dp)
        Text(
            channel.name,
            color = Casa.text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(300.dp),
        )
        Column(Modifier.weight(1f)) {
            val now = info?.now
            if (now == null) {
                Text(stringResource(R.string.no_guide), color = Casa.muted.copy(alpha = 0.5f), fontSize = 14.sp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(now.title, color = Casa.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Text("${time(now.start)}–${time(now.stop)}", color = Casa.muted, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                Spacer(Modifier.height(5.dp))
                ProgressBar(progressOf(now), Modifier.width(260.dp))
                info.next?.let {
                    Spacer(Modifier.height(4.dp))
                    Text("${stringResource(R.string.next)} ${time(it.start)} · ${it.title}", color = Casa.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
