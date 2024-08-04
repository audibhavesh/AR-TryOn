package com.theoctacoder.aropengl

import BackgroundRenderer
import android.content.Context
import android.hardware.display.DisplayManager
import android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.Surface
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.ar.core.Camera
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableException
import com.theoctacoder.aropengl.databinding.ActivityArBinding
import java.io.IOException
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10


class ARActivity : AppCompatActivity(), GLSurfaceView.Renderer {

    private val TAG = "ARActivity"

    lateinit var binding: ActivityArBinding

    lateinit var glSurfaceView: GLSurfaceView
    private var arSession: Session? = null

    private var cameraTextureId = 0
    private var backgroundRenderer: BackgroundRenderer? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityArBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


        glSurfaceView = binding.glSurfaceView

        glSurfaceView.setPreserveEGLContextOnPause(true);
        glSurfaceView.setEGLContextClientVersion(2);
        glSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        glSurfaceView.setRenderer(this);
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        try {
//            if (ArCoreApk.getInstance()
//                    .requestInstall(this, true) == ArCoreApk.InstallStatus.INSTALLED
//            ) {

                arSession = Session(this)
                val config: Config = Config(arSession)
                config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                arSession?.configure(config)
//            }
        } catch (e: UnavailableException) {
            e.printStackTrace()
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {

        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)


        // Create the camera texture
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        cameraTextureId = textures[0]
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, cameraTextureId);
        GLES20.glTexParameteri(
            GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR
        );
        GLES20.glTexParameteri(
            GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR
        );
        GLES20.glTexParameteri(
            GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        );
        GLES20.glTexParameteri(
            GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        );

        // Initialize the background renderer
        backgroundRenderer = BackgroundRenderer(cameraTextureId)
        try {
            backgroundRenderer?.createOnGlThread(this)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to create background renderer", e)
        }
    }


    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height);
        val display = getDisplayCurr()
        val rotation = display.rotation
        val displayRotation = when (rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        arSession?.setDisplayGeometry(displayRotation, width, height)

        Log.d(TAG, "Display Rotation: $displayRotation")
        Log.d(TAG, "Viewport Width: $width, Height: $height")
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        if (arSession == null) {
            return
        }


        // Update session and get current frame
        try {
            arSession!!.setCameraTextureName(cameraTextureId)
            val frame = arSession!!.update()
            val camera: Camera = frame.camera

            // Draw background
            backgroundRenderer!!.draw(frame)

            // Get projection matrix
            val projmtx = FloatArray(16)
            camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f)
//
//            // Get view matrix
            val viewmtx = FloatArray(16)
            camera.getViewMatrix(viewmtx, 0)
//
//            // Compute lighting from average intensity of the image
            val colorCorrectionRgba = FloatArray(4)
            frame.lightEstimate.getColorCorrection(colorCorrectionRgba, 0)

            // Visualize tracked points
            // (Omitted in this example, but you can add point cloud rendering here)

            // Visualize planes
            // (Omitted in this example, but you can add plane visualization here)

            // Visualize anchors
            // (Omitted in this example, but you can add anchor visualization here)

        } catch (t: Throwable) {
            Log.e(TAG, "Exception on the OpenGL thread", t)
        }
    }


    public override fun onResume() {
        super.onResume()
        if (arSession != null) {
            try {
                arSession!!.resume()
            } catch (e: CameraNotAvailableException) {
                e.printStackTrace()
                arSession = null
            }
        }
    }


    public override fun onPause() {
        super.onPause()
        if (arSession != null) {
            arSession!!.pause()
        }
    }
     fun getDisplayCurr(): Display {
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return displayManager.getDisplay(Display.DEFAULT_DISPLAY)
    }

}