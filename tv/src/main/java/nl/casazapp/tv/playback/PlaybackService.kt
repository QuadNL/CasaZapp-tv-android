package nl.casazapp.tv.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Keeps the sound of the player going while the app is in the background ("sound only", #62), with
 * the channel in a notification and on the lock screen. The player itself stays owned by the player
 * screen; this service only lends it a media session.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = Playback.player ?: return stopSelf()
        // Tapping the notification or the lock-screen player opens the app again.
        val open = packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        val builder = MediaSession.Builder(this, player)
        open?.let { builder.setSessionActivity(PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)) }
        session = builder.build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    // Swiping the app away ends the sound too.
    override fun onTaskRemoved(rootIntent: Intent?) {
        Playback.player?.pause()
        stopSelf()
    }

    override fun onDestroy() {
        // The player belongs to the player screen; only the session ends here.
        session?.release()
        session = null
        super.onDestroy()
    }
}
