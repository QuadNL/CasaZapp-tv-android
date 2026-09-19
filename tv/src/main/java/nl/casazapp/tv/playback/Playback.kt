package nl.casazapp.tv.playback

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import nl.casazapp.tv.R

/** True while the app shows only the picture-in-picture window. */
val LocalInPip = staticCompositionLocalOf { false }

/** What the picture-in-picture window's buttons ask the player to do (#62). */
enum class PipCommand { PREVIOUS, NEXT, AUDIO_ONLY }

/**
 * The player that is on screen, shared with the activity (picture-in-picture, leaving the app) and
 * with [PlaybackService] (sound in the background).
 */
object Playback {
    const val ACTION = "nl.casazapp.tv.PIP"
    const val EXTRA = "command"

    var player: ExoPlayer? = null
    /** The player may shrink to a floating window: a phone or tablet, with the setting on. */
    var pipAllowed = false
    val commands = MutableSharedFlow<PipCommand>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** Only the sound plays, in the background; the picture is off until the app comes back. */
    val audioOnly = MutableStateFlow(false)
    private var controller: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null

    fun pipParams(context: Context): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        fun action(command: PipCommand, icon: Int, label: Int): RemoteAction {
            val intent = Intent(ACTION).setPackage(context.packageName).putExtra(EXTRA, command.name)
            val pending = PendingIntent.getBroadcast(
                context,
                command.ordinal,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val text = context.getString(label)
            return RemoteAction(Icon.createWithResource(context, icon), text, text, pending)
        }
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            // Android shows three buttons at most: zap back, sound only, zap on.
            .setActions(
                listOf(
                    action(PipCommand.PREVIOUS, R.drawable.pip_previous, R.string.previous_channel),
                    action(PipCommand.AUDIO_ONLY, R.drawable.pip_headphones, R.string.audio_only),
                    action(PipCommand.NEXT, R.drawable.pip_next, R.string.next_channel),
                ),
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setAutoEnterEnabled(pipAllowed)
        return builder.build()
    }

    /** Keeps the window's buttons and, from Android 12, entering it on leaving the app up to date. */
    fun updatePip(activity: Activity?) {
        if (activity == null) return
        val params = pipParams(activity) ?: return
        runCatching { activity.setPictureInPictureParams(params) }
    }

    /** Picture off, sound on in the background with a notification and lock-screen controls. */
    fun startAudioOnly(activity: Activity) {
        val player = player ?: return
        audioOnly.value = true
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
            .build()
        player.play()
        // A connected controller starts the service; it goes to the foreground while the sound plays.
        val token = SessionToken(activity, ComponentName(activity, PlaybackService::class.java))
        controller = MediaController.Builder(activity.applicationContext, token).buildAsync()
        activity.moveTaskToBack(true)
    }

    /** Back in the app: the picture returns and the background service ends. */
    fun stopAudioOnly(context: Context) {
        if (!audioOnly.value) return
        audioOnly.value = false
        player?.let {
            it.trackSelectionParameters = it.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
                .build()
        }
        controller?.let { MediaController.releaseFuture(it) }
        controller = null
        context.stopService(Intent(context, PlaybackService::class.java))
    }
}
