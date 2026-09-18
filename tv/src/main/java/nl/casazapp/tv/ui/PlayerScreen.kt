package nl.casazapp.tv.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import nl.casazapp.core.api.CasaZappApi
import nl.casazapp.core.api.NowNext
import nl.casazapp.tv.R

private const val OSD_MS = 4_000L

/**
 * Full-screen playback straight from the provider (the server only hands out the URL).
 * Up/down and the channel keys zap through the list the user came from; OK shows the OSD.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    api: CasaZappApi,
    watching: Watching,
    onZap: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val channels = watching.channels
    val channel = channels[watching.index]
    val player = remember { ExoPlayer.Builder(context).build() }
    var failed by remember { mutableStateOf(false) }
    var osd by remember { mutableStateOf(true) }
    var osdAt by remember { mutableStateOf(0L) }
    var guide by remember { mutableStateOf<NowNext?>(null) }
    val focus = remember { FocusRequester() }

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
        osd = true
        osdAt = System.currentTimeMillis()
        try {
            val stream = api.stream(channel.id)
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
        } catch (e: Exception) {
            failed = true
        }
        guide = runCatching { api.nowNext(listOf(channel.id))[channel.id.toString()] }.getOrNull()
    }

    LaunchedEffect(osdAt) {
        delay(OSD_MS)
        osd = false
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BackHandler(onBack = onBack)

    fun zap(step: Int) {
        onZap((watching.index + step + channels.size) % channels.size)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key.nativeKeyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_CHANNEL_UP -> zap(-1)
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN, AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> zap(1)
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_INFO -> {
                        osd = true
                        osdAt = System.currentTimeMillis()
                    }
                    else -> return@onKeyEvent false
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
                fontSize = 20.sp,
                modifier = Modifier.align(Alignment.Center).background(Casa.surface).padding(20.dp),
            )
        }

        if (osd) {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                    .padding(horizontal = 56.dp, vertical = 40.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text(
                        (watching.index + 1).toString(),
                        color = Casa.accent,
                        fontSize = 40.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(channel.name, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
                }
                guide?.now?.let { Text("${stringResource(R.string.now)}  ${it.title}", color = Color.White, fontSize = 20.sp) }
                guide?.next?.let { Text("${stringResource(R.string.next)}  ${it.title}", color = Casa.muted, fontSize = 18.sp) }
            }
        }
    }
}
