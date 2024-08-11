package com.theoctacoder.pose3dtryon

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MyGLRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private val mvpMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)

    private var shirtModel: ShirtModel? = null

    var rotationAngle = 0f
    var translationX = 0f
    var translationY = 0f
    var scale = 1f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        // Initialize your 3D model
        shirtModel = ShirtModel(context, "shirt.obj")
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // Set up the camera view
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, -5f, 0f, 0f, 0f, 0f, 1f, 0f)

        // Calculate the projection and view transformation
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        // Apply transformations to the model matrix
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, translationX, translationY, 0f)
        Matrix.rotateM(modelMatrix, 0, rotationAngle, 0f, 1f, 0f)
        Matrix.scaleM(modelMatrix, 0, scale, scale, scale)

        // Combine the model matrix with the MVP matrix
        val finalMatrix = FloatArray(16)
        Matrix.multiplyMM(finalMatrix, 0, mvpMatrix, 0, modelMatrix, 0)

        // Draw the model
        shirtModel?.draw(finalMatrix)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio: Float = width.toFloat() / height.toFloat()

        // Apply a projection matrix
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 3f, 7f)
    }

    fun updatePose(
        leftShoulderX: Float,
        leftShoulderY: Float,
        rightShoulderX: Float,
        rightShoulderY: Float
    ) {
        shirtModel?.let {
            val centerX = (leftShoulderX + rightShoulderX) / 2
            val centerY = (leftShoulderY + rightShoulderY) / 2
            val shoulderDistance = Math.sqrt(
                Math.pow((leftShoulderX - rightShoulderX).toDouble(), 2.0) +
                        Math.pow((leftShoulderY - rightShoulderY).toDouble(), 2.0)
            ).toFloat()

            translationX = centerX
            translationY = centerY
            scale = shoulderDistance / 90f

            // Calculate rotation based on the angle between shoulders
            rotationAngle = Math.toDegrees(
                Math.atan2(
                    (rightShoulderY - leftShoulderY).toDouble(),
                    (rightShoulderX - leftShoulderX).toDouble()
                )
            ).toFloat()
        }
    }
}