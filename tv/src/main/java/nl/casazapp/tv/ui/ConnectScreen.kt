@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package nl.casazapp.tv.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import nl.casazapp.core.api.CasaZappApi
import nl.casazapp.core.store.Connection
import nl.casazapp.tv.R

private sealed interface Pairing {
    data object Idle : Pairing
    data class Waiting(val code: String) : Pairing
    data object Expired : Pairing
    data object Failed : Pairing
}

/**
 * Pairing without typing a password on the television: the TV shows a code, the user approves it
 * on a signed-in device (Settings → Devices), and the TV collects its token.
 */
@Composable
fun ConnectScreen(onConnected: (Connection) -> Unit) {
    // Start typing after the scheme: that is where the address goes.
    var field by remember { mutableStateOf(TextFieldValue("https://", TextRange(8))) }
    val server = field.text.trim()
    var pairing by remember { mutableStateOf<Pairing>(Pairing.Idle) }
    var attempt by remember { mutableIntStateOf(0) }
    // A text field keeps the D-pad for its cursor; send "down" and "done" on to the button.
    val button = remember { FocusRequester() }

    LaunchedEffect(attempt) {
        if (attempt == 0) return@LaunchedEffect
        val api = CasaZappApi(server)
        try {
            val start = api.startPairing(Build.MODEL ?: "Android TV")
            pairing = Pairing.Waiting(start.code)
            // Codes live 10 minutes; ask every few seconds whether it was approved.
            repeat(200) {
                delay(3_000)
                val token = api.pairingToken(start.code)
                if (token != null) {
                    onConnected(Connection(server.trimEnd('/'), token))
                    return@LaunchedEffect
                }
            }
            pairing = Pairing.Expired
        } catch (e: Exception) {
            pairing = if (pairing is Pairing.Waiting) Pairing.Expired else Pairing.Failed
        } finally {
            api.close()
        }
    }

    Column(
        Modifier.fillMaxSize().padding(64.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("CasaZapp TV", color = Casa.accent, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.connect_title), color = Casa.text, fontSize = 22.sp)

        when (val p = pairing) {
            is Pairing.Waiting -> {
                Text(stringResource(R.string.code_hint), color = Casa.muted, fontSize = 18.sp)
                Text(
                    p.code,
                    color = Casa.accent,
                    fontSize = 64.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                Text(stringResource(R.string.waiting), color = Casa.muted, fontSize = 16.sp)
            }
            else -> {
                Text(stringResource(R.string.server_url), color = Casa.muted, fontSize = 16.sp)
                BasicTextField(
                    value = field,
                    onValueChange = { field = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { button.requestFocus() }),
                    textStyle = TextStyle(color = Casa.text, fontSize = 22.sp),
                    cursorBrush = SolidColor(Casa.accent),
                    modifier = Modifier
                        .onPreviewKeyEvent {
                            if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionDown) {
                                button.requestFocus()
                                true
                            } else {
                                false
                            }
                        }
                        .width(560.dp)
                        .background(Casa.surface, RoundedCornerShape(12.dp))
                        .border(1.dp, Casa.line, RoundedCornerShape(12.dp))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                )
                if (p is Pairing.Expired) Text(stringResource(R.string.code_expired), color = Casa.live)
                if (p is Pairing.Failed) Text(stringResource(R.string.connect_failed), color = Casa.live)
                Button(
                    onClick = { attempt++ },
                    enabled = server.length > "https://".length,
                    modifier = Modifier.focusRequester(button),
                ) {
                    Text(stringResource(R.string.request_code))
                }
                Text(stringResource(R.string.local_mode), color = Casa.muted, fontSize = 14.sp)
            }
        }
    }
}
