package com.auralis.app.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

import com.auralis.app.network.JamWebSocketClient
import com.auralis.app.network.JamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Singleton
class PlaybackManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val jamClient: JamWebSocketClient
) {
    private var mediaController: MediaController? = null
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()
    
    private val _currentTrack = MutableStateFlow<com.auralis.app.domain.model.Track?>(null)
    val currentTrack = _currentTrack.asStateFlow()
    
    init {
        val sessionToken = SessionToken(context, ComponentName(context, AuralisMediaSessionService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            mediaController = controllerFuture.get()
            setupController()
            observeJamState()
        }, MoreExecutors.directExecutor())
    }
    
    private fun observeJamState() {
        CoroutineScope(Dispatchers.Main).launch {
            jamClient.jamState.collect { state ->
                when (state) {
                    is JamState.SyncPlayback -> {
                        if (mediaController?.currentMediaItem?.mediaId != state.trackId) {
                            // In a real app we'd fetch the track details
                        }
                        mediaController?.seekTo(state.position)
                        if (state.isPlaying) mediaController?.play() else mediaController?.pause()
                    }
                    else -> {}
                }
            }
        }
    }
    
    private fun setupController() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }
            
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // In a real app we'd map this back from a DB or playlist repository based on ID
                // For now we'll just parse the basic metadata if it exists
                val title = mediaItem?.mediaMetadata?.title?.toString() ?: ""
                val artist = mediaItem?.mediaMetadata?.artist?.toString() ?: ""
                val artUri = mediaItem?.mediaMetadata?.artworkUri?.toString()
                
                if (title.isNotEmpty()) {
                    _currentTrack.value = com.auralis.app.domain.model.Track(
                        id = mediaItem?.mediaId ?: "",
                        title = title,
                        artist = artist,
                        albumArtUrl = artUri,
                        mediaUrl = mediaItem?.mediaId ?: "",
                        durationMs = 0L
                    )
                }
            }
        })
    }
    
    fun playTrack(track: com.auralis.app.domain.model.Track) {
        val mediaItem = MediaItem.Builder()
            .setMediaId(track.mediaUrl)
            .setUri(track.mediaUrl)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(track.albumArtUrl?.let { android.net.Uri.parse(it) })
                    .build()
            )
            .build()
            
        mediaController?.setMediaItem(mediaItem)
        mediaController?.prepare()
        mediaController?.play()
        
        // Manually set current track immediately for snappier UI
        _currentTrack.value = track
    }
    
    fun togglePlayPause() {
        if (mediaController?.isPlaying == true) {
            mediaController?.pause()
        } else {
            mediaController?.play()
        }
    }
}
