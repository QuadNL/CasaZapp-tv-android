package nl.casazapp.core.api

import kotlinx.serialization.Serializable

// Mirrors packages/shared/src/index.ts in the CasaZapp-tv repo; only the fields the app uses.

@Serializable
data class PairingStart(val code: String, val expiresAt: String)

@Serializable
data class PairingStatus(val status: String, val token: String? = null)

@Serializable
data class Playlist(val id: Int, val name: String)

@Serializable
data class Channel(
    val id: Int,
    val name: String,
    val logo: String? = null,
    val categoryId: Int? = null,
    val favorite: Boolean = false,
)

@Serializable
data class ChannelDetail(
    val id: Int,
    val playlistId: Int,
    val name: String,
    val logo: String? = null,
    val categoryId: Int? = null,
    val categoryName: String? = null,
    val favorite: Boolean = false,
)

/** "Continue watching", shared by the household's devices through the server. */
@Serializable
data class RecentChannel(val channel: ChannelDetail, val context: String? = null)

@Serializable
data class Category(val id: Int, val type: String, val name: String, val enabledCount: Int = 0)

@Serializable
data class ChannelPage(val total: Int, val offset: Int = 0, val items: List<Channel>)

@Serializable
data class ChannelStream(val url: String, val format: String, val userAgent: String? = null)

@Serializable
data class Programme(val title: String, val description: String? = null, val start: String, val stop: String)

@Serializable
data class NowNext(val now: Programme? = null, val next: Programme? = null)

@Serializable
data class ChannelList(val id: Int, val name: String, val channels: Int, val primary: Boolean = false)
