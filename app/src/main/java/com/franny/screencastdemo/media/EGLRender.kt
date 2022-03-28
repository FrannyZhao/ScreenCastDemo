package com.franny.screencastdemo.media

import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLExt
import android.view.Surface
import timber.log.Timber

class EGLRender constructor(
    private val surface: Surface,
    private val width: Int,
    private val height: Int,
    private val fps: Int = 0,
    private val frameCallBack: FrameCallBack? = null
) : OnFrameAvailableListener {
    private var mSurfaceTexture: SurfaceTexture? = null
    private var mEGLDisplay = EGL14.EGL_NO_DISPLAY
    private var mEGLContext = EGL14.EGL_NO_CONTEXT
    private var mEGLContextEncoder = EGL14.EGL_NO_CONTEXT
    private var mEGLSurface = EGL14.EGL_NO_SURFACE
    private var mEGLSurfaceEncoder = EGL14.EGL_NO_SURFACE
    private var decodeSurface: Surface? = null
    private var videoInterval = 0
    private var mFrameAvailable = true
    private var start = false
    private var time: Long = 0
    private var currentTime: Long = 0

    init {
        initFPs(fps)
        eglSetup(surface)
        makeCurrent()
        setup()
    }

    private fun initFPs(fps: Int) {
        videoInterval = 1000 / fps
    }

    /**
     * Prepares EGL.  We want a GLES 2.0 context and a surface that supports pbuffer.
     */
    private fun eglSetup(surface: Surface) {
        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (mEGLDisplay === EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("unable to get EGL14 display")
        }
        val version = IntArray(2)
        if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
            mEGLDisplay = null
            throw RuntimeException("unable to initialize EGL14")
        }
        // Configure EGL for pbuffer and OpenGL ES 2.0, 24-bit RGB.
        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(
                mEGLDisplay, attribList, 0, configs, 0, configs.size,
                numConfigs, 0
            )
        ) {
            throw RuntimeException("unable to find RGB888+recordable ES2 EGL config")
        }
        val configEncoder = getConfig(2)
        // Configure context for OpenGL ES 2.0.
        val attrib_list = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        mEGLContext = EGL14.eglCreateContext(
            mEGLDisplay, configs[0], EGL14.EGL_NO_CONTEXT,
            attrib_list, 0
        )
        checkEglError("eglCreateContext")
        if (mEGLContext == null) {
            throw RuntimeException("null context")
        }
        mEGLContextEncoder = EGL14.eglCreateContext(
            mEGLDisplay, configEncoder, mEGLContext,
            attrib_list, 0
        )
        checkEglError("eglCreateContext")
        if (mEGLContextEncoder == null) {
            throw RuntimeException("null context2")
        }
        // Create a pbuffer surface.
        val surfaceAttribs = intArrayOf(
            EGL14.EGL_WIDTH, width,
            EGL14.EGL_HEIGHT, height,
            EGL14.EGL_NONE
        )
        mEGLSurface = EGL14.eglCreatePbufferSurface(mEGLDisplay, configs[0], surfaceAttribs, 0)
        checkEglError("eglCreatePbufferSurface")
        if (mEGLSurface == null) {
            throw RuntimeException("surface was null")
        }
        val surfaceAttribs2 = intArrayOf(
            EGL14.EGL_NONE
        )
        mEGLSurfaceEncoder = EGL14.eglCreateWindowSurface(
            mEGLDisplay, configEncoder, surface,
            surfaceAttribs2, 0
        ) //creates an EGL window surface and returns its handle
        checkEglError("eglCreateWindowSurface")
        if (mEGLSurfaceEncoder == null) {
            throw RuntimeException("surface was null")
        }
    }

    /**
     * Makes our EGL context and surface current.
     */
    fun makeCurrent() {
        if (!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
            throw RuntimeException("eglMakeCurrent failed")
        }
    }

    /**
     * Creates interconnected instances of TextureRender, SurfaceTexture, and Surface.
     */
    private fun setup() {
        STextureRender.INSTANCE.surfaceCreated()
        if (VERBOSE) Timber.d("textureID=" + STextureRender.INSTANCE.getTextureId())
        mSurfaceTexture = SurfaceTexture(STextureRender.INSTANCE.getTextureId())
        mSurfaceTexture?.setDefaultBufferSize(width, height)
        mSurfaceTexture?.setOnFrameAvailableListener(this)
        decodeSurface = Surface(mSurfaceTexture)
    }

    fun getDecodeSurface(): Surface? {
        return decodeSurface
    }

    private fun getConfig(version: Int): EGLConfig? {
        var renderableType = EGL14.EGL_OPENGL_ES2_BIT
        if (version >= 3) {
            renderableType = renderableType or EGLExt.EGL_OPENGL_ES3_BIT_KHR
        }
        // The actual surface is generally RGBA or RGBX, so situationally omitting alpha
        // doesn't really help.  It can also lead to a huge performance hit on glReadPixels()
        // when reading into a GL_RGBA buffer.
        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, renderableType,
            EGL14.EGL_NONE, 0,  // placeholder for recordable [@-3]
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(
                mEGLDisplay, attribList, 0, configs, 0, configs.size,
                numConfigs, 0
            )
        ) {
            Timber.w("unable to find RGB8888 / $version EGLConfig")
            return null
        }
        return configs[0]
    }

    private fun checkEglError(msg: String) {
        var error: Int
        if (EGL14.eglGetError().also { error = it } != EGL14.EGL_SUCCESS) {
            throw RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error))
        }
    }

    fun makeCurrent(index: Int) {
        if (index == 0) {
            if (!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
                throw RuntimeException("eglMakeCurrent failed")
            }
        } else {
            if (!EGL14.eglMakeCurrent(
                    mEGLDisplay,
                    mEGLSurfaceEncoder,
                    mEGLSurfaceEncoder,
                    mEGLContextEncoder
                )
            ) {
                throw RuntimeException("eglMakeCurrent failed")
            }
        }
    }

    fun setPresentationTime(nsecs: Long) {
        EGLExt.eglPresentationTimeANDROID(mEGLDisplay, mEGLSurfaceEncoder, nsecs)
        checkEglError("eglPresentationTimeANDROID")
    }

    fun awaitNewImage() {
        if (mFrameAvailable) {
            mFrameAvailable = false
            mSurfaceTexture!!.updateTexImage()
        }
    }

    fun swapBuffers(): Boolean {
        val result = EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurfaceEncoder)
        checkEglError("eglSwapBuffers")
        return result
    }

    private var count = 1
    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        mFrameAvailable = true
    }

    private fun computePresentationTimeNsec(frameIndex: Int): Long {
        val ONE_BILLION: Long = 1000000000
        return frameIndex * ONE_BILLION / fps
    }

    fun drawImage() {
        STextureRender.INSTANCE.drawFrame()
    }

    /**
     * 开始录屏
     */
    fun start() {
        start = true
        while (start) {
            makeCurrent(1)
            awaitNewImage()
            currentTime = System.currentTimeMillis()
            if (currentTime - time >= videoInterval) {
                //todo 帧率控制
                drawImage()
                frameCallBack?.onUpdate()
                setPresentationTime(computePresentationTimeNsec(count++))
                swapBuffers()
                time = currentTime
            }
        }
    }

    fun stop() {
        start = false
    }

    companion object {
        private const val VERBOSE = false // lots of logging
    }
}