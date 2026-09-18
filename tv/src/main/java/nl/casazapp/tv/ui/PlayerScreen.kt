@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package nl.casazapp.tv.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.casazapp.core.api.Channel
import nl.casazapp.core.api.ChannelDetail
import nl.casazapp.core.api.NowNext
import nl.casazapp.core.api.Programme
import nl.casazapp.tv.R

private const val OSD_MS = 5_000L

/**
 * The web player on a television: picture filling the screen, the OSD over it, and the guide
 * beside it. The picture plays straight from the provider; the server only hands out the URL.
 *
 * Keys: up/down zap, OK shows the OSD and its buttons, right (or the guide key) opens the guide,
 * back closes the guide, then the player.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    session: Session,
    watching: Watching,
    onZap: (Int) -> Unit,
    onPlaying: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val channels = watching.channels
    val channel = channels[watching.index]
    val player = remember { ExoPlayer.Builder(context).build() }
    var failed by remember { mutableStateOf(false) }
    var osd by remember { mutableStateOf(true) }
    var osdAt by remember { mutableLongStateOf(0L) }
    var detail by remember { mutableStateOf<ChannelDetail?>(null) }
    var guide by remember { mutableStateOf<NowNext?>(null) }
    var favorite by remember { mutableStateOf(channel.favorite) }
    var volume by remember { mutableFloatStateOf(1f) }
    var guideOpen by remember { mutableStateOf(false) }
    val compact = LocalForm.current.compact
    val root = remember { FocusRequester() }
    val firstControl = remember { FocusRequester() }

    fun showOsd() {
        osd = true
        osdAt = System.currentTimeMillis()
    }
    fun zap(step: Int) {
        guideOpen = false
        onZap((watching.index + step + channels.size) % channels.size)
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                failed = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(channel.id) {
        failed = false
        guide = null
        detail = null
        favorite = channel.favorite
        showOsd()
        runCatching {
            val stream = session.api.stream(channel.id)
            val http = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .apply { stream.userAgent?.let { setUserAgent(it) } }
            val item = MediaItem.Builder()
                .setUri(stream.url)
                .apply { if (stream.format != "ts") setMimeType(MimeTypes.APPLICATION_M3U8) }
                .build()
            player.setMediaSource(DefaultMediaSourceFactory(http).createMediaSource(item))
            player.prepare()
            player.playWhenReady = true
            onPlaying(channel.id)
        }.onFailure { failed = true }
        detail = runCatching { session.api.channel(channel.id) }.getOrNull()
        guide = runCatching { session.api.nowNext(listOf(channel.id))[channel.id.toString()] }.getOrNull()
    }

    LaunchedEffect(osdAt) {
        delay(OSD_MS)
        if (!guideOpen) {
            osd = false
            root.requestFocus()
        }
    }
    LaunchedEffect(guideOpen) { if (!guideOpen) root.requestFocus() }
    BackHandler { if (guideOpen) guideOpen = false else onBack() }

    val stage: @Composable (Modifier) -> Unit = { size -> Box(
            size
                .clickable(interactionSource = null, indication = null) { if (osd) osd = false else showOsd() }
                .focusRequester(root)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || guideOpen) return@onPreviewKeyEvent false
                    when (event.key.nativeKeyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_CHANNEL_UP -> zap(-1)
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN, AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> zap(1)
                        AndroidKeyEvent.KEYCODE_GUIDE, AndroidKeyEvent.KEYCODE_MENU -> guideOpen = true
                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> if (!osd) guideOpen = true else return@onPreviewKeyEvent false
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_INFO -> {
                            if (osd) return@onPreviewKeyEvent false
                            showOsd()
                            scope.launch {
                                delay(50)
                                runCatching { firstControl.requestFocus() }
                            }
                        }
                        else -> return@onPreviewKeyEvent false
                    }
                    true
                },
        ) {
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { useController = false; this.player = player } },
                modifier = Modifier.fillMaxSize(),
            )

            if (failed) {
                Text(
                    stringResource(R.string.play_failed),
                    color = Casa.text,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 90.dp)
                        .background(Casa.surface, RoundedCornerShape(12.dp))
                        .border(1.dp, Casa.line, RoundedCornerShape(12.dp))
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }

            // With the guide open the picture is small; its own information would only get in the way.
            if (osd && !guideOpen) {
                // Top bar: back and channel name.
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent)))
                        .padding(horizontal = 32.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(Modifier.size(44.dp).background(Color.White.copy(alpha = 0.1f), CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                        Icon(Icons.back, Color.White)
                    }
                    Text(channel.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }

                // Zap arrows.
                Column(
                    Modifier.align(Alignment.TopEnd).padding(top = 140.dp, end = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    listOf(Icons.up to -1, Icons.down to 1).forEach { (icon, step) ->
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable { zap(step) }
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Icon(icon, Color.White) }
                    }
                }

                Osd(
                    number = watching.index + 1,
                    channel = channel,
                    detail = detail,
                    guide = guide,
                    session = session,
                    favorite = favorite,
                    muted = volume == 0f,
                    firstControl = firstControl,
                    onGuide = { guideOpen = true },
                    onFavorite = {
                        favorite = !favorite
                        val value = favorite
                        scope.launch { runCatching { session.api.setFavorite(channel.id, value) } }
                        showOsd()
                    },
                    onMute = {
                        volume = if (volume == 0f) 1f else 0f
                        player.volume = volume
                        showOsd()
                    },
                    compact = compact,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }
        } }

    if (compact) {
        // The guide is always there below the picture, like the web app on a phone.
        Column(Modifier.fillMaxSize().background(Casa.bg).systemBarsPadding()) {
            stage(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black))
            GuidePanel(
                session = session,
                watching = watching,
                onPick = { index -> onZap(index) },
                compact = true,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
        return
    }

    Row(Modifier.fillMaxSize().background(Color.Black)) {
        stage(Modifier.weight(1f).fillMaxHeight())
        if (guideOpen) {
            GuidePanel(
                session = session,
                watching = watching,
                onPick = { index -> guideOpen = false; onZap(index) },
                modifier = Modifier.fillMaxHeight().width(560.dp),
            )
        }
    }
}

@Composable
private fun Osd(
    number: Int,
    channel: Channel,
    detail: ChannelDetail?,
    guide: NowNext?,
    session: Session,
    favorite: Boolean,
    muted: Boolean,
    firstControl: FocusRequester,
    onGuide: () -> Unit,
    onFavorite: () -> Unit,
    onMute: () -> Unit,
    compact: Boolean,
    modifier: Modifier,
) {
    if (compact) {
        // A phone's picture is small: only the controls, the channel and what is on now.
        Row(
            modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("$number  ${channel.name}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                guide?.now?.let { Text(it.title, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            OsdButton(if (favorite) Icons.starFilled else Icons.star, if (favorite) Casa.accent else Color.White, Modifier, onFavorite)
            OsdButton(if (muted) Icons.muted else Icons.volume, Color.White, Modifier, onMute)
        }
        return
    }
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000), Color(0xE6000000))))
            .padding(start = 48.dp, end = 48.dp, top = 80.dp, bottom = 36.dp),
    ) {
        // Controls above the channel information, like the web player.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OsdButton(Icons.guide, Casa.accent, Modifier.focusRequester(firstControl), onGuide)
            OsdButton(if (favorite) Icons.starFilled else Icons.star, if (favorite) Casa.accent else Color.White, Modifier, onFavorite)
            OsdButton(if (muted) Icons.muted else Icons.volume, Color.White, Modifier, onMute)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(number.toString(), color = Casa.accent, fontSize = 40.sp, fontFamily = CasaFonts.mono)
            ChannelLogo(channel.name, session.logo(channel.logo), session.token, 60.dp)
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(detail?.categoryName ?: "", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
                    Text(
                        stringResource(R.string.live_badge),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(Casa.live, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(channel.name, color = Color.White, fontSize = 34.sp, fontFamily = CasaFonts.display, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.width(720.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GuideLine(stringResource(R.string.now), guide?.now, strong = true)
            guide?.now?.let { ProgressBar(progressOf(it), Modifier.padding(start = 64.dp).fillMaxWidth()) }
            GuideLine(stringResource(R.string.next), guide?.next, strong = false)
            Text(stringResource(R.string.player_hint), color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun OsdButton(icon: ImageVector, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.padding(4.dp).focusRing(CircleShape, onClick = onClick).size(52.dp),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, tint, 28.dp) }
}

@Composable
private fun GuideLine(label: String, programme: Programme?, strong: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, modifier = Modifier.width(52.dp))
        if (programme == null) {
            Text("–", color = Color.White.copy(alpha = 0.4f), fontSize = 15.sp)
        } else {
            Text("${time(programme.start)}–${time(programme.stop)}", color = Color.White.copy(alpha = 0.5f), fontFamily = CasaFonts.mono, fontSize = 13.sp)
            Text(
                programme.title,
                color = if (strong) Color.White else Color.White.copy(alpha = 0.7f),
                fontSize = if (strong) 19.sp else 16.sp,
                fontWeight = if (strong) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Channels below each other, programmes of the selected one beside them. OK on a channel shows
 * its guide while the picture keeps playing; OK again switches to it.
 */
@Composable
private fun GuidePanel(session: Session, watching: Watching, onPick: (Int) -> Unit, modifier: Modifier, compact: Boolean = false) {
    val channels = watching.channels
    var selected by remember { mutableStateOf(watching.index) }
    var programmes by remember { mutableStateOf<List<Programme>?>(null) }
    var nowNext by remember { mutableStateOf<Map<String, NowNext>>(emptyMap()) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (watching.index - 3).coerceAtLeast(0))
    val current = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { current.requestFocus() }
        nowNext = runCatching { session.api.nowNext(channels.map { it.id }) }.getOrDefault(emptyMap())
    }
    LaunchedEffect(selected) {
        programmes = null
        programmes = runCatching { session.api.guide(channels[selected].id) }.getOrDefault(emptyList())
    }

    Column(modifier.background(Casa.bg).border(1.dp, Casa.line)) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.guide), color = Casa.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(
                watching.label,
                color = Casa.muted,
                fontSize = 13.sp,
                modifier = Modifier.border(1.dp, Casa.line, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
        Row(Modifier.fillMaxSize()) {
            LazyColumn((if (compact) Modifier.fillMaxWidth(0.55f) else Modifier.width(250.dp)).fillMaxHeight(), state = listState) {
                itemsIndexed(channels, key = { _, c -> c.id }) { index, c ->
                    val isPlaying = index == watching.index
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(if (isPlaying) Modifier.focusRequester(current) else Modifier)
                            .focusRing(RoundedCornerShape(0.dp), onFocus = { if (it) selected = index }) {
                                if (selected == index) onPick(index) else selected = index
                            }
                            .background(if (isPlaying) Casa.live.copy(alpha = 0.12f) else Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text((index + 1).toString(), color = Casa.muted, fontFamily = CasaFonts.mono, fontSize = 12.sp, modifier = Modifier.width(28.dp))
                        ChannelLogo(c.name, session.logo(c.logo), session.token, 36.dp)
                        Column(Modifier.weight(1f)) {
                            Text(c.name, color = Casa.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(nowNext[c.id.toString()]?.now?.title ?: "", color = Casa.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().border(1.dp, Casa.line)) {
                if (selected != watching.index) {
                    Text(
                        stringResource(R.string.watch_channel, channels[selected].name),
                        color = Casa.accentInk,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(12.dp).fillMaxWidth().background(Casa.accent, RoundedCornerShape(10.dp)).padding(10.dp),
                    )
                }
                val list = programmes
                if (list != null && list.isEmpty()) {
                    Text(stringResource(R.string.no_guide), color = Casa.muted, fontSize = 14.sp, modifier = Modifier.padding(20.dp))
                }
                LazyColumn {
                    items(list.orEmpty(), key = { "${it.start}${it.title}" }) { p ->
                        val playing = progressOf(p) in 0.0001f..0.9999f
                        Column(
                            Modifier.fillMaxWidth().background(if (playing) Casa.raised else Color.Transparent).padding(horizontal = 16.dp, vertical = 9.dp),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(time(p.start), color = if (playing) Casa.accent else Casa.muted, fontFamily = CasaFonts.mono, fontSize = 12.sp)
                                Text(p.title, color = Casa.text, fontSize = 14.sp, fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (playing) ProgressBar(progressOf(p), Modifier.padding(start = 52.dp, top = 6.dp).fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}
