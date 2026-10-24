package com.xiaomifix.notification.model

import android.graphics.drawable.Drawable

data class AppInfo(
    val appName: String,
    val packageName: String,
    val icon: Drawable?,
    var isSelected: Boolean = false,
    var isOptimized: Boolean = true,
    var isWhitelisted: Boolean = false
)
