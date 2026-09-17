package com.auralis.app.network

import android.util.Base64
import android.util.Log
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

            // Upgrade stream to 320 kbps MP4/AAC
            rawUrl.replace("_96.mp4", "_320.mp4")
                .replace("_160.mp4", "_320.mp4")
        } catch (e: Exception) {
            Log.w("JioSaavnDecryptor", "Failed to decrypt media url", e)
            null
        }
    }
}
