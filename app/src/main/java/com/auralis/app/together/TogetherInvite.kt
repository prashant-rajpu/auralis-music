package com.auralis.app.together

/** A room code, plus the secret that decides whether the relay can read what you say in it. */
data class Invite(val code: String, val secret: String?) {
    val strength: EncryptionStrength
        get() = if (secret != null) EncryptionStrength.LINK_SECRET else EncryptionStrength.CODE_ONLY
}

/**
 * The invite link, the QR payload and the six characters someone reads down the phone are all the
 * same thing in different shapes.
 *
 * The secret rides in the link because that is the one channel wide enough to carry it. Typing a
 * bare code still works and still joins the same room — it just cannot carry a hundred bits, and
 * [Invite.strength] is how the UI stays honest about that rather than hiding it.
 */
object TogetherInvite {

    const val SCHEME = "auralis"
    const val HOST = "join"

    fun link(code: String, secret: String?): String {
        val normalized = code.uppercase()
        return if (secret != null) "$SCHEME://$HOST/$normalized?k=${secret.uppercase()}"
        else "$SCHEME://$HOST/$normalized"
    }

    /** What goes in the share sheet: the link, plus enough words that it is obvious what it is. */
    fun shareText(code: String, secret: String?, fromName: String): String {
        val who = fromName.trim().ifEmpty { "Someone" }
        return "$who wants to listen with you on Auralis.\n\n" +
            "Room code: $code\n" +
            link(code, secret)
    }

    /**
     * Tolerant on purpose: this is fed whatever survived a chat app. A bare code, a link, a link
     * someone wrapped in a sentence, or one that came back with an https prefix all resolve.
     */
    fun parse(input: String): Invite? {
        val text = input.trim()
        if (text.isEmpty()) return null

        val match = LINK.find(text)
        if (match != null) {
            val code = match.groupValues[1].uppercase()
            if (!RelayEndpoints.isValidCode(code)) return null
            val secret = match.groupValues[2].takeIf { it.isNotEmpty() }?.uppercase()
            return Invite(code, secret?.takeIf(TogetherCrypto::isValidSecret))
        }

        // No link, so treat it as a hand-typed code and be forgiving about spacing and dashes.
        val bare = RelayEndpoints.normalizeCode(text)
        if (bare.length != RelayEndpoints.CODE_LENGTH) return null
        // Only if that is genuinely all that was there; a longer string is something else entirely.
        if (text.count { it.isLetterOrDigit() } != RelayEndpoints.CODE_LENGTH) return null
        return Invite(bare, secret = null)
    }

    private val LINK = Regex(
        """(?:auralis://join/|https?://[^/\s]+/join/)([A-Za-z0-9]{6})(?:\?k=([A-Za-z0-9]+))?""",
    )
}
