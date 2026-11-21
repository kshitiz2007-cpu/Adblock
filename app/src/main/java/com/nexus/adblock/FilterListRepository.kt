package com.nexus.adblock

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Downloads ABP-compatible lists and atomically replaces the local filter file. */
object FilterListRepository {
    data class Source(val name: String, val url: String)
    val sources = listOf(
        Source("EasyList", "https://easylist.to/easylist/easylist.txt"),
        Source("EasyPrivacy", "https://easylist.to/easylist/easyprivacy.txt"),
        Source("AdGuard Base", "https://filters.adtidy.org/extension/ublock/filters/2.txt")
    )
    const val FILE_NAME = "nexus_filters.txt"
    val defaultRules = listOf(
        "! NEXUS AdBlock starter rules",
        "||doubleclick.net^", "||googlesyndication.com^", "||googleadservices.com^",
        "||google-analytics.com^", "||adservice.google.com^", "||adnxs.com^",
        "||ads.yahoo.com^", "||adserver.com^", "||advertising.com^",
        "||taboola.com^", "||outbrain.com^", "||pixel.facebook.com^", "||amazon-adsystem.com^"
    ).joinToString(System.lineSeparator(), postfix = System.lineSeparator())
    data class UpdateResult(val lists: Int, val ruleCount: Int, val sizeBytes: Int)
    suspend fun update(context: Context): UpdateResult = withContext(Dispatchers.IO) {
        val target = File(context.filesDir, FILE_NAME)
        val downloaded = mutableListOf<String>()
        var success = 0
        for (source in sources) {
            val body = runCatching { download(source.url) }.getOrNull()
            if (!body.isNullOrBlank()) { downloaded += "! Source: " + source.name + System.lineSeparator() + body; success++ }
        }
        if (success == 0) {
            if (!target.exists()) target.writeText(defaultRules)
            return@withContext UpdateResult(0, countRules(target.readText()), target.length().toInt())
        }
        val merged = (listOf(defaultRules) + downloaded).joinToString(System.lineSeparator())
        val temp = File(context.filesDir, FILE_NAME + ".tmp")
        temp.writeText(merged)
        if (!temp.renameTo(target)) { target.writeText(merged); temp.delete() }
        context.getSharedPreferences("nexus_filters", Context.MODE_PRIVATE).edit()
            .putInt("active_lists", success).putInt("rule_count", countRules(merged))
            .putLong("updated_at", System.currentTimeMillis()).apply()
        runCatching { context.sendBroadcast(Intent("com.samsung.android.sbrowser.contentBlocker.ACTION_UPDATE")
            .setData(Uri.parse("package:" + context.packageName))) }
        UpdateResult(success, countRules(merged), merged.toByteArray().size)
    }
    private fun download(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000; connection.readTimeout = 30000; connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "NEXUS-AdBlock/0.1")
        try {
            if (connection.responseCode !in 200..299) error("HTTP " + connection.responseCode)
            return BufferedReader(connection.inputStream.reader(Charsets.UTF_8)).use { it.readText() }
        } finally { connection.disconnect() }
    }
    private fun countRules(text: String) = text.lineSequence().count {
        it.isNotBlank() && !it.trim().startsWith("!") && !it.trim().startsWith("#")
    }
}