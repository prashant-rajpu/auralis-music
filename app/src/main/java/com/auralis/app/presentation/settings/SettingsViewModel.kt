package com.auralis.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auralis.app.domain.model.AudioQualitySetting
import com.auralis.app.domain.model.Track
import com.auralis.app.lyrics.LyricsRepository
import com.auralis.app.playback.*
import com.auralis.app.together.TogetherPreferences
import com.auralis.app.ui.theme.AccentPalette
import com.auralis.app.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SettingsToastEvent {
    data class Success(val message: String) : SettingsToastEvent
    data class Error(val message: String) : SettingsToastEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val preferences: AuralisSettingsPreferences,
    val playlistSharingManager: PlaylistSharingManager,
    val playbackManager: PlaybackManager,
    private val togetherPreferences: TogetherPreferences,
    lyricsRepository: LyricsRepository,
    segmentSkippers: Set<@JvmSuppressWildcards SegmentSkipper>
) : ViewModel() {

    val availableLyricsProviders: List<LyricsProvider> = lyricsRepository.availableProviders
    val hasSegmentSkipper: Boolean = segmentSkippers.isNotEmpty()

    val lyricsProvider = preferences.lyricsProvider
    val lyricsAutoScroll = preferences.lyricsAutoScroll
    val lyricsFontSize = preferences.lyricsFontSize
    val offlineLyricsEnabled = preferences.offlineLyricsEnabled

    val infiniteRadioAutoplay = preferences.infiniteRadioAutoplay
    val sponsorBlockEnabled = preferences.sponsorBlockEnabled
    val audioQuality = preferences.audioQuality
    val hapticIntensity = preferences.hapticIntensity

    val relayUrl = togetherPreferences.relayUrl
    val togetherDisplayName = togetherPreferences.displayName

    val userPlaylists = playlistSharingManager.userPlaylists
    val currentQueue = playbackManager.queue

    private val _importInputText = MutableStateFlow("")
    val importInputText: StateFlow<String> = _importInputText.asStateFlow()

    private val _toastEvent = MutableStateFlow<SettingsToastEvent?>(null)
    val toastEvent: StateFlow<SettingsToastEvent?> = _toastEvent.asStateFlow()

    fun onImportInputChanged(text: String) {
        _importInputText.value = text
    }

    /** False when the address could not be used, so Settings can say so instead of the socket. */
    fun setRelayUrl(url: String): Boolean = togetherPreferences.setRelayUrl(url)

    fun setTogetherDisplayName(name: String) = togetherPreferences.setDisplayName(name)

    fun clearToastEvent() {
        _toastEvent.value = null
    }

    fun setLyricsProvider(provider: LyricsProvider) {
        preferences.setLyricsProvider(provider)
    }

    fun toggleLyricsAutoScroll(enabled: Boolean) {
        preferences.setLyricsAutoScroll(enabled)
    }

    fun setLyricsFontSize(size: LyricsFontSize) {
        preferences.setLyricsFontSize(size)
    }

    fun toggleOfflineLyrics(enabled: Boolean) {
        preferences.setOfflineLyricsEnabled(enabled)
    }

    fun toggleInfiniteRadio(enabled: Boolean) {
        preferences.setInfiniteRadioAutoplay(enabled)
    }

    fun toggleSponsorBlock(enabled: Boolean) {
        preferences.setSponsorBlockEnabled(enabled)
    }

    fun setAudioQuality(quality: AudioQualitySetting) {
        preferences.setAudioQuality(quality)
    }

    fun setHapticIntensity(intensity: HapticIntensity) {
        preferences.setHapticIntensity(intensity)
    }

    /**
     * Imports a playlist from the input text (Base64 URL or plain text lines)
     */
    fun importPlaylistFromCurrentInput(): Boolean {
        val input = _importInputText.value.trim()
        if (input.isEmpty()) {
            _toastEvent.value = SettingsToastEvent.Error("Please paste a playlist share code or track list.")
            return false
        }

        val result = playlistSharingManager.importPlaylistFromInput(input)
        if (result != null) {
            val (title, tracks) = result
            playlistSharingManager.savePlaylist(title, tracks)
            _importInputText.value = ""
            _toastEvent.value = SettingsToastEvent.Success("Imported \"$title\" (${tracks.size} tracks) successfully!")
            return true
        } else {
            _toastEvent.value = SettingsToastEvent.Error("Invalid share code. Ensure it is a valid Auralis code or track list.")
            return false
        }
    }

    /**
     * Exports the current queue or a given tracklist to Base64 code
     */
    fun exportQueueToBase64(): String? {
        val q = currentQueue.value
        if (q.isEmpty()) {
            _toastEvent.value = SettingsToastEvent.Error("Queue is currently empty.")
            return null
        }
        val code = playlistSharingManager.exportPlaylistToBase64("Auralis Mix (${q.size} Songs)", q)
        _toastEvent.value = SettingsToastEvent.Success("Share code copied! Send to friends or keep as backup.")
        return code
    }

    fun exportSavedPlaylist(playlist: SavedPlaylist): String {
        val code = playlistSharingManager.exportPlaylistToBase64(playlist.title, playlist.tracks)
        _toastEvent.value = SettingsToastEvent.Success("Code for \"${playlist.title}\" copied!")
        return code
    }

    fun playSavedPlaylist(playlist: SavedPlaylist) {
        playbackManager.playPlaylist(playlist.tracks)
        _toastEvent.value = SettingsToastEvent.Success("Playing \"${playlist.title}\"")
    }

    fun deleteSavedPlaylist(playlistId: String) {
        playlistSharingManager.deletePlaylist(playlistId)
        _toastEvent.value = SettingsToastEvent.Success("Playlist removed.")
    }

    val topArtists = playbackManager.personalizationManager.topArtists

    // Appearance
    val themeMode = preferences.themeMode
    val accentPalette = preferences.accentPalette

    fun setThemeMode(mode: ThemeMode) {
        preferences.setThemeMode(mode)
    }

    fun setAccentPalette(accent: AccentPalette) {
        preferences.setAccentPalette(accent)
    }
}
