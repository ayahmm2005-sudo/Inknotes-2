package com.example.inknotes.ui.util

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** "5 min. ago", "Yesterday"… localized by the platform. */
@Composable
fun rememberRelativeTime(millis: Long): String = remember(millis) {
    DateUtils.getRelativeTimeSpanString(
        millis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
}
