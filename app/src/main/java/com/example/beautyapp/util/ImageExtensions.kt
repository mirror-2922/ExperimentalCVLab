package com.example.beautyapp.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

fun ImageProxy.toBitmapFiltered(): Bitmap? {
    if (format == 1 || format == 42) { // RGBA_8888
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
        return bitmap
    }

    if (format == ImageFormat.YUV_420_888) {
        val yuvBytes = yuv420ToNv21(this)
        val yuvImage = YuvImage(yuvBytes, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
    
    return null
}

private fun yuv420ToNv21(image: ImageProxy): ByteArray {
    val width = image.width
    val height = image.height
    val ySize = width * height
    val uvSize = width * height / 2
    val nv21 = ByteArray(ySize + uvSize)

    val yPlane = image.planes[0]
    val uPlane = image.planes[1]
    val vPlane = image.planes[2]

    val yBuffer = yPlane.buffer
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer

    var rowStride = yPlane.rowStride
    var pos = 0

    // Copy Y
    if (rowStride == width) {
        yBuffer.get(nv21, 0, ySize)
        pos += ySize
    } else {
        for (row in 0 until height) {
            yBuffer.position(row * rowStride)
            yBuffer.get(nv21, pos, width)
            pos += width
        }
    }

    // Copy UV
    // NV21 is Y..Y VUVU...
    rowStride = vPlane.rowStride
    val pixelStride = vPlane.pixelStride

    val uvWidth = width / 2
    val uvHeight = height / 2

    // Check if we can do a direct copy (special case where pixelStride == 2 and data is interleaved)
    // Often uBuffer and vBuffer are aliases into the same memory block.
    // However, handling it generically pixel-by-pixel is safer for "green/purple" issues.
    
    val vBytes = ByteArray(vBuffer.remaining())
    val uBytes = ByteArray(uBuffer.remaining())
    vBuffer.get(vBytes)
    uBuffer.get(uBytes)

    for (row in 0 until uvHeight) {
        for (col in 0 until uvWidth) {
            val vIndex = row * rowStride + col * pixelStride
            val uIndex = row * uPlane.rowStride + col * uPlane.pixelStride
            
            // Safety check
            if (pos < nv21.size && vIndex < vBytes.size && uIndex < uBytes.size) {
                nv21[pos++] = vBytes[vIndex]
                nv21[pos++] = uBytes[uIndex]
            }
        }
    }

    return nv21
}