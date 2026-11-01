package com.limpacel.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.limpacel.app.analyzer.RecommendationEngine
import com.limpacel.app.analyzer.StorageAnalyzer
import com.limpacel.app.analyzer.SystemActionLauncher
import com.limpacel.app.analyzer.UsageAnalyzer
import com.limpacel.app.model.Recommendation
import com.limpacel.app.model.Severity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme { DashboardScreen() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val storageAnalyzer = remember { StorageAnalyzer(context) }
    val usageAnalyzer = remember { UsageAnalyzer(context) }

    var recommendations by remember { mutableStateOf<List<Recommendation>>(emptyList()) }
    var freeGb by remember { mutableStateOf(0.0) }
    var totalGb by remember { mutableStateOf(0.0) }
    var hasPermission by remember { mutableStateOf(storageAnalyzer.hasUsageAccessPermission()) }
    var loading by remember { mutableStateOf(false) }

    fun runAnalysis() {
        loading = true
        hasPermission = storageAnalyzer.hasUsageAccessPermission()
        val apps = storageAnalyzer.analyzeInstalledApps()
        val (free, total) = storageAnalyzer.getInternalStorageStatus()
        freeGb = free / 1_073_741_824.0
        totalGb = total / 1_073_741_824.0

        val foregroundMap = if (hasPermission) usageAnalyzer.getForegroundTimePerApp() else emptyMap()
        val appsComUso = apps.map { it.copy(totalForegroundTimeMs = foregroundMap[it.packageName] ?: 0L) }

        recommendations = RecommendationEngine.build(appsComUso, free, total)
        loading = false
    }

    LaunchedEffect(Unit) { runAnalysis() }

    Scaffold(topBar = { TopAppBar(title = { Text("LimpaCel") }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {

            if (!hasPermission) {
                Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Diagnóstico limitado",
                            fontWeight = FontWeight.Bold
                        )
                        Text("Conceda acesso de uso para medir tamanho real de cada app e tempo de tela.")
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            SystemActionLauncher.execute(
                                context,
                                Recommendation(
                                    title = "", description = "", severity = Severity.ALTA,
                                    actionLabel = "", actionType = com.limpacel.app.model.ActionType.OPEN_USAGE_ACCESS_SETTINGS
                                )
                            )
                        }) { Text("Conceder acesso") }
                    }
                }
            }

            Text(
                "Armazenamento: %.1f GB livres de %.1f GB".format(freeGb, totalGb),
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = if (totalGb > 0) (1 - freeGb / totalGb).toFloat() else 0f,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recomendações (${recommendations.size})", fontWeight = FontWeight.Bold)
                TextButton(onClick = { runAnalysis() }) { Text(if (loading) "Analisando..." else "Reanalisar") }
            }

            LazyColumn {
                items(recommendations) { rec ->
                    RecommendationCard(rec) { SystemActionLauncher.execute(context, rec) }
                }
            }
        }
    }
}

@Composable
fun RecommendationCard(rec: Recommendation, onAction: () -> Unit) {
    val color = when (rec.severity) {
        Severity.ALTA -> MaterialTheme.colorScheme.errorContainer
        Severity.MEDIA -> MaterialTheme.colorScheme.secondaryContainer
        Severity.BAIXA -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(rec.title, fontWeight = FontWeight.Bold)
            Text(rec.description, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            Button(onClick = onAction) { Text(rec.actionLabel) }
        }
    }
}
