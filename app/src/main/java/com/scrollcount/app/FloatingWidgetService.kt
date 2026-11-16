package com.scrollcount.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class FloatingWidgetService : Service() {

    companion object {
        const val ACTION_UPDATE_COUNT = "com.scrollcount.app.UPDATE_COUNT"
        const val ACTION_APP_FOREGROUND = "com.scrollcount.app.APP_FOREGROUND"
        const val ACTION_APP_BACKGROUND = "com.scrollcount.app.APP_BACKGROUND"
        const val ACTION_MINIMIZE = "com.scrollcount.app.MINIMIZE"
        const val ACTION_CLOSE = "com.scrollcount.app.CLOSE"
        const val ACTION_FORCE_SHOW = "com.scrollcount.app.FORCE_SHOW"

        const val EXTRA_COUNT = "extra_count"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_LIMIT_REACHED = "extra_limit_reached"

        private const val NOTIFICATION_CHANNEL_ID = "scrollcount_overlay_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private lateinit var prefsManager: PreferencesManager
    private lateinit var layoutParams: WindowManager.LayoutParams

    private var isAppInForeground = false
    private var isDrawerOpen = false
    private var currentActivePackage = ""

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefsManager = PreferencesManager(this)
        startForegroundServiceNotification()
        initFloatingWidget()
    }

    private fun startForegroundServiceNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "ScrollCount Floating Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time reel & short count badge"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("ScrollCount Active")
            .setContentText("Monitoring Reels & Shorts mindful consumption")
            .setSmallIcon(R.drawable.ic_badge_play_ring)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun initFloatingWidget() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.layout_floating_widget, null)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        windowManager?.addView(floatingView, layoutParams)
        setupInteractiveListeners()
        updateWidgetUI(prefsManager.getTodayCount())
        refreshVisibility()
    }

    private fun setupInteractiveListeners() {
        val pillView = floatingView?.findViewById<View>(R.id.widget_pill) ?: return
        val drawer = floatingView?.findViewById<View>(R.id.widget_drawer) ?: return
        val btnCloseDrawer = floatingView?.findViewById<View>(R.id.btn_close_drawer)
        val btnPillMinimize = floatingView?.findViewById<View>(R.id.btn_pill_minimize)
        val btnDrawerMinimize = floatingView?.findViewById<View>(R.id.btn_drawer_minimize)
        val btnActionMinimize = floatingView?.findViewById<Button>(R.id.btn_action_minimize)
        val btnActionClose = floatingView?.findViewById<Button>(R.id.btn_action_close_session)

        pillView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(floatingView, layoutParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val deltaX = abs(event.rawX - initialTouchX)
                    val deltaY = abs(event.rawY - initialTouchY)
                    if (deltaX < 15 && deltaY < 15) {
                        isDrawerOpen = !isDrawerOpen
                        drawer.visibility = if (isDrawerOpen) View.VISIBLE else View.GONE
                    }
                    true
                }
                else -> false
            }
        }

        val minimizeAction = View.OnClickListener {
            prefsManager.isMinimized = true
            isDrawerOpen = false
            drawer.visibility = View.GONE
            floatingView?.visibility = View.GONE
            Toast.makeText(
                this,
                "ScrollCount minimized. It will stay hidden and alert you when your limit is reached.",
                Toast.LENGTH_LONG
            ).show()
        }
        btnPillMinimize?.setOnClickListener(minimizeAction)
        btnDrawerMinimize?.setOnClickListener(minimizeAction)
        btnActionMinimize?.setOnClickListener(minimizeAction)

        val closeAction = View.OnClickListener {
            prefsManager.isClosedSession = true
            isDrawerOpen = false
            drawer.visibility = View.GONE
            floatingView?.visibility = View.GONE
            Toast.makeText(this, "ScrollCount dismissed for now.", Toast.LENGTH_SHORT).show()
        }
        btnCloseDrawer?.setOnClickListener(closeAction)
        btnActionClose?.setOnClickListener(closeAction)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_APP_FOREGROUND -> {
                isAppInForeground = true
                currentActivePackage = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
                refreshVisibility()
            }
            ACTION_APP_BACKGROUND -> {
                isAppInForeground = false
                refreshVisibility()
            }
            ACTION_UPDATE_COUNT -> {
                val count = intent.getIntExtra(EXTRA_COUNT, prefsManager.getTodayCount())
                val limitReached = intent.getBooleanExtra(EXTRA_LIMIT_REACHED, false) ||
                    (count >= prefsManager.dailyLimit)
                updateWidgetUI(count)
                if (limitReached) {
                    onLimitReachedPopup(count)
                }
            }
            ACTION_FORCE_SHOW -> {
                prefsManager.isMinimized = false
                prefsManager.isClosedSession = false
                floatingView?.visibility = View.VISIBLE
            }
        }
        return START_STICKY
    }

    private fun refreshVisibility() {
        if (floatingView == null) return
        val count = prefsManager.getTodayCount()
        val limit = prefsManager.dailyLimit
        val isLimitExceeded = count >= limit

        if (!isAppInForeground) {
            floatingView?.visibility = View.GONE
            return
        }

        if (isLimitExceeded) {
            floatingView?.visibility = View.VISIBLE
            showLimitAlertBanner(true)
        } else if (prefsManager.isMinimized || prefsManager.isClosedSession) {
            floatingView?.visibility = View.GONE
        } else {
            floatingView?.visibility = View.VISIBLE
            showLimitAlertBanner(false)
        }
    }

    private fun onLimitReachedPopup(count: Int) {
        prefsManager.isMinimized = false
        prefsManager.isClosedSession = false
        if (isAppInForeground) {
            floatingView?.visibility = View.VISIBLE
            showLimitAlertBanner(true)
            Toast.makeText(this, "Daily Reel Limit Reached! ($count watched)", Toast.LENGTH_LONG).show()
        }
    }

    private fun showLimitAlertBanner(show: Boolean) {
        val banner = floatingView?.findViewById<TextView>(R.id.tv_limit_alert_banner)
        val pillSubtitle = floatingView?.findViewById<TextView>(R.id.tv_widget_subtitle)
        if (show) {
            banner?.visibility = View.VISIBLE
            pillSubtitle?.text = "LIMIT!"
            pillSubtitle?.setTextColor(getColor(R.color.coral_accent))
        } else {
            banner?.visibility = View.GONE
            pillSubtitle?.text = "TODAY"
            pillSubtitle?.setTextColor(getColor(R.color.mint_teal))
        }
    }

    private fun updateWidgetUI(count: Int) {
        val tvCount = floatingView?.findViewById<TextView>(R.id.tv_widget_count)
        val tvDrawerTotal = floatingView?.findViewById<TextView>(R.id.tv_drawer_total)
        val tvDrawerYt = floatingView?.findViewById<TextView>(R.id.tv_drawer_yt)
        val tvDrawerIg = floatingView?.findViewById<TextView>(R.id.tv_drawer_ig)
        val tvDrawerFb = floatingView?.findViewById<TextView>(R.id.tv_drawer_fb)
        val tvDrawerSnap = floatingView?.findViewById<TextView>(R.id.tv_drawer_snap)
        val tvDrawerTime = floatingView?.findViewById<TextView>(R.id.tv_drawer_time)
        val progressBar = floatingView?.findViewById<ProgressBar>(R.id.progress_drawer)

        val ytCount = prefsManager.getYoutubeCount()
        val igCount = prefsManager.getInstagramCount()
        val fbCount = prefsManager.getFacebookCount()
        val snapCount = prefsManager.getSnapchatCount()
        val limit = prefsManager.dailyLimit
        val estMins = (count * 35) / 60

        tvCount?.text = count.toString()
        tvDrawerTotal?.text = count.toString()
        tvDrawerYt?.text = ytCount.toString()
        tvDrawerIg?.text = igCount.toString()
        tvDrawerFb?.text = fbCount.toString()
        tvDrawerSnap?.text = snapCount.toString()
        tvDrawerTime?.text = "${estMins}m"

        progressBar?.max = limit
        progressBar?.progress = count.coerceAtMost(limit)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (floatingView != null) {
            windowManager?.removeView(floatingView)
            floatingView = null
        }
    }
}
