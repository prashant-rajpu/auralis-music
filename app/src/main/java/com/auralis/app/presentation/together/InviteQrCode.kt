package com.auralis.app.presentation.together

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * The invite as a square you can hold up to someone's phone.
 *
 * Rendered at one pixel per module and scaled with nearest-neighbour filtering, rather than asked
 * for at display size: a QR code is not an image to be resampled, and a smoothed one scans badly.
 */
@Composable
fun InviteQrCode(
    content: String,
    modifier: Modifier = Modifier,
    foreground: Color,
    background: Color,
) {
    val bitmap = remember(content, foreground, background) {
        qrBitmap(content, foreground.toArgb(), background.toArgb())
    } ?: return

    Image(
        bitmap = bitmap,
        contentDescription = "Scan to join this listening session",
        modifier = modifier,
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.None,
    )
}

private fun qrBitmap(content: String, foreground: Int, background: Int): ImageBitmap? = try {
    val hints = mapOf(
        // The invite carries a secret, so the payload is long; medium correction keeps the module
        // count sane while still surviving a fingerprint on the screen.
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 1,
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
    val width = matrix.width
    val height = matrix.height
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            pixels[row + x] = if (matrix[x, y]) foreground else background
        }
    }
    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
} catch (e: Exception) {
    null
}
