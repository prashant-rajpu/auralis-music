package com.auralis.app.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * Custom ForwardingPlayer that ensures SystemUI media notifications, lockscreen controls,
 * and Bluetooth devices always show enabled Next and Previous transport buttons,
 * delegating queue navigation commands to PlaybackManager.
 */
@UnstableApi
class AuralisQueueForwardingPlayer(
    player: Player,
    private val onSkipNext: () -> Unit,
    private val onSkipPrevious: () -> Unit
) : ForwardingPlayer(player) {

    override fun getAvailableCommands(): Player.Commands {
        return super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()
    }

    override fun isCommandAvailable(command: Int): Boolean {
        return when (command) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
            else -> super.isCommandAvailable(command)
        }
    }

    override fun seekToNext() {
        onSkipNext()
    }

    override fun seekToNextMediaItem() {
        onSkipNext()
    }

    override fun seekToPrevious() {
        onSkipPrevious()
    }

    override fun seekToPreviousMediaItem() {
        onSkipPrevious()
    }
}
