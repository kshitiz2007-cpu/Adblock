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
import android.util.Base64
import androidx.appcompat.app.AlertDialog
import java.net.HttpURLConnection
import java.net.URL
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

    private data class VpnGateServer(
        val host: String,
        val ip: String,
        val country: String,
        val pingMs: Long,
        val score: Long,
        val encodedConfig: String
    )

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
            text = "Find free VPN Gate servers"
            setOnClickListener { fetchVpnGateServers() }
        })
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
            saveParsedProfile(contents, queryDisplayName(uri).ifBlank { "NEXUS imported profile" }, false)
        } catch (error: Exception) {
            status.text = "Profile import failed: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    private fun fetchVpnGateServers() {
        status.text = "Fetching current VPN Gate volunteer relays…"
        Thread {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL("https://www.vpngate.net/api/iphone/").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 20000
                    setRequestProperty("User-Agent", "NEXUS-Browser-OpenVPN")
                }
                check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                    "VPN Gate API returned HTTP " + connection.responseCode
                }
                val csv = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                require(csv.length <= 25 * 1024 * 1024) { "VPN Gate response is too large" }

                val servers = csv.lineSequence().drop(2).mapNotNull { line ->
                    val fields = parseCsvLine(line)
                    if (fields.size < 15 || fields[14].isBlank()) return@mapNotNull null
                    val score = fields[2].toLongOrNull() ?: 0L
                    val ping = fields[3].toLongOrNull() ?: -1L
                    val encoded = fields[14].trim().trim('"')
                    if (fields[0].isBlank() || fields[1].isBlank() || encoded.length < 100) return@mapNotNull null
                    VpnGateServer(
                        host = fields[0],
                        ip = fields[1],
                        country = fields[5].ifBlank { "Unknown" },
                        pingMs = ping,
                        score = score,
                        encodedConfig = encoded
                    )
                }.sortedByDescending { it.score }.take(20).toList()

                runOnUiThread {
                    if (servers.isEmpty()) {
                        status.text = "No current OpenVPN relays were returned by VPN Gate."
                    } else {
                        showVpnGateServers(servers)
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    status.text = "Could not fetch free relays: ${error.message ?: error.javaClass.simpleName}"
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun parseCsvLine(line: String): List<String> {
        val fields = ArrayList<String>()
        val value = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val ch = line[index]
            when {
                ch == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    value.append('"')
                    index++
                }
                ch == '"' -> quoted = !quoted
                ch == ',' && !quoted -> {
                    fields.add(value.toString())
                    value.setLength(0)
                }
                else -> value.append(ch)
            }
            index++
        }
        fields.add(value.toString())
        return fields
    }

    private fun showVpnGateServers(servers: List<VpnGateServer>) {
        val labels = servers.map { server ->
            val ping = if (server.pingMs >= 0) server.pingMs.toString() + " ms" else "ping n/a"
            server.country + " — " + server.ip + " · " + ping
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Free VPN Gate relays")
            .setMessage(
                "These are public volunteer-operated servers, not NEXUS-operated VPN endpoints. " +
                "Operators may observe connection metadata. Avoid banking, private accounts, or sensitive traffic. " +
                "Use HTTPS and review the relay profile before connecting."
            )
            .setItems(labels) { _, index ->
                try {
                    val selected = servers[index]
                    val bytes = Base64.decode(selected.encodedConfig, Base64.DEFAULT)
                    val config = bytes.toString(Charsets.UTF_8)
                    saveParsedProfile(
                        config,
                        "VPN Gate — " + selected.country + " (" + selected.host + ")",
                        true
                    )
                    requestVpnPermissionAndConnect()
                } catch (error: Exception) {
                    status.text = "Could not import selected relay: ${error.message ?: error.javaClass.simpleName}"
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveParsedProfile(contents: String, name: String, vpnGate: Boolean) {
        require(contents.toByteArray(Charsets.UTF_8).size <= 2 * 1024 * 1024) { "Profile exceeds 2 MiB" }
        require(contents.lineSequence().any {
            val line = it.trim().lowercase()
            line == "client" || line == "pull" || line == "tls-client"
        }) { "A client-mode OpenVPN profile is required" }

        val parser = ConfigParser()
        parser.parseConfig(StringReader(contents))
        val profile = parser.convertProfile()
        profile.mName = name.take(120)
        if (vpnGate) {
            profile.mUsername = "vpn"
            profile.mPassword = "vpn"
        }

        val manager = ProfileManager.getInstance(applicationContext)
        val oldId = prefs.getString(KEY_PROFILE_UUID, null)
        if (oldId != null) {
            ProfileManager.get(applicationContext, oldId)?.let { old ->
                if (old.getUUIDString() != profile.getUUIDString()) {
                    manager.removeProfile(applicationContext, old)
                }
            }
        }
        manager.addProfile(profile)
        ProfileManager.saveProfile(applicationContext, profile)
        manager.saveProfileList(applicationContext)
        prefs.edit()
            .putString(KEY_PROFILE_UUID, profile.getUUIDString())
            .putString(KEY_PROFILE_NAME, profile.mName)
            .apply()
        status.text = "Profile saved in OpenVPN engine storage. Not connected yet."
        updateProfile()
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
