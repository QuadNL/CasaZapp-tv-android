package nl.casazapp.tv.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The shape of the screen, following the web app's breakpoints.
 * [tv]: remote control, 10-foot layout. [compact]: a phone held upright, like the web below `lg`:
 * tab bar at the bottom, picture on top with the guide below it.
 */
data class Form(val tv: Boolean, val compact: Boolean, val phone: Boolean = false)

val LocalForm = staticCompositionLocalOf { Form(tv = true, compact = false) }
