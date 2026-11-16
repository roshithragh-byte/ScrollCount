package com.scrollcount.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
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
    private lateinit var streakManager: StreakManager

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

    // Streak UI views
    private lateinit var tvStreakDays: TextView
    private lateinit var tvStreakChallengeLabel: TextView
    private lateinit var tvStreakFreezes: TextView
    private lateinit var tvStreakMilestoneSub: TextView
    private lateinit var progressStreakMilestone: ProgressBar
    private lateinit var btnChooseChallenge: Button
    private lateinit var btnOpenScratchCard: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefsManager = PreferencesManager(this)
        streakManager = StreakManager(this)
        initViews()
        setupListeners()
        enforceNdaGateIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        val todayCount = prefsManager.getTodayCount()
        val eval = streakManager.evaluateDailyStreak(todayCount, prefsManager.dailyLimit)
        if (eval.newCardEarned != null) {
            Toast.makeText(
                this,
                "Milestone reached! You earned a ${eval.newCardEarned.tier} Scratch Card!",
                Toast.LENGTH_LONG
            ).show()
        } else if (eval.freezeConsumed || eval.streakBroken || eval.tamperDetected) {
            Toast.makeText(this, eval.statusMessage, Toast.LENGTH_LONG).show()
        }
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

        tvStreakDays = findViewById(R.id.tv_streak_days)
        tvStreakChallengeLabel = findViewById(R.id.tv_streak_challenge_label)
        tvStreakFreezes = findViewById(R.id.tv_streak_freezes)
        tvStreakMilestoneSub = findViewById(R.id.tv_streak_milestone_sub)
        progressStreakMilestone = findViewById(R.id.progress_streak_milestone)
        btnChooseChallenge = findViewById(R.id.btn_choose_challenge)
        btnOpenScratchCard = findViewById(R.id.btn_open_scratch_card)
    }

    private fun enforceNdaGateIfNeeded() {
        if (prefsManager.isNdaAccepted) {
            tvVersionNdaBadge.text = "v1.5 • NDA Signed"
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
                tvVersionNdaBadge.text = "v1.5 • NDA Signed"
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

        btnChooseChallenge.setOnClickListener { showStreakChallengeDialog() }

        btnOpenScratchCard.setOnClickListener {
            val pending = streakManager.getUnscratchedCards()
            val cardToShow = if (pending.isNotEmpty()) {
                pending.first()
            } else {
                Toast.makeText(this, "Previewing 7-Day Milestone Scratch Card!", Toast.LENGTH_SHORT).show()
                streakManager.grantDemoRewardIfEmpty()
            }
            showScratchCardDialog(cardToShow)
        }

        btnCopyDiagnostics.setOnClickListener { copyBetaDiagnosticReport() }

        btnReset.setOnClickListener {
            prefsManager.resetTodayCount()
            streakManager.syncCurrentDayCount(0)
            updateDashboardUI()
            startService(Intent(this, FloatingWidgetService::class.java).apply {
                action = FloatingWidgetService.ACTION_UPDATE_COUNT
                putExtra(FloatingWidgetService.EXTRA_COUNT, 0)
            })
        }
    }

    private fun showStreakChallengeDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_streak_challenge, null)
        val rg = view.findViewById<RadioGroup>(R.id.rg_challenge_presets)
        val layoutCustom = view.findViewById<LinearLayout>(R.id.layout_custom_weeks)
        val tvCustomLabel = view.findViewById<TextView>(R.id.tv_custom_weeks_label)
        val seekCustom = view.findViewById<SeekBar>(R.id.seekbar_custom_weeks)
        val btnSave = view.findViewById<Button>(R.id.btn_save_challenge)

        val currentTarget = streakManager.activeChallengeTarget
        when (currentTarget) {
            7 -> rg.check(R.id.rb_7_days)
            21 -> rg.check(R.id.rb_21_days)
            28 -> rg.check(R.id.rb_28_days)
            50 -> rg.check(R.id.rb_50_days)
            100 -> rg.check(R.id.rb_100_days)
            else -> {
                rg.check(R.id.rb_custom_days)
                layoutCustom.visibility = View.VISIBLE
                val weeks = (currentTarget / 7).coerceIn(1, 20)
                seekCustom.progress = weeks
                tvCustomLabel.text = "Custom Duration: $weeks Weeks (${weeks * 7} Days)"
            }
        }

        rg.setOnCheckedChangeListener { _, checkedId ->
            layoutCustom.visibility = if (checkedId == R.id.rb_custom_days) View.VISIBLE else View.GONE
        }

        seekCustom.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val weeks = progress.coerceAtLeast(1)
                tvCustomLabel.text = "Custom Duration: $weeks Weeks (${weeks * 7} Days)"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        val dialog = AlertDialog.Builder(this).setView(view).create()
        btnSave.setOnClickListener {
            when (rg.checkedRadioButtonId) {
                R.id.rb_7_days -> streakManager.setChallengeTarget(7)
                R.id.rb_21_days -> streakManager.setChallengeTarget(21)
                R.id.rb_28_days -> streakManager.setChallengeTarget(28)
                R.id.rb_50_days -> streakManager.setChallengeTarget(50)
                R.id.rb_100_days -> streakManager.setChallengeTarget(100)
                R.id.rb_custom_days -> {
                    val weeks = seekCustom.progress.coerceAtLeast(1)
                    streakManager.setChallengeTarget(weeks * 7, customWeeks = weeks)
                }
            }
            updateDashboardUI()
            dialog.dismiss()
            Toast.makeText(this, "Challenge set: ${streakManager.activeChallengeLabel}", Toast.LENGTH_SHORT).show()
        }
        dialog.show()
    }

    private fun showScratchCardDialog(reward: ScratchCardReward) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_scratch_card, null)
        val tvTier = view.findViewById<TextView>(R.id.tv_scratch_tier)
        val tvSubtitle = view.findViewById<TextView>(R.id.tv_scratch_subtitle)
        val tvRewardTitle = view.findViewById<TextView>(R.id.tv_reward_title)
        val tvCouponCode = view.findViewById<TextView>(R.id.tv_coupon_code)
        val scratchView = view.findViewById<ScratchCardView>(R.id.scratch_card_view)
        val btnCopy = view.findViewById<Button>(R.id.btn_copy_coupon)
        val btnClose = view.findViewById<Button>(R.id.btn_close_scratch)

        tvTier.text = reward.tier
        tvSubtitle.text = reward.rewardSubtitle
        tvRewardTitle.text = reward.rewardTitle
        tvCouponCode.text = reward.couponCode

        val dialog = AlertDialog.Builder(this).setView(view).create()

        scratchView.onRevealedListener = {
            streakManager.markCardScratched(reward.id)
            btnCopy.isEnabled = true
            btnCopy.alpha = 1.0f
            updateDashboardUI()
            Toast.makeText(this, "Reward Unlocked: ${reward.couponCode}!", Toast.LENGTH_SHORT).show()
        }

        btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("ScrollCount Coupon", reward.couponCode))
            Toast.makeText(this, "Copied coupon code ${reward.couponCode}!", Toast.LENGTH_SHORT).show()
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun copyBetaDiagnosticReport() {
        val report = "ScrollCount v1.5 | Total: ${prefsManager.getTodayCount()} / ${prefsManager.dailyLimit} | Streak: ${streakManager.currentStreakDays}d (Target: ${streakManager.activeChallengeTarget}d)"
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("ScrollCount Beta Report", report))
        Toast.makeText(this, "Diagnostic Report copied!", Toast.LENGTH_SHORT).show()
    }

    private fun updateDashboardUI() {
        tvVersionNdaBadge.text = if (prefsManager.isNdaAccepted) "v1.5 • NDA Signed" else "v1.5 • NDA Pending"
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

        // Update Streak UI
        val streak = streakManager.currentStreakDays
        val next7 = streakManager.getNextSevenDayMilestone()
        val daysRemaining = (next7 - streak).coerceAtLeast(0)
        val pendingCount = streakManager.getUnscratchedCards().size

        tvStreakDays.text = "${streak}-Day Focus Streak"
        tvStreakChallengeLabel.text = "Active: ${streakManager.activeChallengeLabel} • Best: ${streakManager.longestStreakDays}d"
        tvStreakFreezes.text = "${streakManager.streakFreezesAvailable} Freeze"
        tvStreakMilestoneSub.text = "$streak / $next7 Days • $daysRemaining days to next Scratch Card"
        progressStreakMilestone.max = next7
        progressStreakMilestone.progress = streak.coerceAtMost(next7)
        btnOpenScratchCard.text = if (pendingCount > 0) "Open Scratch Card ($pendingCount)" else "Preview Scratch Card"
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
