@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package nl.casazapp.tv.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import nl.casazapp.core.api.Channel
import nl.casazapp.core.api.Programme
import nl.casazapp.tv.R

private const val HOURS = 6
private const val SLOT_MIN = 30

/** Start of the window: the half hour before the current one, so what is on now starts on screen. */
private fun windowStart(): Instant {
    val slot = SLOT_MIN * 60_000L
    return Instant.ofEpochMilli(System.currentTimeMillis() / slot * slot - slot)
}

/**
 * Live TV as a timeline, like the web (#17): a row per channel, programmes as blocks along the
 * hours, a line at the current time. All rows scroll sideways together. OK or a tap plays the
 * channel; on a TV, left and right move through the hours.
 */
@Composable
fun LiveTimeline(session: Session, channels: List<Channel>, focusIndex: Int? = null, onPlay: (Int) -> Unit) {
    val form = LocalForm.current
    val scope = rememberCoroutineScope()
    val from = remember { windowStart() }
    var grid by remember { mutableStateOf<Map<String, List<Programme>>?>(null) }
    val scroll = rememberScrollState()
    val rows = rememberLazyListState(initialFirstVisibleItemIndex = ((focusIndex ?: 0) - 2).coerceAtLeast(0))
    val focusRow = remember { FocusRequester() }
    // In the player's guide, the channel you are watching has focus, as on a TV provider's guide.
    LaunchedEffect(focusIndex) {
        if (focusIndex == null) return@LaunchedEffect
        repeat(10) {
            if (runCatching { focusRow.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }
    val perMin: Dp = if (form.tv) 4.dp else 2.6.dp
    val labelWidth: Dp = if (form.compact) 56.dp else 220.dp
    val rowHeight: Dp = if (form.tv) 68.dp else 58.dp
    val slotPx = with(LocalDensity.current) { (perMin * SLOT_MIN).toPx() }

    LaunchedEffect(channels) {
        grid = null
        grid = runCatching { session.api.grid(channels.map { it.id }, from, HOURS) }.getOrDefault(emptyMap())
    }

    fun x(at: Instant): Dp = perMin * ((at.toEpochMilli() - from.toEpochMilli()) / 60_000f)
    val width = perMin * (HOURS * 60f)
    val now = Instant.now()

    Column(Modifier.clip(RoundedCornerShape(16.dp)).border(1.dp, Casa.line, RoundedCornerShape(16.dp)).background(Casa.surface)) {
        // Hours
        Row(Modifier.height(32.dp)) {
            Box(Modifier.width(labelWidth).fillMaxHeight())
            Row(Modifier.horizontalScroll(scroll)) {
                Box(Modifier.width(width).fillMaxHeight()) {
                    repeat(HOURS * 60 / SLOT_MIN) { i ->
                        val at = from.plusSeconds(i * SLOT_MIN * 60L)
                        Text(
                            time(at.toString()),
                            color = Casa.muted,
                            fontFamily = CasaFonts.mono,
                            fontSize = 11.sp,
                            modifier = Modifier.offset(x = x(at) + 6.dp, y = 8.dp),
                        )
                    }
                    NowLine(x(now), width)
                }
            }
        }
        LazyColumn(state = rows) {
            itemsIndexed(channels, key = { _, c -> c.id }) { index, c ->
                TimelineRow(
                    session = session,
                    channel = c,
                    programmes = grid?.get(c.id.toString()),
                    loaded = grid != null,
                    scroll = scroll,
                    labelWidth = labelWidth,
                    height = rowHeight,
                    width = width,
                    x = ::x,
                    now = now,
                    modifier = if (index == focusIndex) Modifier.focusRequester(focusRow) else Modifier,
                    onPlay = { onPlay(index) },
                    onScroll = { step -> scope.launch { scroll.animateScrollBy(step * slotPx) } },
                )
            }
        }
    }
}

@Composable
private fun NowLine(at: Dp, width: Dp) {
    if (at < 0.dp || at > width) return
    Box(Modifier.offset(x = at).width(2.dp).fillMaxHeight().background(Casa.live))
}

@Composable
private fun TimelineRow(
    session: Session,
    channel: Channel,
    programmes: List<Programme>?,
    loaded: Boolean,
    scroll: ScrollState,
    labelWidth: Dp,
    height: Dp,
    width: Dp,
    x: (Instant) -> Dp,
    now: Instant,
    onPlay: () -> Unit,
    onScroll: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val form = LocalForm.current
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .onPreviewKeyEvent {
                if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (it.key) {
                    Key.DirectionRight -> { onScroll(1); true }
                    Key.DirectionLeft -> if (scroll.value > 0) { onScroll(-1); true } else false
                    else -> false
                }
            }
            .focusRing(RoundedCornerShape(0.dp), onClick = onPlay)
            .border(0.5.dp, Casa.line),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.width(labelWidth).fillMaxHeight().background(Casa.surface).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelLogo(channel.name, session.logo(channel.logo), session.token, if (form.tv) 44.dp else 38.dp)
            if (!form.compact) {
                Text(
                    channel.name,
                    color = Casa.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
        Row(Modifier.horizontalScroll(scroll)) {
            Box(Modifier.width(width).fillMaxHeight()) {
                if (loaded && programmes.isNullOrEmpty()) {
                    Text(
                        stringResource(R.string.no_guide),
                        color = Casa.muted.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp),
                    )
                }
                programmes.orEmpty().forEach { p ->
                    val start = Instant.parse(p.start)
                    val stop = Instant.parse(p.stop)
                    val left = maxOf(0.dp, x(start))
                    val right = minOf(width, x(stop))
                    if (right - left < 4.dp) return@forEach
                    val playing = start <= now && stop > now
                    Column(
                        Modifier
                            .offset(x = left + 2.dp)
                            .width(right - left - 4.dp)
                            .padding(vertical = 4.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (playing) Casa.raised else Casa.bg.copy(alpha = 0.4f))
                            .border(1.dp, if (playing) Casa.accent.copy(alpha = 0.6f) else Casa.line, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    ) {
                        Text(p.title, color = Casa.text, fontSize = 13.sp, fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${time(p.start)}–${time(p.stop)}", color = Casa.muted, fontFamily = CasaFonts.mono, fontSize = 10.sp, maxLines = 1)
                    }
                }
                val nowX = x(now)
                if (nowX in 0.dp..width) Box(Modifier.offset(x = nowX).width(2.dp).fillMaxHeight().background(Casa.live))
            }
        }
    }
}
