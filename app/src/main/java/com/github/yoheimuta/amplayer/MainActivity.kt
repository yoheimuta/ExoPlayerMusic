package com.github.yoheimuta.amplayer

import android.content.ComponentName
import android.content.Intent
import android.media.AudioManager
import androidx.databinding.DataBindingUtil
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.util.Log
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.github.yoheimuta.amplayer.playback.MusicService
import com.github.yoheimuta.amplayer.databinding.ActivityMainBinding

private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mediaBrowser: MediaBrowserCompat
    private var songs: List<MediaBrowserCompat.MediaItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.listView.onItemClickListener =
            AdapterView.OnItemClickListener { parent, view, position, id ->
                val intent = Intent(this@MainActivity, NowPlayingActivity::class.java)
                intent.putExtra(NOW_PLAYING_INTENT_MEDIA_ID, songs[position].mediaId)
                startActivity(intent)
            }

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

    private inner class ConnectionCallback : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            Log.i(TAG, "onConnected")

            mediaBrowser.sessionToken.also { token ->
                val mediaController = MediaControllerCompat(
                    this@MainActivity, // Context
                    token
                )

                MediaControllerCompat.setMediaController(this@MainActivity, mediaController)
            }

            mediaBrowser.subscribe(mediaBrowser.getRoot(), SubscriptionCallback());
        }

        override fun onConnectionSuspended() {
            Log.i(TAG, "The Service has crashed")
        }

        override fun onConnectionFailed() {
            Log.i(TAG, "The Service has refused our connection")
        }
    }

    private inner class SubscriptionCallback: MediaBrowserCompat.SubscriptionCallback() {
        override fun onChildrenLoaded(parentId: String, children: List<MediaBrowserCompat.MediaItem>) {
            songs = children

            val titles = songs.map { it.description.title }
            binding.listView.setAdapter(
                ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, titles));
        }
    }
}

