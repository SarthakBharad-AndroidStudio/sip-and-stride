package com.example.sipandstride

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Everything that is written to or read from SharedPreferences lives here.
 *
 * "object" is Kotlin's singleton: there is exactly one SessionStore in the whole app and
 * it is reached as SessionStore.something(...) without creating an instance. Keeping all
 * persistence in one place means no activity has to know the storage keys.
 */
object SessionStore {

    private const val PREFS_NAME = "sip_prefs"

    private const val KEY_SESSIONS = "sessions"
    private const val KEY_WEIGHT = "weight_kg"
    private const val KEY_HEIGHT = "height_cm"
    private const val KEY_CUP = "cup_ml"
    private const val KEY_USE_LOCATION = "use_location"
    private const val KEY_WATER_PREFIX = "water_"

    private const val DEFAULT_WEIGHT = 70
    private const val DEFAULT_HEIGHT = 175
    private const val DEFAULT_CUP = 250

    /** Millilitres of water per kilogram of body weight, a common rule of thumb. */
    private const val ML_PER_KG = 33

    /** Extra water for every 1000 steps walked. */
    private const val ML_PER_1000_STEPS = 100

    /** Goals are rounded to this, because nobody drinks 2337 ml on purpose. */
    private const val GOAL_STEP = 250

    /**
     * Tutorial 6 uses getPreferences(), which gives every activity its OWN file.
     * We need one shared file for four activities, so we use getSharedPreferences()
     * with a fixed name. MODE_PRIVATE means only this app can read it.
     */
    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** "2026-09-23" — used as part of the key so every day gets its own water total. */
    private fun dayKey(millis: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY)
        return format.format(Date(millis))
    }

    private fun today(): String = dayKey(System.currentTimeMillis())

    // ---------------------------------------------------------------- settings

    fun getWeight(context: Context): Int = prefs(context).getInt(KEY_WEIGHT, DEFAULT_WEIGHT)

    fun getHeight(context: Context): Int = prefs(context).getInt(KEY_HEIGHT, DEFAULT_HEIGHT)

    fun getCup(context: Context): Int = prefs(context).getInt(KEY_CUP, DEFAULT_CUP)

    fun getUseLocation(context: Context): Boolean =
        prefs(context).getBoolean(KEY_USE_LOCATION, true)

    fun saveSettings(context: Context, weight: Int, height: Int, cup: Int, useLocation: Boolean) {
        prefs(context).edit()
            .putInt(KEY_WEIGHT, weight)
            .putInt(KEY_HEIGHT, height)
            .putInt(KEY_CUP, cup)
            .putBoolean(KEY_USE_LOCATION, useLocation)
            .apply()
    }

    // ------------------------------------------------------------------ water

    fun getWaterToday(context: Context): Int =
        prefs(context).getInt(KEY_WATER_PREFIX + today(), 0)

    /** Adds (or, with a negative value, removes) water. Never goes below zero. */
    fun addWater(context: Context, ml: Int) {
        val newValue = (getWaterToday(context) + ml).coerceAtLeast(0)

        prefs(context).edit()
            .putInt(KEY_WATER_PREFIX + today(), newValue)
            .apply()
    }

    // --------------------------------------------------------------- sessions

    fun loadSessions(context: Context): List<WalkSession> {
        val raw = prefs(context).getString(KEY_SESSIONS, "") ?: ""
        if (raw.isEmpty()) return emptyList()

        return raw.split("\n")
            .mapNotNull { WalkSession.fromLine(it) }
            .sortedByDescending { it.dateMillis }
    }

    private fun saveSessions(context: Context, sessions: List<WalkSession>) {
        val raw = sessions.joinToString("\n") { it.toLine() }

        prefs(context).edit()
            .putString(KEY_SESSIONS, raw)
            .apply()
    }

    fun addSession(context: Context, session: WalkSession) {
        val all = loadSessions(context).toMutableList()
        all.add(session)
        saveSessions(context, all)
    }

    fun deleteSession(context: Context, id: Long) {
        val remaining = loadSessions(context).filter { it.id != id }
        saveSessions(context, remaining)
    }

    // ------------------------------------------------------------ calculations

    /** All steps from walks recorded today. */
    fun stepsToday(context: Context): Int =
        loadSessions(context)
            .filter { dayKey(it.dateMillis) == today() }
            .sumOf { it.steps }

    /**
     * The daily water goal.
     * weight × 33 ml is the base need; every 1000 steps add 100 ml because you lose water
     * while walking. The result is rounded to the nearest 250 ml (one glass).
     */
    fun goalMl(context: Context): Int {
        val base = getWeight(context) * ML_PER_KG
        val bonus = stepsToday(context) / 1000 * ML_PER_1000_STEPS
        val total = base + bonus

        return (total.toFloat() / GOAL_STEP).roundToInt() * GOAL_STEP
    }

    /**
     * Average step length in metres. The usual estimate is 41.5 % of body height,
     * so a person of 175 cm has a stride of about 0.73 m.
     */
    fun strideMeters(context: Context): Double = getHeight(context) * 0.415 / 100.0
}