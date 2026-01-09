package com.acteam.acl.models

import android.graphics.drawable.Drawable

// Modelo para manejar iconos en la UI
data class AppDetailedInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable
)