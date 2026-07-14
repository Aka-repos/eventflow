package com.app.eventflow.ui.feature.qr

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/** Renderiza el token opaco (JWS) como bitmap QR. El cliente jamás interpreta el contenido. */
fun encodeQrBitmap(content: String, sizePx: Int = 720): Bitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
    }
    return bitmap
}
