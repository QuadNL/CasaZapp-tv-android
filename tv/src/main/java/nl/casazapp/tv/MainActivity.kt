package nl.casazapp.tv

import android.app.UiModeManager
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.ContextWrapper
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import nl.casazapp.tv.playback.LocalInPip
import nl.casazapp.tv.playback.PipCommand
import nl.casazapp.tv.playback.Playback
import java.util.Locale
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.tv.material3.ProvideTextStyle
import nl.casazapp.core.store.ConnectionStore
import nl.casazapp.core.store.UiMode
import nl.casazapp.tv.ui.App
import nl.casazapp.tv.ui.CasaFonts
import nl.casazapp.tv.ui.Form
import nl.casazapp.tv.ui.LocalForm

/**
 * Width of the canvas the TV screens are designed for: the web app's desktop layout. Android TV
 * reports only 960 dp across (even in 4K), so without scaling everything is twice the intended size.
 */
private const val DESIGN_WIDTH_DP = 1600f

/** Below this width a phone gets the web app's mobile layout (Tailwind `lg` is 1024 px, phones are ~400 dp). */
private const val COMPACT_WIDTH_DP = 600

/**
 * Phones are drawn as if 440 dp wide: the web on a phone is denser than Android's defaults, and at
 * full size only a few tiles fit. Wider phones scale up to their real size.
 */
private const val PHONE_DESIGN_WIDTH_DP = 440f

class MainActivity : ComponentActivity() {
    private var inPip by mutableStateOf(false)
    /** The player was paused because the app went out of sight; it plays again on return. */
    private var pausedOnStop = false

    // The buttons of the picture-in-picture window (#62).
    private val pipButtons = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val command = intent.getStringExtra(Playback.EXTRA)?.let { runCatching { PipCommand.valueOf(it) }.getOrNull() }
            command?.let { Playback.commands.tryEmit(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.registerReceiver(this, pipButtons, IntentFilter(Playback.ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
        val store = ConnectionStore(applicationContext)
        val isTelevision = getSystemService(UiModeManager::class.java)
            ?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        setContent {
            val mode by store.uiMode.collectAsState(initial = null)
            val locale by store.locale.collectAsState(initial = null)
            val current = mode ?: return@setContent
            val language = locale ?: return@setContent
            val tv = when (current) {
                UiMode.AUTO -> isTelevision
                UiMode.TV -> true
                UiMode.MOBILE -> false
            }
            LaunchedEffect(tv) {
                requestedOrientation = if (tv) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
            Localized(language.first()) {
                FormScale(tv) { CompositionLocalProvider(LocalInPip provides inPip) { App(store) } }
            }
        }
    }

    // Before Android 12 the window is opened here; from 12 on, the params' auto-enter does it.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.O until Build.VERSION_CODES.S && Playback.pipAllowed && Playback.player != null) {
            Playback.pipParams(this)?.let { runCatching { enterPictureInPictureMode(it) } }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }

    override fun onStart() {
        super.onStart()
        // Back from sound only, or from elsewhere: the picture returns and the channel plays on.
        Playback.stopAudioOnly(this)
        if (pausedOnStop) Playback.player?.play()
        pausedOnStop = false
    }

    // Out of sight without picture-in-picture or sound only: the sound stops too.
    override fun onStop() {
        super.onStop()
        val player = Playback.player
        if (player != null && !Playback.audioOnly.value && player.isPlaying) {
            player.pause()
            pausedOnStop = true
        }
    }

    override fun onDestroy() {
        unregisterReceiver(pipButtons)
        super.onDestroy()
    }
}

/** The language picked in Settings, instead of the device language, for this app only. */
@Composable
private fun Localized(tag: String?, content: @Composable () -> Unit) {
    val base = LocalContext.current
    // Keyed on the live configuration: turning the device must reach the layout, language or not.
    val outer = LocalConfiguration.current
    // Always the same composition shape, so switching language keeps the screen you are on.
    val localized = remember(tag, base, outer) {
        if (tag == null) return@remember base
        val config = Configuration(outer).apply { setLocale(Locale.forLanguageTag(tag)) }
        val resources = base.createConfigurationContext(config).resources
        // A wrapper keeps the activity underneath, which the player and back handling need.
        object : ContextWrapper(base) {
            override fun getResources(): Resources = resources
        }
    }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides if (tag == null) outer else localized.resources.configuration,
        content = content,
    )
}

/** On a TV, scales dp and sp so the layout keeps the web app's desktop proportions. */
@Composable
private fun FormScale(tv: Boolean, content: @Composable () -> Unit) {
    val width = LocalConfiguration.current.screenWidthDp
    val density = LocalDensity.current
    // A phone stays a phone when turned: same scale, judged by its short side.
    val shortSide = LocalConfiguration.current.smallestScreenWidthDp
    val phone = !tv && shortSide < COMPACT_WIDTH_DP
    val compact = phone && width < COMPACT_WIDTH_DP
    val scaled = when {
        tv -> Density(density.density * width / DESIGN_WIDTH_DP, density.fontScale)
        phone -> Density(density.density * minOf(1f, shortSide / PHONE_DESIGN_WIDTH_DP), density.fontScale)
        else -> density
    }
    CompositionLocalProvider(
        LocalDensity provides scaled,
        LocalForm provides Form(tv = tv, compact = compact, phone = phone),
    ) {
        ProvideTextStyle(TextStyle(fontFamily = CasaFonts.sans), content)
    }
}
