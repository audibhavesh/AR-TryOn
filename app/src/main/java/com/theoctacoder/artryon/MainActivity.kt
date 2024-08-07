package com.theoctacoder.artryon

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.media.Image
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.lifecycle.lifecycleScope
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Config
import com.google.ar.core.TrackingFailureReason
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.framework.image.MediaImageBuilder
import com.google.mediapipe.framework.image.MediaImageExtractor
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.permissionx.guolindev.PermissionX
import com.theoctacoder.artryon.databinding.ActivityMainBinding
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.getDescription
import io.github.sceneview.ar.node.AnchorNode
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer


enum class Category(val id: Int) {
    BACKGROUND(0),
    HAIR(1),
    BODY_SKIN(2),
    FACE_SKIN(3),
    CLOTHES(4),
    OTHERS(5)
}

//

class MainActivity : AppCompatActivity(), ImageSegmenterHelper.SegmenterListener {
    lateinit var binding: ActivityMainBinding

    companion object {
        const val ALPHA_COLOR = 128
    }


    var currentCoolDownPeriod = 0
    var coolDownPeriod = 300

    lateinit var sceneView: ARSceneView
    lateinit var loadingView: View
    lateinit var instructionText: TextView

    lateinit var imageSegmenter: ImageSegmenterHelper


    var isLoading = false
        set(value) {
            field = value
            loadingView.isGone = !value
        }

    var anchorNode: AnchorNode? = null
        set(value) {
            if (field != value) {
                field = value
                updateInstructions()
            }
        }

    var trackingFailureReason: TrackingFailureReason? = null
        set(value) {
            if (field != value) {
                field = value
                updateInstructions()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sceneView = binding.sceneView
        instructionText = binding.instructionText
        loadingView = binding.loadingView
        initializeMediaPipe()
        if (!checkPermissions()) {
            requestPermissionForAR()
        }
    }

    private fun initializeAR() {
        sceneView.apply {

            lifecycle = this@MainActivity.lifecycle
            planeRenderer.isEnabled = true

            configureSession { session, config ->
                val filter = CameraConfigFilter(session)
                filter.setFacingDirection(CameraConfig.FacingDirection.FRONT)
                session.cameraConfig = session.getSupportedCameraConfigs(filter)[0]
                config.depthMode = when (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    true -> Config.DepthMode.AUTOMATIC
                    else -> Config.DepthMode.DISABLED
                }
                config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR

            }
            onSessionUpdated = { _, frame ->
                if (anchorNode == null) {
                    try {
                        val image = frame.acquireCameraImage()
                        analyze(image)
                        image.close()
                    }
                    catch (e:Exception){

                    }
//                    frame.getUpdatedPlanes()
//                        .firstOrNull { it.type == Plane.Type.HORIZONTAL_UPWARD_FACING }
//                        ?.let { plane ->
//                            try {
//                                Log.d(
//                                    "IMAGE_LISTENER",
//                                    "Started"
//                                )
//                                val image = frame.acquireCameraImage()
//                                analyze(image)
//                                image.close()
//                            } catch (e: Exception) {
//                                e.printStackTrace()
//                            }
//
//                        }
                }
            }
            onTrackingFailureChanged = { reason ->
                this@MainActivity.trackingFailureReason = reason
            }
        }
    }

    private fun analyze(image: Image) {
        lifecycleScope.launch {
            runBlocking {
                if (currentCoolDownPeriod == 0) {

//                    var bitmap = ARImageFormat.rotateBitmap(
//                        ARImageFormat.imageToBitmap(
//                            image
//                        ), 90f
//                    )
//
//                    //                                binding.segmentedImage.setImageBitmap(
//                    //                                    bitmap
//                    //                                )
//                    Log.d(
//                        "IMAGE_LISTENER 1",
//                        "START"
//                    )
//                    val byteBuffer =
//                        ByteBuffer.allocate(bitmap.width * bitmap.height * 4)
//                    bitmap.copyPixelsToBuffer(byteBuffer)
//                    byteBuffer.rewind()
//                    var mpImage = ByteBufferImageBuilder(
//                        byteBuffer, bitmap.width, bitmap.height,
//                        MPImage.IMAGE_FORMAT_RGB
//                    ).build()

                    binding.overlayView.clear()
                    var mpImage=BitmapImageBuilder(ARImageFormat.imageToBitmap(image)).build()
                    imageSegmenter.segmentLiveStreamFrame2(mpImage, false)
                    Log.d(
                        "IMAGE_LISTENER 1",
                        "Ended"
                    )
                    currentCoolDownPeriod = coolDownPeriod
                    //                                imageResult?.let {
                    //                                    Log.d(
                    //                                        "IMAGE_RESULT",
                    //                                        imageResult.timestampMs().toString()
                    //                                    )
                    //                                    imageSegmenter.returnSegmentationResult(
                    //                                        it,
                    //                                        mpImage
                    //                                    )
                    //                                }
                }
                coolDownPeriod--

            }
        }
    }


    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun updateInstructions() {
        instructionText.text =
            (trackingFailureReason?.getDescription(this) ?: if (anchorNode == null) {
                getString(R.string.point_your_phone_down)
            } else {
                null
            }).toString()
    }

    private fun initializeMediaPipe() {
        lifecycleScope.launch {
            runBlocking {
                imageSegmenter = ImageSegmenterHelper(
                    currentDelegate = ImageSegmenterHelper.DELEGATE_CPU,
                    runningMode = RunningMode.LIVE_STREAM,
                    currentModel = ImageSegmenterHelper.MODEL_SELFIE_MULTICLASS,
                    context = applicationContext, imageSegmenterListener = this@MainActivity
                )
                initializeAR()
            }
        }

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
//            .onForwardToSettings { scope, deniedList ->
//                scope.showForwardToSettingsDialog(
//                    deniedList,
//                    "You need to allow necessary permissions in Settings manually",
//                    "OK",
//                    "Cancel"
//                )
//            }
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

        Log.d(
            "IMAGE_LISTENER 2",
            "${
                resultBundle.results.limit().toString()
            } ${resultBundle.height} ${resultBundle.width}"
        )
        if (resultBundle.results.hasArray()) {

            // Create a bitmap from the cloth mask
            val maskBitmap = Bitmap.createBitmap(
                resultBundle.width,
                resultBundle.height,
                Bitmap.Config.ARGB_8888
            )

            var byteBuffer = resultBundle.results

            val pixels = IntArray(byteBuffer.capacity());
            val originalPixels = IntArray(resultBundle.width * resultBundle.height);


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
            val image = Bitmap.createBitmap(
                pixels,
                resultBundle.width,
                resultBundle.height,
                Bitmap.Config.ARGB_8888
            )

            binding.segmentedImage.visibility = View.VISIBLE
            binding.segmentedImage.setImageBitmap(image)
        }

    }

    override fun onError(error: String, errorCode: Int) {
        Log.d(
            "IMAGE_LISTENER EE",
            "$error"
        )
    }

    override fun onResults(resultBundle: ImageSegmenterHelper.ResultBundle) {
        Log.d(
            "IMAGE_LISTENER RR",
            "${resultBundle.results.limit()}"
        )

        binding.overlayView.setResults(
            resultBundle.results,
            resultBundle.width,
            resultBundle.height
        )
        binding.overlayView.invalidate()
//        updateARScene(resultBundle)

    }


}

