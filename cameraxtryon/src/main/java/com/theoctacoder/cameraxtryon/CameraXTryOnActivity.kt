package com.theoctacoder.cameraxtryon

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.theoctacoder.cameraxtryon.databinding.ActivityCameraXtryOnBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CameraXTryOnActivity : AppCompatActivity(), ImageSegmenterHelper.SegmenterListener {
    lateinit var binding: ActivityCameraXtryOnBinding

    private lateinit var imageSegmenterHelper: ImageSegmenterHelper
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_FRONT

    private var backgroundExecutor: ExecutorService? = null

    private val viewModel: MainViewModel by viewModels()

    companion object {
        private const val TAG = "Image segmenter"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCameraXtryOnBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        startCameraX()
    }

    private fun startCameraX() {
        backgroundExecutor = Executors.newSingleThreadExecutor()
        backgroundExecutor?.execute {
            imageSegmenterHelper = ImageSegmenterHelper(
                context = application,
                runningMode = RunningMode.LIVE_STREAM,
                currentModel = viewModel.currentModel,
                currentDelegate = viewModel.currentDelegate,
                imageSegmenterListener = this
            )

            binding.viewFinder.post {
                // Set up the camera and its use cases
                setUpCamera()
            }
        }
        binding.overlayView.setRunningMode(RunningMode.LIVE_STREAM)
        // Attach listeners to UI control widgets
    }


    private fun setUpCamera() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(applicationContext)
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                bindCameraUseCases()
            }, ContextCompat.getMainExecutor(applicationContext)
        )
    }

    private fun updateControlsUi() {
        backgroundExecutor?.execute {
            imageSegmenterHelper.clearImageSegmenter()
            imageSegmenterHelper.setupImageSegmenter()
        }
        binding.overlayView.clear()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation =
            binding.viewFinder.display.rotation
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {

        // CameraProvider
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        // CameraSelector - makes assumption that we're only using the back camera
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(cameraFacing).build()

        // Preview. Only using the 4:3 ratio because this is the closest to our models
        preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(binding.viewFinder.display.rotation)
            .build()

        imageAnalyzer =
            ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(binding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                // The analyzer can then be assigned to the instance
                .also {
                    it.setAnalyzer(backgroundExecutor!!) { image ->
                        segment(image)
                    }
                }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalyzer
            )

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    override fun onError(error: String, errorCode: Int) {
        runOnUiThread {
            Toast.makeText(applicationContext, error, Toast.LENGTH_SHORT).show()

//            if (errorCode == ImageSegmenterHelper.GPU_ERROR) {
//                binding.bottomSheetLayout.spinnerDelegate.setSelection(
//                    ImageSegmenterHelper.DELEGATE_CPU, false
//                )
//            }
        }

    }

    override fun onResults(resultBundle: ImageSegmenterHelper.ResultBundle) {
        updateARScene(resultBundle)

    }


    override fun onPause() {
        super.onPause()
        backgroundExecutor?.execute {
            with(imageSegmenterHelper) {
                clearListener()
                clearImageSegmenter()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        backgroundExecutor?.execute {
            if (imageSegmenterHelper.isClosed()) {
                imageSegmenterHelper.setListener(this)
                imageSegmenterHelper.setupImageSegmenter()
            }
        }
    }

    private fun segment(imageProxy: ImageProxy) {
        imageSegmenterHelper.segmentLiveStreamFrame(
            imageProxy = imageProxy,
            isFrontCamera = cameraFacing == CameraSelector.LENS_FACING_FRONT
        )
    }

    override fun onDestroy() {
        super.onDestroy()

        backgroundExecutor?.shutdown()
        backgroundExecutor?.awaitTermination(
            Long.MAX_VALUE, TimeUnit.NANOSECONDS
        )
        backgroundExecutor = null
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


//        runOnUiThread {
        binding.overlayView.setResults(
            resultBundle.results,
            resultBundle.originalImage.width,
            resultBundle.originalImage.height
        )
        binding.overlayView.invalidate()
//        }


    }


}