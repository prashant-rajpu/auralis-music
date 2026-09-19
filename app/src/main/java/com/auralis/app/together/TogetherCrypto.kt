package com.auralis.app.together

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * How strong a room's encryption actually is, which depends entirely on how the invite travelled.
 *
 * The relay must not be able to read a couple's messages. Whether it *can* comes down to how much
 * entropy the key was derived from, and a six-character room code only has about thirty bits —
 * enough that nobody guesses it, nowhere near enough to stop whoever holds the ciphertext from
 * trying every one of them offline.
 *
 * So an invite carries a separate secret that the relay never sees. Sharing a link or a QR code
 * carries it automatically. Reading six characters down the phone cannot, and that session falls
 * back to a key derived from the code itself — private from other users, not from a relay operator
 * determined to read it. The UI says which one is in force rather than implying they are the same.
 */
enum class EncryptionStrength {
    /** The invite carried a secret the relay never saw. */
    LINK_SECRET,

    /** Only the room code was available, so that is all the key can come from. */
    CODE_ONLY,
}

data class RoomKey(internal val bytes: ByteArray, val strength: EncryptionStrength) {
    // Data classes compare arrays by reference; a key that does not equal itself would be a
    // genuinely confusing bug to chase later.
    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is RoomKey && strength == other.strength && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + strength.hashCode()
}

/**
 * Client-side encryption for the parts of a session that are nobody else's business: chat,
 * dedications, lyric moments and the notes attached to them.
 *
 * The relay stores and forwards ciphertext it cannot read. Playback state stays plaintext, because
 * the relay needs the timestamps to do its one job and a track id is not a private message.
 *
 * AES-256-GCM with a random 96-bit nonce per message, key from HKDF-SHA256. Deliberately boring.
 */
object TogetherCrypto {

    private const val SECRET_LENGTH = 20
    private const val KEY_BYTES = 32
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val INFO = "auralis-together-v1"

    // java.util.Base64 rather than android.util.Base64: minSdk is 26, so it is available, and it
    // works on the JVM, which is where every test of this runs.
    private val ENCODER: Base64.Encoder = Base64.getEncoder().withoutPadding()
    private val DECODER: Base64.Decoder = Base64.getDecoder()

    /** The same unambiguous alphabet as the room code, so a secret can be read aloud if it must be. */
    private const val ALPHABET = RelayEndpoints.CODE_ALPHABET

    /** About 99 bits. Generated on the host and shared in the invite; never sent to the relay. */
    fun generateRoomSecret(random: SecureRandom = SecureRandom()): String =
        buildString(SECRET_LENGTH) {
            repeat(SECRET_LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }

    fun isValidSecret(secret: String): Boolean =
        secret.length == SECRET_LENGTH && secret.all { it in ALPHABET }

    /**
     * The room code is the salt rather than the secret: it is public by design — the relay routes
     * on it — but it still separates two rooms that somehow shared a secret.
     */
    fun deriveKey(roomCode: String, roomSecret: String?): RoomKey {
        val code = roomCode.uppercase()
        val usable = roomSecret?.takeIf { isValidSecret(it.uppercase()) }?.uppercase()
        val material = usable ?: code
        return RoomKey(
            bytes = hkdf(
                ikm = material.toByteArray(Charsets.UTF_8),
                salt = code.toByteArray(Charsets.UTF_8),
                info = INFO.toByteArray(Charsets.UTF_8),
                length = KEY_BYTES,
            ),
            strength = if (usable != null) EncryptionStrength.LINK_SECRET else EncryptionStrength.CODE_ONLY,
        )
    }

    /** Returns `nonce || ciphertext || tag`, base64'd, which is what goes on the wire. */
    fun seal(key: RoomKey, plaintext: String, random: SecureRandom = SecureRandom()): String {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.bytes, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        }
        val sealed = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return ENCODER.encodeToString(nonce + sealed)
    }

    /**
     * Null on anything that does not open cleanly — a peer on a different secret, a truncated
     * frame, a tampered tag. A message we cannot read is not a reason to end the session.
     */
    fun open(key: RoomKey, envelope: String): String? = try {
        val raw = DECODER.decode(envelope)
        if (raw.size <= NONCE_BYTES) {
            null
        } else {
            val nonce = raw.copyOfRange(0, NONCE_BYTES)
            val body = raw.copyOfRange(NONCE_BYTES, raw.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key.bytes, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            }
            String(cipher.doFinal(body), Charsets.UTF_8)
        }
    } catch (e: Exception) {
        null
    }

    /** RFC 5869, the twenty lines of it this needs. */
    private fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val prk = hmac(salt, ikm)
        val output = ByteArray(length)
        var block = ByteArray(0)
        var written = 0
        var counter = 1
        while (written < length) {
            block = hmac(prk, block + info + byteArrayOf(counter.toByte()))
            val take = minOf(block.size, length - written)
            block.copyInto(output, written, 0, take)
            written += take
            counter++
        }
        return output
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        // An all-zero key is a valid HMAC key, but SecretKeySpec rejects a zero-length one.
        mac.init(SecretKeySpec(if (key.isEmpty()) ByteArray(1) else key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}
