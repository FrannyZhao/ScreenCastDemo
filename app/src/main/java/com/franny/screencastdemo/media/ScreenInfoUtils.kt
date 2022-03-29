package com.franny.screencastdemo.media

import android.content.Context
import android.graphics.Point
import android.view.WindowManager

/**
 * 获取手机高宽密度
 */
fun getScreenWidth(context: Context): Int {
    val display =
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
    val outPoint = Point()
    display.getRealSize(outPoint) // include navigation bar
    return outPoint.x
}

/**
 * 手机屏幕高度
 *
 * @param context
 * @return
 */
fun getScreenHeight(context: Context): Int {
    val display =
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
    val outPoint = Point()
    display.getRealSize(outPoint) // include navigation bar
    return outPoint.y
}

/**
 * 手机屏幕DPI
 *
 * @param context
 * @return
 */
fun getScreenDpi(context: Context): Int {
    return context.resources.displayMetrics.densityDpi
}