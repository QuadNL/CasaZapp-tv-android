@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package nl.casazapp.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.casazapp.core.api.Category
import nl.casazapp.core.api.Channel
import nl.casazapp.core.api.ChannelList
import nl.casazapp.core.api.NowNext
import nl.casazapp.tv.R

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
    var guide by mutableStateOf<Map<String, NowNext>>(emptyMap())
    var failed by mutableStateOf(false)
}

/**
 * Loads lists and categories, then the channels of the selected chip. With [current] (the player's
 * list) nothing changes until a chip is picked; its chip is selected when [currentLabel] names one.
 */
@Composable
fun rememberChannelFilter(session: Session, current: List<Channel>? = null, currentLabel: String? = null): ChannelFilter {
    val api = session.api
    val state = remember { ChannelFilter().apply { channels = current } }
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
        val id = state.playlistId ?: return@LaunchedEffect
        val f = state.filter ?: return@LaunchedEffect
        // The player's own list is already here; only reload when the chip differs.
        if (current != null && loadedFor == null && state.channels != null) {
            loadedFor = f
            state.guide = runCatching { api.nowNext(state.channels!!.map { it.id }) }.getOrDefault(emptyMap())
            return@LaunchedEffect
        }
        loadedFor = f
        state.channels = null
        runCatching {
            val page = api.channels(
                id,
                listId = (f as? Filter.OwnList)?.list?.id,
                categoryId = (f as? Filter.InCategory)?.category?.id,
                favorites = f is Filter.Favorites,
            )
            state.channels = page.items
            state.guide = api.nowNext(page.items.map { it.id })
        }.onFailure { state.failed = true }
    }
    return state
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
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        before()
        if (state.lists.none { it.primary }) {
            item { Chip(all, state.filter == Filter.All) { state.filter = Filter.All } }
        }
        items(state.lists, key = { "l${it.id}" }) { l ->
            Chip("★ ${l.name}", (state.filter as? Filter.OwnList)?.list?.id == l.id) { state.filter = Filter.OwnList(l) }
        }
        item { Chip(favorites, state.filter == Filter.Favorites) { state.filter = Filter.Favorites } }
        items(state.categories, key = { "c${it.id}" }) { c ->
            Chip(c.name.ifEmpty { "—" }, (state.filter as? Filter.InCategory)?.category?.id == c.id) {
                state.filter = Filter.InCategory(c)
            }
        }
    }
}
