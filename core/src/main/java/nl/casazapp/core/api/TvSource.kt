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
    suspend fun stream(channelId: Int): ChannelStream
    fun close()
}
