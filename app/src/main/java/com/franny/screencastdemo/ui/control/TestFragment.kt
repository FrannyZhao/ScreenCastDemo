package com.franny.screencastdemo.ui.control

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.MediaController
import androidx.fragment.app.Fragment
import com.franny.screencastdemo.databinding.FragmentTestBinding

class TestFragment : Fragment() {
    private var _binding: FragmentTestBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTestBinding.inflate(inflater, container, false)
        val root: View = binding.root
        binding.videoPlayer.setMediaController(MediaController(requireContext()))
        val videoUri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + requireContext().packageName + "/raw/sample_1920_1080")
        binding.videoPlayer.setVideoURI(videoUri)
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}