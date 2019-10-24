package com.github.yoheimuta.amplayer

import android.content.ComponentName
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.github.yoheimuta.amplayer.databinding.NowPlayingBinding
import com.github.yoheimuta.amplayer.extensions.id
import com.github.yoheimuta.amplayer.extensions.title
import com.github.yoheimuta.amplayer.playback.GET_PLAYER_COMMAND
import com.github.yoheimuta.amplayer.playback.MUSIC_SERVICE_BINDER_KEY
import com.github.yoheimuta.amplayer.playback.MusicService

const val NOW_PLAYING_INTENT_MEDIA_ID = "mediaId"
private const val TAG = "NowPlayingActivity"

class NowPlayingActivity: AppCompatActivity() {

    private var mediaId: String? = null
    private lateinit var binding: NowPlayingBinding
    private lateinit var mediaBrowser: MediaBrowserCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.now_playing)

        binding = DataBindingUtil.setContentView(this, R.layout.now_playing)
        mediaId = intent.getStringExtra(NOW_PLAYING_INTENT_MEDIA_ID)
        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, MusicService::class.java),
            ConnectionCallback(),
            null // optional Bundle
        )
    }

    public override fun onStart() {
        super.onStart()
        mediaBrowser.connect()
    }

    public override fun onResume() {
        super.onResume()
        volumeControlStream = AudioManager.STREAM_MUSIC
    }

    public override fun onStop() {
        super.onStop()
        mediaBrowser.disconnect()
    }

    private fun buildUI() {
        val mediaController = MediaControllerCompat.getMediaController(this)
        mediaController.sendCommand(GET_PLAYER_COMMAND, Bundle(), ResultReceiver(Handler()))
    }

    private inner class ConnectionCallback : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            Log.i(TAG, "onConnected")

            mediaBrowser.sessionToken.also { token ->
                val mediaController = MediaControllerCompat(
                    this@NowPlayingActivity, // Context
                    token
                ).apply {
                    registerCallback(MediaControllerCallback())
                }

                MediaControllerCompat.setMediaController(this@NowPlayingActivity, mediaController)
            }

            buildUI()
        }

        override fun onConnectionSuspended() {
            Log.i(TAG, "The Service has crashed")
        }

        override fun onConnectionFailed() {
            Log.i(TAG, "The Service has refused our connection")
        }
    }

    private inner class ResultReceiver(handler: Handler): android.os.ResultReceiver(handler) {
        override fun onReceiveResult(resultCode: Int, resultData: Bundle) {
            val service = resultData.getBinder(MUSIC_SERVICE_BINDER_KEY)
            if (service is MusicService.MusicServiceBinder) {
                if (binding.playerView.player != null) {
                    return
                }
                val player = service.getExoPlayer()
                binding.playerView.player = player

                val mediaController =
                    MediaControllerCompat.getMediaController(this@NowPlayingActivity)
                if (mediaController.metadata.id == mediaId) {
                    return
                }
                mediaController.getTransportControls().prepareFromMediaId(mediaId, null)
            }
        }
    }

    private inner class MediaControllerCallback: MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            if (metadata == null) {
                return
            }

            binding.playerView.
                findViewById<View>(R.id.exo_controller).
                findViewById<TextView>(R.id.song_title).
                setText(metadata.title)
        }
    }
}
