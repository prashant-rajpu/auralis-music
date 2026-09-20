package com.auralis.app.together

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.MessageDigest

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

    // --- call signalling -------------------------------------------------------------------
    //
    // WebRTC needs the two phones to swap a session description and a list of candidate
    // addresses before any media flows. That exchange rides here, inside the same encryption as
    // everything else, for two reasons: the relay needs nothing from it, and a candidate list is
    // a list of the addresses your phone can be reached on. Sending it in the clear would hand
    // the server the one genuinely sensitive thing a call produces.

    /** "Can we talk?" — sent before any WebRTC work, so the other side can decline cheaply. */
    @Serializable
    @SerialName("callInvite")
    data class CallInvite(val withVideo: Boolean) : TogetherNote

    @Serializable
    @SerialName("callDecline")
    data object CallDecline : TogetherNote

    /** The caller's session description. */
    @Serializable
    @SerialName("callOffer")
    data class CallOffer(val sdp: String, val withVideo: Boolean) : TogetherNote

    /** The callee's session description. */
    @Serializable
    @SerialName("callAnswer")
    data class CallAnswer(val sdp: String) : TogetherNote

    /**
     * One candidate address. These trickle in after the offer rather than arriving with it,
     * because waiting for the full list before sending anything adds seconds to every call.
     */
    @Serializable
    @SerialName("callIce")
    data class CallIce(
        val candidate: String,
        val sdpMid: String?,
        val sdpMLineIndex: Int,
    ) : TogetherNote

    @Serializable
    @SerialName("callEnd")
    data class CallEnd(val reason: String = "") : TogetherNote

    /** Camera or microphone toggled mid-call, so the other side can show it. */
    @Serializable
    @SerialName("callMedia")
    data class CallMedia(val audioEnabled: Boolean, val videoEnabled: Boolean) : TogetherNote
}

/** True for the notes that are call plumbing rather than something a person said. */
val TogetherNote.isCallSignalling: Boolean
    get() = this is TogetherNote.CallInvite ||
        this is TogetherNote.CallDecline ||
        this is TogetherNote.CallOffer ||
        this is TogetherNote.CallAnswer ||
        this is TogetherNote.CallIce ||
        this is TogetherNote.CallEnd ||
        this is TogetherNote.CallMedia

object TogetherNotes {

    /**
     * A stable id for a message, derived from the sealed bytes themselves.
     *
     * Every note is sealed under a fresh nonce, so two identical messages produce different
     * ciphertext and this is unique per message. It is also the same on both phones and the same
     * whether a message arrives as a live broadcast or inside the history a rejoin replays, which
     * is exactly what stops a reconnect from repeating the last fifty things you said.
     *
     * A digest rather than the ciphertext itself only because it is a sensible length for a key.
     */
    fun idFor(ciphertext: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(ciphertext.toByteArray(Charsets.UTF_8))
            .take(16)
            .joinToString("") { "%02x".format(it) }

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
