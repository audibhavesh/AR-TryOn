package com.theoctacoder.artryon

/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.View
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.mediapipe.tasks.vision.core.RunningMode
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min


class OverlayView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {
    companion object {
        const val ALPHA_COLOR = 128
    }

    var shirtBitmap: Bitmap

    init {
        shirtBitmap = BitmapFactory.decodeResource(resources, R.drawable.shirt)
    }

    private var scaleBitmap: Bitmap? = null
    private var runningMode: RunningMode = RunningMode.IMAGE

    fun clear() {
        scaleBitmap = null
        invalidate()
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        scaleBitmap?.let {

            val left = (width - scaleBitmap!!.width) / 2f
            val top = (height - scaleBitmap!!.height).toFloat()
//            println("TOPPP ${(height - scaleBitmap!!.height) / 2F} $top")
//            canvas.drawBitmap(it, 0f, 0f, null)

            val boundingBox = calculateBoundingBox(it)

            val scaleX = boundingBox.width().toFloat() / shirtBitmap.width
            val scaleY = boundingBox.height().toFloat() / shirtBitmap.height
            matrix.postScale(scaleX, scaleY)
            // Position the shirt image over the cloth segment
            matrix.postTranslate(boundingBox.left.toFloat(), boundingBox.top.toFloat())
//            matrix.postTranslate(left.toFloat(), top.toFloat())

            // Apply the transformation to the shirt image
//            val transformedShirtBitmap = Bitmap.createBitmap(
//                shirtBitmap, 0, 0, shirtBitmap.width, shirtBitmap.height, matrix, true
//            )

            println("BOUNDDDING WIDTH HEIGHT ${boundingBox.width()} ${boundingBox.height()} $scaleX $scaleY")
            var transformedShirtBitmap = Bitmap.createBitmap(
                shirtBitmap, 0, 0, shirtBitmap.width, shirtBitmap.height, matrix, true
            )
            transformedShirtBitmap =
                Bitmap.createScaledBitmap(transformedShirtBitmap, it.width, it.height, false)
            canvas.drawBitmap(
                transformedShirtBitmap,
                boundingBox.left.toFloat()-310 ,
                boundingBox.top.toFloat() - 200,
                null
            )
//            canvas.drawBitmap(it, left, top, null)
        }
    }

    fun setRunningMode(runningMode: RunningMode) {
        this.runningMode = runningMode
    }

    fun setResults(
        byteBuffer: ByteBuffer,
        outputWidth: Int,
        outputHeight: Int
    ) {
        findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            // Create the mask bitmap with colors and the set of detected labels.
            val pixels = IntArray(byteBuffer.capacity())
            for (i in pixels.indices) {
                // Using unsigned int here because selfie segmentation returns 0 or 255U (-1 signed)
                // with 0 being the found person, 255U for no label.
                // Deeplab uses 0 for background and other labels are 1-19,
                // so only providing 20 colors from ImageSegmenterHelper -> labelColors
                val index = byteBuffer.get(i).toUInt() % 20U
                if (index == 4U) {
                    val color = ImageSegmenterHelper.labelColors[index.toInt()].toAlphaColor()
                    pixels[i] = color
                }
            }
            val m: Matrix = Matrix()
            m.preScale(-1F, 1F)


            var scaleFactor = when (runningMode) {
                RunningMode.IMAGE,
                RunningMode.VIDEO -> {
                    min(width * 1f / outputWidth, height * 1f / outputHeight)
                }

                RunningMode.LIVE_STREAM -> {
                    // PreviewView is in FILL_START mode. So we need to scale up the
                    // landmarks to match with the size that the captured images will be
                    // displayed.
                    max(width * 1F / outputWidth, height * 1f / outputHeight)
                }
            }


            val metrics: DisplayMetrics = context.resources.displayMetrics

            var image = Bitmap.createBitmap(
                pixels,
                outputWidth,
                outputHeight,
                Bitmap.Config.ARGB_8888
            )


            image = Bitmap.createBitmap(
                image,
                0,
                0,
                outputWidth,
                outputHeight,
                m,
                false
            )

            scaleFactor = 6.8F

            val scaleWidth = (outputWidth * scaleFactor).toInt()
            val scaleHeight = (outputHeight * scaleFactor).toInt()
//        val scaleWidth =(width*1.3).toInt()
//        val scaleHeight = (height).toInt()
//        println("DFFFFFF $scaleFactor $outputWidth $outputHeight $width $height ${metrics.widthPixels} ${metrics.heightPixels} $scaleWidth $scaleHeight ")
//        scaleBitmap = scaleBitmapHeight(image, height)
//        scaleBitmap?.let {
//            scaleBitmap=scaleBitmapToFit(scaleBitmap!!,scaleBitmap!!.width,scaleBitmap!!.height)
//        }

            scaleBitmap = Bitmap.createScaledBitmap(image, width + 500, height, true)
//        scaleBitmap = Bitmap.createScaledBitmap(
//            image, scaleWidth - 1, scaleHeight + 100, false
//        )

// Assuming you have the dimensions of the segmented torso area


            invalidate()
        }
    }

    fun calculateBoundingBox(mask: Bitmap): Rect {
        var minX = mask.width
        var minY = mask.height
        var maxX = 0
        var maxY = 0

        for (y in 0 until mask.height) {
            for (x in 0 until mask.width) {
                val alpha = Color.alpha(mask.getPixel(x, y))
                if (alpha > 0) {  // This checks if the pixel is part of the segmented area
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        return Rect(minX, minY, maxX - minX, maxY - minY)
    }

    fun scaleBitmapToFit(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val scaledWidth: Int
        val scaledHeight: Int

        if (bitmap.width > bitmap.height) {
            // Width is greater, scale by width
            scaledWidth = targetWidth
            scaledHeight = (targetWidth / aspectRatio).toInt()
        } else {
            // Height is greater or equal, scale by height
            scaledWidth = (targetHeight * aspectRatio).toInt()
            scaledHeight = targetHeight
        }

        return Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
    }

    fun scaleBitmapAndKeepRation(
        targetBmp: Bitmap,
        reqWidthInPixels: Int,
        reqHeightInPixels: Int,
    ): Bitmap {
        val matrix = Matrix()
        matrix.setRectToRect(
            RectF(0f, 0f, targetBmp.width.toFloat(), targetBmp.height.toFloat()),
            RectF(0f, 0f, reqWidthInPixels.toFloat(), reqHeightInPixels.toFloat()),
            Matrix.ScaleToFit.CENTER
        )
        val scaledBitmap =
            Bitmap.createBitmap(targetBmp, 0, 0, targetBmp.width, targetBmp.height, matrix, true)
        return scaledBitmap
    }

    private fun scaleBitmapHeight(bitmap: Bitmap, targetHeight: Int): Bitmap {
        val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val scaledHeight = targetHeight
        val scaledWidth = (targetHeight * aspectRatio).toInt()

        return Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
    }
}

fun Int.toAlphaColor(): Int {
    return Color.argb(
        OverlayView.ALPHA_COLOR,
        Color.red(this),
        Color.green(this),
        Color.blue(this)
    )
}