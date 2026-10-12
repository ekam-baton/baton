package com.ekam.baton.feature.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ekam.baton.core.data.db.entity.AuditLogEntity
import com.ekam.baton.core.data.db.entity.TokenLedgerEntity
import com.ekam.baton.core.ui.components.glassCard
import org.koin.compose.viewmodel.koinViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExecutionLedgerScreen(
    onNavigateBack: () -> Unit,
    viewModel: ExecutionLedgerViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val ledgerEntries by viewModel.filteredLedgerEntries.collectAsStateWithLifecycle()
    val allLedgerEntries by viewModel.ledgerEntries.collectAsStateWithLifecycle()
    val grandTotalTokens by viewModel.grandTotalTokens.collectAsStateWithLifecycle()
    val grandTotalCost by viewModel.grandTotalCost.collectAsStateWithLifecycle()
    val agentsMap by viewModel.agentsMap.collectAsStateWithLifecycle()
    val selectedModelFilter by viewModel.selectedModelFilter.collectAsStateWithLifecycle()

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showPruneDialog by remember { mutableStateOf(false) }

    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy - HH:mm:ss", Locale.getDefault()) }

    // Distinct models for filter pills
    val availableModels = remember(allLedgerEntries) {
        listOf("ALL") + allLedgerEntries.map { it.modelName }.distinct().sorted()
    }

    if (showPruneDialog) {
        AlertDialog(
            onDismissRequest = { showPruneDialog = false },
            title = {
                Text(
                    text = "Prune Ledger History?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "This will retain the 1,000 most recent records across cryptographic audit blocks and token execution telemetry, keeping encrypted SQLite storage lean.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.pruneLedgerEntries(1000)
                        viewModel.triggerRollingGenesisBlock()
                        showPruneDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("PRUNE NOW", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPruneDialog = false }) {
                    Text("CANCEL", color = Color.White.copy(alpha = 0.7f))
                }
            },
            containerColor = Color(0xFF161F30)
        )
    }

    Scaffold(
        containerColor = Color(0xFF0D131F), // Abyssal Deep Ocean Blue
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Execution & Economics Ledger",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Immutable Telemetry & Cryptographic Trail",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.tertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val report = viewModel.generateExportReport()
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, report)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Export Baton Ledger"))
                        }
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Export Report",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    IconButton(onClick = { showPruneDialog = true }) {
                        Icon(
                            Icons.Default.CleaningServices,
                            contentDescription = "Prune Records",
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D131F).copy(alpha = 0.85f)
                )
            )
        },
        floatingActionButton = {
            if (selectedTabIndex == 1) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.triggerRollingGenesisBlock() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Default.VerifiedUser, contentDescription = null) },
                    text = {
                        Text(
                            "MINT GENESIS BLOCK",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab Switcher
            PrimaryTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color(0xFF0D131F),
                contentColor = MaterialTheme.colorScheme.tertiary,
                indicator = {
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(selectedTabIndex),
                        color = Color(0xFF00D2FF),
                        height = 3.dp
                    )
                }
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    icon = { Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    text = {
                        Text(
                            text = "Economics & Tokens",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    selectedContentColor = Color(0xFF00D2FF),
                    unselectedContentColor = Color.White.copy(alpha = 0.6f)
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    icon = { Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    text = {
                        Text(
                            text = "Cryptographic Audit",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    selectedContentColor = Color(0xFF00D2FF),
                    unselectedContentColor = Color.White.copy(alpha = 0.6f)
                )
            }

            when (selectedTabIndex) {
                0 -> EconomicsTabContent(
                    grandTotalTokens = grandTotalTokens,
                    grandTotalCost = grandTotalCost,
                    allEntries = allLedgerEntries,
                    filteredEntries = ledgerEntries,
                    agentsMap = agentsMap,
                    availableModels = availableModels,
                    selectedModelFilter = selectedModelFilter,
                    onSelectModel = { viewModel.setModelFilter(it) },
                    dateFormatter = dateFormatter
                )
                1 -> AuditTabContent(
                    auditLogs = auditLogs,
                    dateFormatter = dateFormatter
                )
            }
        }
    }
}

@Composable
private fun EconomicsTabContent(
    grandTotalTokens: Int,
    grandTotalCost: Double,
    allEntries: List<TokenLedgerEntity>,
    filteredEntries: List<TokenLedgerEntity>,
    agentsMap: Map<String, com.ekam.baton.core.data.db.entity.AgentEntity>,
    availableModels: List<String>,
    selectedModelFilter: String?,
    onSelectModel: (String?) -> Unit,
    dateFormatter: SimpleDateFormat
) {
    val avgBurnRate = remember(allEntries) {
        if (allEntries.isNotEmpty()) allEntries.map { it.burnRateTks }.average() else 0.0
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // KPI Summary Metric Cards Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KpiMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "TOTAL SPEND",
                    value = String.format(Locale.US, "$%.4f", grandTotalCost),
                    subtext = "USD Aggregated",
                    accentColor = Color(0xFF10B981) // Emerald
                )
                KpiMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "TOKENS",
                    value = NumberFormat.getIntegerInstance().format(grandTotalTokens),
                    subtext = "Prompt + Comp",
                    accentColor = Color(0xFF306EE8) // Cobalt
                )
                KpiMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "THROUGHPUT",
                    value = String.format(Locale.US, "%.1f", avgBurnRate),
                    subtext = "Tokens / Sec",
                    accentColor = Color(0xFF00D2FF) // Cyan
                )
            }
        }

        // Model Filter Pills
        if (availableModels.size > 2) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "FILTER BY MODEL",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(availableModels) { model ->
                            val isSelected = if (model == "ALL") {
                                selectedModelFilter == null || selectedModelFilter == "ALL"
                            } else {
                                selectedModelFilter.equals(model, ignoreCase = true)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                        else Color(0xFF161F30).copy(alpha = 0.6f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) Color(0xFF00D2FF) else Color.White.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .clickable {
                                        onSelectModel(if (model == "ALL") null else model)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = model,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) Color(0xFF00D2FF) else Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TRANSACTION LEDGER TIMELINE",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                    color = Color.White.copy(alpha = 0.6f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF306EE8).copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${filteredEntries.size} EVENTS",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                        color = Color(0xFF00D2FF)
                    )
                }
            }
        }

        // Empty state or Items
        if (filteredEntries.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(shape = RoundedCornerShape(16.dp))
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Speed,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "NO TOKEN TRANSACTIONS",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Execute swarm orchestrations or agent prompts to stream real-time token economics telemetry into this immutable ledger.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(filteredEntries, key = { it.id }) { entry ->
                val agent = agentsMap[entry.agentId]
                val agentName = agent?.name ?: "Agent ${entry.agentId.take(6)}"
                val accentColor = remember(agent?.colorAccent) {
                    try {
                        Color(android.graphics.Color.parseColor(agent?.colorAccent ?: "#306EE8"))
                    } catch (e: Exception) {
                        Color(0xFF306EE8)
                    }
                }
                TokenLedgerCard(
                    entry = entry,
                    agentName = agentName,
                    agentAccent = accentColor,
                    timestamp = dateFormatter.format(Date(entry.timestamp))
                )
            }
        }
    }
}

@Composable
private fun AuditTabContent(
    auditLogs: List<AuditLogEntity>,
    dateFormatter: SimpleDateFormat
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Chain Integrity Card
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(shape = RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SHA-256 MERKLE CHAIN",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                        color = Color(0xFF10B981)
                    )
                    Text(
                        text = "Cryptographically sealed audit trail for regulatory compliance & forensic verification.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Ledger blocks
        items(auditLogs.size) { index ->
            val log = auditLogs[index]
            LedgerBlockCard(
                action = log.action,
                entity = log.entityName,
                timestamp = dateFormatter.format(Date(log.timestamp)),
                hash = "0x" + log.hash.take(8) + "..." + log.hash.takeLast(4),
                isVerified = log.signature != "unsigned"
            )
        }

        // End of chain marker
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Gavel,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (auditLogs.isEmpty()) "LEDGER EMPTY" else "GENESIS BLOCK REACHED",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun KpiMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtext: String,
    accentColor: Color
) {
    Column(
        modifier = modifier
            .glassCard(shape = RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            ),
            color = Color.White.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            ),
            color = accentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtext,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = Color.White.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun TokenLedgerCard(
    entry: TokenLedgerEntity,
    agentName: String,
    agentAccent: Color,
    timestamp: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        // Header Row: Agent Chip + Swarm Tag + Timestamp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(agentAccent)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = agentName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.swarmId != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF00D2FF).copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "SWARM",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFF00D2FF)
                        )
                    }
                }
            }
            Text(
                text = timestamp,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action Context & Model
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = entry.actionContext,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = entry.modelName,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Metrics Footer (Tokens / Cost / Burn Rate)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.25f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tokens
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${entry.totalTokens} tks",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "(P:${entry.promptTokens}/C:${entry.completionTokens})",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp
                    ),
                    color = Color.White.copy(alpha = 0.5f)
                )
            }

            // Burn Rate
            Text(
                text = "${entry.burnRateTks} tk/s",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                ),
                color = Color(0xFF00D2FF)
            )

            // Cost
            Text(
                text = "+$${String.format(Locale.US, "%.5f", entry.estimatedCostUsd)}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFF10B981)
            )
        }
    }
}

@Composable
fun LedgerBlockCard(
    action: String,
    entity: String,
    timestamp: String,
    hash: String,
    isVerified: Boolean
) {
    val actionColor = when(action) {
        "INSERT" -> Color(0xFF10B981) // Emerald
        "UPDATE" -> Color(0xFF306EE8) // Cobalt
        "DELETE" -> Color(0xFFEF4444) // Red
        else -> Color.Gray
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Timeline Dot & Line Segment
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(end = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(actionColor)
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }

        // Ledger Data
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = action,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = actionColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = entity,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Cryptographic Hash Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "SHA256: $hash",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isVerified) {
                    Icon(
                        Icons.Default.VerifiedUser,
                        contentDescription = "Verified",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text(
                        "UNVERIFIED",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = Color(0xFFF59E0B)
                    )
                }
            }
        }
    }
}
