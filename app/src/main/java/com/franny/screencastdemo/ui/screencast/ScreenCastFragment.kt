package com.franny.screencastdemo.ui.screencast

import android.app.Instrumentation
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.franny.screencastdemo.databinding.FragmentScreencastBinding
import com.franny.screencastdemo.media.MediaEncoder
import com.franny.screencastdemo.network.tcp.TCPCallback
import com.franny.screencastdemo.network.tcp.TCPMessageHeader
import com.franny.screencastdemo.network.utils.ByteTools
import com.franny.screencastdemo.ui.dashboard.DashboardViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.nio.ByteBuffer

class ScreenCastFragment : Fragment(), SurfaceHolder.Callback {
    private var _binding: FragmentScreencastBinding? = null
    private val binding get() = _binding!!
    private val dashboardViewModel by activityViewModels<DashboardViewModel>()
    private var mDecoder: MediaCodec? = null
    private var mCount = 0
    private var mMediaFormat: MediaFormat? = null
    private var isSurfaceReady = false
    private var surfaceWidth = 0
    private var surfaceHeight = 0

    private val tcpCallback = object : TCPCallback {
        override fun onConnect(ip: String?) {
            ip?.let {
                Timber.d("connect ip $ip, remoteScreen (${dashboardViewModel.remoteScreenWidth}, ${dashboardViewModel.remoteScreenHeight})")
                MainScope().launch {
                    val layoutParameter = binding.surfaceviewPlayer.layoutParams
                    if (dashboardViewModel.remoteScreenWidth * surfaceHeight < surfaceWidth * dashboardViewModel.remoteScreenHeight) {
                        surfaceWidth = surfaceHeight * dashboardViewModel.remoteScreenWidth / dashboardViewModel.remoteScreenHeight
                    } else if (dashboardViewModel.remoteScreenWidth * surfaceHeight > surfaceWidth * dashboardViewModel.remoteScreenHeight) {
                        surfaceHeight = surfaceWidth * dashboardViewModel.remoteScreenHeight / dashboardViewModel.remoteScreenWidth
                    }
                    Timber.d("surface is ($surfaceWidth, $surfaceHeight)")
                    layoutParameter.width = surfaceWidth
                    layoutParameter.height = surfaceHeight
                    binding.surfaceviewPlayer.layoutParams = layoutParameter
                    isSurfaceReady = true
                    mMediaFormat = MediaFormat.createVideoFormat(
                        MediaEncoder.MIME_TYPE,
                        dashboardViewModel.remoteScreenWidth,
                        dashboardViewModel.remoteScreenHeight
                    )
                    mDecoder = MediaCodec.createDecoderByType(MediaEncoder.MIME_TYPE)
                    mDecoder?.configure(mMediaFormat, binding.surfaceviewPlayer.holder.surface, null, 0)
                    mDecoder?.start()
                }
            }
        }

        override fun onDisconnect() {
            Timber.d("disconnect")
            isSurfaceReady = false
            try {
                mDecoder?.stop()
                mDecoder = null
            } catch (e: Exception) {

            }
        }

        override fun onFrame(data: ByteArray) {
            if (!isSurfaceReady || mDecoder == null) {
                return
            }
            Timber.i("onFrame buf size ${data.size}")
            val inputBuffers: Array<ByteBuffer> = mDecoder!!.getInputBuffers()
            val inputBufferIndex: Int = mDecoder!!.dequeueInputBuffer(0)
            if (inputBufferIndex >= 0) {
                val inputBuffer = inputBuffers[inputBufferIndex]
                inputBuffer.clear()
                inputBuffer.put(data, 0, data.size)
                mDecoder?.queueInputBuffer(
                    inputBufferIndex,
                    0,
                    data.size,
                    (mCount * 1000000 / TIME_INTERVAL).toLong(),
                    0
                )
                mCount++
            }
            val bufferInfo = MediaCodec.BufferInfo()
            var outputBufferIndex: Int = mDecoder!!.dequeueOutputBuffer(bufferInfo, 0)
            while (outputBufferIndex >= 0) {
                mDecoder?.releaseOutputBuffer(outputBufferIndex, true)
                outputBufferIndex = mDecoder!!.dequeueOutputBuffer(bufferInfo, 0)
            }
        }

        override fun processMoveAction(action: Int, x: Int, y: Int) {
            Timber.d("processMoveAction $action, $x, $y")
            CoroutineScope(Dispatchers.IO).launch {
                instrumentation.sendPointerSync(
                    MotionEvent.obtain(
                        SystemClock.uptimeMillis(),
                        SystemClock.uptimeMillis(),
                        action,
                        x.toFloat(),
                        y.toFloat(),
                        0
                    )
                )
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScreencastBinding.inflate(inflater, container, false)
        val root: View = binding.root
        dashboardViewModel.screenCastCallback = tcpCallback
        binding.surfaceviewPlayer.holder.addCallback(this)
        binding.surfaceviewPlayer.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            surfaceWidth = right - left
            surfaceHeight = bottom - top
            Timber.d("surfaceWidth is $surfaceWidth, surfaceHeight is $surfaceHeight")
        }
        binding.surfaceviewPlayer.setOnTouchListener { v, event ->
            Timber.d("onTouch ${event.action} ${event.x} ${event.y}")
            val remoteX = event.x * dashboardViewModel.remoteScreenWidth / surfaceWidth
            val remoteY = event.y * dashboardViewModel.remoteScreenHeight / surfaceHeight

            val head = byteArrayOf(TCPMessageHeader.ACTION_SCREEN_MOTION)
            val actionBytes = ByteTools.intToBytes(event.action)
            val remoteXBytes = ByteTools.intToBytes(remoteX.toInt())
            val remoteYBytes = ByteTools.intToBytes(remoteY.toInt())
            val data = ByteArray(head.size + actionBytes.size + remoteXBytes.size + remoteYBytes.size)
            System.arraycopy(head, 0, data, 0, head.size)
            System.arraycopy(actionBytes, 0, data, head.size, actionBytes.size)
            System.arraycopy(remoteXBytes, 0, data, head.size + actionBytes.size, remoteXBytes.size)
            System.arraycopy(remoteYBytes, 0, data, head.size + actionBytes.size + remoteXBytes.size, remoteYBytes.size)
            dashboardViewModel.tcpServerThread?.send(data)
            true
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mDecoder?.stop()
        _binding = null
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Timber.d("surfaceCreated")
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Timber.d("surfaceChanged format: $format, width: $width, height: $height")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Timber.d("surfaceDestroyed")
        isSurfaceReady = false
        mDecoder?.stop()
    }

    companion object {
        private const val TIME_INTERVAL = 1000
        private val instrumentation = Instrumentation()
    }
}