package com.mycookbuddy.app

import android.app.Activity.MODE_PRIVATE
import android.content.Context
import androidx.core.content.edit


class Utils {
    companion object {
        @JvmStatic
        fun refreshHomeScreen(context: Context, refresh: Boolean) {
            val sharedPreferences = context.getSharedPreferences("MyCookBuddyPrefs", MODE_PRIVATE)
            sharedPreferences.edit { putBoolean("shouldRefresh", refresh) }
        }
    }
}
