import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@Composable
fun AppHeader(
    status: String,
    onLoad: () -> Unit,
    onSaveCurrent: () -> Unit,
    onBatchSave: () -> Unit,
    onGenerateReport: () -> Unit,
    onToggleTheme: () -> Unit,
    isDarkMode: Boolean
) {
    var showDownloadMenu by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().height(60.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("LX Plotter", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(16.dp))
                Text(status, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onLoad) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Load") }
                Box {
                    TextButton(onClick = { showDownloadMenu = true }) { Icon(Icons.Default.ArrowDropDown, null); Spacer(Modifier.width(4.dp)); Text("Download") }
                    DropdownMenu(expanded = showDownloadMenu, onDismissRequest = { showDownloadMenu = false }) {
                        DropdownMenuItem(text = { Text("Download Current View") }, onClick = { onSaveCurrent(); showDownloadMenu = false })
                        DropdownMenuItem(text = { Text("Download All (Batch)") }, onClick = { onBatchSave(); showDownloadMenu = false })
                    }
                }
                HeaderIconBtn(Icons.Default.Edit, "Report", onGenerateReport)
                HeaderIconBtn(if (isDarkMode) Icons.Default.Brightness7 else Icons.Default.Brightness4, if (isDarkMode) "Light Mode" else "Dark Mode", onToggleTheme)
            }
        }
    }
}

@Composable
fun HeaderIconBtn(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(label)
    }
}

@Composable
fun LeftPanel(history: List<String>, onHistoryItemClick: (String) -> Unit, onDeleteHistoryItem: (String) -> Unit) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier.width(250.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface)
            .drawBehind { drawLine(borderColor, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx()) }
            .padding(16.dp)
    ) {
        Text("History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        if (history.isEmpty()) {
            Text("No recent files", fontSize = 12.sp, color = Color.Gray)
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
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick(path) }) {
        Row(modifier = Modifier.padding(8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DateRange, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(file.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(file.parent ?: "", fontSize = 10.sp, maxLines = 1)
                }
            }
            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null) }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Delete", color = Color.Red) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }, onClick = { onDelete(path); showMenu = false })
                }
            }
        }
    }
}

@Composable
fun TableCell(text: String, isHeader: Boolean = false, color: Color = Color.Black) {
    Box(
        modifier = Modifier.fillMaxWidth().height(30.dp).border(BorderStroke(0.5.dp, Color.LightGray)),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 11.sp, fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal, color = color, textAlign = TextAlign.Center)
    }
}

@Composable
fun ScaleInput(label: String, value: Double, onValueChange: (Double) -> Unit) {
    Column {
        Text(label, fontSize = 10.sp)
        OutlinedTextField(
            value = if(value == 0.0) "" else value.toInt().toString(),
            onValueChange = { onValueChange(it.toDoubleOrNull() ?: 0.0) },
            modifier = Modifier.width(70.dp).height(50.dp),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
        )
    }
}