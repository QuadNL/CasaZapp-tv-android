@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package nl.casazapp.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import nl.casazapp.core.store.UiMode
import nl.casazapp.tv.BuildConfig
import nl.casazapp.tv.R

@Composable
fun SettingsScreen(serverUrl: String, uiMode: UiMode, onUiMode: (UiMode) -> Unit, onUnpair: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(stringResource(R.string.nav_settings), color = Casa.text, fontSize = 32.sp, fontFamily = CasaFonts.display, fontWeight = FontWeight.SemiBold)
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().background(Casa.surface, RoundedCornerShape(16.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.server), color = Casa.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(serverUrl, color = Casa.muted, fontSize = 15.sp)
                }
                CasaButton(stringResource(R.string.unpair), primary = false, onClick = onUnpair)
            }
            Text(stringResource(R.string.display), color = Casa.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.display_hint), color = Casa.muted, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(UiMode.AUTO to R.string.display_auto, UiMode.TV to R.string.display_tv, UiMode.MOBILE to R.string.display_mobile)
                    .forEach { (mode, label) -> Chip(stringResource(label), uiMode == mode) { onUiMode(mode) } }
            }
            Text("${stringResource(R.string.version)} ${BuildConfig.VERSION_NAME}", color = Casa.muted, fontSize = 14.sp)
        }
    }
}
