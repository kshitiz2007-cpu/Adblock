package com.nexus.adblock

import android.os.Bundle
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
                        Text("• EasyList — ads", color = Color.LightGray)
                        Text("• EasyPrivacy — trackers", color = Color.LightGray)
                        Text("• AdGuard Base — general ads", color = Color.LightGray)
                        Text(refreshStatus, color = if (refreshStatus.startsWith("Update complete")) Mint else Color.LightGray, style = MaterialTheme.typography.bodySmall)
                        Button(enabled = !refreshing, onClick = {
                            refreshing = true
                            refreshStatus = "Downloading filter lists…"
                            scope.launch {
                                val result = runCatching { FilterListRepository.update(this@NexusDashboardContext()) }
                                result.onSuccess {
                                    listCount = it.lists
                                    ruleCount = it.ruleCount
                                    refreshStatus = if (it.lists > 0) "Update complete: ${it.lists} lists" else "Update failed; kept previous rules"
                                }.onFailure { err ->
                                    refreshStatus = "Update error: ${err.message ?: "unknown"}"
                                }
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
                        Text("Content blocker provider is registered. Samsung validation and device testing are still pending.", color = Color(0xFFFFC66D), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("NEXUS AdBlock · v0.2.0", color = Color.Gray, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

// Small helper so the composable can access the Activity Context for repository work.
@Composable
private fun NexusDashboardContext(): android.content.Context {
    return androidx.compose.ui.platform.LocalContext.current
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
