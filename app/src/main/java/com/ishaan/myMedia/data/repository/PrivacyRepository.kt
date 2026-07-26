package com.ishaan.myMedia.data.repository

import android.content.Context
import com.ishaan.myMedia.data.model.PrivacyPrefs
import java.security.MessageDigest

class PrivacyRepository(context: Context) {

    private val prefs = context.getSharedPreferences("gallery_prefs", Context.MODE_PRIVATE)

    fun getPrivacyPrefs(): PrivacyPrefs = PrivacyPrefs(
        pinEnabled         = prefs.getBoolean("pin_enabled", false),
        pin                = prefs.getString("pin_hash", "") ?: "",
        biometricsEnabled  = prefs.getBoolean("biometrics_enabled", false),
        instantLockEnabled = prefs.getBoolean("instant_lock_enabled", false),
    )

    fun savePin(pin: String) {
        prefs.edit()
            .putString("pin_hash", hash(pin))
            .putBoolean("pin_enabled", true)
            .apply()
    }

    fun clearPin() {
        prefs.edit()
            .remove("pin_hash")
            .putBoolean("pin_enabled", false)
            .putBoolean("biometrics_enabled", false)
            .apply()
    }

    fun setBiometricsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("biometrics_enabled", enabled).apply()
    }

    fun setInstantLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("instant_lock_enabled", enabled).apply()
    }

    fun checkPin(input: String): Boolean =
        hash(input) == (prefs.getString("pin_hash", "") ?: "")

    private fun hash(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}