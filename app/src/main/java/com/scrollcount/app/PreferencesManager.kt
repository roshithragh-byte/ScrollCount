package com.scrollcount.app

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("scrollcount_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TODAY_DATE = "today_date"
        private const val KEY_TODAY_COUNT = "today_count"
        private const val KEY_DAILY_LIMIT = "daily_limit"
        private const val KEY_PAUSE_INTERVAL = "pause_interval"
        private const val KEY_PAUSE_ENABLED = "pause_enabled"
        private const val KEY_OVERLAY_ENABLED = "overlay_enabled"
        private const val KEY_IS_MINIMIZED = "is_minimized"
        private const val KEY_IS_CLOSED_SESSION = "is_closed_session"
        private const val KEY_AUTO_HIDE_OUTSIDE = "auto_hide_outside"
        private const val KEY_NDA_ACCEPTED = "nda_accepted"
        private const val KEY_NDA_TIMESTAMP = "nda_accepted_timestamp"

        private const val KEY_COUNT_YT = "count_youtube"
        private const val KEY_COUNT_IG = "count_instagram"
        private const val KEY_COUNT_FB = "count_facebook"
        private const val KEY_COUNT_TT = "count_tiktok"
        private const val KEY_COUNT_SNAP = "count_snapchat"
        private const val KEY_COUNT_REDDIT = "count_reddit"
        private const val KEY_COUNT_OTHER = "count_other"
    }

    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun checkDateRollover() {
        val today = getTodayDateString()
        val storedDate = prefs.getString(KEY_TODAY_DATE, "")
        if (storedDate != today) {
            prefs.edit()
                .putString(KEY_TODAY_DATE, today)
                .putInt(KEY_TODAY_COUNT, 0)
                .putInt(KEY_COUNT_YT, 0)
                .putInt(KEY_COUNT_IG, 0)
                .putInt(KEY_COUNT_FB, 0)
                .putInt(KEY_COUNT_TT, 0)
                .putInt(KEY_COUNT_SNAP, 0)
                .putInt(KEY_COUNT_REDDIT, 0)
                .putInt(KEY_COUNT_OTHER, 0)
                .putBoolean(KEY_IS_MINIMIZED, false)
                .putBoolean(KEY_IS_CLOSED_SESSION, false)
                .apply()
        }
    }

    fun getTodayCount(): Int {
        checkDateRollover()
        return prefs.getInt(KEY_TODAY_COUNT, 0)
    }

    fun getYoutubeCount(): Int = prefs.getInt(KEY_COUNT_YT, 0)
    fun getInstagramCount(): Int = prefs.getInt(KEY_COUNT_IG, 0)
    fun getFacebookCount(): Int = prefs.getInt(KEY_COUNT_FB, 0)
    fun getTiktokCount(): Int = prefs.getInt(KEY_COUNT_TT, 0)
    fun getSnapchatCount(): Int = prefs.getInt(KEY_COUNT_SNAP, 0)
    fun getRedditCount(): Int = prefs.getInt(KEY_COUNT_REDDIT, 0)

    fun incrementCount(packageName: String): Int {
        checkDateRollover()
        val current = prefs.getInt(KEY_TODAY_COUNT, 0) + 1
        val editor = prefs.edit().putInt(KEY_TODAY_COUNT, current)

        when {
            packageName.contains("youtube") -> editor.putInt(KEY_COUNT_YT, prefs.getInt(KEY_COUNT_YT, 0) + 1)
            packageName.contains("instagram") -> editor.putInt(KEY_COUNT_IG, prefs.getInt(KEY_COUNT_IG, 0) + 1)
            packageName.contains("facebook") || packageName.contains("katana") -> editor.putInt(KEY_COUNT_FB, prefs.getInt(KEY_COUNT_FB, 0) + 1)
            packageName.contains("musically") || packageName.contains("trill") -> editor.putInt(KEY_COUNT_TT, prefs.getInt(KEY_COUNT_TT, 0) + 1)
            packageName.contains("snapchat") -> editor.putInt(KEY_COUNT_SNAP, prefs.getInt(KEY_COUNT_SNAP, 0) + 1)
            packageName.contains("reddit") -> editor.putInt(KEY_COUNT_REDDIT, prefs.getInt(KEY_COUNT_REDDIT, 0) + 1)
            else -> editor.putInt(KEY_COUNT_OTHER, prefs.getInt(KEY_COUNT_OTHER, 0) + 1)
        }
        editor.apply()
        return current
    }

    fun resetTodayCount() {
        val today = getTodayDateString()
        prefs.edit()
            .putString(KEY_TODAY_DATE, today)
            .putInt(KEY_TODAY_COUNT, 0)
            .putInt(KEY_COUNT_YT, 0)
            .putInt(KEY_COUNT_IG, 0)
            .putInt(KEY_COUNT_FB, 0)
            .putInt(KEY_COUNT_TT, 0)
            .putInt(KEY_COUNT_SNAP, 0)
            .putInt(KEY_COUNT_REDDIT, 0)
            .putInt(KEY_COUNT_OTHER, 0)
            .putBoolean(KEY_IS_MINIMIZED, false)
            .putBoolean(KEY_IS_CLOSED_SESSION, false)
            .apply()
    }

    var dailyLimit: Int
        get() = prefs.getInt(KEY_DAILY_LIMIT, 30)
        set(value) = prefs.edit().putInt(KEY_DAILY_LIMIT, value).apply()

    var pauseInterval: Int
        get() = prefs.getInt(KEY_PAUSE_INTERVAL, 15)
        set(value) = prefs.edit().putInt(KEY_PAUSE_INTERVAL, value).apply()

    var isPauseEnabled: Boolean
        get() = prefs.getBoolean(KEY_PAUSE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_PAUSE_ENABLED, value).apply()

    var isOverlayEnabled: Boolean
        get() = prefs.getBoolean(KEY_OVERLAY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_OVERLAY_ENABLED, value).apply()

    var isMinimized: Boolean
        get() = prefs.getBoolean(KEY_IS_MINIMIZED, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_MINIMIZED, value).apply()

    var isClosedSession: Boolean
        get() = prefs.getBoolean(KEY_IS_CLOSED_SESSION, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_CLOSED_SESSION, value).apply()

    var isAutoHideOutside: Boolean
        get() = prefs.getBoolean(KEY_AUTO_HIDE_OUTSIDE, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_HIDE_OUTSIDE, value).apply()

    var isNdaAccepted: Boolean
        get() = prefs.getBoolean(KEY_NDA_ACCEPTED, false)
        set(value) = prefs.edit().putBoolean(KEY_NDA_ACCEPTED, value).apply()

    var ndaAcceptedTimestamp: String
        get() = prefs.getString(KEY_NDA_TIMESTAMP, "Not Accepted") ?: "Not Accepted"
        set(value) = prefs.edit().putString(KEY_NDA_TIMESTAMP, value).apply()
}
