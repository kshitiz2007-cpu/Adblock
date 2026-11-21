package com.orbit.browser.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding

/**
 * NEXUS VPN profile setup foundation.
 * Import and local profile storage are implemented; tunnel engine is not yet wired.
 */
class VpnActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var profileInfo: TextView
    private val prefs by lazy { getSharedPreferences("nexus_vpn_profiles", MODE_PRIVATE) }

    private val profilePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) { }
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (!text.contains("client") || !text.contains("remote")) {
                status.text = "Invalid profile: .ovpn must contain client and remote directives."
                return@registerForActivityResult
            }
            prefs.edit().putString("profile_text", text).putString("profile_name", uri.lastPathSegment ?: "Imported profile").apply()
            updateProfile()
            status.text = "Profile imported and saved locally. VPN engine integration is the next step."
        } catch (e: Exception) {
            status.text = "Could not import profile: ${e.message ?: "file access error"}"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        root.addView(TextView(this).apply { text = "NEXUS VPN"; textSize = 28f; setTextColor(0xFF10231D.toInt() })
        root.addView(TextView(this).apply { text = "OpenVPN profiles"; textSize = 16f; setPadding(0, dp(6), 0, dp(18)) })
        status = TextView(this).apply { text = "VPN tunnel is not connected."; textSize = 15f; setPadding(dp(12), dp(12), dp(12), dp(12)) }
        root.addView(status)
        profileInfo = TextView(this).apply { textSize = 15f; setPadding(dp(12), dp(12), dp(12), dp(18)) }
        root.addView(profileInfo)
        root.addView(Button(this).apply { text = "Import .ovpn profile"; setOnClickListener { profilePicker.launch(arrayOf("*/*")) } })
        root.addView(Button(this).apply { text = "Remove saved profile"; setOnClickListener {
            prefs.edit().clear().apply(); updateProfile(); status.text = "Profile removed."
        } })
        root.addView(TextView(this).apply {
            text = "Connection controls will be enabled after the OpenVPN engine and Android VPN service are integrated. This screen does not currently route traffic through a VPN."
            textSize = 14f
            setPadding(0, dp(18), 0, 0)
        })
        setContentView(root)
        updateProfile()
    }

    private fun updateProfile() {
        val name = prefs.getString("profile_name", null)
        val text = prefs.getString("profile_text", null)
        profileInfo.text = if (name != null && text != null) "Saved profile: $name\nConfiguration stored on this device." else "No profile imported yet."
    }
}
