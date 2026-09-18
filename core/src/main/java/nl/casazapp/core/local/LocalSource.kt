package nl.casazapp.core.local

import android.util.Base64
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import java.io.File
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import nl.casazapp.core.api.Category
import nl.casazapp.core.api.Channel
import nl.casazapp.core.api.ChannelDetail
import nl.casazapp.core.api.ChannelList
import nl.casazapp.core.api.ChannelPage
import nl.casazapp.core.api.ChannelStream
import nl.casazapp.core.api.NowNext
import nl.casazapp.core.api.Playlist
import nl.casazapp.core.api.Programme
import nl.casazapp.core.api.TvSource

/** A playlist added on the device itself: Xtream Codes or an M3U URL. */
@Serializable
data class LocalPlaylist(
    val type: String,
    val name: String,
    val url: String,
    val username: String? = null,
    val password: String? = null,
) {
    val isXtream get() = type == XTREAM

    companion object {
        const val XTREAM = "xtream"
        const val M3U = "m3u"
    }
}

@Serializable
private data class StoredChannel(
    val id: Int,
    val name: String,
    val logo: String?,
    val categoryId: Int?,
    /** Xtream: stream id. M3U: the stream URL. */
    val ref: String,
)

@Serializable
private data class Catalogue(
    val categories: List<Category>,
    val channels: List<StoredChannel>,
    val syncedAt: Long,
)

@Serializable
private data class LocalState(val favorites: Set<Int> = emptySet())

private const val REFRESH_MS = 24 * 3600 * 1000L
private const val GUIDE_CACHE_MS = 10 * 60 * 1000L

/**
 * Does on the device what the server does otherwise: fetches the playlist, keeps it in `files/local`,
 * remembers favourites and asks Xtream for the guide of the channels on screen. Streams go straight
 * to the provider either way.
 */
class LocalSource(dir: File, private val playlist: LocalPlaylist) : TvSource {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(OkHttp) {
        install(HttpTimeout) { requestTimeoutMillis = 60_000 }
        expectSuccess = true
    }
    private val folder = File(dir, "local").apply { mkdirs() }
    // One set of files per playlist, so switching playlists never mixes channels or favourites.
    private val key = Integer.toHexString("${playlist.type}|${playlist.url}|${playlist.username}".hashCode())
    private val catalogueFile = File(folder, "catalogue-$key.json")
    private val stateFile = File(folder, "state-$key.json")
    private val lock = Mutex()
    private var catalogue: Catalogue? = null
    private var state = LocalState()
    private val guideCache = ConcurrentHashMap<Int, Pair<Long, List<Programme>>>()
    private val baseUrl = playlist.url.trim().trimEnd('/').substringBefore("/player_api.php")

    /** Loads the stored catalogue, or fetches it when missing or a day old. */
    private suspend fun data(): Catalogue = lock.withLock {
        catalogue?.let { return it }
        withContext(Dispatchers.IO) {
            state = runCatching { json.decodeFromString<LocalState>(stateFile.readText()) }.getOrDefault(LocalState())
            val stored = runCatching { json.decodeFromString<Catalogue>(catalogueFile.readText()) }.getOrNull()
            val fresh = if (stored == null || System.currentTimeMillis() - stored.syncedAt > REFRESH_MS) {
                runCatching { fetch() }.getOrElse { if (stored != null) stored else throw it }
            } else {
                stored
            }
            catalogue = fresh
            fresh
        }
    }

    /** Fetches the playlist again, for "refresh" in Settings. */
    suspend fun refresh(): Int = lock.withLock {
        withContext(Dispatchers.IO) {
            val fresh = fetch()
            catalogue = fresh
            fresh.channels.size
        }
    }

    private suspend fun fetch(): Catalogue {
        val result = if (playlist.isXtream) fetchXtream() else fetchM3u()
        catalogueFile.writeText(json.encodeToString(Catalogue.serializer(), result))
        return result
    }

    private suspend fun xtream(action: String, extra: Map<String, String> = emptyMap()): JsonElement {
        val text = client.get("$baseUrl/player_api.php") {
            parameter("username", playlist.username)
            parameter("password", playlist.password)
            parameter("action", action)
            extra.forEach { (k, v) -> parameter(k, v) }
        }.bodyAsText()
        return json.parseToJsonElement(text)
    }

    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private suspend fun fetchXtream(): Catalogue {
        val cats = (xtream("get_live_categories") as? JsonArray).orEmpty().mapNotNull {
            val o = it.jsonObject
            val id = o["category_id"].text()?.toIntOrNull() ?: return@mapNotNull null
            id to (o["category_name"].text() ?: "")
        }
        val channels = (xtream("get_live_streams") as? JsonArray).orEmpty().mapNotNull {
            val o = it.jsonObject
            val id = o["stream_id"].text()?.toIntOrNull() ?: return@mapNotNull null
            StoredChannel(id, o["name"].text() ?: "", o["stream_icon"].text(), o["category_id"].text()?.toIntOrNull(), id.toString())
        }
        return catalogueOf(cats, channels)
    }

    private suspend fun fetchM3u(): Catalogue {
        val text = client.get(playlist.url.trim()).bodyAsText()
        val groups = LinkedHashMap<String, Int>()
        val channels = mutableListOf<StoredChannel>()
        var pending: Pair<String, Map<String, String>>? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF") -> {
                    val attrs = Regex("""([\w-]+)="([^"]*)"""").findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
                    // The name follows the first comma after the last attribute: names may contain commas.
                    val name = line.substring(line.lastIndexOf('"').coerceAtLeast(0)).substringAfter(",").trim()
                    pending = name to attrs
                }
                line.isNotEmpty() && !line.startsWith("#") && pending != null -> {
                    val (name, attrs) = pending!!
                    val group = attrs["group-title"]?.takeIf { it.isNotBlank() }
                    val categoryId = group?.let { groups.getOrPut(it) { groups.size + 1 } }
                    channels += StoredChannel(channels.size + 1, name, attrs["tvg-logo"]?.takeIf { it.isNotBlank() }, categoryId, line)
                    pending = null
                }
            }
        }
        return catalogueOf(groups.map { (name, id) -> id to name }, channels)
    }

    private fun catalogueOf(cats: List<Pair<Int, String>>, channels: List<StoredChannel>): Catalogue {
        val counts = channels.groupingBy { it.categoryId }.eachCount()
        return Catalogue(
            categories = cats.map { (id, name) -> Category(id, "live", name, counts[id] ?: 0) }.filter { it.enabledCount > 0 },
            channels = channels,
            syncedAt = System.currentTimeMillis(),
        )
    }

    private fun StoredChannel.toChannel() = Channel(id, name, logo, categoryId, id in state.favorites)

    override suspend fun playlists() = listOf(Playlist(1, playlist.name))

    override suspend fun lists() = emptyList<ChannelList>()

    override suspend fun categories(playlistId: Int) = data().categories

    override suspend fun channels(
        playlistId: Int,
        listId: Int?,
        categoryId: Int?,
        favorites: Boolean,
        limit: Int,
        offset: Int,
    ): ChannelPage {
        val all = data().channels.filter {
            (categoryId == null || it.categoryId == categoryId) && (!favorites || it.id in state.favorites)
        }
        return ChannelPage(all.size, offset, all.drop(offset).take(limit).map { it.toChannel() })
    }

    override suspend fun channel(id: Int): ChannelDetail {
        val data = data()
        val c = data.channels.first { it.id == id }
        return ChannelDetail(c.id, 1, c.name, c.logo, c.categoryId, data.categories.firstOrNull { it.id == c.categoryId }?.name, c.id in state.favorites)
    }

    override suspend fun setFavorite(channelId: Int, favorite: Boolean) {
        data()
        lock.withLock {
            state = state.copy(favorites = if (favorite) state.favorites + channelId else state.favorites - channelId)
            withContext(Dispatchers.IO) { stateFile.writeText(json.encodeToString(LocalState.serializer(), state)) }
        }
    }

    /** Xtream only: M3U playlists have no guide in local mode (XMLTV is too heavy for a TV box). */
    override suspend fun guide(channelId: Int): List<Programme> {
        if (!playlist.isXtream) return emptyList()
        guideCache[channelId]?.let { (at, list) -> if (System.currentTimeMillis() - at < GUIDE_CACHE_MS) return list }
        val listings = runCatching {
            xtream("get_short_epg", mapOf("stream_id" to channelId.toString(), "limit" to "24"))
                .jsonObject["epg_listings"]?.jsonArray.orEmpty()
        }.getOrDefault(emptyList())
        val now = System.currentTimeMillis() / 1000
        val programmes = listings.mapNotNull {
            val o = it.jsonObject
            val start = o["start_timestamp"].text()?.toLongOrNull() ?: return@mapNotNull null
            val stop = o["stop_timestamp"].text()?.toLongOrNull() ?: return@mapNotNull null
            val title = decode(o["title"].text()) ?: return@mapNotNull null
            if (stop < now) return@mapNotNull null
            Programme(title, decode(o["description"].text()), Instant.ofEpochSecond(start).toString(), Instant.ofEpochSecond(stop).toString())
        }
        guideCache[channelId] = System.currentTimeMillis() to programmes
        return programmes
    }

    private fun decode(value: String?): String? = value?.let {
        runCatching { String(Base64.decode(it, Base64.DEFAULT)).trim() }.getOrDefault(it).ifBlank { null }
    }

    /** One request per channel, a few at a time, so the provider is not flooded. */
    override suspend fun nowNext(channelIds: List<Int>): Map<String, NowNext> {
        if (!playlist.isXtream) return emptyMap()
        val gate = Semaphore(4)
        return coroutineScope {
            channelIds.take(40).map { id ->
                async { gate.withPermit { id.toString() to guide(id).let { NowNext(it.getOrNull(0), it.getOrNull(1)) } } }
            }.awaitAll().toMap()
        }
    }

    /** Xtream short EPG per channel (cached, a few at a time); only the first rows, to spare the provider. */
    override suspend fun grid(channelIds: List<Int>, from: Instant, hours: Int): Map<String, List<Programme>> {
        if (!playlist.isXtream) return emptyMap()
        val to = from.plusSeconds(hours * 3600L)
        val gate = Semaphore(4)
        return coroutineScope {
            channelIds.take(40).map { id ->
                async {
                    gate.withPermit {
                        id.toString() to guide(id).filter { Instant.parse(it.stop) > from && Instant.parse(it.start) < to }
                    }
                }
            }.awaitAll().toMap()
        }
    }

    override suspend fun stream(channelId: Int): ChannelStream {
        val c = data().channels.first { it.id == channelId }
        return if (playlist.isXtream) {
            val u = java.net.URLEncoder.encode(playlist.username ?: "", "UTF-8")
            val p = java.net.URLEncoder.encode(playlist.password ?: "", "UTF-8")
            ChannelStream("$baseUrl/live/$u/$p/${c.ref}.ts", "ts")
        } else {
            ChannelStream(c.ref, if (c.ref.contains(".m3u8")) "hls" else "ts")
        }
    }

    override fun close() = client.close()
}

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
