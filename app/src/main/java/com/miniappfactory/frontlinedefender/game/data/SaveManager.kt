package com.miniappfactory.frontlinedefender.game.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages game progress, high scores, level stars, and settings.
 * Designed so future levels (20+ levels, star progression, upgrade tree) can seamlessly build on this.
 */
class SaveManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("frontline_defender_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_HIGH_SCORE = "high_score"
        private const val KEY_ENEMIES_KILLED = "enemies_killed"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_LEVEL_STARS_PREFIX = "level_stars_"
    }

    var highScore: Int
        get() = prefs.getInt(KEY_HIGH_SCORE, 0)
        set(value) {
            if (value > highScore) {
                prefs.edit().putInt(KEY_HIGH_SCORE, value).apply()
            }
        }

    var totalEnemiesKilled: Int
        get() = prefs.getInt(KEY_ENEMIES_KILLED, 0)
        set(value) {
            prefs.edit().putInt(KEY_ENEMIES_KILLED, value).apply()
        }

    fun addEnemiesKilled(count: Int) {
        totalEnemiesKilled += count
    }

    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SOUND_ENABLED, value).apply()
        }

    fun getLevelStars(levelId: Int): Int {
        return prefs.getInt("$KEY_LEVEL_STARS_PREFIX$levelId", 0)
    }

    fun setLevelStars(levelId: Int, stars: Int) {
        val current = getLevelStars(levelId)
        if (stars > current) {
            prefs.edit().putInt("$KEY_LEVEL_STARS_PREFIX$levelId", stars).apply()
        }
    }
}
