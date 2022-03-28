package com.franny.screencastdemo.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.core.os.bundleOf
import androidx.core.view.get
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.franny.screencastdemo.R
import com.franny.screencastdemo.databinding.FragmentDashboardBinding
import com.franny.screencastdemo.media.ScreenRecordService
import com.franny.screencastdemo.media.ScreenRecorder
import timber.log.Timber

class DashboardFragment : Fragment() {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val dashboardViewModel by activityViewModels<DashboardViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Timber.d("onCreateView")
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        val root: View = binding.root

        dashboardViewModel.localIP.observe(viewLifecycleOwner) {
            binding.localIp.text = if (it.isNullOrEmpty()) {
                getString(R.string.network_error)
            } else {
                String.format(getString(R.string.local_ip), it)
            }
        }

        dashboardViewModel.remoteDeviceIPs.observe(viewLifecycleOwner) { remoteDeviceIPs ->
            binding.startCastControl.isEnabled = remoteDeviceIPs != null &&
                    remoteDeviceIPs.isNotEmpty() &&
                    dashboardViewModel.connectedIP.value == null
            binding.remoteDevicesSelector.removeAllViews()
            remoteDeviceIPs?.forEachIndexed { index, remoteDeviceIP ->
                val radioButton = RadioButton(context)
                radioButton.text = remoteDeviceIP
                radioButton.id = index
                if (remoteDeviceIPs.size == 1) {
                    radioButton.isChecked = true
                }
                binding.remoteDevicesSelector.addView(radioButton)
            }
        }

        dashboardViewModel.connectedIP.observe(viewLifecycleOwner) {
            Timber.d("connectedIP is $it")
            val remoteDeviceIPs = dashboardViewModel.remoteDeviceIPs.value
            binding.startCastControl.isEnabled = remoteDeviceIPs != null &&
                    remoteDeviceIPs.isNotEmpty() &&
                    it == null
            binding.stopCastControl.isEnabled = it != null
            binding.remoteDevicesSelector.isEnabled = it != null
            if (it != null && dashboardViewModel.isSender) {
                val serviceIntent = Intent(requireContext(), ScreenRecordService::class.java)
                val bundle = bundleOf(
                    Pair("type", ScreenRecordService.COMMAND_RECORD),
                )
                serviceIntent.putExtras(bundle)
                requireContext().startForegroundService(serviceIntent)
                ScreenRecorder.INSTANCE.setCallback(dashboardViewModel.screenRecordCallback)
            }
        }

        dashboardViewModel.log.observe(viewLifecycleOwner) { newLog ->
            val oldText = binding.logPanel.text
            binding.logPanel.text = "$oldText\n$newLog"
        }

        binding.clearLog.setOnClickListener {
            binding.logPanel.text = ""
        }

        binding.startCastControl.setOnClickListener {
            val selectedIP = (binding.remoteDevicesSelector[binding.remoteDevicesSelector.checkedRadioButtonId] as RadioButton).text.toString()
            Timber.d("selected IP is $selectedIP")
            dashboardViewModel.requestCastAndControl(selectedIP)
        }

        binding.stopCastControl.setOnClickListener {
            dashboardViewModel.stopCastAndControl()
        }

        return root
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}