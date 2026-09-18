@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package nl.casazapp.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import nl.casazapp.tv.BuildConfig
import nl.casazapp.tv.R

@Composable
fun SettingsScreen(serverUrl: String, onUnpair: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(stringResource(R.string.nav_settings), color = Casa.text, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
        Column(
            Modifier.width(640.dp).background(Casa.surface, RoundedCornerShape(16.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.server), color = Casa.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(serverUrl, color = Casa.muted, fontSize = 15.sp)
                }
                Button(onClick = onUnpair) { Text(stringResource(R.string.unpair)) }
            }
            Text("${stringResource(R.string.version)} ${BuildConfig.VERSION_NAME}", color = Casa.muted, fontSize = 14.sp)
        }
    }
}
