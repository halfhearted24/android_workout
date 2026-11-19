package com.strongppl.tracker.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.time.LocalDate

private val Context.settingsDataStore by preferencesDataStore(name = "strong_ppl_settings")
class SettingsStore(private val context: Context) {
    private val anchorKey = stringPreferencesKey("programme_anchor_monday")
    suspend fun getOrCreateAnchor(today: LocalDate): LocalDate {
        val prefs = context.settingsDataStore.data.first()
        val saved = prefs[anchorKey]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (saved != null) return saved
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        context.settingsDataStore.edit { it[anchorKey] = monday.toString() }
        return monday
    }
}
