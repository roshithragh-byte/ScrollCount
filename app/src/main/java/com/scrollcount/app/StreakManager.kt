package com.scrollcount.app

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class ScratchCardReward(
    val id: String,
    val tier: String,
    val milestoneDay: Int,
    val couponCode: String,
    val rewardTitle: String,
    val rewardSubtitle: String,
    val isScratched: Boolean = false
)

data class StreakEvaluationResult(
    val statusMessage: String,
    val streakIncremented: Boolean = false,
    val freezeConsumed: Boolean = false,
    val streakBroken: Boolean = false,
    val newCardEarned: ScratchCardReward? = null,
    val tamperDetected: Boolean = false
)

class StreakManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("scrollcount_streaks", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CURRENT_STREAK = "current_streak_days"
        private const val KEY_LONGEST_STREAK = "longest_streak_days"
        private const val KEY_ACTIVE_CHALLENGE_DAYS = "active_challenge_target"
        private const val KEY_CHALLENGE_LABEL = "active_challenge_label"
        private const val KEY_STREAK_FREEZES = "streak_freezes_available"
        private const val KEY_LAST_EVALUATED_DATE = "last_evaluated_date"
        private const val KEY_LAST_KNOWN_COUNT = "last_known_day_count"
        private const val KEY_LAST_WALL_MS = "last_wall_clock_ms"
        private const val KEY_LAST_ELAPSED_MS = "last_elapsed_realtime_ms"
        private const val KEY_CARD_QUEUE_JSON = "unscratched_card_queue"
        private const val KEY_INITIALIZED = "streak_initialized"

        const val MAX_FREEZES = 2
        val PRESET_TARGETS = listOf(7, 21, 28, 50, 100)
    }

    init {
        if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            val today = getTodayDateString()
            prefs.edit()
                .putBoolean(KEY_INITIALIZED, true)
                .putInt(KEY_CURRENT_STREAK, 0)
                .putInt(KEY_LONGEST_STREAK, 0)
                .putInt(KEY_ACTIVE_CHALLENGE_DAYS, 7)
                .putString(KEY_CHALLENGE_LABEL, "7-Day Focus Starter")
                .putInt(KEY_STREAK_FREEZES, 1) // 1 free onboarding Streak Freeze
                .putString(KEY_LAST_EVALUATED_DATE, today)
                .putInt(KEY_LAST_KNOWN_COUNT, 0)
                .putLong(KEY_LAST_WALL_MS, System.currentTimeMillis())
                .putLong(KEY_LAST_ELAPSED_MS, SystemClock.elapsedRealtime())
                .putString(KEY_CARD_QUEUE_JSON, "[]")
                .apply()
        }
    }

    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    var currentStreakDays: Int
        get() = prefs.getInt(KEY_CURRENT_STREAK, 0)
        private set(value) = prefs.edit().putInt(KEY_CURRENT_STREAK, value).apply()

    var longestStreakDays: Int
        get() = prefs.getInt(KEY_LONGEST_STREAK, 0)
        private set(value) = prefs.edit().putInt(KEY_LONGEST_STREAK, value).apply()

    var activeChallengeTarget: Int
        get() = prefs.getInt(KEY_ACTIVE_CHALLENGE_DAYS, 7)
        private set(value) = prefs.edit().putInt(KEY_ACTIVE_CHALLENGE_DAYS, value).apply()

    var activeChallengeLabel: String
        get() = prefs.getString(KEY_CHALLENGE_LABEL, "7-Day Focus Starter") ?: "7-Day Focus Starter"
        private set(value) = prefs.edit().putString(KEY_CHALLENGE_LABEL, value).apply()

    var streakFreezesAvailable: Int
        get() = prefs.getInt(KEY_STREAK_FREEZES, 1)
        private set(value) = prefs.edit().putInt(KEY_STREAK_FREEZES, value.coerceIn(0, MAX_FREEZES)).apply()

    fun setChallengeTarget(days: Int, customWeeks: Int? = null) {
        val label = when {
            customWeeks != null -> "Custom ${customWeeks * 7}-Day Sprint (${customWeeks}w)"
            days == 7 -> "7-Day Focus Starter"
            days == 21 -> "21-Day Dopamine Rewire"
            days == 28 -> "1-Month Reset (28 Days)"
            days == 50 -> "50-Day Deep Discipline"
            days == 100 -> "100-Day Centurion"
            else -> "${days}-Day Focus Challenge"
        }
        prefs.edit()
            .putInt(KEY_ACTIVE_CHALLENGE_DAYS, days)
            .putString(KEY_CHALLENGE_LABEL, label)
            .apply()
    }

    /**
     * Records today's latest count so midnight rollover can evaluate yesterday accurately.
     */
    fun syncCurrentDayCount(todayCount: Int) {
        prefs.edit()
            .putInt(KEY_LAST_KNOWN_COUNT, todayCount)
            .putLong(KEY_LAST_WALL_MS, System.currentTimeMillis())
            .putLong(KEY_LAST_ELAPSED_MS, SystemClock.elapsedRealtime())
            .apply()
    }

    /**
     * Evaluates streak progression on day rollover with anti-tamper clock verification.
     */
    fun evaluateDailyStreak(todayCount: Int, dailyLimit: Int): StreakEvaluationResult {
        val today = getTodayDateString()
        val lastDate = prefs.getString(KEY_LAST_EVALUATED_DATE, today) ?: today
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val prevWall = prefs.getLong(KEY_LAST_WALL_MS, nowWall)
        val prevElapsed = prefs.getLong(KEY_LAST_ELAPSED_MS, nowElapsed)

        // Anti-tamper: detect backward clock rollback
        if (nowWall < prevWall - 60_000L) {
            return StreakEvaluationResult(
                statusMessage = "System clock rollback detected. Streak evaluation paused.",
                tamperDetected = true
            )
        }

        // Same day: just update snapshot
        if (lastDate == today) {
            syncCurrentDayCount(todayCount)
            return StreakEvaluationResult(statusMessage = "Active day in progress.")
        }

        // Anti-tamper: if elapsedRealtime advanced while device stayed booted, verify wall delta isn't wildly fabricated
        if (nowElapsed > prevElapsed) {
            val wallDelta = nowWall - prevWall
            val elapsedDelta = nowElapsed - prevElapsed
            if (abs(wallDelta - elapsedDelta) > 12 * 3600_000L && wallDelta > 48 * 3600_000L) {
                prefs.edit()
                    .putString(KEY_LAST_EVALUATED_DATE, today)
                    .putLong(KEY_LAST_WALL_MS, nowWall)
                    .putLong(KEY_LAST_ELAPSED_MS, nowElapsed)
                    .apply()
                return StreakEvaluationResult(
                    statusMessage = "Clock jump detected; day skipped for streak fairness.",
                    tamperDetected = true
                )
            }
        }

        val yesterdayCount = prefs.getInt(KEY_LAST_KNOWN_COUNT, 0)
        var result: StreakEvaluationResult

        if (yesterdayCount <= dailyLimit) {
            val newStreak = currentStreakDays + 1
            currentStreakDays = newStreak
            if (newStreak > longestStreakDays) {
                longestStreakDays = newStreak
            }

            // Multiple-of-7 milestone OR exact challenge target completion awards a Scratch Card!
            var earnedCard: ScratchCardReward? = null
            if (newStreak > 0 && (newStreak % 7 == 0 || newStreak == activeChallengeTarget)) {
                earnedCard = buildRewardForMilestone(newStreak)
                enqueueScratchCard(earnedCard)
                if (newStreak == 7 && streakFreezesAvailable < MAX_FREEZES) {
                    streakFreezesAvailable = (streakFreezesAvailable + 1).coerceAtMost(MAX_FREEZES)
                }
            }

            result = StreakEvaluationResult(
                statusMessage = "Day $newStreak qualified! Stayed under $dailyLimit reels.",
                streakIncremented = true,
                newCardEarned = earnedCard
            )
        } else {
            // Exceeded daily limit yesterday
            if (streakFreezesAvailable > 0 && currentStreakDays > 0) {
                streakFreezesAvailable -= 1
                result = StreakEvaluationResult(
                    statusMessage = "Limit exceeded yesterday, but a Streak Freeze protected your ${currentStreakDays}-day streak!",
                    freezeConsumed = true
                )
            } else {
                val lostStreak = currentStreakDays
                currentStreakDays = 0
                result = StreakEvaluationResult(
                    statusMessage = if (lostStreak > 0) "Streak of $lostStreak days reset. Start fresh today!" else "New day started.",
                    streakBroken = lostStreak > 0
                )
            }
        }

        prefs.edit()
            .putString(KEY_LAST_EVALUATED_DATE, today)
            .putInt(KEY_LAST_KNOWN_COUNT, todayCount)
            .putLong(KEY_LAST_WALL_MS, nowWall)
            .putLong(KEY_LAST_ELAPSED_MS, nowElapsed)
            .apply()

        return result
    }

    /**
     * Multiples-of-7 milestone reward generator.
     */
    fun buildRewardForMilestone(day: Int): ScratchCardReward {
        val id = "card_${day}_${System.currentTimeMillis()}"
        return when {
            day >= 98 -> ScratchCardReward(
                id = id,
                tier = "DIAMOND TIER",
                milestoneDay = day,
                couponCode = "CENTURION100",
                rewardTitle = "Free ScrollCount Lifetime Pro",
                rewardSubtitle = "$day-Day Mastery! Permanent Pro Access + Hall of Fame Badge"
            )
            day >= 49 -> ScratchCardReward(
                id = id,
                tier = "PLATINUM TIER",
                milestoneDay = day,
                couponCode = "DEEP60",
                rewardTitle = "60% Off Lifetime Pass + OLED Theme",
                rewardSubtitle = "$day-Day Deep Discipline Milestone Unlocked"
            )
            day == 28 -> ScratchCardReward(
                id = id,
                tier = "GOLD TIER",
                milestoneDay = day,
                couponCode = "MONTH250",
                rewardTitle = "₹250 / \$3 Productivity Credit",
                rewardSubtitle = "1-Month (28-Day) Full Reset Milestone"
            )
            day == 21 -> ScratchCardReward(
                id = id,
                tier = "GOLD TIER",
                milestoneDay = day,
                couponCode = "REWIRE40",
                rewardTitle = "40% Off Lifetime + Partner Perk",
                rewardSubtitle = "21-Day Dopamine Rewire Milestone"
            )
            day == 14 -> ScratchCardReward(
                id = id,
                tier = "SILVER TIER",
                milestoneDay = day,
                couponCode = "FOCUS1MO",
                rewardTitle = "1-Month Partner Audio/Reading Trial",
                rewardSubtitle = "14-Day (2 Weeks) Habit Former Milestone"
            )
            day == 7 -> ScratchCardReward(
                id = id,
                tier = "BRONZE TIER",
                milestoneDay = day,
                couponCode = "SCROLL20",
                rewardTitle = "1x Streak Freeze + 20% Off Annual Pro",
                rewardSubtitle = "7-Day (Week 1) Focus Starter Milestone"
            )
            else -> {
                val weeks = (day / 7).coerceAtLeast(1)
                ScratchCardReward(
                    id = id,
                    tier = "WEEK $weeks REWARD",
                    milestoneDay = day,
                    couponCode = "STREAK${day}PRO",
                    rewardTitle = "${(weeks * 10).coerceAtMost(50)}% Off ScrollCount Pro",
                    rewardSubtitle = "$day-Day Multiple-of-7 Milestone Unlocked"
                )
            }
        }
    }

    fun getNextSevenDayMilestone(): Int {
        val current = currentStreakDays
        return ((current / 7) + 1) * 7
    }

    fun isStreakAtRisk(todayCount: Int, dailyLimit: Int): Boolean {
        if (currentStreakDays <= 0 || dailyLimit <= 0) return false
        val threshold = (dailyLimit * 0.8f).toInt()
        return todayCount in threshold until dailyLimit
    }

    fun getUnscratchedCards(): List<ScratchCardReward> {
        val raw = prefs.getString(KEY_CARD_QUEUE_JSON, "[]") ?: "[]"
        val list = mutableListOf<ScratchCardReward>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val card = ScratchCardReward(
                    id = obj.getString("id"),
                    tier = obj.getString("tier"),
                    milestoneDay = obj.getInt("milestoneDay"),
                    couponCode = obj.getString("couponCode"),
                    rewardTitle = obj.getString("rewardTitle"),
                    rewardSubtitle = obj.getString("rewardSubtitle"),
                    isScratched = obj.optBoolean("isScratched", false)
                )
                if (!card.isScratched) {
                    list.add(card)
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun enqueueScratchCard(card: ScratchCardReward) {
        val raw = prefs.getString(KEY_CARD_QUEUE_JSON, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
        val obj = JSONObject().apply {
            put("id", card.id)
            put("tier", card.tier)
            put("milestoneDay", card.milestoneDay)
            put("couponCode", card.couponCode)
            put("rewardTitle", card.rewardTitle)
            put("rewardSubtitle", card.rewardSubtitle)
            put("isScratched", card.isScratched)
        }
        arr.put(obj)
        prefs.edit().putString(KEY_CARD_QUEUE_JSON, arr.toString()).apply()
    }

    fun markCardScratched(cardId: String) {
        val raw = prefs.getString(KEY_CARD_QUEUE_JSON, "[]") ?: "[]"
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.getString("id") == cardId) {
                    obj.put("isScratched", true)
                }
            }
            prefs.edit().putString(KEY_CARD_QUEUE_JSON, arr.toString()).apply()
        } catch (_: Exception) {}
    }

    /**
     * Grants a sample 7-day milestone scratch card when testing/demoing rewards.
     */
    fun grantDemoRewardIfEmpty(): ScratchCardReward {
        val existing = getUnscratchedCards()
        if (existing.isNotEmpty()) return existing.first()
        val demoDay = if (currentStreakDays >= 7) currentStreakDays else 7
        val card = buildRewardForMilestone(demoDay)
        enqueueScratchCard(card)
        return card
    }
}
