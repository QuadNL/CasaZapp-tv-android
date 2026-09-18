package nl.casazapp.tv

import android.app.UiModeManager
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.ContextWrapper
import android.content.res.Resources
import android.os.Bundle
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            Localized(language.first()) { FormScale(tv) { App(store) } }
        }
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
