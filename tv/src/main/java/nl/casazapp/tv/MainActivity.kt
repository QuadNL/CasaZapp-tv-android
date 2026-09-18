package nl.casazapp.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.tv.material3.ProvideTextStyle
import nl.casazapp.core.store.ConnectionStore
import nl.casazapp.tv.ui.App
import nl.casazapp.tv.ui.CasaFonts

/**
 * Width of the canvas the screens are designed for: the web app's desktop layout. Android TV
 * reports only 960 dp across (even in 4K), so without scaling everything is twice the intended size.
 */
private const val DESIGN_WIDTH_DP = 1600f

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = ConnectionStore(applicationContext)
        setContent { DesignScale { App(store) } }
    }
}

/** Scales dp and sp so the layout keeps the web app's proportions on any screen width. */
@Composable
private fun DesignScale(content: @Composable () -> Unit) {
    val width = LocalConfiguration.current.screenWidthDp
    val density = LocalDensity.current
    val scale = width / DESIGN_WIDTH_DP
    CompositionLocalProvider(
        LocalDensity provides Density(density.density * scale, density.fontScale),
    ) {
        ProvideTextStyle(TextStyle(fontFamily = CasaFonts.sans), content)
    }
}
