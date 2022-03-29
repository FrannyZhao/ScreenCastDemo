package com.franny.screencastdemo.media

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.view.Surface
import timber.log.Timber
import java.io.IOException
import java.nio.ByteBuffer

class MediaEncoder constructor(

) : Thread() {
    private var mediaCodec: MediaCodec? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val mBufferInfo = MediaCodec.BufferInfo()
    private var eglRender: EGLRender? = null
    private var surface: Surface? = null

    //编码参数相关
    private var frameBit = 2000000 //2MB
    private var videoFps = 8
    private var sps: ByteArray? = null
    private var pps: ByteArray? = null

    // screen
    private var localScreenWidth = 0
    private var localScreenHeight = 0
    private var localScreenDpi = 0

    private var projection: MediaProjection? = null
    private var screenRecordCallback: ScreenRecordCallback? = null

    private val frameCallback = object : FrameCallBack {
        override fun onUpdate() {
            startEncode()
        }
    }

    /**
     * 设置视频FPS
     *
     * @param fps
     */
    fun setVideoFPS(fps: Int): MediaEncoder {
        videoFps = fps
        return this
    }

    /**
     * 设置视屏编码采样率
     *
     * @param bit
     */
    fun setVideoBit(bit: Int): MediaEncoder {
        frameBit = bit
        return this
    }

    fun setCallback(callback: ScreenRecordCallback): MediaEncoder {
        screenRecordCallback = callback
        return this
    }

    fun setContext(context: Context): MediaEncoder {
        localScreenWidth = getScreenWidth(context)
        localScreenHeight = getScreenHeight(context)
        localScreenDpi = getScreenDpi(context)
        return this
    }

    fun setMediaProjection(mediaProjection: MediaProjection): MediaEncoder {
        projection = mediaProjection
        return this
    }

    override fun run() {
        super.run()
        try {
            prepareEncoder()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        virtualDisplay = projection?.createVirtualDisplay(
            "screen",
            localScreenWidth,
            localScreenHeight,
            localScreenDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
            eglRender?.getDecodeSurface(),
            null,
            null
        )
        virtualDisplay?.let {
            // 开始录屏
            eglRender?.start()
            release()
        }
    }

    /**
     * 初始化编码器
     */
    @Throws(IOException::class)
    private fun prepareEncoder() {
        Timber.i("record surface size: ($localScreenWidth, $localScreenHeight)")
        val mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, localScreenWidth, localScreenHeight)
        mediaFormat.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, frameBit)
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, frameInterval)
        mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE)
        mediaCodec?.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        surface = mediaCodec?.createInputSurface()
        surface?.let {
            eglRender = EGLRender(it, localScreenWidth, localScreenHeight, videoFps, frameCallback)
            mediaCodec?.start()
        }
    }

    private fun startEncode() {
        val index = mediaCodec!!.dequeueOutputBuffer(mBufferInfo, TIMEOUT_US.toLong())
        if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            resetOutputFormat()
        } else if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
//            Log.d("---", "retrieving buffers time out!");
//            try {
//                // wait 10ms
//                Thread.sleep(10);
//            } catch (InterruptedException e) {
//            }
        } else if (index >= 0) {
            encodeToVideoTrack(mediaCodec!!.getOutputBuffer(index))
            mediaCodec!!.releaseOutputBuffer(index, false)
        }
    }

    private fun encodeToVideoTrack(encodeData: ByteBuffer?) {
//        ByteBuffer encodeData = mediaCodec.getOutputBuffer(index);
        var encodeData = encodeData
        if (mBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
            mBufferInfo.size = 0
        }
        if (mBufferInfo.size == 0) {
            encodeData = null
        } else {
            Timber.d("got buffer, info: size= ${mBufferInfo.size}, presentationTimeUs=${mBufferInfo.presentationTimeUs}, offset=${mBufferInfo.offset}")
        }
        if (encodeData != null) {
            encodeData.position(mBufferInfo.offset)
            encodeData.limit(mBufferInfo.offset + mBufferInfo.size)
            //            muxer.writeSampleData(mVideoTrackIndex, encodeData, mBufferInfo);//写入文件
            val bytes: ByteArray
            if (mBufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
                //todo 关键帧上添加sps,和pps信息
                bytes = ByteArray(mBufferInfo.size + sps!!.size + pps!!.size)
                System.arraycopy(sps, 0, bytes, 0, sps!!.size)
                System.arraycopy(pps, 0, bytes, sps!!.size, pps!!.size)
                encodeData[bytes, sps!!.size + pps!!.size, mBufferInfo.size]
            } else {
                bytes = ByteArray(mBufferInfo.size)
                encodeData[bytes, 0, mBufferInfo.size]
            }
            screenRecordCallback?.sendScreenRecordData(bytes)
            Timber.d("send:" + mBufferInfo.size + "\tflag:" + mBufferInfo.flags)
        }
    }

    private fun resetOutputFormat() {
        val newFormat = mediaCodec!!.outputFormat
        Timber.i("output format changed.\n new format: $newFormat")
        getSpsPpsByteBuffer(newFormat)
    }

    /**
     * 获取编码SPS和PPS信息
     * @param newFormat
     */
    private fun getSpsPpsByteBuffer(newFormat: MediaFormat) {
        sps = newFormat.getByteBuffer("csd-0")?.array()
        pps = newFormat.getByteBuffer("csd-1")?.array()
    }

    fun stopScreen() {
        eglRender?.stop()
    }

    fun release() {
        mediaCodec?.stop()
        mediaCodec?.release()
        mediaCodec = null
        virtualDisplay?.release()
    }

    companion object {
        const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val frameRate = 20 //这里指的是Mediacodec30张图为1组 ，并不是视屏本身的FPS
        private const val frameInterval = 1 //关键帧间隔 一组加一个关键帧
        private const val TIMEOUT_US = 10000
    }
}