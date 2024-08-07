package com.theoctacoder.artryon

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.media.Image
import java.nio.ByteBuffer


object ARImageFormat {

    fun convertYuvToRgb(image: Image): Bitmap {
        require(image.format == ImageFormat.YUV_420_888) { "Unsupported image format: " + image.format }

        val width = image.width
        val height = image.height
        val rgbBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer[nv21, 0, ySize]
        uBuffer[nv21, ySize, uSize / 2]
        vBuffer[nv21, ySize + uSize / 2, vSize / 2]

        val argb8888 = IntArray(width * height)
        decodeYUV420SP(argb8888, nv21, width, height)
        rgbBitmap.setPixels(argb8888, 0, width, 0, 0, width, height)

        // Resize the bitmap to 256x256
        val resizedBitmap = Bitmap.createScaledBitmap(rgbBitmap, 256, 256, true)
        return resizedBitmap
    }

    private fun decodeYUV420SP(rgb: IntArray, yuv420sp: ByteArray, width: Int, height: Int) {
        val frameSize = width * height

        var j = 0
        var yp = 0
        while (j < height) {
            var uvp = frameSize + (j shr 1) * width
            var u = 0
            var v = 0
            var i = 0
            while (i < width) {
                var y = (0xff and (yuv420sp[yp].toInt())) - 16
                if (y < 0) y = 0
                if ((i and 1) == 0) {
                    v = (0xff and yuv420sp[uvp++].toInt()) - 128
                    u = (0xff and yuv420sp[uvp++].toInt()) - 128
                }

                val y1192 = 1192 * y
                var r = (y1192 + 1634 * v)
                var g = (y1192 - 833 * v - 400 * u)
                var b = (y1192 + 2066 * u)

                if (r < 0) r = 0
                else if (r > 262143) r = 262143
                if (g < 0) g = 0
                else if (g > 262143) g = 262143
                if (b < 0) b = 0
                else if (b > 262143) b = 262143

                rgb[yp] =
                    -0x1000000 or ((r shl 6) and 0xff0000) or ((g shr 2) and 0xff00) or ((b shr 10) and 0xff)
                i++
                yp++
            }
            j++
        }
    }

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
            ImageFormat.YUV_420_888 -> convertYuvToRgb(image)
            ImageFormat.JPEG -> jpegImageToBitmap(image)
            else -> throw IllegalArgumentException("Unsupported image format: ${image.format}")
        }
    }

    fun jpegImageToBitmap(image: Image): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
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
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
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


     fun toBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height, Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }
}