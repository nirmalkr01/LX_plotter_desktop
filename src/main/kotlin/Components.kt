import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

// --- HELPER FOR CUSTOM BORDERS ---
fun Modifier.customBorder(
    width: Dp,
    color: Color,
    top: Boolean = false,
    bottom: Boolean = false,
    start: Boolean = false,
    end: Boolean = false
) = this.drawBehind {
    val strokeWidth = width.toPx()
    if (top) drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth)
    if (bottom) drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth)
    if (start) drawLine(color, Offset(0f, 0f), Offset(0f, size.height), strokeWidth)
    if (end) drawLine(color, Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth)
}

// --- HEADER ---
@Composable
fun AppHeader(
    status: String,
    onLoad: () -> Unit,
    onSaveCurrent: () -> Unit,
    onBatchSave: () -> Unit,
    onGenerateReport: () -> Unit,
    onShowInstructions: () -> Unit
) {
    var showDownloadMenu by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timeline, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(8.dp))
                Text("LX Plotter", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(16.dp))
                Text(status, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            }

            // Buttons Order: Load -> Report -> Download -> Instruction
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // 1. Load
                Button(onClick = onLoad, contentPadding = PaddingValues(horizontal = 12.dp), shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Load")
                }

                // 2. Report
                IconButton(onClick = onGenerateReport) {
                    Icon(Icons.Default.Description, "Report", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }

                // 3. Download
                Box {
                    TextButton(onClick = { showDownloadMenu = true }) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Download")
                    }
                    DropdownMenu(expanded = showDownloadMenu, onDismissRequest = { showDownloadMenu = false }) {
                        DropdownMenuItem(text = { Text("Current View (.png)") }, onClick = { onSaveCurrent(); showDownloadMenu = false })
                        DropdownMenuItem(text = { Text("All Views (Batch)") }, onClick = { onBatchSave(); showDownloadMenu = false })
                    }
                }

                // 4. Instructions
                IconButton(onClick = onShowInstructions) {
                    Icon(Icons.Default.Info, "Help", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

// --- INSTRUCTION DIALOG ---
@Composable
fun InstructionDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Instructions & Logic") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Text("1. Graph to Ground Conversion", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Formula: Real Value = Measured Graph Unit × Scale", fontSize = 13.sp)
                    Text("Example: Scale 2000. Measured 5 units. Real = 5 × 2000 = 10,000 units.", fontSize = 12.sp, color = Color.Gray)
                }
                HorizontalDivider()
                Column {
                    Text("2. Reference Logic (Zero Point)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("• With Thalweg: The distance with the lowest Pre-Monsoon level becomes 0.", fontSize = 13.sp)
                    Text("• Without Thalweg: The middle distance becomes 0. (If even count, deeper of two middle points is used).", fontSize = 13.sp)
                    Text("• L-Section: Generated from the calculated '0' point of every X-Section.", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

// --- LEFT PANEL ---
@Composable
fun LeftPanel(history: List<String>, onHistoryItemClick: (String) -> Unit, onDeleteHistoryItem: (String) -> Unit) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .width(260.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind { drawLine(borderColor, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx()) }
            .padding(12.dp)
    ) {
        Text("History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))

        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No recent files", fontSize = 12.sp, color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history) { path -> HistoryItemRow(path, onHistoryItemClick, onDeleteHistoryItem) }
            }
        }
    }
}

@Composable
fun HistoryItemRow(path: String, onClick: (String) -> Unit, onDelete: (String) -> Unit) {
    val file = File(path)
    var showMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clickable { onClick(path) }
    ) {
        Row(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.InsertDriveFile, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(file.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(file.parent ?: "", fontSize = 10.sp, maxLines = 1, color = Color.Gray)
                }
            }
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.MoreVert, null, modifier = Modifier.size(16.dp)) }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Delete", color = Color.Red) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(16.dp)) },
                        onClick = { onDelete(path); showMenu = false }
                    )
                }
            }
        }
    }
}

// --- DATA TABLE ---
@Composable
fun CompactDataTable(data: List<RiverPoint>, type: String, preColor: Color, postColor: Color) {
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
            .background(Color.White, RoundedCornerShape(4.dp))
    ) {
        // Fixed Header Column
        Column(
            modifier = Modifier
                .width(150.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .customBorder(1.dp, MaterialTheme.colorScheme.outlineVariant, end = true)
                .padding(bottom = 12.dp)
        ) {
            HeaderCell("Parameter", Color.Black, true)
            HeaderCell("Post Monsoon:", postColor)
            HeaderCell("Pre Monsoon:", preColor)
            HeaderCell(if (type == "L-Section") "Chainage in mt:" else "Offset in mt:", Color.Black)
        }

        // Scrollable Data Area
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Row(modifier = Modifier.fillMaxSize().horizontalScroll(scrollState)) {
                data.forEach { point ->
                    Column(modifier = Modifier.width(90.dp).fillMaxHeight()) {
                        DataCell("", Color.Transparent, true)
                        DataCell(String.format("%.2f", point.postMonsoon), postColor)
                        DataCell(String.format("%.2f", point.preMonsoon), preColor)
                        DataCell(String.format("%.1f", if (type == "L-Section") point.chainage else point.distance), Color.Black)
                    }
                }
            }
            HorizontalScrollbar(
                adapter = rememberScrollbarAdapter(scrollState),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
fun HeaderCell(text: String, color: Color, isTop: Boolean = false) {
    Box(
        modifier = Modifier.fillMaxWidth().height(32.dp).customBorder(0.5.dp, Color.LightGray.copy(alpha = 0.5f), bottom = true)
            .background(if(isTop) Color.LightGray.copy(alpha = 0.2f) else Color.Transparent),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
fun DataCell(text: String, color: Color, isTop: Boolean = false) {
    Box(
        modifier = Modifier.fillMaxWidth().height(32.dp).customBorder(0.5.dp, Color.LightGray.copy(alpha = 0.5f), bottom = true, start = true)
            .background(if(isTop) Color.LightGray.copy(alpha = 0.2f) else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 11.sp, color = color, textAlign = TextAlign.Center)
    }
}

// --- UTILS ---
@Composable
fun StyleSelector(label: String, isDotted: Boolean, onDottedChange: (Boolean) -> Unit, color: Color, onColorChange: (Color) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val colors = listOf(Color.Red, Color.Blue, Color(0xFF006400), Color.Black, Color.Magenta, Color.Cyan)

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))

        Box {
            Box(
                modifier = Modifier.size(20.dp).background(color, androidx.compose.foundation.shape.CircleShape)
                    .border(1.dp, Color.Gray, androidx.compose.foundation.shape.CircleShape).clickable { expanded = true }
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                colors.forEach { c ->
                    DropdownMenuItem(
                        text = { Box(Modifier.size(20.dp).background(c, androidx.compose.foundation.shape.CircleShape)) },
                        onClick = { onColorChange(c); expanded = false }
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))

        OutlinedButton(
            onClick = { onDottedChange(!isDotted) },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            modifier = Modifier.height(28.dp),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(if (isDotted) "Dotted" else "Solid", fontSize = 10.sp)
        }
    }
}

@Composable
fun ScaleInput(label: String, value: Double, onValueChange: (Double) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.sp)
        Spacer(Modifier.width(4.dp))
        BasicTextField(
            value = if (value == 0.0) "" else value.toInt().toString(),
            onValueChange = { onValueChange(it.toDoubleOrNull() ?: 0.0) },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, textAlign = TextAlign.Center),
            modifier = Modifier
                .width(60.dp).height(24.dp)
                .background(Color.White, RoundedCornerShape(4.dp))
                .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                .wrapContentHeight(Alignment.CenterVertically)
        )
    }
}