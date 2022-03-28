package com.franny.screencastdemo.ui.screencast

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Bundle
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.franny.screencastdemo.databinding.FragmentScreencastBinding
import com.franny.screencastdemo.media.MediaEncoder
import com.franny.screencastdemo.media.getScreenHeight
import com.franny.screencastdemo.media.getScreenWidth
import com.franny.screencastdemo.network.tcp.TCPCallback
import com.franny.screencastdemo.ui.dashboard.DashboardViewModel
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
    private var isSurfaceDestroyed = true

    private val tcpCallback = object : TCPCallback {
        override fun onConnect(ip: String?) {
            ip?.let {
                Timber.d("connect ip $ip")
                MainScope().launch {
                    binding.surfaceviewMask.visibility = View.INVISIBLE
                }
            }
        }

        override fun onDisconnect() {
            Timber.d("disconnect")
            MainScope().launch {
                binding.surfaceviewMask.visibility = View.VISIBLE
            }
        }

        override fun onFrame(data: ByteArray) {
            if (isSurfaceDestroyed || mDecoder == null) {
                return
            }
            Timber.i("onFrame buf size ${data.size}")
            MainScope().launch {
                binding.surfaceviewMask.visibility = View.INVISIBLE
            }
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

        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScreencastBinding.inflate(inflater, container, false)
        val root: View = binding.root
        mMediaFormat = MediaFormat.createVideoFormat(
            MediaEncoder.MIME_TYPE,
            getScreenWidth(requireContext()),
            getScreenHeight(requireContext())
        )
        binding.surfaceviewPlayer.holder.addCallback(this)
        dashboardViewModel.connectedIP.observe(viewLifecycleOwner) {
            Timber.d("connectedIP is $it")
            if (it != null && !dashboardViewModel.isSender) {
                dashboardViewModel.tcpServerThread?.addTCPCallback(tcpCallback)
            }
        }
        return root
    }

    override fun onResume() {
        super.onResume()
        if (dashboardViewModel.connectedIP.value != null && !dashboardViewModel.isSender) {
            dashboardViewModel.tcpServerThread?.addTCPCallback(tcpCallback)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopDecoder()
        _binding = null
    }

    private fun stopDecoder() {
        mDecoder?.stop()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Timber.d("surfaceCreated")
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Timber.d("surfaceChanged format: $format, width: $width, height: $height")
        isSurfaceDestroyed = false
        mDecoder = MediaCodec.createDecoderByType(MediaEncoder.MIME_TYPE)
        mDecoder?.configure(mMediaFormat, binding.surfaceviewPlayer.holder.surface, null, 0)
        mDecoder?.start()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Timber.d("surfaceDestroyed")
        isSurfaceDestroyed = true
        stopDecoder()
    }

    companion object {
        private const val TIME_INTERVAL = 1000
    }
}