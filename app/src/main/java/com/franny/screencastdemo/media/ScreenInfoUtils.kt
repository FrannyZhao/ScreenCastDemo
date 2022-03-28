package com.franny.screencastdemo.media

import android.content.Context

/**
 * 获取手机高宽密度
 */
fun getScreenWidth(context: Context): Int {
    return context.resources.displayMetrics.widthPixels
}

/**
 * 手机屏幕高度
 *
 * @param context
 * @return
 */
fun getScreenHeight(context: Context): Int {
    return context.resources.displayMetrics.heightPixels
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