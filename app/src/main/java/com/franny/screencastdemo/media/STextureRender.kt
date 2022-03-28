package com.franny.screencastdemo.media

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.FloatBuffer

class STextureRender private constructor() {
    private val mMVPMatrix = FloatArray(16)
    private val mSTMatrix = FloatArray(16)
    private var mProgram = 0
    private var mTextureID = -12345
    private var muMVPMatrixHandle = 0
    private var muSTMatrixHandle = 0
    private var maPositionHandle = 0
    private var maTextureHandle = 0

    init {
        Matrix.setIdentityM(mSTMatrix, 0)
    }

    fun getTextureId(): Int {
        return mTextureID
    }

    /**
     * Initializes GL state.  Call this after the EGL surface has been created and made current.
     */
    fun surfaceCreated() {
        mProgram = GlUtil.INSTANCE.createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        if (mProgram == 0) {
            throw RuntimeException("failed creating program")
        }
        maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition")
        maTextureHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord")
        muMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix")
        muSTMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uSTMatrix")
        mTextureID = initTex()
//        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
//                GLES20.GL_NEAREST);
//        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
//                GLES20.GL_LINEAR);
//        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
//                GLES20.GL_CLAMP_TO_EDGE);
//        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
//                GLES20.GL_CLAMP_TO_EDGE);
    }

    /**
     * create external texture
     *
     * @return texture ID
     */
    fun initTex(): Int {
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex[0])
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST
        )
        return tex[0]
    }

    /**
     * Draws the external texture in SurfaceTexture onto the current EGL surface.
     */
    fun drawFrame() {
        GLES20.glUseProgram(mProgram)
        // Enable the "aPosition" vertex attribute.
        GLES20.glEnableVertexAttribArray(maPositionHandle)
        // Connect vertexBuffer to "aPosition".
        GLES20.glVertexAttribPointer(
            maPositionHandle, 3,
            GLES20.GL_FLOAT, false, 3 * FLOAT_SIZE_BYTES, FULL_RECTANGLE_BUF
        )
        // Enable the "aTextureCoord" vertex attribute.
        GLES20.glEnableVertexAttribArray(maTextureHandle)
        // Connect texBuffer to "aTextureCoord".
        GLES20.glVertexAttribPointer(
            maTextureHandle, 4,
            GLES20.GL_FLOAT, false, 4 * FLOAT_SIZE_BYTES, FULL_RECTANGLE_TEX_BUF
        )
        Matrix.setIdentityM(mMVPMatrix, 0)
        GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix, 0)
        GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mSTMatrix, 0)
        // Draw the rect.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        // Done -- disable vertex array, texture, and program.
        GLES20.glDisableVertexAttribArray(maPositionHandle)
        GLES20.glDisableVertexAttribArray(maTextureHandle)
        GLES20.glUseProgram(0)
    }

    companion object {
        val INSTANCE = STextureRender()
        private const val FLOAT_SIZE_BYTES = 4
        private val FULL_RECTANGLE_COORDS = floatArrayOf(
            -1.0f, -1.0f, 1.0f,  // 0 bottom left
            1.0f, -1.0f, 1.0f,  // 1 bottom right
            -1.0f, 1.0f, 1.0f,  // 2 top left
            1.0f, 1.0f, 1.0f // 3 top right
        )
        private val FULL_RECTANGLE_TEX_COORDS = floatArrayOf(
            0.0f, 1.0f, 1f, 1.0f,  // 0 bottom left
            1.0f, 1.0f, 1f, 1.0f,  // 1 bottom right
            0.0f, 0.0f, 1f, 1.0f,  // 2 top left
            1.0f, 0.0f, 1f, 1.0f // 3 top right
        )
        private val FULL_RECTANGLE_BUF: FloatBuffer = GlUtil.INSTANCE.createFloatBuffer(FULL_RECTANGLE_COORDS)
        private val FULL_RECTANGLE_TEX_BUF: FloatBuffer = GlUtil.INSTANCE.createFloatBuffer(FULL_RECTANGLE_TEX_COORDS)
        private const val VERTEX_SHADER = "uniform mat4 uMVPMatrix;\n" +
                "uniform mat4 uSTMatrix;\n" +
                "attribute vec4 aPosition;\n" +
                "attribute vec4 aTextureCoord;\n" +
                "varying vec4 vTextureCoord;\n" +
                "void main() {\n" +
                "    gl_Position = uMVPMatrix * aPosition;\n" +
                "    vTextureCoord = uSTMatrix * aTextureCoord;\n" +
                "}\n"
        private const val FRAGMENT_SHADER = "#extension GL_OES_EGL_image_external : require\n" +
                "precision mediump float;\n" +  // highp here doesn't seem to matter
                "varying vec4 vTextureCoord;\n" +
                "uniform samplerExternalOES sTexture;\n" +
                "void main() {\n" +
                "    gl_FragColor = texture2D(sTexture, vTextureCoord.xy/vTextureCoord.z);" +
                "}\n"
    }
}