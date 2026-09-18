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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import android.content.pm.ActivityInfo
import android.content.ContextWrapper
import android.content.Context
import android.app.Activity
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
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
    val tvLook = LocalForm.current.tv
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

    // Phones and tablets may turn the screen from the player; leaving it gives the choice back to the sensor.
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(tvLook) {
        onDispose { if (!tvLook) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    fun rotate() {
        activity?.requestedOrientation = if (landscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        showOsd()
    }

    val stage: @Composable (Modifier) -> Unit = { size -> Box(
            size
                .clickable(interactionSource = null, indication = null) { if (osd) osd = false else showOsd() }
                .pointerInput(tvLook) {
                    if (tvLook) return@pointerInput
                    // Like the web: swipe up for the guide, down to send it away; zapping is on the arrows.
                    var dy = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dy = 0f },
                        onDragEnd = { if (dy < -80f) guideOpen = true else if (dy > 80f) guideOpen = false },
                        onVerticalDrag = { _, amount -> dy += amount },
                    )
                }
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
                    fontSize = 16.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 80.dp, start = 16.dp, end = 16.dp)
                        .background(Casa.surface, RoundedCornerShape(12.dp))
                        .border(1.dp, Casa.line, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }

            // With the guide beside a TV picture, the picture's own information would only get in the way.
            if (osd && (!tvLook || !guideOpen)) {
                // Top bar: back and channel name.
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent)))
                        .padding(horizontal = if (tvLook) 32.dp else 16.dp, vertical = if (tvLook) 24.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(if (tvLook) 44.dp else 40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                        Icon(Icons.back, Color.White, if (tvLook) 24.dp else 20.dp)
                    }
                    Text(channel.name, color = Color.White, fontSize = if (tvLook) 18.sp else 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                // Zap arrows, vertically centred on the right like the web player.
                Column(
                    Modifier.align(Alignment.CenterEnd).padding(end = if (tvLook) 28.dp else 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    listOf(Icons.up to -1, Icons.down to 1).forEach { (icon, step) ->
                        Box(
                            Modifier
                                .size(if (tvLook) 48.dp else 40.dp)
                                .clip(CircleShape)
                                .clickable { zap(step) }
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Icon(icon, Color.White, if (tvLook) 24.dp else 20.dp) }
                    }
                }

                Osd(
                    number = watching.index + 1,
                    channel = channel,
                    detail = detail,
                    guide = guide,
                    session = session,
                    favorite = favorite,
                    volume = volume,
                    firstControl = firstControl,
                    onGuide = { guideOpen = !guideOpen; showOsd() },
                    onFavorite = {
                        favorite = !favorite
                        val value = favorite
                        scope.launch { runCatching { session.api.setFavorite(channel.id, value) } }
                        showOsd()
                    },
                    onVolume = {
                        volume = it
                        player.volume = it
                        showOsd()
                    },
                    onRotate = if (tvLook) null else ::rotate,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }
        } }

    if (compact) {
        // Like the web on a phone held upright: the picture fills the screen; the guide slides in below it.
        Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
            stage(Modifier.fillMaxWidth().weight(1f))
            if (guideOpen) {
                GuidePanel(
                    session = session,
                    watching = watching,
                    onPick = { index -> onZap(index) },
                    onClose = { guideOpen = false },
                    narrow = true,
                    modifier = Modifier.fillMaxWidth().weight(1.7f),
                )
            }
        }
        return
    }

    Row(Modifier.fillMaxSize().background(Color.Black)) {
        stage(Modifier.weight(1f).fillMaxHeight())
        if (guideOpen) {
            GuidePanel(
                session = session,
                watching = watching,
                onPick = { index -> if (tvLook) guideOpen = false; onZap(index) },
                onClose = if (tvLook) null else ({ guideOpen = false }),
                modifier = if (tvLook) Modifier.fillMaxHeight().width(560.dp) else Modifier.fillMaxHeight().weight(0.75f).systemBarsPadding(),
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** The web player's on-screen display: controls, then the channel, then now and next. */
@Composable
private fun Osd(
    number: Int,
    channel: Channel,
    detail: ChannelDetail?,
    guide: NowNext?,
    session: Session,
    favorite: Boolean,
    volume: Float,
    firstControl: FocusRequester,
    onGuide: () -> Unit,
    onFavorite: () -> Unit,
    onVolume: (Float) -> Unit,
    onRotate: (() -> Unit)?,
    modifier: Modifier,
) {
    val tv = LocalForm.current.tv
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000), Color(0xE6000000))))
            .then(if (tv) Modifier.padding(start = 48.dp, end = 48.dp, top = 80.dp, bottom = 36.dp) else Modifier.padding(start = 20.dp, end = 12.dp, top = 48.dp, bottom = 16.dp)),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            VolumeControl(volume, onVolume)
            onRotate?.let { OsdButton(Icons.rotate, Color.White, Modifier, it) }
            OsdButton(Icons.guide, Casa.accent, Modifier.focusRequester(firstControl), onGuide)
            OsdButton(if (favorite) Icons.starFilled else Icons.star, if (favorite) Casa.accent else Color.White, Modifier, onFavorite)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (tv) 18.dp else 12.dp)) {
            Text(number.toString(), color = Casa.accent, fontSize = if (tv) 40.sp else 26.sp, fontFamily = CasaFonts.mono)
            ChannelLogo(channel.name, session.logo(channel.logo), session.token, if (tv) 60.dp else 44.dp)
            Column(Modifier.weight(1f, fill = false)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(detail?.categoryName ?: "", color = Color.White.copy(alpha = 0.7f), fontSize = if (tv) 15.sp else 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Text(
                        stringResource(R.string.live_badge),
                        color = Color.White,
                        fontSize = if (tv) 11.sp else 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(Casa.live, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
                Text(channel.name, color = Color.White, fontSize = if (tv) 34.sp else 20.sp, fontFamily = CasaFonts.display, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(if (tv) 16.dp else 10.dp))
        Column((if (tv) Modifier.width(720.dp) else Modifier.fillMaxWidth()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GuideLine(stringResource(R.string.now), guide?.now, strong = true)
            guide?.now?.let { ProgressBar(progressOf(it), Modifier.padding(start = if (tv) 64.dp else 48.dp).fillMaxWidth()) }
            GuideLine(stringResource(R.string.next), guide?.next, strong = false)
            if (tv) Text(stringResource(R.string.player_hint), color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun OsdButton(icon: ImageVector, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    val tv = LocalForm.current.tv
    Box(
        modifier.padding(2.dp).focusRing(CircleShape, onClick = onClick).size(if (tv) 52.dp else 42.dp),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, tint, if (tv) 28.dp else 22.dp) }
}

/**
 * Mute button with a volume slider, like the web player. Drag or tap the bar; on a TV, left and
 * right change it while it has focus.
 */
@Composable
private fun VolumeControl(volume: Float, onChange: (Float) -> Unit) {
    val tv = LocalForm.current.tv
    var before by remember { mutableFloatStateOf(1f) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OsdButton(if (volume == 0f) Icons.muted else Icons.volume, Color.White, Modifier) {
            if (volume == 0f) {
                onChange(before.takeIf { it > 0f } ?: 1f)
            } else {
                before = volume
                onChange(0f)
            }
        }
        var focused by remember { mutableStateOf(false) }
        var widthPx by remember { mutableFloatStateOf(1f) }
        Box(
            Modifier
                .width(if (tv) 140.dp else 88.dp)
                .height(28.dp)
                .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent {
                    if (it.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (it.key) {
                        Key.DirectionLeft -> { onChange((volume - 0.1f).coerceIn(0f, 1f)); true }
                        Key.DirectionRight -> { onChange((volume + 0.1f).coerceIn(0f, 1f)); true }
                        else -> false
                    }
                }
                .focusable()
                .pointerInput(Unit) {
                    detectTapGestures { onChange((it.x / widthPx).coerceIn(0f, 1f)) }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ -> onChange((change.position.x / widthPx).coerceIn(0f, 1f)) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.25f)))
            Box(Modifier.fillMaxWidth(volume).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (focused) Casa.accent else Color.White))
        }
    }
}

@Composable
private fun GuideLine(label: String, programme: Programme?, strong: Boolean) {
    val tv = LocalForm.current.tv
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (tv) 12.dp else 8.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = if (tv) 13.sp else 11.sp, modifier = Modifier.width(if (tv) 52.dp else 40.dp))
        if (programme == null) {
            Text("–", color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp)
        } else {
            Text("${time(programme.start)}–${time(programme.stop)}", color = Color.White.copy(alpha = 0.5f), fontFamily = CasaFonts.mono, fontSize = if (tv) 13.sp else 11.sp)
            Text(
                programme.title,
                color = if (strong) Color.White else Color.White.copy(alpha = 0.7f),
                fontSize = if (tv) (if (strong) 19.sp else 16.sp) else (if (strong) 15.sp else 13.sp),
                fontWeight = if (strong) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The web player's guide. Wide (TV, landscape): channels below each other with the programmes of
 * the selected one beside them. Narrow (phone upright): the channels; tap one for its programmes.
 * Picking the selected channel again switches to it.
 */
@Composable
private fun GuidePanel(
    session: Session,
    watching: Watching,
    onPick: (Int) -> Unit,
    modifier: Modifier,
    narrow: Boolean = false,
    onClose: (() -> Unit)? = null,
) {
    val channels = watching.channels
    val tv = LocalForm.current.tv
    // Narrow: null shows the channel list; a channel shows its programmes.
    var selected by remember { mutableStateOf<Int?>(if (narrow) null else watching.index) }
    val shown = selected ?: watching.index
    var programmes by remember { mutableStateOf<List<Programme>?>(null) }
    var nowNext by remember { mutableStateOf<Map<String, NowNext>>(emptyMap()) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (watching.index - 3).coerceAtLeast(0))
    val current = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { current.requestFocus() }
        nowNext = runCatching { session.api.nowNext(channels.map { it.id }) }.getOrDefault(emptyMap())
    }
    LaunchedEffect(shown) {
        programmes = null
        programmes = runCatching { session.api.guide(channels[shown].id) }.getOrDefault(emptyList())
    }

    val channelList: @Composable (Modifier) -> Unit = { size ->
        LazyColumn(size, state = listState) {
            itemsIndexed(channels, key = { _, c -> c.id }) { index, c ->
                val isPlaying = index == watching.index
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (isPlaying) Modifier.focusRequester(current) else Modifier)
                        .focusRing(RoundedCornerShape(0.dp), onFocus = { if (it && !narrow) selected = index }) {
                            if (shown == index && (!narrow || selected != null)) onPick(index) else selected = index
                        }
                        .background(if (index == shown && !narrow) Casa.raised else Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = if (tv) 8.dp else 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.width(3.dp).height(32.dp).background(if (isPlaying) Casa.live else Color.Transparent))
                    Text((index + 1).toString(), color = Casa.muted, fontFamily = CasaFonts.mono, fontSize = 12.sp, modifier = Modifier.width(24.dp))
                    ChannelLogo(c.name, session.logo(c.logo), session.token, 36.dp)
                    Column(Modifier.weight(1f)) {
                        Text(c.name, color = Casa.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(nowNext[c.id.toString()]?.now?.title ?: "", color = Casa.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (c.favorite) Icon(Icons.starFilled, Casa.accent, 14.dp)
                }
            }
        }
    }

    val programmeList: @Composable (Modifier) -> Unit = { size ->
        Column(size) {
            if (shown != watching.index) {
                CasaButton(
                    stringResource(R.string.watch_channel, channels[shown].name),
                    Modifier.padding(12.dp).fillMaxWidth(),
                ) { onPick(shown) }
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
                        if (playing) {
                            p.description?.let { Text(it, color = Casa.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 52.dp, top = 4.dp)) }
                            ProgressBar(progressOf(p), Modifier.padding(start = 52.dp, top = 6.dp).fillMaxWidth())
                        }
                    }
                }
            }
        }
    }

    Column(modifier.background(Casa.bg).border(1.dp, Casa.line)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (narrow && selected != null) {
                Box(Modifier.size(32.dp).focusRing(CircleShape) { selected = null }, contentAlignment = Alignment.Center) {
                    Icon(Icons.back, Casa.muted, 18.dp)
                }
            }
            Text(
                if (narrow && selected != null) channels[shown].name else stringResource(R.string.guide),
                color = Casa.text,
                fontSize = if (tv) 18.sp else 15.sp,
                fontFamily = CasaFonts.display,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                watching.label,
                color = Casa.muted,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier.border(1.dp, Casa.line, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp),
            )
            onClose?.let {
                Box(Modifier.size(32.dp).focusRing(CircleShape, onClick = it), contentAlignment = Alignment.Center) {
                    Icon(Icons.close, Casa.muted, 18.dp)
                }
            }
        }
        if (narrow) {
            if (selected == null) channelList(Modifier.fillMaxSize()) else programmeList(Modifier.fillMaxSize())
        } else {
            Row(Modifier.fillMaxSize()) {
                channelList((if (tv) Modifier.width(250.dp) else Modifier.weight(1f)).fillMaxHeight())
                programmeList(Modifier.weight(1.25f).fillMaxHeight().border(1.dp, Casa.line))
            }
        }
    }
}
