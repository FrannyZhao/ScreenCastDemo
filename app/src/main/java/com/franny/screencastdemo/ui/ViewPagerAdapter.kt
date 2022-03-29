package com.franny.screencastdemo.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.franny.screencastdemo.ui.control.TestFragment
import com.franny.screencastdemo.ui.dashboard.DashboardFragment
import com.franny.screencastdemo.ui.screencast.ScreenCastFragment

class ViewPagerAdapter(
    fragmentActivity: FragmentActivity
) : FragmentStateAdapter(fragmentActivity) {

    private val fragmentMap = mapOf<Int, Fragment>(
        0 to DashboardFragment(),
        1 to ScreenCastFragment(),
        2 to TestFragment(),
    )

    override fun getItemCount(): Int {
        return fragmentMap.keys.size
    }

    override fun createFragment(position: Int): Fragment {
        return fragmentMap[position]!!
    }
}