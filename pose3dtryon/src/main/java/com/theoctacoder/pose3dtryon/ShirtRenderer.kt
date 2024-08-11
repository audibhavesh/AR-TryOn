package com.theoctacoder.pose3dtryon

import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import org.rajawali3d.Object3D
import org.rajawali3d.lights.DirectionalLight
import org.rajawali3d.lights.PointLight
import org.rajawali3d.loader.LoaderOBJ
import org.rajawali3d.loader.ParsingException
import org.rajawali3d.materials.Material
import org.rajawali3d.materials.methods.DiffuseMethod
import org.rajawali3d.math.vector.Vector3
import org.rajawali3d.renderer.Renderer


class ShirtRenderer(context: Context) : Renderer(context) {


    private lateinit var shirtModel: Object3D

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
            directionalLight.power = 3f
            currentScene.addLight(directionalLight)
            // Load the OBJ file
            val objParser = LoaderOBJ(context.resources, mTextureManager, R.raw.shirt_model)
            objParser.parse()

            // The OBJ file might contain multiple objects,  we'll assume it's just one for simplicity
            shirtModel = objParser.parsedObject
            currentScene.addChild(shirtModel)
//            shirtModel.setPosition(Vector3.ZERO);

            // Add the model to the scene
            centerObject(shirtModel)

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
//        material.isTransparent = false

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
        obj.getBoundingBox()
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
}