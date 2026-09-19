@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package nl.casazapp.tv.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
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

/** How long the OSD stays after the last key or tap. */
private const val OSD_MS = 8_000L

/**
 * The web player. The picture plays straight from the provider; the server only hands out the URL.
 *
 * TV keys: up/down zap; OK shows the OSD and hides it again; with the OSD shown, down reaches its
 * buttons. Right or the guide key opens the guide: the picture shrinks to the top left, the
 * channel's information beside it and the timeline below. Back closes the guide, then the player.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    session: Session,
    watching: Watching,
    onZap: (Int) -> Unit,
    onPlaying: (Int) -> Unit,
    onBack: () -> Unit,
    /** A channel picked from another list in the guide: the player zaps through that list from then on. */
    onSwitch: (Watching) -> Unit,
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
    // Holding OK on the remote opens this; it adds or removes the channel from the favourites.
    var favoriteMenu by remember { mutableStateOf(false) }
    var okHeld by remember { mutableStateOf(false) }
    val form = LocalForm.current
    val compact = form.compact
    val tvLook = form.tv
    val root = remember { FocusRequester() }
    val firstControl = remember { FocusRequester() }
    // 0 = only the picture, 1 = guide fully in; animated both ways.
    val guideShown by animateFloatAsState(if (guideOpen) 1f else 0f, tween(280), label = "guide")

    fun showOsd() {
        osd = true
        osdAt = System.currentTimeMillis()
    }
    fun hideOsd() {
        osd = false
        runCatching { root.requestFocus() }
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
        if (!guideOpen) hideOsd()
    }
    LaunchedEffect(guideOpen) { if (!guideOpen) runCatching { root.requestFocus() } }
    BackHandler { if (guideOpen) guideOpen = false else onBack() }

    // Phones and tablets may turn the screen from the player; leaving it gives the choice back to the sensor.
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(tvLook) {
        onDispose { if (!tvLook) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Turned on its side, the picture gets the whole screen: no status or navigation bar (a swipe
    // from the edge shows them for a moment). Upright and outside the player they stay.
    DisposableEffect(activity, landscape, tvLook) {
        val window = activity?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (!tvLook && landscape) {
            bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            bars?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { bars?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    fun rotate() {
        activity?.requestedOrientation = if (landscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        showOsd()
    }

    val osdPanel: @Composable (Modifier, Boolean) -> Unit = { modifier, overlay ->
        Osd(
            number = watching.index + 1,
            channel = channel,
            detail = detail,
            guide = guide,
            session = session,
            favorite = favorite,
            volume = volume,
            firstControl = firstControl,
            overlay = overlay,
            onGuide = { guideOpen = !guideOpen; showOsd() },
            onFavorite = {
                favorite = !favorite
                val value = favorite
                scope.launch { runCatching { session.api.setFavorite(channel.id, value) } }
                showOsd()
            },
            onVolume = if (tvLook) null else { v: Float ->
                volume = v
                player.volume = v
                showOsd()
            },
            onRotate = if (tvLook) null else ::rotate,
            onLeave = { hideOsd() },
            modifier = modifier,
        )
    }

    val stage: @Composable (Modifier) -> Unit = { size -> Box(
            size
                .background(Color.Black)
                // Touch only: on a TV, clickable would treat OK's key-up as a second press and hide the OSD again.
                .then(
                    if (tvLook) {
                        Modifier
                    } else {
                        Modifier.clickable(interactionSource = null, indication = null) {
                            // With the guide beside it, a tap on the small picture makes it big again.
                            when {
                                guideOpen && !compact -> guideOpen = false
                                osd -> hideOsd()
                                else -> showOsd()
                            }
                        }
                    },
                )
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
                    if (guideOpen || favoriteMenu) return@onPreviewKeyEvent false
                    val code = event.key.nativeKeyCode
                    val ok = code == AndroidKeyEvent.KEYCODE_DPAD_CENTER || code == AndroidKeyEvent.KEYCODE_ENTER
                    if (ok) {
                        // OK acts on release, so holding it can open the favourite pop-up instead.
                        when {
                            event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0 -> okHeld = false
                            event.type == KeyEventType.KeyDown && (event.nativeKeyEvent.isLongPress || event.nativeKeyEvent.repeatCount >= 6) && !okHeld -> {
                                okHeld = true
                                favoriteMenu = true
                            }
                            event.type == KeyEventType.KeyUp && !okHeld -> if (osd) hideOsd() else showOsd()
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (code) {
                        // Up and down always zap, with or without the OSD.
                        AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_CHANNEL_UP -> zap(-1)
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN, AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> zap(1)
                        AndroidKeyEvent.KEYCODE_GUIDE, AndroidKeyEvent.KEYCODE_MENU, AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> guideOpen = true
                        AndroidKeyEvent.KEYCODE_INFO -> if (osd) hideOsd() else showOsd()
                        else -> return@onPreviewKeyEvent false
                    }
                    true
                },
        ) {
            // A phone held upright: the OSD lives inside the picture, which sits in the middle of the screen.
            val inPicture = compact
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(if (inPicture) Modifier.fillMaxWidth().aspectRatio(16f / 9f) else Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx -> PlayerView(ctx).apply { useController = false; this.player = player } },
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (inPicture && osd) {
                        PictureOsd(
                            number = watching.index + 1,
                            channel = channel,
                            guide = guide,
                            session = session,
                            favorite = favorite,
                            volume = volume,
                            onFavorite = {
                                favorite = !favorite
                                val value = favorite
                                scope.launch { runCatching { session.api.setFavorite(channel.id, value) } }
                                showOsd()
                            },
                            onVolume = { v ->
                                volume = v
                                player.volume = v
                                showOsd()
                            },
                            onRotate = ::rotate,
                        )
                    }
                }
            }

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

            // With the guide open on a TV, the small picture's own information would only get in the way.
            if (osd && !(guideOpen && !compact)) {
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent)))
                        .padding(horizontal = if (tvLook) 32.dp else 16.dp, vertical = if (tvLook) 24.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (!tvLook) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                            Icon(Icons.back, Color.White, 20.dp)
                        }
                    }
                    Text(channel.name, color = Color.White, fontSize = if (tvLook) 18.sp else 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                // Zap arrows for touch; a remote zaps with up and down.
                if (!tvLook && !inPicture) {
                    Column(
                        Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        listOf(Icons.up to -1, Icons.down to 1).forEach { (icon, step) ->
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable { zap(step) }
                                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { Icon(icon, Color.White, 20.dp) }
                        }
                    }
                }

                if (!inPicture) osdPanel(Modifier.align(Alignment.BottomStart), true)
            }
        } }

    if (favoriteMenu) {
        FavoritePrompt(
            channel = channel,
            favorite = favorite,
            onToggle = {
                favorite = !favorite
                val value = favorite
                scope.launch { runCatching { session.api.setFavorite(channel.id, value) } }
                favoriteMenu = false
                showOsd()
            },
            onClose = { favoriteMenu = false },
        )
    }

    if (!compact) {
        // The TV guide, also on a phone on its side and a tablet: the picture shrinks to the top left,
        // the channel beside it, and below the timeline with the same chips as Live TV.
        BoxWithConstraints(Modifier.fillMaxSize().background(Casa.bg)) {
            val small = 0.3f
            val scale = 1f - (1f - small) * guideShown
            val pad = (if (tvLook) 32.dp else 12.dp) * guideShown
            val w = maxWidth * scale
            val h = maxHeight * scale
            val fullWidth = maxWidth
            val fullHeight = maxHeight
            if (guideShown > 0f) {
                Column(
                    Modifier
                        .offset(x = w + pad * 2, y = pad)
                        .width(fullWidth - w - pad * 3)
                        .height(h)
                        .alpha(guideShown),
                    verticalArrangement = Arrangement.Center,
                ) {
                    ChannelInfo(watching.index + 1, channel, detail, guide, session, trailing = {
                        if (!tvLook) {
                            Box(Modifier.size(40.dp).focusRing(CircleShape) { guideOpen = false }, contentAlignment = Alignment.Center) {
                                Icon(Icons.close, Casa.muted, 22.dp)
                            }
                        }
                    })
                }
                Column(
                    Modifier
                        .offset(x = pad, y = h + pad * 1.5f)
                        .width(fullWidth - pad * 2)
                        .height(fullHeight - h - pad * 2.5f)
                        .alpha(guideShown),
                ) {
                    if (guideOpen || guideShown > 0.5f) {
                        val filter = rememberChannelFilter(session, channels, watching.label)
                        val label = filter.label()
                        FilterChips(filter, session, Modifier.padding(bottom = 10.dp))
                        val list = filter.channels
                        if (list == null) {
                            Text(stringResource(R.string.loading), color = Casa.muted)
                        } else {
                            val here = list.indexOfFirst { it.id == channel.id }.takeIf { it >= 0 }
                            LiveTimeline(
                                session,
                                list,
                                focusIndex = here ?: 0,
                                onNearEnd = { scope.launch { filter.loadMore(session) } },
                            ) { index ->
                                guideOpen = false
                                when {
                                    list === channels -> if (index != watching.index) onZap(index)
                                    else -> onSwitch(Watching(list, index, label, filter.context()))
                                }
                            }
                        }
                    }
                }
            }
            stage(Modifier.offset(x = pad, y = pad).size(w, h).clip(RoundedCornerShape(12.dp * guideShown)))
        }
        return
    }

    // A phone held upright: the guide slides in below the picture.
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
        val guideHeight = maxHeight * 0.63f * guideShown
        val stageHeight = maxHeight - guideHeight
        Column {
            stage(Modifier.fillMaxWidth().height(stageHeight))
            if (guideShown > 0f) {
                GuidePanel(
                    session = session,
                    watching = watching,
                    onPick = { index -> onZap(index) },
                    onSwitch = { guideOpen = false; onSwitch(it) },
                    onClose = { guideOpen = false },
                    narrow = true,
                    modifier = Modifier.fillMaxWidth().height(guideHeight),
                )
            }
        }
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Held OK on the remote: add the channel to the favourites or take it off. */
@Composable
private fun FavoritePrompt(channel: Channel, favorite: Boolean, onToggle: () -> Unit, onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.background(Casa.surface, RoundedCornerShape(16.dp)).border(1.dp, Casa.line, RoundedCornerShape(16.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(channel.name, color = Casa.text, fontSize = 20.sp, fontFamily = CasaFonts.display, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CasaButton(
                    stringResource(if (favorite) R.string.favorite_remove else R.string.favorite_add),
                    Modifier.focusRequester(first),
                    onClick = onToggle,
                )
                CasaButton(stringResource(R.string.exit_stay), primary = false, onClick = onClose)
            }
        }
    }
}

/** Number, logo, category with LIVE, name, then now and next: the heart of the OSD and the TV guide. */
@Composable
private fun ChannelInfo(number: Int, channel: Channel, detail: ChannelDetail?, guide: NowNext?, session: Session, trailing: @Composable () -> Unit = {}) {
    val tv = LocalForm.current.tv
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (tv) 18.dp else 12.dp)) {
            Text(number.toString(), color = Casa.accent, fontSize = if (tv) 40.sp else 26.sp, fontFamily = CasaFonts.mono)
            ChannelLogo(channel.name, session.logo(channel.logo), session.token, if (tv) 60.dp else 44.dp)
            Column(Modifier.weight(1f)) {
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
            trailing()
        }
        Spacer(Modifier.height(if (tv) 16.dp else 10.dp))
        Column((if (tv) Modifier.width(720.dp) else Modifier.fillMaxWidth()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GuideLine(stringResource(R.string.now), guide?.now, strong = true)
            guide?.now?.let { ProgressBar(progressOf(it), Modifier.padding(start = if (tv) 64.dp else 48.dp).fillMaxWidth()) }
            GuideLine(stringResource(R.string.next), guide?.next, strong = false)
        }
    }
}

/**
 * The web player's on-screen display. Upright on a phone the buttons sit above the channel; wider,
 * they sit at the end of the channel's row, level with its name, like the web on a desktop.
 */
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
    overlay: Boolean,
    onGuide: () -> Unit,
    onFavorite: () -> Unit,
    onVolume: ((Float) -> Unit)?,
    onRotate: (() -> Unit)?,
    onLeave: () -> Unit,
    modifier: Modifier,
) {
    val form = LocalForm.current
    val tv = form.tv
    val controls: @Composable () -> Unit = {
        Row(
            // Up or back from the buttons returns to the picture.
            Modifier.onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && (it.key == Key.DirectionUp || it.key == Key.Back)) {
                    onLeave(); true
                } else {
                    false
                }
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onVolume != null) VolumeControl(volume, onVolume)
            onRotate?.let { OsdButton(Icons.rotate, Color.White, Modifier, it) }
            OsdButton(if (favorite) Icons.starFilled else Icons.star, if (favorite) Casa.accent else Color.White, Modifier.focusRequester(firstControl), onFavorite)
            OsdButton(Icons.guide, Casa.accent, Modifier, onGuide)
        }
    }
    val sideBySide = !form.compact
    Column(
        modifier
            .fillMaxWidth()
            .then(if (overlay) Modifier.background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000), Color(0xE6000000)))) else Modifier)
            .then(
                when {
                    tv -> Modifier.padding(start = 48.dp, end = 48.dp, top = 80.dp, bottom = 36.dp)
                    overlay -> Modifier.padding(start = 20.dp, end = 12.dp, top = 48.dp, bottom = 16.dp).systemBarsPadding()
                    else -> Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
                },
            ),
    ) {
        if (!sideBySide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { controls() }
        ChannelInfo(number, channel, detail, guide, session, trailing = {
            when {
                tv -> if (favorite) Icon(Icons.starFilled, Casa.accent, 32.dp)
                sideBySide -> controls()
            }
        })
        if (tv) Text(stringResource(R.string.player_hint), color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

/**
 * The OSD inside the picture of a phone held upright: favourite in the top right corner; at the
 * bottom the channel (its name cut off when long), what is on, and volume and rotate beside it.
 */
@Composable
private fun PictureOsd(
    number: Int,
    channel: Channel,
    guide: NowNext?,
    session: Session,
    favorite: Boolean,
    volume: Float,
    onFavorite: () -> Unit,
    onVolume: (Float) -> Unit,
    onRotate: () -> Unit,
) {
    var sliderOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
            OsdButton(if (favorite) Icons.starFilled else Icons.star, if (favorite) Casa.accent else Color.White, Modifier, onFavorite)
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                .padding(start = 12.dp, end = 4.dp, top = 24.dp, bottom = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(number.toString(), color = Casa.accent, fontSize = 18.sp, fontFamily = CasaFonts.mono)
                ChannelLogo(channel.name, session.logo(channel.logo), session.token, 30.dp)
                Column(Modifier.weight(1f)) {
                    Text(channel.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    guide?.now?.let { Text(it.title, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                OsdButton(if (volume == 0f) Icons.muted else Icons.volume, Color.White, Modifier) { sliderOpen = !sliderOpen }
                OsdButton(Icons.rotate, Color.White, Modifier, onRotate)
            }
            guide?.now?.let { ProgressBar(progressOf(it), Modifier.padding(top = 4.dp, end = 8.dp).fillMaxWidth()) }
        }
        if (sliderOpen) {
            VerticalVolume(volume, onVolume, Modifier.align(Alignment.BottomEnd).padding(end = 50.dp, bottom = 58.dp))
        }
    }
}

/** A vertical volume bar that opens from the speaker icon; drag or tap along it. */
@Composable
private fun VerticalVolume(volume: Float, onChange: (Float) -> Unit, modifier: Modifier) {
    var heightPx by remember { mutableFloatStateOf(1f) }
    Box(
        modifier
            .width(36.dp)
            .height(110.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) { detectTapGestures { onChange((1f - it.y / heightPx).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, _ -> onChange((1f - change.position.y / heightPx).coerceIn(0f, 1f)) }
            }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.25f)))
        Box(Modifier.width(4.dp).fillMaxHeight(volume).clip(RoundedCornerShape(2.dp)).background(Casa.accent))
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
 * Volume like the web player: mute button and a bar. With [onChange] null (a TV) it only shows the
 * TV's own volume and mute, which the remote controls.
 */
@Composable
private fun VolumeControl(volume: Float, onChange: ((Float) -> Unit)?) {
    val tv = LocalForm.current.tv
    var before by remember { mutableFloatStateOf(1f) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        val icon = if (volume == 0f) Icons.muted else Icons.volume
        if (onChange == null) {
            Box(Modifier.padding(2.dp).size(if (tv) 52.dp else 42.dp), contentAlignment = Alignment.Center) {
                Icon(icon, Color.White, if (tv) 28.dp else 22.dp)
            }
        } else {
            OsdButton(icon, Color.White, Modifier) {
                if (volume == 0f) {
                    onChange(before.takeIf { it > 0f } ?: 1f)
                } else {
                    before = volume
                    onChange(0f)
                }
            }
        }
        var focused by remember { mutableStateOf(false) }
        var widthPx by remember { mutableFloatStateOf(1f) }
        Box(
            Modifier
                .width(if (tv) 140.dp else 88.dp)
                .height(28.dp)
                .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
                .then(
                    if (onChange == null) {
                        Modifier
                    } else {
                        Modifier
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
                            .pointerInput(Unit) { detectTapGestures { onChange((it.x / widthPx).coerceIn(0f, 1f)) } }
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures { change, _ -> onChange((change.position.x / widthPx).coerceIn(0f, 1f)) }
                            }
                    },
                ),
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
 * The web player's guide on phones and tablets. Wide: channels below each other with the
 * programmes of the selected one beside them. Narrow (phone upright): the channels; tap one for its
 * programmes. Picking the selected channel again switches to it.
 */
@Composable
private fun GuidePanel(
    session: Session,
    watching: Watching,
    onPick: (Int) -> Unit,
    /** A channel from another list than the one being watched, picked with the chips. */
    onSwitch: (Watching) -> Unit,
    modifier: Modifier,
    narrow: Boolean = false,
    onClose: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    // The same chips as Live TV and the TV guide: your list, favourites, categories.
    val filter = rememberChannelFilter(session, watching.channels, watching.label)
    val label = filter.label()
    val channels = filter.channels ?: watching.channels
    val playingId = watching.channels[watching.index].id
    val playingIndex = channels.indexOfFirst { it.id == playingId }
    // Narrow: null shows the channel list; a channel shows its programmes.
    var selected by remember { mutableStateOf<Int?>(if (narrow) null else watching.index) }
    LaunchedEffect(channels) { if (narrow) selected = null }
    val shown = (selected ?: playingIndex).coerceIn(0, (channels.size - 1).coerceAtLeast(0))
    var programmes by remember { mutableStateOf<List<Programme>?>(null) }
    val nowNext = filter.guide
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (watching.index - 3).coerceAtLeast(0))
    fun pick(index: Int) {
        if (channels === watching.channels) onPick(index) else onSwitch(Watching(channels, index, label, filter.context()))
    }

    LaunchedEffect(shown, channels) {
        programmes = null
        val channel = channels.getOrNull(shown) ?: return@LaunchedEffect
        programmes = runCatching { session.api.guide(channel.id) }.getOrDefault(emptyList())
    }

    val channelList: @Composable (Modifier) -> Unit = { size ->
        LazyColumn(size, state = listState) {
            itemsIndexed(channels, key = { _, c -> c.id }) { index, c ->
                if (index >= channels.size - 20) LaunchedEffect(channels.size) { filter.loadMore(session) }
                val isPlaying = index == playingIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .focusRing(RoundedCornerShape(0.dp)) {
                            if (shown == index && (!narrow || selected != null)) pick(index) else selected = index
                        }
                        .background(if (index == shown && !narrow) Casa.raised else Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
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
            if (shown != playingIndex && channels.isNotEmpty()) {
                CasaButton(
                    stringResource(R.string.watch_channel, channels[shown].name),
                    Modifier.padding(12.dp).fillMaxWidth(),
                ) { pick(shown) }
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
                if (narrow && selected != null) channels.getOrNull(shown)?.name ?: "" else stringResource(R.string.guide),
                color = Casa.text,
                fontSize = 15.sp,
                fontFamily = CasaFonts.display,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            onClose?.let {
                Box(Modifier.size(32.dp).focusRing(CircleShape, onClick = it), contentAlignment = Alignment.Center) {
                    Icon(Icons.close, Casa.muted, 18.dp)
                }
            }
        }
        if (selected == null || !narrow) FilterChips(filter, session, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp))
        if (narrow) {
            if (selected == null) channelList(Modifier.fillMaxSize()) else programmeList(Modifier.fillMaxSize())
        } else {
            Row(Modifier.fillMaxSize()) {
                channelList(Modifier.weight(1f).fillMaxHeight())
                programmeList(Modifier.weight(1.25f).fillMaxHeight().border(1.dp, Casa.line))
            }
        }
    }
}
