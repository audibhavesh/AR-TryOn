package com.theoctacoder.artryon

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.permissionx.guolindev.PermissionX
import com.theoctacoder.artryon.databinding.ActivityMainBinding
import com.theoctacoder.artryon.helper.ARCoreSessionLifecycleHelper
import com.theoctacoder.artryon.helper.DepthSettings
import com.theoctacoder.artryon.helper.InstantPlacementSettings
import com.theoctacoder.artryon.mediapipe.MediaPipeListener
import com.theoctacoder.artryon.samplerender.SampleRender


//

class MainActivity : AppCompatActivity(), ImageSegmenterHelper.SegmenterListener,
    MediaPipeListener {
    lateinit var binding: ActivityMainBinding
    lateinit var renderer: HelloArRenderer


    companion object {
        private const val TAG = "AR_TRY_ON"
        const val ALPHA_COLOR = 128
    }

    lateinit var arCoreSessionHelper: ARCoreSessionLifecycleHelper
    lateinit var imageSegmenter: ImageSegmenterHelper

    var currentCoolDownPeriod = 0
    var totalCoolDownPeriod = 100

    lateinit var view: HelloArView

    val depthSettings = DepthSettings()
    val instantPlacementSettings = InstantPlacementSettings()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)

        initializeMediaPipe()
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

//        initializeAR()
//        initializeMediaPipe()
        if (!checkPermissions()) {
            requestPermissionForAR()
        }
    }

    private fun initializeAR() {

        arCoreSessionHelper = ARCoreSessionLifecycleHelper(this)

        arCoreSessionHelper.exceptionCallback =
            { exception ->
                val message =
                    when (exception) {
                        is UnavailableUserDeclinedInstallationException ->
                            "Please install Google Play Services for AR"

                        is UnavailableApkTooOldException -> "Please update ARCore"
                        is UnavailableSdkTooOldException -> "Please update this app"
                        is UnavailableDeviceNotCompatibleException -> "This device does not support AR"
                        is CameraNotAvailableException -> "Camera not available. Try restarting the app."
                        else -> "Failed to create AR session: $exception"
                    }
                Log.e(TAG, "ARCore threw an exception", exception)
                view.snackbarHelper.showError(this, message)
            }


        arCoreSessionHelper.beforeSessionResume = ::configureSession
        lifecycle.addObserver(arCoreSessionHelper)
        renderer = HelloArRenderer(this)
        lifecycle.addObserver(renderer)
        view = HelloArView(this@MainActivity)
        lifecycle.addObserver(view)

        SampleRender(view.surfaceView, renderer, assets)

        depthSettings.onCreate(this)
        instantPlacementSettings.onCreate(this)
        binding.segmentedImage.setRunningMode(RunningMode.LIVE_STREAM)


    }

    fun configureSession(session: Session) {
//    session.configure(
//      session.config.apply {
//        lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
//
//        // Depth API is used if it is configured in Hello AR's settings.
//        depthMode =
//          if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
//            Config.DepthMode.AUTOMATIC
//          } else {
//            Config.DepthMode.DISABLED
//          }
//
//        // Instant Placement is used if it is configured in Hello AR's settings.
//        instantPlacementMode =
//          if (instantPlacementSettings.isInstantPlacementEnabled) {
//            InstantPlacementMode.LOCAL_Y_UP
//          } else {
//            InstantPlacementMode.DISABLED
//          }
//      }
//    )
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }


    private fun initializeMediaPipe() {

        imageSegmenter = ImageSegmenterHelper(
            currentDelegate = ImageSegmenterHelper.DELEGATE_CPU,
            runningMode = RunningMode.LIVE_STREAM,
            currentModel = ImageSegmenterHelper.MODEL_SELFIE_MULTICLASS,
            context = this, imageSegmenterListener = this
        )
        imageSegmenter.setupImageSegmenter()
        initializeAR()
    }


    private fun requestPermissionForAR() {
        PermissionX.init(this)
            .permissions(android.Manifest.permission.CAMERA)
            .onExplainRequestReason { scope, deniedList ->
                scope.showRequestReasonDialog(
                    deniedList,
                    "Core fundamental are based on these permissions",
                    "OK",
                    "Cancel"
                )
            }
            .onForwardToSettings { scope, deniedList ->
//                scope.showForwardToSettingsDialog(
//                    deniedList,
//                    "You need to allow necessary permissions in Settings manually",
//                    "OK",
//                    "Cancel"
//                )
            }
            .request { allGranted, grantedList, deniedList ->
                if (allGranted) {

                    Toast.makeText(this, "All permissions are granted", Toast.LENGTH_LONG).show()
//                    initializeMediaPipe()
//                    initializeAR()
                } else {
                    Toast.makeText(
                        this,
                        "These permissions are denied: $deniedList",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }


    private fun updateARScene(resultBundle: ImageSegmenterHelper.ResultBundle) {
        // Assuming the cloth category has a specific index (e.g., 1)
        val clothCategoryIndex = 1

//        Log.d(
//            "IMAGE_LISTENER",
//            "${
//                resultBundle.results.limit().toString()
//            } ${resultBundle.height} ${resultBundle.width}"
//        )
//
//        // Create a bitmap from the cloth mask
//        val maskBitmap = Bitmap.createBitmap(
//            resultBundle.width,
//            resultBundle.height,
//            Bitmap.Config.ARGB_8888
//        )
//
//        var byteBuffer = resultBundle.results
//
//        val pixels = IntArray(byteBuffer.capacity());
//        val originalPixels = IntArray(resultBundle.width * resultBundle.height);
//
//
//        for (i in pixels.indices) {
//            // Using unsigned int here because selfie segmentation returns 0 or 255U (-1 signed)
//            // with 0 being the found person, 255U for no label.
//            // Deeplab uses 0 for background and other labels are 1-19,
//            // so only providing 20 colors from ImageSegmenterHelper -> labelColors
//            val index = byteBuffer.get(i).toUInt() % 20U
//            if (index == 4U) {
//                val color = ImageSegmenterHelper.labelColors[index.toInt()].toAlphaColor()
//                pixels[i] = color
//            }
//        }
//        val m: Matrix = Matrix()
//        m.preScale(-1F, 1F)
//
//        var image = Bitmap.createBitmap(
//            pixels,
//            resultBundle.originalImage.width,
//            resultBundle.originalImage.height,
//            Bitmap.Config.ARGB_8888
//        )
//        image = Bitmap.createBitmap(
//            image,
//            0,
//            0,
//            resultBundle.originalImage.width,
//            resultBundle.originalImage.height,
//            m,
//            false
//        )


        runOnUiThread {
            binding.segmentedImage.visibility = View.VISIBLE
            binding.segmentedImage.setResults(
                resultBundle.results,
                resultBundle.originalImage.width,
                resultBundle.originalImage.height
            )
        }


    }

    override fun onError(error: String, errorCode: Int) {
//        println("ERRORR STRING $error")
        Log.d(
            "IMAGE_LISTENER",
            "$error"
        )
    }

    override fun onResults(resultBundle: ImageSegmenterHelper.ResultBundle) {
//        Log.d(
//            "IMAGE_LISTENER",
//            "In Results ${resultBundle.results.capacity()}"
//        )
        updateARScene(resultBundle)

    }

    override fun onRestart() {
        super.onRestart()
//        binding.surfaceview.onResume()
    }

    override fun onPause() {
        super.onPause()
//        binding.surfaceview.onPause()
    }

    override fun segmentCurrentFrame(frame: Frame) {
        try {
            val image = frame.acquireCameraImage()
            var originalBitmap = ARImageFormat.imageToBitmap(image)
            var bitmap = ARImageFormat.rotateBitmap(bitmap = originalBitmap, -90F)
            val mpImage = BitmapImageBuilder(bitmap).build()
            imageSegmenter.segmentLiveStreamFrame2(mpImage, true)
            image.close()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}

//fun Int.toAlphaColor(): Int {
//    return Color.argb(
//        MainActivity.ALPHA_COLOR,
//        Color.red(this),
//        Color.green(this),
//        Color.blue(this)
//    )
//}