package com.github.yoheimuta.amplayer.playback

import android.net.Uri
import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import com.github.yoheimuta.amplayer.extensions.id
import com.github.yoheimuta.amplayer.extensions.toMediaSource
import com.google.android.exoplayer2.ControlDispatcher
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.upstream.DataSource

private const val TAG = "MediaSessionHelper"

class AMPlaybackPreparer(
    private val musicSource: MusicSource,
    private val exoPlayer: ExoPlayer,
    private val dataSourceFactory: DataSource.Factory
) : MediaSessionConnector.PlaybackPreparer {
    override fun getSupportedPrepareActions(): Long =
        PlaybackStateCompat.ACTION_PREPARE or
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID or
                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID

    override fun onPrepare(playWhenReady: Boolean) {
        Log.i(TAG, "onPrepare")

        musicSource.whenReady {
            val first = musicSource.toList().getOrNull(0)
            onPrepareFromMediaId(first?.id, playWhenReady, null)
        }
    }

    override fun onPrepareFromMediaId(mediaId: String?, playWhenReady: Boolean, extras: Bundle?) {
        Log.i(TAG, "onPrepareFromMediaId: mediaID=$mediaId")

        musicSource.whenReady {
            val itemToPlay: MediaMetadataCompat? = musicSource.find { item ->
                item.id == mediaId
            }
            if (itemToPlay == null) {
                Log.w(TAG, "Content not found: MediaID=$mediaId")
            } else {
                val metadataList = musicSource.toList()
                val mediaSource = metadataList.toMediaSource(dataSourceFactory)

                val initialWindowIndex = metadataList.indexOf(itemToPlay)

                exoPlayer.setPlayWhenReady(playWhenReady)
                exoPlayer.prepare(mediaSource)
                exoPlayer.seekTo(initialWindowIndex, 0)
            }
        }
    }

    override fun onPrepareFromSearch(query: String?, playWhenReady: Boolean, extras: Bundle?) = Unit

    override fun onPrepareFromUri(uri: Uri?, playWhenReady: Boolean, extras: Bundle?) = Unit

    override fun onCommand(
        player: Player?,
        controlDispatcher: ControlDispatcher?,
        command: String?,
        extras: Bundle?,
        cb: ResultReceiver?
    ) = false
}
