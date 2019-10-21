package com.github.yoheimuta.amplayer.playback

import android.app.Notification
import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.annotation.Nullable
import androidx.media.MediaBrowserServiceCompat
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import android.app.PendingIntent
import android.graphics.Bitmap
import android.os.Binder
import android.os.ResultReceiver
import androidx.core.content.ContextCompat
import com.github.yoheimuta.amplayer.R
import com.github.yoheimuta.amplayer.extensions.flag
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Log
import com.google.android.exoplayer2.util.Util
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "MusicService"
private const val MEDIA_ROOT_ID = "media_root_id"
private const val CHANNEL_ID: String = "com.github.yoheimuta.amplayer.playback"
private const val AMPLAYER_USER_AGENT = "amplayer.next"
private const val NOTIFICATION_ID: Int = 0xb339
private const val NETWORK_FAILURE = "com.github.yoheimuta.amplayer.playback.session.NETWORK_FAILURE"

const val GET_PLAYER_COMMAND = "getPlayer"
const val MUSIC_SERVICE_BINDER_KEY = "MusicServiceBinder"

class MusicService: MediaBrowserServiceCompat() {
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var mediaController: MediaControllerCompat
    private lateinit var mediaSessionConnector: MediaSessionConnector
    private lateinit var playerNotificationManager: PlayerNotificationManager
    private lateinit var musicSource: MusicSource
    private lateinit var becomingNoisyReceiver: BecomingNoisyReceiver

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private val playerCommandReceiver = GetPlayerCommandReceiver()

    private val exoPlayer: ExoPlayer by lazy {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        ExoPlayerFactory.newSimpleInstance(this).apply {
            setAudioAttributes(audioAttributes, true)
        }
    }

    override fun onCreate() {
        super.onCreate()

        Log.i(TAG, "onCreate")

        musicSource = InMemorySource()
        serviceScope.launch {
            musicSource.load()
        }

        val sessionActivityPendingIntent =
            packageManager?.getLaunchIntentForPackage(packageName)?.let { sessionIntent ->
                PendingIntent.getActivity(this, 0, sessionIntent, 0)
            }

        mediaSession = MediaSessionCompat(baseContext, TAG).apply {
            isActive = true
            setSessionActivity(sessionActivityPendingIntent)
            setSessionToken(sessionToken)
        }

        becomingNoisyReceiver =
            BecomingNoisyReceiver(context = this, sessionToken = mediaSession.sessionToken)

        mediaController = MediaControllerCompat(this, mediaSession).also {
            it.registerCallback(MediaControllerCallback(becomingNoisyReceiver))
        }

        mediaSessionConnector = MediaSessionConnector(mediaSession).also {
            val dataSourceFactory = DefaultDataSourceFactory(
                this, Util.getUserAgent(this, AMPLAYER_USER_AGENT), null
            )

            it.setPlaybackPreparer(AMPlaybackPreparer(musicSource, exoPlayer, dataSourceFactory))
            it.setPlayer(exoPlayer)
            it.setQueueNavigator(AMQueueNavigator(mediaSession))
            it.registerCustomCommandReceiver(playerCommandReceiver)
        }

        playerNotificationManager = PlayerNotificationManager.createWithNotificationChannel(
            this,
            CHANNEL_ID,
            R.string.notification_channel,
            R.string.notification_channel_description,
            NOTIFICATION_ID,
            MediaDescriptionAdapter(),
            NotificationListener()
        ).apply {
            setMediaSessionToken(sessionToken)
            setPlayer(exoPlayer)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        super.onTaskRemoved(rootIntent)

        exoPlayer.stop(true)
    }

    override fun onDestroy() {
        mediaSession.run {
            isActive = false
            release()
        }
        becomingNoisyReceiver.unregister()
        playerNotificationManager.setPlayer(null);
        mediaSessionConnector.unregisterCustomCommandReceiver(playerCommandReceiver)
        exoPlayer.release()

        serviceJob.cancel()
    }

    override fun onLoadChildren(parentId: String,
                                result: Result<List<MediaItem>>) {
        if (parentId != MEDIA_ROOT_ID) {
            result.sendResult(null)
            return
        }

        val resultsSent = musicSource.whenReady { successfullyInitialized ->
            if (successfullyInitialized) {
                val children = musicSource.toList().map { item ->
                    MediaItem(item.description, item.flag)
                }
                result.sendResult(children)
            } else {
                mediaSession.sendSessionEvent(NETWORK_FAILURE, null)
                result.sendResult(null)
            }
        }

        if (!resultsSent) {
            result.detach()
        }
    }

    @Nullable
    override fun onGetRoot(clientPackageName: String,
                           clientUid: Int,
                           rootHints: Bundle?): BrowserRoot? {
        return BrowserRoot(MEDIA_ROOT_ID, null)
    }

    private inner class MediaDescriptionAdapter: PlayerNotificationManager.MediaDescriptionAdapter {
        override fun getCurrentContentTitle(player: Player): String {
            return mediaController.metadata.description.title.toString()
        }

        override fun getCurrentContentText(player: Player): String? {
            return ""
        }

        override fun getCurrentLargeIcon(
            player: Player,
            callback: PlayerNotificationManager.BitmapCallback
        ): Bitmap? {
            return mediaController.metadata.description.iconBitmap
        }

        override fun createCurrentContentIntent(player: Player): PendingIntent? {
            return mediaController.sessionActivity
        }
    }

    private inner class NotificationListener: PlayerNotificationManager.NotificationListener {
        private var isForegroundService = false

        override fun onNotificationPosted(
            notificationId: Int,
            notification: Notification?,
            ongoing: Boolean
        ) {
            when {
                ongoing && !isForegroundService -> {
                    ContextCompat.startForegroundService(
                        applicationContext,
                        Intent(applicationContext, this@MusicService.javaClass)
                    )
                    startForeground(notificationId, notification)
                    isForegroundService = true
                }
                !ongoing && isForegroundService -> {
                    stopForeground(false)
                    isForegroundService = false
                }
            }
        }

        override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
            stopSelf()
        }
    }

    private inner class GetPlayerCommandReceiver: MediaSessionConnector.CommandReceiver {
        override fun onCommand(
            player: Player,
            controlDispatcher: ControlDispatcher,
            command: String,
            extras: Bundle,
            cb: ResultReceiver
        ): Boolean {
            if (command != GET_PLAYER_COMMAND) {
                return false
            }

            val bundle = Bundle()
            bundle.putBinder(MUSIC_SERVICE_BINDER_KEY, MusicServiceBinder())
            cb.send(0, bundle)
            return true
        }
    }

    inner class MusicServiceBinder : Binder() {
        fun getExoPlayer() = exoPlayer
    }
}

private class MediaControllerCallback(private val becomingNoisyReceiver: BecomingNoisyReceiver)
    : MediaControllerCompat.Callback() {
    override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
        state?.let { updateNoisyReceiver(it) }
    }

    private fun updateNoisyReceiver(state: PlaybackStateCompat) {
        val updatedState = state.state

        if (updatedState == PlaybackStateCompat.STATE_NONE) {
            return
        }

        when (updatedState) {
            PlaybackStateCompat.STATE_BUFFERING,
            PlaybackStateCompat.STATE_PLAYING -> {
                becomingNoisyReceiver.register()
            }
            else -> {
                becomingNoisyReceiver.unregister()
            }
        }
    }
}

