package com.theoctacoder.artryon

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
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
import com.theoctacoder.artryon.databinding.ActivityMediaPipeBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MediaPipeActivity : AppCompatActivity() , ImageSegmenterHelper.SegmenterListener{
    lateinit var fragmentCameraBinding: ActivityMediaPipeBinding

    private lateinit var imageSegmenterHelper: ImageSegmenterHelper
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_FRONT


    companion object {
        private const val TAG = "Image segmenter"
    }

    private val viewModel: MainViewModel by viewModels()

    private var backgroundExecutor: ExecutorService? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fragmentCameraBinding = ActivityMediaPipeBinding.inflate(layoutInflater)
        setContentView(fragmentCameraBinding.root)

        backgroundExecutor = Executors.newSingleThreadExecutor()
        backgroundExecutor?.execute {
            imageSegmenterHelper = ImageSegmenterHelper(
                context =this,
                runningMode = RunningMode.LIVE_STREAM,
                currentModel = viewModel.currentModel,
                currentDelegate = viewModel.currentDelegate,
                imageSegmenterListener = this
            )

            fragmentCameraBinding.viewFinder.post {
                // Set up the camera and its use cases
                setUpCamera()
            }
        }
        fragmentCameraBinding.overlayView.setRunningMode(RunningMode.LIVE_STREAM)
        // Attach listeners to UI control widgets
        initBottomSheetControls()
    }

    private fun initBottomSheetControls() {

        // When clicked, change the underlying hardware used for inference. Current options are CPU
        // GPU, and NNAPI
        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(
            viewModel.currentDelegate, false
        )
        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    imageSegmenterHelper.currentDelegate = position
                    updateControlsUi()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    /* no op */
                }
            }

        fragmentCameraBinding.bottomSheetLayout.spinnerModel.setSelection(
            viewModel.currentModel, false
        )

        fragmentCameraBinding.bottomSheetLayout.spinnerModel.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    imageSegmenterHelper.currentModel = position
                    updateControlsUi()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    /* no op */
                }
            }
    }

    private fun setUpCamera() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                bindCameraUseCases()
            }, ContextCompat.getMainExecutor(this)
        )
    }


    private fun updateControlsUi() {
        backgroundExecutor?.execute {
            imageSegmenterHelper.clearImageSegmenter()
            imageSegmenterHelper.setupImageSegmenter()
        }
        fragmentCameraBinding.overlayView.clear()
    }


    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation =
            fragmentCameraBinding.viewFinder.display.rotation
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
            .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
            .build()

        imageAnalyzer =
            ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
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
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun segment(imageProxy: ImageProxy) {
        imageSegmenterHelper.segmentLiveStreamFrame(
            imageProxy = imageProxy,
            isFrontCamera = cameraFacing == CameraSelector.LENS_FACING_FRONT)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onError(error: String, errorCode: Int) {
        runOnUiThread {
            Toast.makeText(applicationContext, error, Toast.LENGTH_SHORT).show()

            if (errorCode == ImageSegmenterHelper.GPU_ERROR) {
                fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(
                    ImageSegmenterHelper.DELEGATE_CPU, false
                )
            }
        }
    }

    override fun onResults(
        resultBundle: ImageSegmenterHelper.ResultBundle
    ) {
        fragmentCameraBinding?.let { binding ->
            binding.bottomSheetLayout.inferenceTimeVal.text =
                String.format("%d ms", resultBundle.inferenceTime)
            binding.overlayView.setResults(
                resultBundle.results,
                resultBundle.width,
                resultBundle.height
            )
            binding.overlayView.invalidate()
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

    override fun onPause() {
        super.onPause()

        backgroundExecutor?.execute {
            with(imageSegmenterHelper) {
                clearListener()
                clearImageSegmenter()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        backgroundExecutor?.shutdown()
        backgroundExecutor?.awaitTermination(
            Long.MAX_VALUE, TimeUnit.NANOSECONDS
        )
        backgroundExecutor = null
    }
}