package com.theoctacoder.pose3dtryon

import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import org.rajawali3d.Object3D
import org.rajawali3d.lights.DirectionalLight
import org.rajawali3d.loader.LoaderOBJ
import org.rajawali3d.loader.ParsingException
import org.rajawali3d.materials.Material
import org.rajawali3d.materials.methods.DiffuseMethod
import org.rajawali3d.materials.textures.Texture
import org.rajawali3d.math.vector.Vector3
import org.rajawali3d.renderer.Renderer
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt


class ShirtRenderer(context: Context) : Renderer(context) {


    private var shirtModel: Object3D? = null

    override fun initScene() {
        try {
//            currentScene.backgroundColor = Color.TRANSPARENT
            currentScene.backgroundColor = Color.argb(0, 0, 0, 0)

//            var mLight = DirectionalLight()
//            mLight.setPosition(0.0, 0.0, 4.0)
//            mLight.setPower(3f)
//            mLight.setColor(1.0f, 1.0f, 1.0f)
//            currentScene.addLight(mLight)


            val directionalLight = DirectionalLight(1.0, 0.2, -1.0)
            directionalLight.setColor(1.0f, 1.0f, 1.0f)
            directionalLight.power = 2f
            currentScene.addLight(directionalLight)

            val earthTexture: Texture = Texture("Earth", R.drawable.shirt_base_color)

            textureManager.addTexture(earthTexture)

            // Load the OBJ file
            val objParser = LoaderOBJ(context.resources, mTextureManager, R.raw.shirt_model)
            objParser.parse()

            // The OBJ file might contain multiple objects,  we'll assume it's just one for simplicity
            shirtModel = objParser.parsedObject
            currentScene.addChild(shirtModel)
//            shirtModel.setPosition(Vector3.ZERO);

            // Add the model to the scene
            shirtModel?.let {
                centerObject(it)
                it.setAlpha(1.0f)
            }
            currentCamera.z = 80.0

            // Position the camera
//            currentCamera.z = 4.0

        } catch (e: ParsingException) {
            e.printStackTrace()
        }
    }

    private fun setOpaqueShirtMaterial(obj: Object3D) {
        val material = Material()
        material.color = Color.WHITE  // Or any base color you want
        material.diffuseMethod = DiffuseMethod.Lambert()
        material.setColorInfluence(0f);

        val earthTexture: Texture = Texture("Earth", R.drawable.shirt_base_color)
        material.addTexture(earthTexture);

//        material.isTransparent = false
        material.setColor(Color.WHITE)
        for (i in 0 until obj.numChildren) {
            val child = obj.getChildAt(i)
            child.material = material
        }
    }


    private fun centerObject(obj: Object3D) {
        // Store the original position
        val originalPosition = obj.position.clone()

        // Reset position to origin before scaling
        obj.position = Vector3()

        // Apply scaling
        obj.setScale(40.0)

        // Force update of object's boundaries
//        obj.getBoundingBox()
        val boundingBox = obj.boundingBox

        // Calculate the center offset after scaling
        val centerOffset = Vector3(
            -(boundingBox.max.x + boundingBox.min.x) / 2f,
            -(boundingBox.max.y + boundingBox.min.y) / 2f,
            -(boundingBox.max.z + boundingBox.min.z) / 2f
        )

        // Apply the center offset and restore the original position
//        obj.position = centerOffset
//        obj.position = obj.position.add(Vector3(0.0, -10.0, 0.0))  // Adjust values to fine-tune

        obj.position = obj.position.add(Vector3(95.0, -55.0, -15.0))

    }

    override fun onRender(elapsedTime: Long, deltaTime: Double) {
        super.onRender(elapsedTime, deltaTime)
        // You can add any animation or updates here
    }

    override fun onOffsetsChanged(
        xOffset: Float,
        yOffset: Float,
        xOffsetStep: Float,
        yOffsetStep: Float,
        xPixelOffset: Int,
        yPixelOffset: Int,
    ) {
        // Handle offset changes if needed
    }

    override fun onTouchEvent(event: MotionEvent) {
        // Handle touch events if needed
    }

    fun updateShirt(
        poseLandmarkerResults: PoseLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE
    ) {
        shirtModel?.let { shirtModel ->
            var scaleFactor = 2.0f
            poseLandmarkerResults.let {
                val landmarks = it.landmarks()
                if (landmarks.isNotEmpty()) {
                    val leftShoulder = landmarks[0][11]
                    val rightShoulder = landmarks[0][12]

                    val leftHip = landmarks[0][23]
                    val rightHip = landmarks[0][24]
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

//                    shirtModel.getBoundingBox()
                    // Calculate 3D position (this might require additional logic depending on your setup)
                    val shirtPosition = doubleArrayOf(
                        ((leftShoulder.x() + rightShoulder.x()) / 2).toDouble(),
                        ((leftShoulder.y() + rightShoulder.y()) / 2).toDouble(),
                        ((leftShoulder.z() + rightShoulder.z()) / 2).toDouble()
                    )
                    val angle = atan2(
                        (rightShoulder.y() * imageHeight * scaleFactor - leftShoulder.y() * imageHeight * scaleFactor),
                        (rightShoulder.x() * imageWidth * scaleFactor - leftShoulder.x() * imageWidth * scaleFactor)
                    ) * (180 / Math.PI).toFloat()
                    val adjustedAngle = if (angle > 90 || angle < -90) angle + 180 else angle

                    var shirtScale = calculateScale(rightShoulder, leftShoulder)
//                val perspective = 0.9f // Adjust as needed
//                val matrixValues = FloatArray(9)
//                    println("SCALE ${calculateScale(rightShoulder, leftShoulder)}")
//                    shirtModel.setScale(shirtScale.toDouble() + 35)
//                    val heightScale: Double =
//                        (calculateScale(leftHip, rightHip) / shirtScale).toDouble() * 100
//                    println(heightScale)
//                    shirtModel.setScaleY(heightScale)
//
//
                    val shoulderToHipHeight: Float =
                        (leftHip.y() + rightHip.y()) / 2 - (leftShoulder.y() + rightShoulder.y()) / 2

                    val widthScale = calculateScale(leftShoulder, rightShoulder) * 100
                    val heightScaleRatio = (shoulderToHipHeight / widthScale) * 100 + 35 * 2
                    shirtModel.setScale(
                        widthScale.toDouble(),
                        heightScaleRatio.toDouble(),
                        widthScale.toDouble()
                    )

                    println("POSTRTI ${shirtPosition[0] * widthScale * 3}   ${shirtPosition[1] * heightScaleRatio * 1.4}")
                    shirtModel.position =
                        Vector3(shirtPosition[0] * 4 * widthScale, -shirtPosition[1] * 2.2* heightScaleRatio, -15.0)
//                    shirtModel.setRotation(0.0,adjustedAngle.toDouble(),0.0)
//                    shirtModel.position = shirtModel.position.add(Vector3(xOffset.toDouble()+20, -(yOffset.toDouble()+20), -15.0))
                    println(" ${shirtModel.position} ${shirtModel.scale}")
                    reloadRenderTargets()
//
                }
            }
        }
    }

    private fun calculateScale(left: NormalizedLandmark, right: NormalizedLandmark): Float {
        return sqrt((right.x() - left.x()).pow(2.0f) + (right.y() - left.y()).pow(2.0f))
            .toFloat()
    }
}