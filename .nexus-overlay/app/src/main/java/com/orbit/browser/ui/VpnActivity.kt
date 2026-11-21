package com.orbit.browser.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import de.blinkt.openvpn.VpnProfile
import de.blinkt.openvpn.core.ConfigParser
import de.blinkt.openvpn.core.ConnectionStatus
import de.blinkt.openvpn.core.OpenVPNService
import de.blinkt.openvpn.core.ProfileManager
import de.blinkt.openvpn.core.VPNLaunchHelper
import de.blinkt.openvpn.core.VpnStatus
import java.io.StringReader

/** NEXUS OpenVPN client. Connected is shown only when the native engine reports it. */
class VpnActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var profileName: TextView
    private lateinit var profileInfo: TextView
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var keyPasswordInput: EditText

    private val prefs by lazy { getSharedPreferences("nexus_vpn_profiles", MODE_PRIVATE) }

    private val profilePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) importProfile(uri)
    }

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startOpenVpnEngine()
        } else {
            status.text = "VPN permission denied. No tunnel was started."
        }
    }

    private val engineStateListener = object : VpnStatus.StateListener {
        override fun updateState(state: String, logmessage: String, localizedResId: Int, level: ConnectionStatus, intent: Intent) {
            runOnUiThread {
                val detail = logmessage.takeIf { it.isNotBlank() } ?: state
                status.text = when (level) {
                    ConnectionStatus.LEVEL_CONNECTED -> "Connected — OpenVPN engine reports an active tunnel"
                    ConnectionStatus.LEVEL_NOTCONNECTED -> "Disconnected${if (detail.isNotBlank()) ": $detail" else ""}"
                    ConnectionStatus.LEVEL_AUTH_FAILED -> "Authentication failed${if (detail.isNotBlank()) ": $detail" else ""}"
                    ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT -> "OpenVPN needs credentials${if (detail.isNotBlank()) ": $detail" else ""}"
                    ConnectionStatus.LEVEL_NONETWORK -> "Waiting for network"
                    else -> "OpenVPN $state${if (detail.isNotBlank()) ": $detail" else ""}"
                }
            }
        }

        override fun setConnectedVPN(uuid: String) {
            // Profile identity only. The CONNECTED state callback is authoritative.
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        updateProfile()
    }

    override fun onResume() {
        super.onResume()
        VpnStatus.addStateListener(engineStateListener)
    }

    override fun onPause() {
        VpnStatus.removeStateListener(engineStateListener)
        super.onPause()
    }

    private fun buildScreen() {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(28))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        root.addView(TextView(this).apply {
            text = "NEXUS VPN"
            textSize = 28f
            setTextColor(0xFF10231D.toInt())
        })
        root.addView(TextView(this).apply {
            text = "OpenVPN native tunnel"
            textSize = 16f
            setPadding(0, dp(6), 0, dp(16))
        })
        status = TextView(this).apply {
            text = "Disconnected"
            textSize = 15f
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        root.addView(status)
        profileName = TextView(this).apply {
            textSize = 15f
            setPadding(dp(8), dp(10), dp(8), dp(4))
        }
        root.addView(profileName)
        profileInfo = TextView(this).apply {
            textSize = 13f
            setPadding(dp(8), 0, dp(8), dp(14))
        }
        root.addView(profileInfo)
        root.addView(Button(this).apply {
            text = "Import .ovpn profile"
            setOnClickListener {
                profilePicker.launch(arrayOf("application/x-openvpn-profile", "application/octet-stream", "text/plain", "*/*"))
            }
        })
        usernameInput = EditText(this).apply {
            hint = "VPN username (when required)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        root.addView(usernameInput)
        passwordInput = EditText(this).apply {
            hint = "VPN password (when required)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(passwordInput)
        keyPasswordInput = EditText(this).apply {
            hint = "Private-key / PKCS#12 password (when required)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(keyPasswordInput)
        root.addView(Button(this).apply {
            text = "Connect VPN"
            setOnClickListener { requestVpnPermissionAndConnect() }
        })
        root.addView(Button(this).apply {
            text = "Disconnect VPN"
            setOnClickListener { disconnectOpenVpn() }
        })
        root.addView(Button(this).apply {
            text = "Remove saved profile"
            setOnClickListener { removeSavedProfile() }
        })
        root.addView(TextView(this).apply {
            text = "Connection status is taken from OpenVPN engine callbacks. Import a trusted profile and provide credentials only when it requires them."
            textSize = 13f
            setPadding(0, dp(16), 0, 0)
        })
        setContentView(root)
    }

    private fun importProfile(uri: Uri) {
        try {
            val contents = contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: throw IllegalArgumentException("Unable to read the selected profile")
            require(contents.toByteArray(Charsets.UTF_8).size <= 2 * 1024 * 1024) { "Profile exceeds 2 MiB" }
            require(contents.lineSequence().any {
                val line = it.trim().lowercase()
                line == "client" || line == "pull" || line == "tls-client"
            }) { "A client-mode OpenVPN profile is required" }
            val parser = ConfigParser()
            parser.parseConfig(StringReader(contents))
            val profile = parser.convertProfile()
            profile.mName = queryDisplayName(uri).ifBlank { "NEXUS imported profile" }.take(120)

            val manager = ProfileManager.getInstance(applicationContext)
            val oldId = prefs.getString(KEY_PROFILE_UUID, null)
            if (oldId != null) {
                ProfileManager.get(applicationContext, oldId)?.let { old ->
                    if (old.getUUIDString() != profile.getUUIDString()) manager.removeProfile(applicationContext, old)
                }
            }
            manager.addProfile(profile)
            ProfileManager.saveProfile(applicationContext, profile)
            manager.saveProfileList(applicationContext)
            prefs.edit()
                .putString(KEY_PROFILE_UUID, profile.getUUIDString())
                .putString(KEY_PROFILE_NAME, profile.mName)
                .apply()
            status.text = "Profile parsed and saved in OpenVPN profile storage. Not connected."
            updateProfile()
        } catch (error: Exception) {
            status.text = "Profile import failed: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index).orEmpty()
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':').orEmpty()
    }

    private fun loadSavedProfile(): VpnProfile? {
        val uuid = prefs.getString(KEY_PROFILE_UUID, null) ?: return null
        return ProfileManager.get(applicationContext, uuid)
    }

    private fun updateProfile() {
        val profile = loadSavedProfile()
        if (profile == null) {
            profileName.text = "No VPN profile imported"
            profileInfo.text = "Import an .ovpn file to configure the native OpenVPN engine."
            usernameInput.setText("")
            passwordInput.setText("")
            keyPasswordInput.setText("")
        } else {
            profileName.text = "Profile: ${profile.mName}"
            profileInfo.text = "This profile is stored by OpenVPN's profile manager. Native tunnel support is enabled."
            usernameInput.setText(profile.mUsername.orEmpty())
            passwordInput.setText(profile.mPassword.orEmpty())
            keyPasswordInput.setText(profile.mKeyPassword.orEmpty().ifBlank { profile.mPKCS12Password.orEmpty() })
        }
    }

    private fun requestVpnPermissionAndConnect() {
        val profile = loadSavedProfile()
        if (profile == null) {
            status.text = "Import an .ovpn profile first."
            return
        }
        if (profile.isUserPWAuth()) {
            val enteredUser = usernameInput.text.toString()
            val enteredPassword = passwordInput.text.toString()
            if (enteredUser.isNotBlank()) profile.mUsername = enteredUser
            if (enteredPassword.isNotBlank()) profile.mPassword = enteredPassword
            if (profile.mUsername.isNullOrBlank() || profile.mPassword.isNullOrBlank()) {
                status.text = "This profile requires a VPN username and password."
                return
            }
        }
        val keyPassword = keyPasswordInput.text.toString()
        if (keyPassword.isNotBlank()) {
            profile.mKeyPassword = keyPassword
            profile.mPKCS12Password = keyPassword
        }
        if (profile.needUserPWInput(null, null) != 0) {
            status.text = "This profile requires its private-key or PKCS#12 password."
            return
        }
        try {
            ProfileManager.saveProfile(applicationContext, profile)
            val consent = VpnService.prepare(this)
            if (consent != null) {
                status.text = "Waiting for Android VPN permission…"
                vpnPermission.launch(consent)
            } else {
                startOpenVpnEngine()
            }
        } catch (error: Exception) {
            status.text = "Unable to prepare VPN: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    private fun startOpenVpnEngine() {
        try {
            val profile = loadSavedProfile()
            if (profile == null) {
                status.text = "Saved OpenVPN profile could not be loaded."
                return
            }
            VPNLaunchHelper.startOpenVpn(profile, applicationContext, "NEXUS Browser", true)
            status.text = "OpenVPN engine starting. Waiting for authenticated tunnel state…"
        } catch (error: Exception) {
            status.text = "OpenVPN failed to start: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    private fun disconnectOpenVpn() {
        try {
            startService(Intent(this, OpenVPNService::class.java).setAction(OpenVPNService.DISCONNECT_VPN))
            status.text = "Disconnect requested from OpenVPN engine…"
        } catch (error: Exception) {
            status.text = "Disconnect failed: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    private fun removeSavedProfile() {
        disconnectOpenVpn()
        val uuid = prefs.getString(KEY_PROFILE_UUID, null)
        if (uuid != null) {
            ProfileManager.get(applicationContext, uuid)?.let {
                ProfileManager.getInstance(applicationContext).removeProfile(applicationContext, it)
            }
        }
        prefs.edit().remove(KEY_PROFILE_UUID).remove(KEY_PROFILE_NAME).apply()
        usernameInput.setText("")
        passwordInput.setText("")
        keyPasswordInput.setText("")
        status.text = "Saved OpenVPN profile removed."
        updateProfile()
    }

    companion object {
        private const val KEY_PROFILE_UUID = "profile_uuid"
        private const val KEY_PROFILE_NAME = "profile_name"
    }
}
