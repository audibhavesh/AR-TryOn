package com.theoctacoder.artryon

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.media.Image
import java.nio.ByteBuffer

object ARImageManager {
    fun byteBufferToBitmap(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        format: Bitmap.Config
    ): Bitmap {
        buffer.rewind() // Ensure the buffer's position is set to the beginning
        val bitmap = Bitmap.createBitmap(width, height, format)
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun imageToBitmap(image: Image): Bitmap {
        return when (image.format) {
            ImageFormat.YUV_420_888 -> yuv420888ToBitmap(image)
            ImageFormat.JPEG -> jpegImageToBitmap(image)
            else -> throw IllegalArgumentException("Unsupported image format: ${image.format}")
        }
    }

    fun jpegImageToBitmap(image: Image): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val originalBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val rgbaBitmap: Bitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)

        return rgbaBitmap
    }

    fun yuv420888ToBitmap(image: Image): Bitmap {
        val yBuffer = image.planes[0].buffer // Y
        val uBuffer = image.planes[1].buffer // U
        val vBuffer = image.planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // U and V are swapped
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage =
            android.graphics.YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, image.width, image.height), 100, out)
        val imageBytes = out.toByteArray()
        val originalBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        val rgbaBitmap: Bitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)

        return rgbaBitmap
    }


    fun imageToByteBuffer(image: Image): ByteBuffer {
        val planes = image.planes
        val yPlane = planes[0].buffer
        val uPlane = planes[1].buffer
        val vPlane = planes[2].buffer

        val ySize = yPlane.remaining()
        val uSize = uPlane.remaining()
        val vSize = vPlane.remaining()

        val totalSize = ySize + uSize + vSize

        val byteBuffer = ByteBuffer.allocateDirect(totalSize)

        // Copy Y channel
        byteBuffer.put(yPlane)
        // Copy U channel
        byteBuffer.put(uPlane)
        // Copy V channel
        byteBuffer.put(vPlane)

        byteBuffer.rewind()
        return byteBuffer
    }
}