@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package nl.casazapp.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import nl.casazapp.core.api.Category
import nl.casazapp.core.api.Channel
import nl.casazapp.core.api.ChannelList
import nl.casazapp.core.api.NowNext
import nl.casazapp.tv.R

private const val PAGE = 500

/** With more categories than this, they move from the chip row into a searchable list. */
private const val CHIP_CATEGORIES = 8

/** What the chips can select; the player zaps through the same selection. */
sealed interface Filter {
    data object All : Filter
    data object Favorites : Filter
    data class OwnList(val list: ChannelList) : Filter
    data class InCategory(val category: Category) : Filter
}

/** The chips of Live TV and the guide: your lists, favourites and (without a primary list) categories. */
class ChannelFilter {
    var playlistId by mutableStateOf<Int?>(null)
    var lists by mutableStateOf<List<ChannelList>>(emptyList())
    var categories by mutableStateOf<List<Category>>(emptyList())
    /** Null: the channels handed in, e.g. the list the player is zapping through. */
    var filter by mutableStateOf<Filter?>(null)
    var channels by mutableStateOf<List<Channel>?>(null)
    /** How many channels the selection has; more than [channels] until they are all loaded. */
    var total by mutableStateOf(0)
    var guide by mutableStateOf<Map<String, NowNext>>(emptyMap())
    var failed by mutableStateOf(false)
    internal var loadingMore = false
}

private suspend fun ChannelFilter.page(session: Session, offset: Int) = session.api.channels(
    playlistId!!,
    listId = (filter as? Filter.OwnList)?.list?.id,
    categoryId = (filter as? Filter.InCategory)?.category?.id,
    favorites = filter is Filter.Favorites,
    limit = PAGE,
    offset = offset,
)

/** The next page of channels, when the list or timeline scrolls near its end. */
suspend fun ChannelFilter.loadMore(session: Session) {
    val loaded = channels ?: return
    if (loadingMore || filter == null || playlistId == null || loaded.size >= total) return
    loadingMore = true
    try {
        val next = runCatching { page(session, loaded.size) }.getOrNull() ?: return
        channels = loaded + next.items
        guide = guide + runCatching { session.api.nowNext(next.items.take(200).map { it.id }) }.getOrDefault(emptyMap())
    } finally {
        loadingMore = false
    }
}

/**
 * Loads lists and categories, then the channels of the selected chip. With [current] (the player's
 * list) nothing changes until a chip is picked; its chip is selected when [currentLabel] names one.
 */
@Composable
fun rememberChannelFilter(session: Session, current: List<Channel>? = null, currentLabel: String? = null): ChannelFilter {
    val api = session.api
    val state = remember { ChannelFilter().apply { channels = current; total = current?.size ?: 0 } }
    val favoritesLabel = stringResource(R.string.favorites)
    LaunchedEffect(Unit) {
        runCatching {
            val id = api.playlists().firstOrNull()?.id
            state.lists = api.lists()
            val primary = state.lists.firstOrNull { it.primary }
            if (id != null && primary == null) state.categories = api.categories(id)
            state.playlistId = id
            if (current == null) {
                // With a primary list the app shows own lists instead of the categories, like the web.
                state.filter = primary?.let { Filter.OwnList(it) } ?: Filter.All
            } else {
                state.filter = state.lists.firstOrNull { it.name == currentLabel }?.let { Filter.OwnList(it) }
                    ?: state.categories.firstOrNull { it.name == currentLabel }?.let { Filter.InCategory(it) }
                    ?: Filter.Favorites.takeIf { currentLabel == favoritesLabel }
            }
        }.onFailure { state.failed = true }
    }
    var loadedFor by remember { mutableStateOf<Filter?>(null) }
    LaunchedEffect(state.playlistId, state.filter) {
        val f = state.filter ?: return@LaunchedEffect
        if (state.playlistId == null) return@LaunchedEffect
        // The player's own list is already here; only reload when the chip differs.
        if (current != null && loadedFor == null && state.channels != null) {
            loadedFor = f
            state.guide = runCatching { api.nowNext(state.channels!!.take(200).map { it.id }) }.getOrDefault(emptyMap())
            return@LaunchedEffect
        }
        loadedFor = f
        state.channels = null
        runCatching {
            val page = state.page(session, 0)
            state.total = page.total
            state.channels = page.items
            state.guide = api.nowNext(page.items.take(200).map { it.id })
        }.onFailure { state.failed = true }
    }
    return state
}

/** The selection as the web app names it, for "continue watching". */
fun ChannelFilter.context(): String? = when (val f = filter) {
    null, Filter.All -> null
    Filter.Favorites -> "favorites"
    is Filter.OwnList -> "list:${f.list.id}"
    is Filter.InCategory -> f.category.id.toString()
}

@Composable
fun ChannelFilter.label(): String = when (val f = filter) {
    null, Filter.All -> stringResource(R.string.all)
    Filter.Favorites -> stringResource(R.string.favorites)
    is Filter.OwnList -> f.list.name
    is Filter.InCategory -> f.category.name
}

@Composable
fun FilterChips(state: ChannelFilter, modifier: Modifier = Modifier, before: LazyListScope.() -> Unit = {}) {
    val favorites = stringResource(R.string.favorites)
    val all = stringResource(R.string.all)
    var picking by remember { mutableStateOf(false) }
    // Providers list hundreds of categories; as chips they cannot be found, so they get a searchable list.
    val manyCategories = state.categories.size > CHIP_CATEGORIES
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        before()
        if (state.lists.none { it.primary }) {
            item { Chip(all, state.filter == Filter.All) { state.filter = Filter.All } }
        }
        items(state.lists, key = { "l${it.id}" }) { l ->
            Chip("★ ${l.name}", (state.filter as? Filter.OwnList)?.list?.id == l.id) { state.filter = Filter.OwnList(l) }
        }
        item { Chip(favorites, state.filter == Filter.Favorites) { state.filter = Filter.Favorites } }
        if (manyCategories) {
            item {
                val chosen = (state.filter as? Filter.InCategory)?.category
                Chip(
                    chosen?.name?.ifEmpty { "—" }?.let { "$it ▾" } ?: "${stringResource(R.string.categories, state.categories.size)} ▾",
                    chosen != null,
                ) { picking = true }
            }
        } else {
            items(state.categories, key = { "c${it.id}" }) { c ->
                Chip(c.name.ifEmpty { "—" }, (state.filter as? Filter.InCategory)?.category?.id == c.id) {
                    state.filter = Filter.InCategory(c)
                }
            }
        }
    }
    if (picking) {
        CategoryPicker(state.categories, onPick = { state.filter = Filter.InCategory(it); picking = false }, onClose = { picking = false })
    }
}

/** All categories with their channel counts, searchable; works with a remote and with touch. */
@Composable
private fun CategoryPicker(categories: List<Category>, onPick: (Category) -> Unit, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = remember(query, categories) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) categories else categories.filter { it.name.lowercase().contains(q) }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(24.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .background(Casa.surface, RoundedCornerShape(16.dp))
                .border(1.dp, Casa.line, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.categories, categories.size), color = Casa.text, fontSize = 18.sp, fontFamily = CasaFonts.display)
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = Casa.text, fontSize = 16.sp),
                cursorBrush = SolidColor(Casa.accent),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text(stringResource(R.string.search_category), color = Casa.muted, fontSize = 16.sp)
                    inner()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Casa.bg, RoundedCornerShape(10.dp))
                    .border(1.dp, Casa.line, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
            LazyColumn {
                items(shown, key = { it.id }) { c ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(if (c == shown.firstOrNull()) Modifier.focusRequester(first) else Modifier)
                            .focusRing { onPick(c) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(c.name.ifEmpty { "—" }, color = Casa.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(c.enabledCount.toString(), color = Casa.muted, fontFamily = CasaFonts.mono, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
