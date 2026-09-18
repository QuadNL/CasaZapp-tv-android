package nl.casazapp.tv

import android.app.UiModeManager
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = ConnectionStore(applicationContext)
        val isTelevision = getSystemService(UiModeManager::class.java)
            ?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        setContent {
            val mode by store.uiMode.collectAsState(initial = null)
            val current = mode ?: return@setContent
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
            FormScale(tv) { App(store) }
        }
    }
}

/** On a TV, scales dp and sp so the layout keeps the web app's desktop proportions. */
@Composable
private fun FormScale(tv: Boolean, content: @Composable () -> Unit) {
    val width = LocalConfiguration.current.screenWidthDp
    val density = LocalDensity.current
    val scaled = if (tv) Density(density.density * width / DESIGN_WIDTH_DP, density.fontScale) else density
    CompositionLocalProvider(
        LocalDensity provides scaled,
        LocalForm provides Form(tv = tv, compact = !tv && width < COMPACT_WIDTH_DP),
    ) {
        ProvideTextStyle(TextStyle(fontFamily = CasaFonts.sans), content)
    }
}
