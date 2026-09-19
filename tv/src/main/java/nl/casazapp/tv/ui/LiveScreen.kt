@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package nl.casazapp.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import nl.casazapp.core.api.Category
import nl.casazapp.core.api.Channel
import nl.casazapp.core.api.ChannelList
import nl.casazapp.core.api.NowNext
import nl.casazapp.tv.R

/** Live TV like the web app: chips for your lists and categories, channels as a timeline or a list. */
@Composable
fun LiveScreen(session: Session, onWatch: (Watching) -> Unit) {
    val state = rememberChannelFilter(session)
    val scope = rememberCoroutineScope()
    val form = LocalForm.current
    var timeline by rememberSaveable { mutableStateOf(true) }
    val label = state.label()
    val switch: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(stringResource(R.string.timeline), timeline) { timeline = true }
            Chip(stringResource(R.string.list), !timeline) { timeline = false }
        }
    }

    Column {
        if (form.phone && !form.compact) {
            // A phone on its side has little height: title, switch and chips share one line.
            Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.nav_live), color = Casa.text, fontSize = 20.sp, fontFamily = CasaFonts.display, fontWeight = FontWeight.SemiBold)
                FilterChips(state, session, Modifier.padding(start = 16.dp).weight(1f)) { item { switch() } }
            }
        } else {
            Text(stringResource(R.string.nav_live), color = Casa.text, fontSize = if (form.compact) 24.sp else 32.sp, fontFamily = CasaFonts.display, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                state.channels?.let {
                    Text(stringResource(R.string.channel_count, state.total), color = Casa.muted, fontSize = 15.sp, modifier = Modifier.weight(1f))
                }
                // Timeline or list, like the switch on the web.
                switch()
            }
            FilterChips(state, session, Modifier.padding(vertical = 18.dp))
        }

        val current = state.channels
        when {
            state.failed -> Text(stringResource(R.string.connect_failed), color = Casa.live)
            current == null -> Text(stringResource(R.string.loading), color = Casa.muted)
            timeline -> LiveTimeline(session, current, onNearEnd = { scope.launch { state.loadMore(session) } }) { index ->
                onWatch(Watching(current, index, label, state.context()))
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(current, key = { _, c -> c.id }) { index, channel ->
                    if (index >= current.size - 20) LaunchedEffect(current.size) { state.loadMore(session) }
                    ChannelRow(index + 1, channel, state.guide[channel.id.toString()], session) {
                        onWatch(Watching(current, index, label, state.context()))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(number: Int, channel: Channel, info: NowNext?, session: Session, onClick: () -> Unit) {
    if (LocalForm.current.compact) {
        Row(
            Modifier.fillMaxWidth().focusRing(onClick = onClick).padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChannelLogo(channel.name, session.logo(channel.logo), session.token, 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(channel.name, color = Casa.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val now = info?.now
                if (now != null) {
                    Text(now.title, color = Casa.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ProgressBar(progressOf(now), Modifier.fillMaxWidth())
                }
            }
        }
        return
    }
    Row(
        Modifier.fillMaxWidth().focusRing(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(number.toString(), color = Casa.muted, fontFamily = CasaFonts.mono, fontSize = 15.sp, modifier = Modifier.width(40.dp))
        ChannelLogo(channel.name, session.logo(channel.logo), session.token, 52.dp)
        Text(
            channel.name,
            color = Casa.text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(300.dp),
        )
        Column(Modifier.weight(1f)) {
            val now = info?.now
            if (now == null) {
                Text(stringResource(R.string.no_guide), color = Casa.muted.copy(alpha = 0.5f), fontSize = 14.sp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(now.title, color = Casa.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Text("${time(now.start)}–${time(now.stop)}", color = Casa.muted, fontFamily = CasaFonts.mono, fontSize = 12.sp)
                }
                Spacer(Modifier.height(5.dp))
                ProgressBar(progressOf(now), Modifier.width(260.dp))
                info.next?.let {
                    Spacer(Modifier.height(4.dp))
                    Text("${stringResource(R.string.next)} ${time(it.start)} · ${it.title}", color = Casa.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
