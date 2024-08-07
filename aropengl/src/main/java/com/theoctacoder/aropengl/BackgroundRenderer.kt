import android.content.Context
import android.graphics.Bitmap
import android.media.Image
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import com.google.ar.core.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer


class BackgroundRenderer(var cameraTextureId: Int) {
    private var quadProgram = 0
    private var quadPositionParam = 0
    private var quadTexCoordParam = 0
//

    private lateinit var quadCoords: FloatBuffer
    private lateinit var quadTexCoords: FloatBuffer

    fun createOnGlThread(context: Context) {
        // Create the background texture
//        val textures = IntArray(1)
//        GLES20.glGenTextures(1, textures, 0)
//        textureId = textures[0]
//        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
//        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
//        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        // Initialize vertex buffer objects
        quadCoords = ByteBuffer.allocateDirect(QUAD_COORDS.size * FLOAT_SIZE)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(QUAD_COORDS)
                position(0)
            }

        quadTexCoords = ByteBuffer.allocateDirect(QUAD_TEXCOORDS.size * FLOAT_SIZE)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(QUAD_TEXCOORDS)
                position(0)
            }

        // Create the shader program
        quadProgram = createProgram(context)
        quadPositionParam = GLES20.glGetAttribLocation(quadProgram, "a_Position")
        quadTexCoordParam = GLES20.glGetAttribLocation(quadProgram, "a_TexCoord")
    }

    fun draw(frame: Frame) {
        // No need to set the camera texture, ARCore handles it
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)

        GLES20.glUseProgram(quadProgram)

        // Set the vertex positions
        GLES20.glVertexAttribPointer(quadPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, quadCoords)
        GLES20.glEnableVertexAttribArray(quadPositionParam)

        // Set the texture coordinates
        GLES20.glVertexAttribPointer(quadTexCoordParam, TEXCOORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, quadTexCoords)
        GLES20.glEnableVertexAttribArray(quadTexCoordParam)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(quadPositionParam)
        GLES20.glDisableVertexAttribArray(quadTexCoordParam)

        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)


    }

    private fun createProgram(context: Context): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, context.assets.open("shaders/background_vertex.glsl").bufferedReader().use { it.readText() })
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, context.assets.open("shaders/background_fragment.glsl").bufferedReader().use { it.readText() })

        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.e(TAG, "Error linking program: " + GLES20.glGetProgramInfoLog(program))
            GLES20.glDeleteProgram(program)
            return 0
        }

        return program
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            Log.e(TAG, "Error compiling shader: " + GLES20.glGetShaderInfoLog(shader))
            GLES20.glDeleteShader(shader)
            return 0
        }

        return shader
    }

    companion object {
        private const val TAG = "BackgroundRenderer"
        private const val FLOAT_SIZE = 4
        private const val COORDS_PER_VERTEX = 3
        private const val TEXCOORDS_PER_VERTEX = 2
        private val QUAD_COORDS = floatArrayOf(-1.0f, -1.0f, 0.0f, -1.0f, 1.0f, 0.0f, 1.0f, -1.0f, 0.0f, 1.0f, 1.0f, 0.0f)
        private val QUAD_TEXCOORDS = floatArrayOf(0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f)
    }
}