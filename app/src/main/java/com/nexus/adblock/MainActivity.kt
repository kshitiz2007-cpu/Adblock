package com.nexus.adblock

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val Ink = Color(0xFF07110D)
private val Panel = Color(0xFF12221A)
private val Mint = Color(0xFF43E08C)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NexusDashboard() }
    }
}

@Composable
private fun NexusDashboard() {
    var protection by remember { mutableStateOf(true) }
    var mode by remember { mutableStateOf("Strict") }
    var refreshStatus by remember { mutableStateOf("Filter lists ready for update") }
    var refreshing by remember { mutableStateOf(false) }
    var listCount by remember { mutableIntStateOf(0) }
    var ruleCount by remember { mutableIntStateOf(0) }
    var samsungSettingsStatus by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("nexus_filters", android.content.Context.MODE_PRIVATE)
        listCount = prefs.getInt("active_lists", 0)
        ruleCount = prefs.getInt("rule_count", 0)
    }
    val scope = rememberCoroutineScope()
    MaterialTheme(colorScheme = darkColorScheme(primary = Mint, background = Ink, surface = Panel)) {
        Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = Mint, modifier = Modifier.size(32.dp))
                    Column {
                        Text("NEXUS", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
                        Text("ADBLOCK · EARLY ACCESS", color = Mint, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (protection) "Protection enabled" else "Protection paused", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Samsung Internet filter provider", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = protection, onCheckedChange = { protection = it })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Lists refreshed", listCount.toString(), Modifier.weight(1f))
                    StatCard("Rules loaded", ruleCount.toString(), Modifier.weight(1f))
                }
                Text("Protection mode", color = Color.White, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Balanced", "Strict", "Custom").forEach { item ->
                        FilterChip(selected = mode == item, onClick = { mode = item }, label = { Text(item) })
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Filter lists", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("EasyList · Ads", color = Color.LightGray)
                        Text("EasyPrivacy · Tracking protection", color = Color.LightGray)
                        Text("AdGuard Base · General ads", color = Color.LightGray)
                        Text(refreshStatus, color = if (refreshStatus.startsWith("Update complete")) Mint else Color.LightGray, style = MaterialTheme.typography.bodySmall)
                        Button(enabled = !refreshing, onClick = {
                            refreshing = true
                            refreshStatus = "Downloading filter lists…"
                            scope.launch {
                                runCatching { FilterListRepository.update(context) }
                                    .onSuccess { result ->
                                        listCount = result.lists
                                        ruleCount = result.ruleCount
                                        refreshStatus = if (result.lists > 0) "Update complete: ${result.lists} lists" else "Update failed; retained previous filters"
                                    }
                                    .onFailure { error -> refreshStatus = "Update error: ${error.message ?: "unknown"}" }
                                refreshing = false
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (refreshing) "Updating…" else "Update filter lists")
                        }
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Samsung Internet", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Content blocker provider registered. Device testing and Samsung validation pending.", color = Color(0xFFFFC66D), style = MaterialTheme.typography.bodySmall)
                        Button(onClick = {
                            val intent = Intent("com.samsung.android.sbrowser.contentBlocker.ACTION_SETTING")
                            runCatching { context.startActivity(intent) }
                                .onSuccess { samsungSettingsStatus = "Samsung Ad blockers settings opened." }
                                .onFailure { samsungSettingsStatus = "Samsung Internet did not expose the Ad blockers settings shortcut." }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Open Samsung Ad blocker settings")
                        }
                        if (samsungSettingsStatus.isNotBlank()) Text(samsungSettingsStatus, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("NEXUS AdBlock · v0.2.0", color = Color.Gray, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, color = Mint, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(title, color = Color.LightGray, style = MaterialTheme.typography.labelMedium)
        }
    }
}
