package com.example.data

import android.content.Context
import android.content.SharedPreferences

object SettingsManager {
    private const val PREFS_NAME = "MacroAssistantSettings"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL = "selected_model"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!this::prefs.isInitialized) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun getApiKey(): String {
        return prefs.getString(KEY_API_KEY, "") ?: ""
    }

    fun setApiKey(apiKey: String) {
        prefs.edit().putString(KEY_API_KEY, apiKey).apply()
    }

    fun getSelectedModel(): String {
        return prefs.getString(KEY_MODEL, "gemini-2.0-flash") ?: "gemini-2.0-flash"
    }

    fun setSelectedModel(model: String) {
        prefs.edit().putString(KEY_MODEL, model).apply()
    }
}
