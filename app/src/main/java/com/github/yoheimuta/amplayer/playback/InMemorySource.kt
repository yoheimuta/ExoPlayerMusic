package com.github.yoheimuta.amplayer.playback

import android.support.v4.media.MediaMetadataCompat

class InMemorySource() : AbstractMusicSource() {

    private var catalog: List<MediaMetadataCompat> = emptyList()

    init {
        state = STATE_INITIALIZING
    }

    override fun iterator(): Iterator<MediaMetadataCompat> = catalog.iterator()

    override suspend fun load() {
        catalog = getCatalog()
        state = STATE_INITIALIZED
    }

    private suspend fun getCatalog(): List<MediaMetadataCompat> {
        return listOf(
            Pair(
                "https://storage.googleapis.com/maison-great-dev/oss/musicplayer/tagmp3_1473200_1.mp3",
                "TEST_1"
            ),
            Pair(
                "https://storage.googleapis.com/maison-great-dev/oss/musicplayer/tagmp3_2160166.mp3",
                "TEST_2"
            )
        ).map { (url, title) ->
            MediaMetadataCompat.Builder()
                .apply {
                    putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, url)
                    putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, url)
                    putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                }
                .build()
        }
    }
}
