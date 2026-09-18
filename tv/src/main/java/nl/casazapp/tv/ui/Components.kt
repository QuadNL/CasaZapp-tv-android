@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package nl.casazapp.tv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import nl.casazapp.core.api.Programme

/** The web app's icons (see Design.kt), drawn as 24×24 strokes. */
object Icons {
    private fun icon(d: String, filled: Boolean = false): ImageVector =
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = if (filled) SolidColor(Color.White) else null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()

    val home = icon(IconPaths.home)
    val tv = icon(IconPaths.tv)
    val settings = icon(IconPaths.settings)
    val back = icon(IconPaths.back)
    val up = icon(IconPaths.up)
    val down = icon(IconPaths.down)
    val star = icon(IconPaths.star)
    val starFilled = icon(IconPaths.star, filled = true)
    val guide = icon(IconPaths.guide)
    val play = icon(IconPaths.play, filled = true)
    val volume = icon(IconPaths.volume)
    val muted = icon(IconPaths.muted)
}

@Composable
fun Icon(vector: ImageVector, tint: Color = Casa.text, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Image(vector, contentDescription = null, colorFilter = ColorFilter.tint(tint), modifier = modifier.size(size))
}

/** Something that can take D-pad focus and shows it with the accent ring, like `:focus-visible` on the web. */
@Composable
fun Modifier.focusRing(
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    onFocus: (Boolean) -> Unit = {},
    onClick: () -> Unit,
): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .clip(shape)
        .background(if (focused) Casa.raised else Color.Transparent)
        .border(2.dp, if (focused) Casa.accent else Color.Transparent, shape)
        .onFocusChanged {
            focused = it.isFocused
            onFocus(it.isFocused)
        }
        // OK on the remote; handled here so it works the same inside the player's key handling.
        .onKeyEvent {
            val ok = it.key == Key.DirectionCenter || it.key == Key.Enter || it.key == Key.NumPadEnter
            if (ok && it.type == KeyEventType.KeyDown) {
                onClick()
                true
            } else {
                ok
            }
        }
        .clickable(onClick = onClick)
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .focusRing(RoundedCornerShape(50), onClick = onClick)
            .background(if (selected) Casa.text else Color.Transparent, RoundedCornerShape(50))
            .border(1.dp, Casa.line, RoundedCornerShape(50))
            .padding(horizontal = 18.dp, vertical = 9.dp),
    ) {
        Text(
            label,
            color = if (selected) Casa.bg else Casa.muted,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

private fun hueOf(text: String) = text.fold(7) { h, c -> (h * 31 + c.code) % 360 }

/** Channel logo with coloured initials underneath, like the web app: provider logos are often missing. */
@Composable
fun ChannelLogo(name: String, logoUrl: String?, token: String, size: Dp = 48.dp) {
    val context = LocalContext.current
    val initials = name
        .replace(Regex("^[A-Z]{2,3}\\s*[|:]\\s*"), "")
        .replace(Regex("\\b(HD|FHD|UHD|4K|SD)\\b", RegexOption.IGNORE_CASE), "")
        .trim()
        .split(Regex("\\s+"))
        .mapNotNull { it.firstOrNull() }
        .joinToString("")
        .take(3)
        .uppercase()
    val hue = hueOf(name).toFloat()
    Box(
        Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(Color.hsl(hue, 0.35f, 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = Color.hsl(hue, 0.8f, 0.75f), fontSize = (size.value / 4).sp, fontWeight = FontWeight.Bold)
        if (logoUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(logoUrl)
                    .httpHeaders(NetworkHeaders.Builder().set("Authorization", "Bearer $token").build())
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().background(Color.Transparent).padding(4.dp),
            )
        }
    }
}

@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.height(4.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.15f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(Casa.accent))
    }
}

private val clock = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

fun time(iso: String): String = runCatching { clock.format(Instant.parse(iso)) }.getOrDefault("")

fun progressOf(p: Programme, now: Long = System.currentTimeMillis()): Float = runCatching {
    val start = Instant.parse(p.start).toEpochMilli()
    val stop = Instant.parse(p.stop).toEpochMilli()
    ((now - start).toFloat() / (stop - start)).coerceIn(0f, 1f)
}.getOrDefault(0f)
