@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package nl.casazapp.tv.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import nl.casazapp.core.local.LocalPlaylist
import nl.casazapp.core.local.LocalSource
import nl.casazapp.core.store.SourceMode
import nl.casazapp.tv.R

/** First launch: "How do you want to use CasaZapp TV?" */
@Composable
fun WelcomeScreen(onChoose: (SourceMode) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("CasaZapp TV", color = Casa.accent, fontSize = 34.sp, fontFamily = CasaFonts.display, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.welcome_title), color = Casa.text, fontSize = 22.sp)
        // A remote needs something focused to start from.
        val first = remember { androidx.compose.ui.focus.FocusRequester() }
        androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
        Choice(stringResource(R.string.mode_local), stringResource(R.string.mode_local_hint), Modifier.focusRequester(first)) { onChoose(SourceMode.LOCAL) }
        Choice(stringResource(R.string.mode_server), stringResource(R.string.mode_server_hint)) { onChoose(SourceMode.SERVER) }
    }
}

@Composable
private fun Choice(title: String, hint: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .focusRing(RoundedCornerShape(16.dp), onClick = onClick)
            .border(1.dp, Casa.line, RoundedCornerShape(16.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, color = Casa.text, fontSize = 20.sp, fontFamily = CasaFonts.display, fontWeight = FontWeight.SemiBold)
        Text(hint, color = Casa.muted, fontSize = 15.sp)
    }
}

/** A playlist on the device itself: Xtream Codes or an M3U URL, like "Add playlist" on the web. */
@Composable
fun LocalSetupScreen(initial: LocalPlaylist?, onSaved: (LocalPlaylist) -> Unit, onBack: () -> Unit) {
    // Without this, Back closes the app: the main screen's handler is not there yet (#78).
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(initial?.type ?: LocalPlaylist.XTREAM) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var url by remember { mutableStateOf(initial?.url ?: "http://") }
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var password by remember { mutableStateOf(initial?.password ?: "") }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val xtream = type == LocalPlaylist.XTREAM
    val file = type == LocalPlaylist.M3U_FILE
    // On a TV the remote needs somewhere to start: the chosen playlist type (#80).
    val tv = LocalForm.current.tv
    val first = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { if (tv) runCatching { first.requestFocus() } }
    val focusIf = { chosen: Boolean -> if (chosen) Modifier.focusRequester(first) else Modifier }
    var fileName by remember { mutableStateOf(if (initial?.type == LocalPlaylist.M3U_FILE) File(initial.url).name else "") }
    val complete = if (file) {
        url.startsWith("/")
    } else {
        url.length > "http://".length && (!xtream || (username.isNotBlank() && password.isNotBlank()))
    }
    // The chosen file is copied into the app's own storage, so it stays readable for later refreshes.
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val copy = withContext(Dispatchers.IO) {
                runCatching {
                    val target = File(File(context.filesDir, "local").apply { mkdirs() }, "upload-${System.currentTimeMillis()}.m3u")
                    context.contentResolver.openInputStream(uri)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
                    target
                }.getOrNull()
            }
            if (copy != null) {
                url = copy.absolutePath
                fileName = uri.lastPathSegment?.substringAfterLast('/') ?: copy.name
                if (name.isBlank()) name = fileName.substringBeforeLast('.')
            } else {
                failed = true
            }
        }
    }

    fun save() {
        if (!complete || busy) return
        val playlist = LocalPlaylist(
            type = type,
            name = name.ifBlank { "Playlist" },
            url = url.trim(),
            username = username.trim().takeIf { xtream },
            password = password.takeIf { xtream },
        )
        busy = true
        failed = false
        scope.launch {
            // Load it once before saving, so a typo shows up here and not on an empty Live TV.
            val source = LocalSource(context.filesDir, playlist)
            val ok = runCatching { source.refresh() > 0 }.getOrDefault(false)
            source.close()
            busy = false
            if (ok) onSaved(playlist) else failed = true
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.local_title), color = Casa.text, fontSize = 26.sp, fontFamily = CasaFonts.display, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.local_hint), color = Casa.muted, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("Xtream Codes", xtream, focusIf(initial?.type == null || initial.type == LocalPlaylist.XTREAM)) { type = LocalPlaylist.XTREAM }
                Chip("M3U-URL", type == LocalPlaylist.M3U, focusIf(initial?.type == LocalPlaylist.M3U)) {
                    type = LocalPlaylist.M3U
                    if (url.startsWith("/")) url = "http://"
                }
                Chip(stringResource(R.string.m3u_file), file, focusIf(initial?.type == LocalPlaylist.M3U_FILE)) { type = LocalPlaylist.M3U_FILE }
            }
            Field(stringResource(R.string.playlist_name), name, { name = it })
            if (file) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CasaButton(stringResource(R.string.choose_file), primary = false) {
                        runCatching { pickFile.launch(arrayOf("*/*")) }.onFailure { failed = true }
                    }
                    if (fileName.isNotEmpty()) Text(stringResource(R.string.file_chosen, fileName), color = Casa.muted, fontSize = 14.sp)
                }
            } else {
                Field(stringResource(if (xtream) R.string.server_url else R.string.m3u_url), url, { url = it }, KeyboardType.Uri)
            }
            if (xtream) {
                Field(stringResource(R.string.username), username, { username = it })
                Field(stringResource(R.string.password), password, { password = it }, KeyboardType.Password, secret = true)
            }
            if (failed) Text(stringResource(R.string.local_failed), color = Casa.live, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CasaButton(stringResource(if (busy) R.string.loading else R.string.local_save), enabled = complete && !busy) { save() }
                CasaButton(stringResource(R.string.back), primary = false, onClick = onBack)
            }
        }
    }
}

/** A labelled text field that passes the D-pad on, so the remote can reach the next field. */
@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
) {
    val focus = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Casa.muted, fontSize = 14.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            textStyle = TextStyle(color = Casa.text, fontSize = 18.sp),
            cursorBrush = SolidColor(Casa.accent),
            modifier = Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent {
                    if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (it.key) {
                        Key.DirectionDown -> focus.moveFocus(FocusDirection.Down)
                        Key.DirectionUp -> focus.moveFocus(FocusDirection.Up)
                        else -> false
                    }
                }
                .background(Casa.surface, RoundedCornerShape(10.dp))
                .border(1.dp, Casa.line, RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
