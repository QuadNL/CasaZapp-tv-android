@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package nl.casazapp.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import nl.casazapp.tv.R
import nl.casazapp.tv.update.Updater

/** "A new build is available" above every screen, with the button that installs it. */
@Composable
fun UpdateBanner(release: Updater.Release, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Float?>(null) }
    var failed by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .background(Casa.surface, RoundedCornerShape(12.dp))
            .border(1.dp, Casa.accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.update_available, release.name), color = Casa.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            val p = progress
            Text(
                when {
                    failed -> stringResource(R.string.update_failed)
                    p != null -> stringResource(R.string.update_downloading, (p * 100).toInt())
                    else -> stringResource(R.string.update_hint)
                },
                color = if (failed) Casa.live else Casa.muted,
                fontSize = 13.sp,
            )
        }
        CasaButton(stringResource(R.string.update_install), enabled = progress == null || failed) {
            failed = false
            progress = 0f
            scope.launch {
                runCatching { Updater.install(context.applicationContext, release) { progress = it } }
                    .onFailure { failed = true }
            }
        }
    }
}
