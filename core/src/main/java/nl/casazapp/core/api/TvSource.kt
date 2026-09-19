package nl.casazapp.core.api

/**
 * Where the screens get channels, guide and streams from: a CasaZapp TV server ([CasaZappApi]) or
 * the device itself ([nl.casazapp.core.local.LocalSource]). The screens don't know which.
 */
interface TvSource {
    suspend fun playlists(): List<Playlist>
    suspend fun lists(): List<ChannelList>
    suspend fun categories(playlistId: Int): List<Category>
    suspend fun channels(
        playlistId: Int,
        listId: Int? = null,
        categoryId: Int? = null,
        favorites: Boolean = false,
        limit: Int = 500,
        offset: Int = 0,
    ): ChannelPage
    suspend fun channel(id: Int): ChannelDetail
    suspend fun guide(channelId: Int): List<Programme>
    suspend fun setFavorite(channelId: Int, favorite: Boolean)
    suspend fun nowNext(channelIds: List<Int>): Map<String, NowNext>
    /** Programmes per channel id from [from] for [hours] hours, for the Live TV timeline. */
    suspend fun grid(channelIds: List<Int>, from: java.time.Instant, hours: Int): Map<String, List<Programme>>
    suspend fun stream(channelId: Int): ChannelStream
    /** The channel watched last on any device, with what it was zapped through; null when unknown. */
    suspend fun recent(): RecentChannel? = null
    /** [context]: a category id, "favorites" or "list:<id>", as the web app uses. */
    suspend fun setRecent(channelId: Int, context: String?) {}
    fun close()
}
