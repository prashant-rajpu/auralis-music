package com.auralis.app.data.remote.jiosaavn

import android.util.Base64
import android.util.Log
import com.auralis.app.domain.model.AudioQualitySetting
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object JioSaavnDecryptor {
    private const val DES_KEY = "38346591"

    /**
     * Decrypts the JioSaavn encrypted media url using DES in ECB mode with PKCS5 padding,
     * then upgrades the audio link to 320 kbps CD quality.
     */
    fun decryptMediaUrl(encryptedUrl: String?): String? {
        if (encryptedUrl.isNullOrBlank()) return null
        return try {
            val keyBytes = DES_KEY.toByteArray(Charsets.UTF_8)
            val keySpec = SecretKeySpec(keyBytes, "DES")
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)

            val decodedBytes = Base64.decode(encryptedUrl, Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            val rawUrl = String(decryptedBytes, Charsets.UTF_8).trim()

            withQuality(rawUrl, AudioQualitySetting.HIGH)
        } catch (e: Exception) {
            Log.w("JioSaavnDecryptor", "Failed to decrypt media url", e)
            null
        }
    }

    private val BITRATE_SUFFIX = Regex("""_(96|160|320)\.mp4""")

    /** JioSaavn serves the same file at 96, 160 and 320 kbps; pick the variant for the setting. */
    fun withQuality(url: String, quality: AudioQualitySetting): String {
        val kbps = when (quality) {
            AudioQualitySetting.HIGH -> 320
            AudioQualitySetting.STANDARD -> 160
            AudioQualitySetting.DATA_SAVER -> 96
        }
        return url.replace(BITRATE_SUFFIX, "_$kbps.mp4")
    }
}
