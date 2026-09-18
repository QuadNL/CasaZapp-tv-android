@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package nl.casazapp.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import nl.casazapp.core.store.SourceMode
import nl.casazapp.core.store.UiMode
import nl.casazapp.tv.BuildConfig
import nl.casazapp.tv.R

/** Like the web's Settings: language first, then this device's connection and display. */
@Composable
fun SettingsScreen(
    mode: SourceMode,
    serverUrl: String?,
    locale: String?,
    onLocale: (String?) -> Unit,
    uiMode: UiMode,
    onUiMode: (UiMode) -> Unit,
    onUnpair: () -> Unit,
    onSwitchMode: (SourceMode) -> Unit,
) {
    val compact = LocalForm.current.compact
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.nav_settings), color = Casa.text, fontSize = if (compact) 24.sp else 32.sp, fontFamily = CasaFonts.display, fontWeight = FontWeight.SemiBold)

        Section {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.language), color = Casa.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Automatic follows the device language; NL and EN as on the web.
                    listOf(null to stringResource(R.string.display_auto), "nl" to "NL", "en" to "EN")
                        .forEach { (tag, label) -> Chip(label, locale == tag) { onLocale(tag) } }
                }
            }
        }

        Section {
            if (mode == SourceMode.SERVER) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.server), color = Casa.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(serverUrl ?: "", color = Casa.muted, fontSize = 14.sp)
                    }
                    CasaButton(stringResource(R.string.unpair), primary = false, onClick = onUnpair)
                }
                CasaButton(stringResource(R.string.switch_to_local), primary = false) { onSwitchMode(SourceMode.LOCAL) }
            } else {
                Text(stringResource(R.string.mode_local), color = Casa.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.mode_local_hint), color = Casa.muted, fontSize = 14.sp)
                CasaButton(stringResource(R.string.switch_to_server), primary = false) { onSwitchMode(SourceMode.SERVER) }
            }
        }

        Section {
            Text(stringResource(R.string.display), color = Casa.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.display_hint), color = Casa.muted, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(UiMode.AUTO to R.string.display_auto, UiMode.TV to R.string.display_tv, UiMode.MOBILE to R.string.display_mobile)
                    .forEach { (mode, label) -> Chip(stringResource(label), uiMode == mode) { onUiMode(mode) } }
            }
        }

        Text("${stringResource(R.string.version)} ${BuildConfig.VERSION_NAME}", color = Casa.muted, fontSize = 13.sp)
    }
}

@Composable
fun Section(content: @Composable () -> Unit) {
    Column(
        Modifier.widthIn(max = 640.dp).fillMaxWidth().background(Casa.surface, RoundedCornerShape(16.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}
