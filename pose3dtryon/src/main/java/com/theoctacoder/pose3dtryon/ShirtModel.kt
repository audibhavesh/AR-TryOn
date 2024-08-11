package com.theoctacoder.pose3dtryon


import android.content.Context
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer

class ShirtModel(context: Context, objFilePath: String) {

    private val vertexBuffer: FloatBuffer
    private val indexBuffer: IntBuffer

    private val vertexStride = 5 * 4 // 5 floats per vertex * 4 bytes per float
    private val vertexCount: Int

    private val vertexShaderCode = """
        attribute vec4 vPosition;
        attribute vec2 vTexCoord;
        varying vec2 texCoord;
        uniform mat4 uMVPMatrix;
        void main() {
            gl_Position = uMVPMatrix * vPosition;
            texCoord = vTexCoord;
        }
    """

    private val fragmentShaderCode = """
        precision mediump float;
        varying vec2 texCoord;
        uniform sampler2D uTexture;
        void main() {
            gl_FragColor = texture2D(uTexture, texCoord);
        }
    """

    private val program: Int
    private val positionHandle: Int
    private val texCoordHandle: Int
    private val mvpMatrixHandle: Int
    private val textureHandle: Int

    init {
        // Load OBJ data
        val (vertices, indices) = loadObjFile(context, objFilePath)

        vertexCount = indices.size

        val vertexByteBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
        vertexBuffer = vertexByteBuffer.asFloatBuffer().put(vertices)
        vertexBuffer.position(0)

        val indexByteBuffer = ByteBuffer.allocateDirect(indices.size * 4)
            .order(ByteOrder.nativeOrder())
        indexBuffer = indexByteBuffer.asIntBuffer().put(indices)
        indexBuffer.position(0)

        // Initialize shaders and program
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        program = GLES20.glCreateProgram().apply {
            GLES20.glAttachShader(this, vertexShader)
            GLES20.glAttachShader(this, fragmentShader)
            GLES20.glLinkProgram(this)
        }

        positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        texCoordHandle = GLES20.glGetAttribLocation(program, "vTexCoord")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        textureHandle = GLES20.glGetUniformLocation(program, "uTexture")
    }

    fun draw(mvpMatrix: FloatArray) {
        GLES20.glUseProgram(program)

        // Pass the vertex data to the shader
        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, vertexStride, vertexBuffer)
        GLES20.glEnableVertexAttribArray(positionHandle)

        vertexBuffer.position(3)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, vertexStride, vertexBuffer)
        GLES20.glEnableVertexAttribArray(texCoordHandle)

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        // Bind texture (not shown here, but assumed to be done elsewhere)

        // Draw the model
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, vertexCount, GLES20.GL_UNSIGNED_INT, indexBuffer)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        return shader
    }

    private fun loadObjFile(context: Context, objFilePath: String): Pair<FloatArray, IntArray> {
        // Implement OBJ file loading and parsing here.
        // This is a placeholder implementation. You can use libraries like OBJLoader or write your own parser.

        // Example of dummy data
        val vertices = floatArrayOf(
            // Positions and texture coordinates
        )
        val indices = intArrayOf(
            // Indices
        )

        return Pair(vertices, indices)
    }
}
