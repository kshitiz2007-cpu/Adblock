package com.orbit.browser.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/** NEXUS VPN profile manager. No tunnel is claimed until the OpenVPN engine is integrated. */
class VpnActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var profileInfo: TextView
    private val prefs by lazy { getSharedPreferences("nexus_vpn_profiles", MODE_PRIVATE) }

    private val profilePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            val profile = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val parsed = parseProfile(profile)
            if (parsed.remote == null || !parsed.hasClientDirective) {
                status.text = "Invalid .ovpn profile: client and remote endpoint are required."
                return@registerForActivityResult
            }
            prefs.edit()
                .putString("profile_text", profile)
                .putString("profile_name", uri.lastPathSegment?.take(120) ?: "Imported profile")
                .putString("profile_remote", parsed.remote)
                .putString("profile_proto", parsed.proto ?: "unspecified")
                .putBoolean("profile_auth_userpass", parsed.usesUserPass)
                .putBoolean("profile_cert_auth", parsed.hasInlineCert)
                .apply()
            updateProfile()
            status.text = "Profile validated and saved locally. Tunnel engine is not connected."
        } catch (e: Exception) {
            status.text = "Profile import failed: ${e.message ?: "file access error"}"
        }
    }

    private data class ProfileSummary(val remote: String?, val proto: String?, val hasClientDirective: Boolean, val usesUserPass: Boolean, val hasInlineCert: Boolean)

    private fun parseProfile(text: String): ProfileSummary {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith(";") }.toList()
        val remoteLine = lines.firstOrNull { it.startsWith("remote ", ignoreCase = true) }
        val remote = remoteLine?.split(Regex("\\s+"))?.let { parts ->
            if (parts.size >= 2) parts[1] + (parts.getOrNull(2)?.let { ":$it" } ?: "") else null
        }
        val proto = lines.firstOrNull { it.startsWith("proto ", ignoreCase = true) }?.substringAfter(" ")?.trim()
        return ProfileSummary(
            remote = remote,
            proto = proto,
            hasClientDirective = lines.any { it.equals("client", ignoreCase = true) },
            usesUserPass = lines.any { it.equals("auth-user-pass", ignoreCase = true) },
            hasInlineCert = lines.any { it.startsWith("<ca>", true) || it.startsWith("<cert>", true) || it.startsWith("<key>", true) }
        )
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
        root.addView(TextView(this).apply { text = "OpenVPN profile manager"; textSize = 16f; setPadding(0, dp(6), 0, dp(18)) })
        status = TextView(this).apply { text = "VPN tunnel: disconnected"; textSize = 15f; setPadding(dp(12), dp(12), dp(12), dp(12)) }
        root.addView(status)
        profileInfo = TextView(this).apply { textSize = 15f; setPadding(dp(12), dp(12), dp(12), dp(18)) }
        root.addView(profileInfo)
        root.addView(Button(this).apply { text = "Import .ovpn profile"; setOnClickListener { profilePicker.launch(arrayOf("application/octet-stream", "text/plain", "*/*")) } })
        root.addView(Button(this).apply { text = "Remove saved profile"; setOnClickListener {
            prefs.edit().remove("profile_text").remove("profile_name").remove("profile_remote").remove("profile_proto").remove("profile_auth_userpass").remove("profile_cert_auth").apply()
            updateProfile()
            status.text = "Saved profile removed."
        } })
        root.addView(TextView(this).apply {
            text = "Profile details are stored locally. VPN traffic is not routed yet; OpenVPN engine and Android VPN service integration are pending."
            textSize = 14f
            setPadding(0, dp(18), 0, 0)
        })
        setContentView(root)
        updateProfile()
    }

    private fun updateProfile() {
        val name = prefs.getString("profile_name", null)
        val remote = prefs.getString("profile_remote", null)
        val proto = prefs.getString("profile_proto", null)
        val hasProfile = prefs.getString("profile_text", null) != null
        profileInfo.text = if (hasProfile) {
            "Profile: $name\\nServer endpoint: ${remote ?: "unknown"}\\nProtocol: ${proto ?: "unspecified"}\\nAuthentication: ${if (prefs.getBoolean("profile_auth_userpass", false)) "username/password" else if (prefs.getBoolean("profile_cert_auth", false)) "certificate/key" else "profile-defined"}"
        } else "No profile imported.\\nImport a trusted .ovpn configuration to prepare the VPN profile."
    }
}
