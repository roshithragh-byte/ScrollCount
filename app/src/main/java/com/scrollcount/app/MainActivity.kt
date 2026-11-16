package com.scrollcount.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var prefsManager: PreferencesManager
    private lateinit var tvVersionNdaBadge: TextView
    private lateinit var tvTodayCount: TextView
    private lateinit var tvCountYouTube: TextView
    private lateinit var tvCountInstagram: TextView
    private lateinit var tvCountFacebook: TextView
    private lateinit var tvCountSnapchat: TextView
    private lateinit var tvCountTikTok: TextView
    private lateinit var tvTimeLost: TextView
    private lateinit var tvLimitVal: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var seekBarLimit: SeekBar
    private lateinit var switchOverlay: SwitchCompat
    private lateinit var btnRestoreBadge: Button
    private lateinit var btnAccessibility: Button
    private lateinit var btnOverlayPerm: Button
    private lateinit var btnCopyDiagnostics: Button
    private lateinit var btnReset: Button
    private lateinit var statusA11y: TextView
    private lateinit var statusOverlay: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefsManager = PreferencesManager(this)
        initViews()
        setupListeners()
        enforceNdaGateIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        updateDashboardUI()
        checkPermissions()
    }

    private fun initViews() {
        tvVersionNdaBadge = findViewById(R.id.tv_version_nda_badge)
        tvTodayCount = findViewById(R.id.tv_hero_count)
        tvCountYouTube = findViewById(R.id.tv_count_youtube)
        tvCountInstagram = findViewById(R.id.tv_count_instagram)
        tvCountFacebook = findViewById(R.id.tv_count_facebook)
        tvCountSnapchat = findViewById(R.id.tv_count_snapchat)
        tvCountTikTok = findViewById(R.id.tv_count_tiktok)
        tvTimeLost = findViewById(R.id.tv_hero_time)
        tvLimitVal = findViewById(R.id.tv_limit_value)
        progressBar = findViewById(R.id.progress_today)
        seekBarLimit = findViewById(R.id.seekbar_limit)
        switchOverlay = findViewById(R.id.switch_overlay_service)
        btnRestoreBadge = findViewById(R.id.btn_restore_badge)
        btnAccessibility = findViewById(R.id.btn_open_accessibility)
        btnOverlayPerm = findViewById(R.id.btn_open_overlay_perm)
        btnCopyDiagnostics = findViewById(R.id.btn_copy_diagnostics)
        btnReset = findViewById(R.id.btn_reset_tally)
        statusA11y = findViewById(R.id.tv_status_a11y)
        statusOverlay = findViewById(R.id.tv_status_overlay)
    }

    private fun enforceNdaGateIfNeeded() {
        if (prefsManager.isNdaAccepted) {
            tvVersionNdaBadge.text = "v1.4 • NDA Signed"
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Closed Beta Non-Disclosure Agreement (NDA)")
            .setCancelable(false)
            .setMessage("By participating in the ScrollCount Closed Beta Program, you agree to keep pre-release builds and algorithms confidential.")
            .setPositiveButton("I Accept the NDA") { _, _ ->
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault()).format(Date())
                prefsManager.isNdaAccepted = true
                prefsManager.ndaAcceptedTimestamp = timestamp
                tvVersionNdaBadge.text = "v1.4 • NDA Signed"
            }
            .setNegativeButton("Decline & Exit") { _, _ -> finishAffinity() }
            .show()
    }

    private fun setupListeners() {
        seekBarLimit.progress = prefsManager.dailyLimit
        tvLimitVal.text = "${prefsManager.dailyLimit} Reels"
        seekBarLimit.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val clamped = progress.coerceAtLeast(10)
                tvLimitVal.text = "$clamped Reels"
                prefsManager.dailyLimit = clamped
                progressBar.max = clamped
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        switchOverlay.isChecked = prefsManager.isOverlayEnabled
        switchOverlay.setOnCheckedChangeListener { _, isChecked ->
            prefsManager.isOverlayEnabled = isChecked
            if (isChecked) {
                if (Settings.canDrawOverlays(this)) startOverlayService()
                else {
                    switchOverlay.isChecked = false
                    Toast.makeText(this, "Please grant Overlay permission first", Toast.LENGTH_SHORT).show()
                }
            } else stopOverlayService()
        }

        btnRestoreBadge.setOnClickListener {
            prefsManager.isMinimized = false
            prefsManager.isClosedSession = false
            startService(Intent(this, FloatingWidgetService::class.java).apply { action = FloatingWidgetService.ACTION_FORCE_SHOW })
        }

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnOverlayPerm.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }

        btnCopyDiagnostics.setOnClickListener { copyBetaDiagnosticReport() }

        btnReset.setOnClickListener {
            prefsManager.resetTodayCount()
            updateDashboardUI()
            startService(Intent(this, FloatingWidgetService::class.java).apply {
                action = FloatingWidgetService.ACTION_UPDATE_COUNT
                putExtra(FloatingWidgetService.EXTRA_COUNT, 0)
            })
        }
    }

    private fun copyBetaDiagnosticReport() {
        val report = "ScrollCount v1.4 | Total: ${prefsManager.getTodayCount()} / ${prefsManager.dailyLimit}"
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("ScrollCount Beta Report", report))
        Toast.makeText(this, "Diagnostic Report copied!", Toast.LENGTH_SHORT).show()
    }

    private fun updateDashboardUI() {
        tvVersionNdaBadge.text = if (prefsManager.isNdaAccepted) "v1.4 • NDA Signed" else "v1.4 • NDA Pending"
        val totalCount = prefsManager.getTodayCount()
        tvTodayCount.text = totalCount.toString()
        tvCountYouTube.text = prefsManager.getYoutubeCount().toString()
        tvCountInstagram.text = prefsManager.getInstagramCount().toString()
        tvCountFacebook.text = prefsManager.getFacebookCount().toString()
        tvCountSnapchat.text = prefsManager.getSnapchatCount().toString()
        tvCountTikTok.text = prefsManager.getTiktokCount().toString()
        tvTimeLost.text = "~${(totalCount * 35) / 60}m spent doomscrolling"
        progressBar.max = prefsManager.dailyLimit
        progressBar.progress = totalCount.coerceAtMost(prefsManager.dailyLimit)
    }

    private fun checkPermissions() {
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
        statusOverlay.text = if (hasOverlay) "Active & Granted" else "Permission Required"
        statusOverlay.setTextColor(getColor(if (hasOverlay) R.color.mint_teal else R.color.coral_accent))
        btnOverlayPerm.visibility = if (hasOverlay) View.GONE else View.VISIBLE

        val hasA11y = isAccessibilityServiceEnabled(this, ReelsAccessibilityService::class.java)
        statusA11y.text = if (hasA11y) "Active & Tracking" else "Disabled (Tap to Enable)"
        statusA11y.setTextColor(getColor(if (hasA11y) R.color.mint_teal else R.color.coral_accent))
        btnAccessibility.visibility = if (hasA11y) View.GONE else View.VISIBLE
    }

    private fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
        val expected = "${context.packageName}/${service.name}"
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.contains(expected)
    }

    private fun startOverlayService() {
        val intent = Intent(this, FloatingWidgetService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun stopOverlayService() {
        stopService(Intent(this, FloatingWidgetService::class.java))
    }
}
