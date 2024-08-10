package com.theoctacoder.cameraxtryon

/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
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
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

class PoseOverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results: PoseLandmarkerResult? = null
    private var pointPaint = Paint()
    private var linePaint = Paint()

    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    private var scaleBitmap: Bitmap? = null

    var shirtBitmap: Bitmap
    var shirtMatrix: Matrix? = null

    init {
        shirtBitmap = BitmapFactory.decodeResource(resources, R.drawable.shirt_red)

    }

    init {
        initPaints()
    }

    fun clear() {
        results = null
        pointPaint.reset()
        linePaint.reset()
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        linePaint.color = ContextCompat.getColor(context!!, R.color.mp_color_primary)
        linePaint.strokeWidth = LANDMARK_STROKE_WIDTH
        linePaint.style = Paint.Style.STROKE

        pointPaint.color = Color.YELLOW
        pointPaint.strokeWidth = LANDMARK_STROKE_WIDTH
        pointPaint.style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        drawPoseLandmarks(canvas)
        shirtMatrix?.let {
            // Draw the shirt on the canvas
            canvas.drawBitmap(shirtBitmap, it, null)
        }
    }

    private fun drawPoseLandmarks(canvas: Canvas) {
        results?.let { poseLandmarkerResult ->
            for (landmark in poseLandmarkerResult.landmarks()) {

                for (normalizedLandmark in landmark) {
                    canvas.drawPoint(
                        normalizedLandmark.x() * imageWidth * scaleFactor,
                        normalizedLandmark.y() * imageHeight * scaleFactor,
                        pointPaint
                    )
                }

                PoseLandmarker.POSE_LANDMARKS.forEach {
                    canvas.drawLine(
                        poseLandmarkerResult.landmarks().get(0).get(it!!.start())
                            .x() * imageWidth * scaleFactor,
                        poseLandmarkerResult.landmarks().get(0).get(it.start())
                            .y() * imageHeight * scaleFactor,
                        poseLandmarkerResult.landmarks().get(0).get(it.end())
                            .x() * imageWidth * scaleFactor,
                        poseLandmarkerResult.landmarks().get(0).get(it.end())
                            .y() * imageHeight * scaleFactor,
                        linePaint
                    )
                }
            }
        }
    }

    fun setResults(
        poseLandmarkerResults: PoseLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE
    ) {
        results = poseLandmarkerResults

        this.imageHeight = imageHeight
        this.imageWidth = imageWidth

        scaleFactor = when (runningMode) {
            RunningMode.IMAGE, RunningMode.VIDEO -> {
                min(width * 1f / imageWidth, height * 1f / imageHeight)
            }

            RunningMode.LIVE_STREAM -> {
                // PreviewView is in FILL_START mode. So we need to scale up the
                // landmarks to match with the size that the captured images will be
                // displayed.
                max(width * 1f / imageWidth, height * 1f / imageHeight)
            }
        }
        results?.let {
            val landmarks = it.landmarks()
            if (landmarks.isNotEmpty()) {
                val leftShoulder = landmarks[0][11]
                val rightShoulder = landmarks[0][12]
                val shirtCenterX =
                    ((leftShoulder.x() * imageWidth * scaleFactor + rightShoulder.x() * imageWidth * scaleFactor) / 2)
                val shirtCenterY =
                    ((leftShoulder.y() * imageHeight * scaleFactor + rightShoulder.y() * imageHeight * scaleFactor) / 2)
                val shoulderDistance = Math.sqrt(
                    Math.pow(
                        (leftShoulder.x() * imageWidth * scaleFactor - rightShoulder.x() * imageWidth * scaleFactor).toDouble(),
                        2.0
                    ) + Math.pow(
                        (leftShoulder.y() * imageHeight * scaleFactor - rightShoulder.y() * imageHeight * scaleFactor).toDouble(),
                        2.0
                    )
                ).toFloat()

                var shirtScale =
                    shoulderDistance / shirtBitmap.width // referenceShirtWidth is a predefined constant

                shirtScale += 0.2f
                var xOffset = 50F
                var yOffset = 50F

                xOffset = (xOffset * shirtScale * 45) / 2
                yOffset = (yOffset * shirtScale * 22) / 2
                println("Shirt Scale $shirtScale XOff $xOffset YOFF $yOffset")

                if (shirtScale > 0.6) {
                    xOffset = 750f // Adjust this value based on trial
                    yOffset = 450f
//                    shirtScale = 0.7f
                }
//                if (shirtScale < 0.4) {
//                    xOffset = 430f // Adjust this value based on trial
//                    yOffset = 200f
//                } else if (shirtScale < 0.5) {
//                    xOffset = 470f // Adjust this value based on trial
//                    yOffset = 200f
//                } else {
//                    xOffset = 750f // Adjust this value based on trial
//                    yOffset = 450f
//                    shirtScale = 0.7f
//                }

                val angle = atan2(
                    (rightShoulder.y() * imageHeight * scaleFactor - leftShoulder.y() * imageHeight * scaleFactor),
                    (rightShoulder.x() * imageWidth * scaleFactor - leftShoulder.x() * imageWidth * scaleFactor)
                ) * (180 / Math.PI).toFloat()
                val adjustedAngle = if (angle > 90 || angle < -90) angle + 180 else angle

//                val perspective = 0.9f // Adjust as needed
//                val matrixValues = FloatArray(9)

                shirtMatrix = Matrix()
                shirtMatrix?.postScale(shirtScale, shirtScale)

//                shirtMatrix?.getValues(matrixValues)
//                matrixValues[Matrix.MSKEW_X] = perspective
//                matrixValues[Matrix.MSKEW_Y] = perspective
//                shirtMatrix?.setValues(matrixValues)

//                val rotationMatrix = Matrix()
//                rotationMatrix.postRotate(
//                    rotationX,
//                    shirtBitmap.width / 2f,
//                    shirtBitmap.height / 2f
//                )
//                rotationMatrix.postRotate(
//                    rotationY,
//                    shirtBitmap.width / 2f,
//                    shirtBitmap.height / 2f
//                )
//                shirtMatrix?.postConcat(rotationMatrix)
//                shirtMatrix?.postRotate(
//                    adjustedAngle,
//                    shirtBitmap.width / 2f,
//                    shirtBitmap.height / 2f
//                )

                shirtMatrix?.postTranslate(shirtCenterX - xOffset, shirtCenterY - yOffset)

                // Draw the shirt on the canvas
                invalidate()
            }
        }
    }

    companion object {
        private const val LANDMARK_STROKE_WIDTH = 12F
    }
}