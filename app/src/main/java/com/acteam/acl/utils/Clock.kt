package com.acteam.acl.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun getDateTimeNow() : Array<String> {
    val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val date = Date()

    return arrayOf(
        timeFormat.format(date).toString(),
        dateFormat.format(date).toString()
    )
}