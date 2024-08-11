package com.theoctacoder.pose3dtryon

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class MyGLSurfaceView : GLSurfaceView {

    private lateinit var renderer: MyGLRenderer

    constructor(context: Context) : super(context) {
        init(context)
    }

    // Constructor with Context and AttributeSet
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(context)
    }

    private fun init(context: Context) {
        // Set the OpenGL ES version
        setEGLContextClientVersion(2)

        // Set the renderer
        renderer = MyGLRenderer(context)
        setRenderer(renderer)

        // Set the render mode to render when there is a change
        renderMode = RENDERMODE_WHEN_DIRTY
    }


//    init {
//        setEGLContextClientVersion(2)  // Use OpenGL ES 2.0
//        renderer = MyGLRenderer(context)
//        setRenderer(renderer)
//        renderMode = RENDERMODE_WHEN_DIRTY  // Render only when explicitly requested
//    }

    fun updatePose(poseLandmarkerResult: PoseLandmarkerResult) {
        val landmarks = poseLandmarkerResult.landmarks()
        if (landmarks.isNotEmpty()) {
            val leftShoulder = landmarks[0][11]
            val rightShoulder = landmarks[0][12]
            renderer.updatePose(
                leftShoulder.x(),
                leftShoulder.y(),
                rightShoulder.x(),
                rightShoulder.y()
            )
            requestRender()  // Request to render the new pose
        }
    }
}
