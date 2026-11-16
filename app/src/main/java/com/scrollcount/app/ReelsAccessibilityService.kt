package com.scrollcount.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ReelsAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ReelsA11yService"
        private const val DEBOUNCE_INTERVAL_MS = 1000L

        val SUPPORTED_PACKAGES = setOf(
            "com.instagram.android", "com.instagram.lite",
            "com.google.android.youtube", "com.google.android.apps.youtube.mango",
            "com.facebook.katana", "com.facebook.lite",
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
            "com.snapchat.android", "com.reddit.frontpage",
            "com.twitter.android", "com.twitter.android.lite",
            "com.pinterest", "in.moj.app", "com.eterno.shortvideos", "in.sharechat.android"
        )

        private val YT_SHORTS_VIEW_IDS = listOf(
            "com.google.android.youtube:id/reel_recycler",
            "com.google.android.youtube:id/reel_watch_fragment_root",
            "com.google.android.youtube:id/reel_watch_fragment",
            "com.google.android.youtube:id/reel_player_page",
            "com.google.android.youtube:id/reel_player_view",
            "com.google.android.youtube:id/shorts_container",
            "com.google.android.youtube:id/shorts_player"
        )
    }

    private lateinit var prefsManager: PreferencesManager
    private var lastScrollTime = 0L
    private var currentForegroundPackage = ""
    private var lastShortSignature: String? = null

    override fun onCreate() {
        super.onCreate()
        prefsManager = PreferencesManager(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            handleWindowStateChanged(packageName)
        }

        if (!isSupportedApp(packageName)) return

        when {
            packageName.contains("youtube") -> handleYouTubeEvent(event)
            packageName.contains("instagram") -> handleInstagramEvent(event)
            packageName.contains("facebook") || packageName.contains("katana") -> handleFacebookEvent(event)
            packageName.contains("snapchat") -> handleSnapchatEvent(event)
            packageName.contains("musically") || packageName.contains("trill") -> handleTikTokEvent(event)
            packageName.contains("reddit") -> handleRedditEvent(event)
            else -> handleGenericShortVideoEvent(packageName, event)
        }
    }

    private fun isSupportedApp(pkg: String): Boolean {
        if (SUPPORTED_PACKAGES.contains(pkg)) return true
        return SUPPORTED_PACKAGES.any { pkg.startsWith(it) || it.startsWith(pkg) }
    }

    private fun handleWindowStateChanged(packageName: String) {
        if (packageName == currentForegroundPackage) return
        currentForegroundPackage = packageName
        val isTargetApp = isSupportedApp(packageName)
        val intent = Intent(this, FloatingWidgetService::class.java).apply {
            action = if (isTargetApp) FloatingWidgetService.ACTION_APP_FOREGROUND else FloatingWidgetService.ACTION_APP_BACKGROUND
            putExtra(FloatingWidgetService.EXTRA_PACKAGE_NAME, packageName)
        }
        startService(intent)
    }

    private fun handleYouTubeEvent(event: AccessibilityEvent) {
        val root = rootInActiveWindow ?: event.source
        if (!isYouTubeShortsActive(event, root)) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_VIEW_SELECTED -> triggerIncrement("com.google.android.youtube", "YT_SCROLL_OR_SELECT")
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val sig = extractTitleSignature(root, "com.google.android.youtube:id/reel_player_title")
                if (!sig.isNullOrBlank() && sig != lastShortSignature) {
                    lastShortSignature = sig
                    triggerIncrement("com.google.android.youtube", "YT_SIGNATURE_CHANGED")
                }
            }
        }
    }

    private fun isYouTubeShortsActive(event: AccessibilityEvent, root: AccessibilityNodeInfo?): Boolean {
        val sourceId = event.source?.viewIdResourceName ?: ""
        if (sourceId.contains("reel") || sourceId.contains("shorts")) return true
        val className = event.className?.toString() ?: ""
        if (className.contains("Reel", ignoreCase = true) || className.contains("Short", ignoreCase = true)) return true
        if (root == null) return false
        try {
            for (viewId in YT_SHORTS_VIEW_IDS) {
                val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
                if (!nodes.isNullOrEmpty()) return true
            }
            val shortsTabNodes = root.findAccessibilityNodeInfosByText("Shorts")
            if (!shortsTabNodes.isNullOrEmpty()) {
                for (node in shortsTabNodes) {
                    if (node.isSelected || node.parent?.isSelected == true) return true
                }
            }
            val remixNodes = root.findAccessibilityNodeInfosByText("Remix")
            if (!remixNodes.isNullOrEmpty()) return true
        } catch (e: Exception) {
            Log.w(TAG, "YT node check error: ${e.message}")
        }
        return false
    }

    private fun handleInstagramEvent(event: AccessibilityEvent) {
        val className = event.className?.toString() ?: ""
        val sourceId = event.source?.viewIdResourceName ?: ""
        val isReel = className.contains("ViewPager") || className.contains("RecyclerView") ||
            sourceId.contains("reel") || sourceId.contains("clips")
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && isReel) {
            triggerIncrement("com.instagram.android", "IG_REEL_SCROLLED")
        }
    }

    private fun handleFacebookEvent(event: AccessibilityEvent) {
        val className = event.className?.toString() ?: ""
        val sourceId = event.source?.viewIdResourceName ?: ""
        val isFbReel = sourceId.contains("reel") || sourceId.contains("fb_shorts") ||
            sourceId.contains("short_form") || className.contains("ViewPager") || className.contains("RecyclerView")
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && isFbReel) {
            triggerIncrement("com.facebook.katana", "FB_REEL_SCROLLED")
        }
    }

    private fun handleSnapchatEvent(event: AccessibilityEvent) {
        val sourceId = event.source?.viewIdResourceName ?: ""
        val className = event.className?.toString() ?: ""
        val isSpotlight = sourceId.contains("spotlight") || sourceId.contains("vertical_pager") || className.contains("ViewPager")
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && isSpotlight) {
            triggerIncrement("com.snapchat.android", "SNAP_SPOTLIGHT_SCROLLED")
        }
    }

    private fun handleTikTokEvent(event: AccessibilityEvent) {
        val className = event.className?.toString() ?: ""
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED &&
            (className.contains("ViewPager") || className.contains("RecyclerView"))) {
            triggerIncrement("com.zhiliaoapp.musically", "TIKTOK_SCROLLED")
        }
    }

    private fun handleRedditEvent(event: AccessibilityEvent) {
        val sourceId = event.source?.viewIdResourceName ?: ""
        val className = event.className?.toString() ?: ""
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED &&
            (sourceId.contains("video") || className.contains("ViewPager") || className.contains("RecyclerView"))) {
            triggerIncrement("com.reddit.frontpage", "REDDIT_VIDEO_SCROLLED")
        }
    }

    private fun handleGenericShortVideoEvent(packageName: String, event: AccessibilityEvent) {
        val className = event.className?.toString() ?: ""
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED &&
            (className.contains("ViewPager") || className.contains("RecyclerView"))) {
            triggerIncrement(packageName, "GENERIC_SHORT_SCROLLED")
        }
    }

    private fun extractTitleSignature(root: AccessibilityNodeInfo?, viewId: String): String? {
        if (root == null) return null
        try {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            if (!nodes.isNullOrEmpty()) {
                val text = nodes[0].text?.toString()
                if (!text.isNullOrBlank()) return text
            }
        } catch (_: Exception) {}
        return null
    }

    private fun triggerIncrement(packageName: String, reason: String) {
        val now = SystemClock.uptimeMillis()
        if (now - lastScrollTime < DEBOUNCE_INTERVAL_MS) return
        lastScrollTime = now
        val newCount = prefsManager.incrementCount(packageName)
        val limit = prefsManager.dailyLimit
        Log.i(TAG, "[$packageName] Counted via $reason! Today Total: $newCount / Goal: $limit")

        val intent = Intent(this, FloatingWidgetService::class.java).apply {
            action = FloatingWidgetService.ACTION_UPDATE_COUNT
            putExtra(FloatingWidgetService.EXTRA_COUNT, newCount)
            if (newCount >= limit) {
                putExtra(FloatingWidgetService.EXTRA_LIMIT_REACHED, true)
            }
        }
        startService(intent)
    }

    override fun onInterrupt() {
        Log.w(TAG, "ReelsAccessibilityService Interrupted.")
    }
}
