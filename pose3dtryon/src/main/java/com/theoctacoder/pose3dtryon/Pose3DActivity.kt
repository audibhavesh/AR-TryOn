package com.theoctacoder.pose3dtryon

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
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
import androidx.lifecycle.lifecycleScope
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.theoctacoder.pose3dtryon.databinding.ActivityPose3DactivityBinding
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class Pose3DActivity : AppCompatActivity(), PoseLandmarkerHelper.LandmarkerListener {

    lateinit var binding: ActivityPose3DactivityBinding

    companion object {
        private const val TAG = "Pose Landmarker"
    }

    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper
    private val viewModel: PoseViewModel by viewModels()
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_FRONT

    private var modelInstance: ModelInstance? = null

    private lateinit var backgroundExecutor: ExecutorService

    lateinit var renderer: ShirtRenderer

    var childNoes = mutableListOf<ModelNode>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPose3DactivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        renderer = ShirtRenderer(this)


        setupSceneView()
        setupCameraX()
        setupRajawaliSurface()
        binding.flipCamera.setOnClickListener {
            toggleCamera()
        }
    }

    private fun setupRajawaliSurface() {

        binding.rajawaliSurface.setTransparent(true)
        binding.rajawaliSurface.setZOrderOnTop(true)
        binding.rajawaliSurface.setSurfaceRenderer(renderer)
        binding.rajawaliSurface.requestRenderUpdate()
    }

    private fun setupSceneView() {
        lifecycleScope.launch {
//            val hdrFile = "studio_small_09_2k.hdr"
//
//            binding.sceneView.environmentLoader.loadHDREnvironment(hdrFile).apply {
//                binding.sceneView.indlirectLight = this?.indirectLight
//                binding.sceneView.skybox = this?.skybox
//            }
//            binding.sceneView.isVisible = false

            binding.sceneView.cameraNode.apply {
                position = Position(z = 4.0f)
            }

            addModel()
        }
    }

    fun addModel() {
        val modelFile = "MaterialSuite.glb"
        modelInstance = binding.sceneView.modelLoader.createModelInstance(modelFile)
        binding.sceneView.cameraNode.apply {
            position = Position(z = 4.0f)
        }

        modelInstance?.let {
            val modelNode = ModelNode(
                modelInstance = it,
                scaleToUnits = 2.0f,
            )
            modelNode.scale = Scale(0.05f)
            binding.sceneView.addChildNode(modelNode)
        }
    }

    fun toggleCamera() {

        if (cameraFacing == CameraSelector.LENS_FACING_FRONT) cameraFacing =
            CameraSelector.LENS_FACING_BACK;
        else if (cameraFacing == CameraSelector.LENS_FACING_BACK) cameraFacing =
            CameraSelector.LENS_FACING_FRONT;

        setupCameraX()
    }

    private fun setupCameraX() {
        backgroundExecutor = Executors.newSingleThreadExecutor()

        // Wait for the views to be properly laid out
        binding.viewFinder.post {
            // Set up the camera and its use cases
            setUpCamera()
        }

        // Create the PoseLandmarkerHelper that will handle the inference
        backgroundExecutor.execute {
            poseLandmarkerHelper = PoseLandmarkerHelper(
                context = applicationContext,
                runningMode = RunningMode.LIVE_STREAM,
                minPoseDetectionConfidence = viewModel.currentMinPoseDetectionConfidence,
                minPoseTrackingConfidence = viewModel.currentMinPoseTrackingConfidence,
                minPosePresenceConfidence = viewModel.currentMinPosePresenceConfidence,
                currentDelegate = viewModel.currentDelegate,
                currentModel = viewModel.currentModel,
                poseLandmarkerHelperListener = this
            )
        }
    }

    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(applicationContext)
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                bindCameraUseCases()
            }, ContextCompat.getMainExecutor(applicationContext)
        )
    }

    // Declare and bind preview, capture and analysis use cases
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {

        // CameraProvider
        val cameraProvider =
            cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        val cameraSelector = CameraSelector.Builder().requireLensFacing(cameraFacing).build()

        // Preview. Only using the 4:3 ratio because this is the closest to our models
        preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(binding.viewFinder.display.rotation).build()

        // ImageAnalysis. Using RGBA 8888 to match how our models work
        imageAnalyzer = ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(binding.viewFinder.display.rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()
            // The analyzer can then be assigned to the instance
            .also {
                it.setAnalyzer(backgroundExecutor) { image ->
                    detectPose(image)
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

    private fun detectPose(imageProxy: ImageProxy) {
        if (this::poseLandmarkerHelper.isInitialized) {
            poseLandmarkerHelper.detectLiveStream(
                imageProxy = imageProxy,
                isFrontCamera = cameraFacing == CameraSelector.LENS_FACING_FRONT
            )
        }
    }

    override fun onResume() {
        super.onResume()
        backgroundExecutor.execute {
            if (this::poseLandmarkerHelper.isInitialized) {
                if (poseLandmarkerHelper.isClose()) {
                    poseLandmarkerHelper.setupPoseLandmarker()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()

        if (this::poseLandmarkerHelper.isInitialized) {
            viewModel.setMinPoseDetectionConfidence(poseLandmarkerHelper.minPoseDetectionConfidence)
            viewModel.setMinPoseTrackingConfidence(poseLandmarkerHelper.minPoseTrackingConfidence)
            viewModel.setMinPosePresenceConfidence(poseLandmarkerHelper.minPosePresenceConfidence)
            viewModel.setDelegate(poseLandmarkerHelper.currentDelegate)

            // Close the PoseLandmarkerHelper and release resources
            backgroundExecutor.execute { poseLandmarkerHelper.clearPoseLandmarker() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(
            Long.MAX_VALUE, TimeUnit.NANOSECONDS
        )
    }

    override fun onResults(
        resultBundle: PoseLandmarkerHelper.ResultBundle,
    ) {
        runOnUiThread {

            // Pass necessary information to OverlayView for drawing on the canvas
//            binding.overlay.setResults(
//                resultBundle.results.first(),
//                resultBundle.inputImageHeight,
//                resultBundle.inputImageWidth,
//                RunningMode.LIVE_STREAM
//            )
//
//            // Force a redraw
//            binding.overlay.invalidate()

//            println("SIZE  " + resultBundle.results.size)
//            binding.glSurface.updatePose(resultBundle.results.first())

            val landmarks = resultBundle.results.first().landmarks()
//            if (landmarks.isNotEmpty()) {
//                val leftShoulder = landmarks[0][11]
//                val rightShoulder = landmarks[0][12]
//                val centerX = (leftShoulder.x() + rightShoulder.x()) / 2
//                val centerY = (leftShoulder.y() + rightShoulder.y()) / 2
//                val shoulderDistance = Math.sqrt(
//                    Math.pow(
//                        (leftShoulder.x() - rightShoulder.x()).toDouble(),
//                        2.0
//                    ) + Math.pow((leftShoulder.y() - rightShoulder.y()).toDouble(), 2.0)
//                ).toFloat()
//
//                var translationX = centerX
//                var translationY = centerY
//                var scale = shoulderDistance / 90f
//
//                // Calculate rotation based on the angle between shoulders
//                var rotationAngle = Math.toDegrees(
//                    Math.atan2(
//                        (rightShoulder.y() - leftShoulder.y()).toDouble(),
//                        (rightShoulder.x() - leftShoulder.x()).toDouble()
//                    )
//                ).toFloat()
//                modelInstance?.let {
//                    val modelNode = ModelNode(
//                        modelInstance = it,
//                        scaleToUnits = 2.0f,
//                    )
////                    modelNode.rotation= Rotation()
//                    modelNode.position = Position(translationX, translationY)
//                    modelNode.scale = Scale(scale)
//                    if (childNoes.isNotEmpty()) {
//                        childNoes.forEach { node ->
//                            binding.sceneView.removeChildNode(node)
//                        }
//                    }
//                    childNoes.add(modelNode)
//                    println("HEREEEEE ${modelNode.scale} ${binding.sceneView.childNodes.size}")
//                    binding.sceneView.addChildNodes(childNoes)
//
//                }
//            }
            
            renderer.updateShirt(
                resultBundle.results.first(),
                imageWidth = resultBundle.inputImageWidth,
                imageHeight = resultBundle.inputImageHeight
            )
            binding.rajawaliSurface.requestRender()
        }
    }

    override fun onError(error: String, errorCode: Int) {
        runOnUiThread {
            Toast.makeText(applicationContext, error, Toast.LENGTH_SHORT).show()

        }
    }
}