package nl.casazapp.core.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ApiException(val status: HttpStatusCode) : Exception("HTTP ${status.value}")

/**
 * Talks to a CasaZapp TV server. Pairing works without a token; everything else uses the device
 * token the server handed out after a signed-in user approved the code.
 */
class CasaZappApi(baseUrl: String, private val token: String? = null) : TvSource {
    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        defaultRequest { url(baseUrl.trimEnd('/') + "/") }
        expectSuccess = false
    }

    private suspend inline fun <reified T> HttpResponse.ok(): T {
        if (status.value !in 200..299) throw ApiException(status)
        return body()
    }

    // ---- pairing, no token yet ----

    suspend fun startPairing(deviceName: String): PairingStart =
        client.post("api/pair/start") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("deviceName", deviceName) })
        }.ok()

    /** Null while waiting; the token once, when the code was approved. */
    suspend fun pairingToken(code: String): String? {
        val status: PairingStatus = client.get("api/pair/status") { parameter("code", code) }.ok()
        return status.token
    }

    // ---- with the device token ----

    private suspend inline fun <reified T> authed(path: String, crossinline query: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {}): T =
        client.get(path) {
            token?.let { bearerAuth(it) }
            query()
        }.ok()

    override suspend fun playlists(): List<Playlist> = authed("api/playlists")

    override suspend fun lists(): List<ChannelList> = authed("api/lists")

    override suspend fun categories(playlistId: Int): List<Category> =
        authed("api/playlists/$playlistId/categories") { parameter("type", "live") }

    /**
     * Visible channels, like the web app's Live TV: all of them, one category, the favourites, or
     * one of the user's own lists (in its own order).
     */
    override suspend fun channels(
        playlistId: Int,
        listId: Int?,
        categoryId: Int?,
        favorites: Boolean,
        limit: Int,
        offset: Int,
    ): ChannelPage =
        authed("api/playlists/$playlistId/channels") {
            parameter("limit", limit)
            parameter("offset", offset)
            listId?.let { parameter("listId", it) }
            categoryId?.let { parameter("categoryId", it) }
            if (favorites) parameter("favorites", true)
        }

    override suspend fun channel(id: Int): ChannelDetail = authed("api/channels/$id")

    /** Every programme of one channel from now on, for the guide next to the picture. */
    override suspend fun guide(channelId: Int): List<Programme> = authed("api/epg/channel/$channelId")

    override suspend fun setFavorite(channelId: Int, favorite: Boolean) {
        val response = client.patch("api/channels/$channelId") {
            token?.let { bearerAuth(it) }
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("favorite", favorite) })
        }
        if (response.status.value !in 200..299) throw ApiException(response.status)
    }

    override suspend fun nowNext(channelIds: List<Int>): Map<String, NowNext> =
        if (channelIds.isEmpty()) emptyMap()
        else authed("api/epg/now-next") { parameter("channels", channelIds.take(200).joinToString(",")) }

    /** The provider URL itself: a native player streams directly, not through the server. */
    override suspend fun stream(channelId: Int): ChannelStream = authed("api/channels/$channelId/stream")

    override fun close() = client.close()
}
