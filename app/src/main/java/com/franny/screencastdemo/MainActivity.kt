package com.franny.screencastdemo

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.viewpager2.widget.ViewPager2
import com.franny.screencastdemo.databinding.ActivityMainBinding
import com.franny.screencastdemo.media.ScreenRecordService
import com.franny.screencastdemo.ui.ViewPagerAdapter
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val adapter = ViewPagerAdapter(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val bottomNavView: BottomNavigationView = binding.bottomNavView
        val navViewPager = binding.navViewpager
        navViewPager.adapter = adapter
        navViewPager.offscreenPageLimit = 3
        navViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                bottomNavView.menu.getItem(position).isChecked = true
            }
        })
        bottomNavView.setOnNavigationItemSelectedListener {
            when(it.itemId) {
                R.id.navigation_dashboard -> navViewPager.currentItem = 0
                R.id.navigation_screen_cast -> navViewPager.currentItem = 1
                R.id.navigation_control -> navViewPager.currentItem = 2
            }
            true
        }
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), 233)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 233 && data != null) {
            val serviceIntent = Intent(this, ScreenRecordService::class.java)
            val bundle = bundleOf(
                Pair("type", ScreenRecordService.COMMAND_INIT),
                Pair("code", resultCode),
                Pair("data", data)
            )
            serviceIntent.putExtras(bundle)
            startForegroundService(serviceIntent)
        }
    }
}