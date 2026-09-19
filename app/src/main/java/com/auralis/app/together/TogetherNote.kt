package com.auralis.app.together

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Everything two people say to each other in a session, as one encrypted channel.
 *
 * The relay carries exactly one kind of private message — `chat`, holding ciphertext — and every
 * intimate thing rides inside it: a note, a song dedicated with a line attached, a lyric someone
 * long-pressed, a knock, a goodnight. Giving each of those its own wire type would have told the
 * relay which is which, and "she sent him a dedication at 11:40pm" is itself worth not leaking.
 * This way the relay cannot tell a knock from a paragraph.
 *
 * It also means none of this needed a relay change, so an older worker carries it all unchanged.
 */
@Serializable
sealed interface TogetherNote {

    @Serializable
    @SerialName("text")
    data class Text(val body: String) : TogetherNote

    /** A song sent with something said about it. It is the note that makes it a dedication. */
    @Serializable
    @SerialName("dedication")
    data class Dedication(val track: TrackRef, val note: String) : TogetherNote

    /**
     * One line of a song, sent at the moment it is playing. This is what people already do over
     * text, screenshot and all; it just belongs here instead.
     */
    @Serializable
    @SerialName("lyric")
    data class LyricMoment(val line: String, val track: TrackRef, val positionMs: Long) : TogetherNote

    /** Thinking of you. Costs nothing, means something. */
    @Serializable
    @SerialName("knock")
    data object Knock : TogetherNote

    /**
     * Fade out and stop on both phones at the same moment, in *room* time so it really is the same
     * moment. For falling asleep on a call.
     */
    @Serializable
    @SerialName("goodnight")
    data class Goodnight(val atServerMs: Long) : TogetherNote
}

object TogetherNotes {

    fun seal(key: RoomKey, note: TogetherNote): String =
        TogetherCrypto.seal(key, TogetherJson.encodeToString(TogetherNote.serializer(), note))

    /**
     * Null only when the message will not decrypt at all — a peer on a different invite.
     *
     * Anything that opens but is not a note is treated as plain text, because that is exactly what
     * an older build sent: a bare string. A version skew should read as a slightly plain message,
     * not as a broken one.
     */
    fun open(key: RoomKey, ciphertext: String): TogetherNote? {
        val opened = TogetherCrypto.open(key, ciphertext) ?: return null
        return runCatching { TogetherJson.decodeFromString(TogetherNote.serializer(), opened) }
            .getOrElse { TogetherNote.Text(opened) }
    }
}
