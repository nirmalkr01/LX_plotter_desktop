import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

// --- DATA CLASS FOR ERRORS ---
data class DataError(val chainage: Double, val offset: Double, val pre: Double, val post: Double)

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
    errors: List<DataError>,
    onNavigateToError: (Double) -> Unit,
    onLoad: () -> Unit,
    onDownloadCsv: () -> Unit,
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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {

                // --- NOTIFICATION ICON ---
                NotificationSection(errors, onNavigateToError)

                VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp))

                // 1. Load
                Button(onClick = onLoad, contentPadding = PaddingValues(horizontal = 12.dp), shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Load")
                }

                // 2. Download Menu
                Box {
                    Button(onClick = { showDownloadMenu = true }, contentPadding = PaddingValues(horizontal = 12.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) {
                        Icon(Icons.Default.Download, "Download", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Download")
                    }
                    DropdownMenu(expanded = showDownloadMenu, onDismissRequest = { showDownloadMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Report") },
                            leadingIcon = { Icon(Icons.Default.Description, null) },
                            onClick = { onGenerateReport(); showDownloadMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("CSV (Modified)") },
                            leadingIcon = { Icon(Icons.Default.GridOn, null) },
                            onClick = { onDownloadCsv(); showDownloadMenu = false }
                        )
                    }
                }

                // 3. Instructions
                IconButton(onClick = onShowInstructions) {
                    Icon(Icons.Default.Info, "Help", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

@Composable
fun NotificationSection(errors: List<DataError>, onNavigate: (Double) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val hasErrors = errors.isNotEmpty()

    Box {
        IconButton(onClick = { if (hasErrors) expanded = true }) {
            BadgedBox(
                badge = {
                    if (hasErrors) {
                        Badge(containerColor = Color.Red) {
                            Text(errors.size.toString(), color = Color.White)
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = if (hasErrors) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    tint = if (hasErrors) Color.Red else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(300.dp).heightIn(max = 400.dp)
        ) {
            Text(
                "Data Inconsistencies (Pre > Post)",
                modifier = Modifier.padding(12.dp),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.Red
            )
            HorizontalDivider()
            errors.forEach { error ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("Chainage: ${error.chainage}m", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Offset: ${error.offset}m (Pre: ${error.pre} > Post: ${error.post})", fontSize = 11.sp)
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.ErrorOutline, null, tint = Color.Red) },
                    onClick = {
                        onNavigate(error.chainage)
                        expanded = false
                    }
                )
            }
        }
    }
}

// --- CSV MAPPING DIALOG ---
@Composable
fun CsvMappingDialog(
    headers: List<String>,
    previewRows: List<List<String>>,
    onDismiss: () -> Unit,
    onConfirm: (Map<String, Int>) -> Unit
) {
    var chainageIdx by remember { mutableStateOf(headers.indexOfFirst { it.contains("chain", ignoreCase = true) }.let { if(it == -1) 0 else it }) }
    var distIdx by remember { mutableStateOf(headers.indexOfFirst { it.contains("dist", ignoreCase = true) || it.contains("offset", ignoreCase = true) }.let { if(it == -1) 1 else it }) }
    var preIdx by remember { mutableStateOf(headers.indexOfFirst { it.contains("pre", ignoreCase = true) }.let { if(it == -1) 2 else it }) }
    var postIdx by remember { mutableStateOf(headers.indexOfFirst { it.contains("post", ignoreCase = true) }.let { if(it == -1) 3 else it }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Map CSV Columns", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.width(600.dp)) {
                Text("Select which column corresponds to each required field:", fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Chainage", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        ColumnSelector(headers, chainageIdx) { chainageIdx = it }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Distance/Offset", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        ColumnSelector(headers, distIdx) { distIdx = it }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Pre-Monsoon Level", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        ColumnSelector(headers, preIdx) { preIdx = it }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Post-Monsoon Level", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        ColumnSelector(headers, postIdx) { postIdx = it }
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                Text("File Preview (First 5 Rows):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))

                Column(Modifier.border(1.dp, Color.LightGray).fillMaxWidth()) {
                    Row(Modifier.background(Color.LightGray.copy(alpha=0.3f)).padding(4.dp)) {
                        headers.forEach { h -> Text(h, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1) }
                    }
                    previewRows.take(5).forEach { row ->
                        Row(Modifier.border(0.5.dp, Color.LightGray.copy(alpha=0.3f)).padding(4.dp)) {
                            for(i in headers.indices) {
                                val txt = if(i < row.size) row[i] else ""
                                Text(txt, fontSize = 10.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(mapOf(
                    "chainage" to chainageIdx,
                    "distance" to distIdx,
                    "pre" to preIdx,
                    "post" to postIdx
                ))
            }) { Text("Confirm Load") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ColumnSelector(options: List<String>, selectedIdx: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            val text = if(selectedIdx in options.indices) options[selectedIdx] else "Select..."
            Text(text, fontSize = 12.sp, maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, name ->
                DropdownMenuItem(
                    text = { Text(name, fontSize = 12.sp) },
                    onClick = { onSelect(index); expanded = false }
                )
            }
        }
    }
}

// --- INSTRUCTION DIALOG ---
@Composable
fun InstructionDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Complete Application Walkthrough", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(450.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Step 1: Loading Data", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    Text("• Click the 'Load' button in the top header.", fontSize = 13.sp)
                    Text("• Select your CSV file. A mapping window will appear.", fontSize = 13.sp)
                    Text("• Use the dropdowns to match your CSV headers to 'Chainage', 'Offset', and 'Monsoon Levels'.", fontSize = 13.sp)
                    Text("• Check the preview table to ensure data looks correct, then click 'Confirm Load'.", fontSize = 13.sp)
                }

                HorizontalDivider()

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Step 2: Analysis & Style", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    Text("• Switch between X-Section and L-Section using the top ribbon.", fontSize = 13.sp)
                    Text("• Use the 'Style' tab to change colors, thickness, or toggle dotted lines for Pre/Post series.", fontSize = 13.sp)
                    Text("• Check notifications (Bell icon) for any Pre-Monsoon levels that are higher than Post-Monsoon.", fontSize = 13.sp)
                    Text("• Edit values directly in the bottom table by enabling 'Manual Mode'.", fontSize = 13.sp)
                }

                HorizontalDivider()

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Step 3: Preparing the Layout", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    Text("• Click 'Download' -> 'Report' to enter the Designer Screen.", fontSize = 13.sp)
                    Text("• In the designer, create pages using the '+' button at the bottom.", fontSize = 13.sp)
                    Text("• Enable 'Grid Mode' (Blue chip at the top) to see available slots on the paper.", fontSize = 13.sp)
                    Text("• Select a graph from the left list, click an empty slot on the page, and press 'Add to Slot'.", fontSize = 13.sp)
                }

                HorizontalDivider()

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Step 4: Interactive Customization", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    Text("• Inside the 'Image View', use the 'Select Element' dropdown to pick 'RIVER' text or 'Blue Lines'.", fontSize = 13.sp)
                    Text("• Drag the elements directly on the preview graph to position them (e.g., move bank indicators).", fontSize = 13.sp)
                    Text("• For L-Sections, use 'Auto Split' to automatically divide long river profiles into multiple printable slots.", fontSize = 13.sp)
                }

                HorizontalDivider()

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Step 5: Annotation & Final PDF", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    Text("• Use 'Insert & Text' tab to add manual notes, arrows, or shapes to your report pages.", fontSize = 13.sp)
                    Text("• Set Global Header info (Annexure, B1 Text) and apply it to all pages at once.", fontSize = 13.sp)
                    Text("• Finally, click the red 'Export PDF' button. Choose a save location and the app will generate and open your final multi-page engineering document.", fontSize = 13.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it!") } }
    )
}

// --- LEFT PANEL ---
@Composable
fun LeftPanel(history: List<String>, onHistoryItemClick: (String) -> Unit, onDeleteHistoryItem: (String) -> Unit) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier.width(260.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface)
            .drawBehind { drawLine(borderColor, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx()) }
            .padding(12.dp)
    ) {
        Text("History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No recent files", fontSize = 12.sp, color = Color.Gray) }
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
        Row(modifier = Modifier.padding(8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
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
                    DropdownMenuItem(text = { Text("Delete", color = Color.Red) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(16.dp)) }, onClick = { onDelete(path); showMenu = false })
                }
            }
        }
    }
}

// --- DATA TABLE WITH EDITING ---
@Composable
fun CompactDataTable(
    data: List<RiverPoint>,
    type: String,
    preColor: Color,
    postColor: Color,
    isManualMode: Boolean,
    hasManualZero: Boolean,
    onManualModeToggle: (Boolean) -> Unit,
    onUpdateValue: (String, Double, Double) -> Unit,
    onSetZero: (String) -> Unit,
    onResetZero: () -> Unit
) {
    val scrollState = rememberScrollState()
    val isLSection = type == "L-Section"
    val canEdit = isManualMode && !isLSection

    Row(
        modifier = Modifier.fillMaxWidth().height(182.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)).background(Color.White, RoundedCornerShape(4.dp))
    ) {
        Column(
            modifier = Modifier.width(160.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow).customBorder(1.dp, MaterialTheme.colorScheme.outlineVariant, end = true).padding(bottom = 12.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().height(32.dp).customBorder(0.5.dp, Color.LightGray.copy(alpha = 0.5f), bottom = true).background(Color.LightGray.copy(alpha = 0.2f)),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isManualMode,
                        onCheckedChange = if (!isLSection) onManualModeToggle else null,
                        enabled = !isLSection,
                        modifier = Modifier.scale(0.7f)
                    )
                    Text(
                        if(isLSection) "Read-Only" else "Manual Mode",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if(isLSection) Color.Gray else Color.Black
                    )
                }
            }
            HeaderCell("Post Monsoon:", postColor)
            HeaderCell("Pre Monsoon:", preColor)
            HeaderCell("Difference:", Color(0xFF616161))
            HeaderCell(if (isLSection) "Chainage in mt:" else "Offset in mt:", Color.Black)
        }

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Row(modifier = Modifier.fillMaxSize().horizontalScroll(scrollState)) {
                data.forEach { point ->
                    val diff = point.postMonsoon - point.preMonsoon
                    val isError = point.preMonsoon > point.postMonsoon

                    Column(modifier = Modifier.width(90.dp).fillMaxHeight()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(32.dp).customBorder(0.5.dp, Color.LightGray.copy(alpha = 0.5f), bottom = true, start = true).background(Color.LightGray.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (point.isZeroPoint) {
                                if (hasManualZero) {
                                    if(canEdit) {
                                        TextButton(onClick = onResetZero, contentPadding = PaddingValues(0.dp)) {
                                            Text("ZERO (M)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Red)
                                        }
                                    } else {
                                        Text("ZERO (M)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Red)
                                    }
                                } else {
                                    Text("ZERO (A)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF006400))
                                }
                            } else if (canEdit) {
                                TextButton(onClick = { onSetZero(point.id) }, contentPadding = PaddingValues(0.dp)) {
                                    Text("Set 0", fontSize = 9.sp)
                                }
                            }
                        }

                        EditableDataCell(String.format("%.2f", point.postMonsoon), postColor, canEdit, isError) { newVal ->
                            newVal.toDoubleOrNull()?.let { onUpdateValue(point.id, point.preMonsoon, it) }
                        }
                        EditableDataCell(String.format("%.2f", point.preMonsoon), preColor, canEdit, isError) { newVal ->
                            newVal.toDoubleOrNull()?.let { onUpdateValue(point.id, it, point.postMonsoon) }
                        }

                        DataCell(String.format("%.2f", diff), if (isError) Color.Red else Color.Black)
                        DataCell(String.format("%.1f", if (isLSection) point.chainage else point.distance), if (point.isZeroPoint) Color.Red else Color.Black)
                    }
                }
            }
            HorizontalScrollbar(adapter = rememberScrollbarAdapter(scrollState), modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 4.dp))
        }
    }
}

@Composable
fun EditableDataCell(text: String, color: Color, isEditable: Boolean, highlightError: Boolean = false, onCommit: (String) -> Unit) {
    var isEditing by remember { mutableStateOf(false) }
    var tempText by remember { mutableStateOf(text) }

    LaunchedEffect(text) {
        if (!isEditing) tempText = text
    }

    LaunchedEffect(isEditable) {
        if (!isEditable) isEditing = false
    }

    Box(
        modifier = Modifier.fillMaxWidth().height(32.dp)
            .customBorder(0.5.dp, Color.LightGray.copy(alpha = 0.5f), bottom = true, start = true)
            .background(if (highlightError) Color.Red.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(enabled = isEditable) { isEditing = true },
        contentAlignment = Alignment.Center
    ) {
        if (isEditing) {
            BasicTextField(
                value = tempText,
                onValueChange = { tempText = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = color, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth().background(Color.Yellow.copy(alpha = 0.3f)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    isEditing = false
                    onCommit(tempText)
                })
            )
        } else {
            Text(text, fontSize = 11.sp, color = if(isEditable) color else color.copy(alpha=0.6f), textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun HeaderCell(text: String, color: Color, isTop: Boolean = false) {
    Box(
        modifier = Modifier.fillMaxWidth().height(32.dp).customBorder(0.5.dp, Color.LightGray.copy(alpha = 0.5f), bottom = true).background(if (isTop) Color.LightGray.copy(alpha = 0.2f) else Color.Transparent),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
fun DataCell(text: String, color: Color, isTop: Boolean = false) {
    Box(
        modifier = Modifier.fillMaxWidth().height(32.dp).customBorder(0.5.dp, Color.LightGray.copy(alpha = 0.5f), bottom = true, start = true).background(if (isTop) Color.LightGray.copy(alpha = 0.2f) else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 11.sp, color = color, textAlign = TextAlign.Center)
    }
}

@Composable
fun StyleSelector(
    label: String,
    isDotted: Boolean,
    onDottedChange: (Boolean) -> Unit,
    color: Color,
    onColorChange: (Color) -> Unit,
    thickness: Float,
    onThicknessChange: (Float) -> Unit,
    showPoints: Boolean,
    onShowPointsChange: (Boolean) -> Unit
) {
    var expandedColor by remember { mutableStateOf(false) }
    var expandedThickness by remember { mutableStateOf(false) }
    val colors = listOf(Color.Red, Color.Blue, Color(0xFF006400), Color.Black, Color.Magenta, Color.Cyan)
    val thicknessOptions = listOf(1f, 2f, 3f, 5f, 8f)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
        Box {
            Box(modifier = Modifier.size(20.dp).background(color, androidx.compose.foundation.shape.CircleShape).border(1.dp, Color.Gray, androidx.compose.foundation.shape.CircleShape).clickable { expandedColor = true })
            DropdownMenu(expanded = expandedColor, onDismissRequest = { expandedColor = false }) {
                colors.forEach { c -> DropdownMenuItem(text = { Box(Modifier.size(20.dp).background(c, androidx.compose.foundation.shape.CircleShape)) }, onClick = { onColorChange(c); expandedColor = false }) }
            }
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { onDottedChange(!isDotted) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), modifier = Modifier.height(28.dp), shape = RoundedCornerShape(4.dp)) { Text(if (isDotted) "Dotted" else "Solid", fontSize = 10.sp) }
        Spacer(Modifier.width(8.dp))
        Box {
            OutlinedButton(onClick = { expandedThickness = true }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp), modifier = Modifier.height(28.dp), shape = RoundedCornerShape(4.dp)) { Text("${thickness.toInt()}px", fontSize = 10.sp) }
            DropdownMenu(expanded = expandedThickness, onDismissRequest = { expandedThickness = false }) {
                thicknessOptions.forEach { t -> DropdownMenuItem(text = { Text("${t.toInt()}px", fontSize = 12.sp) }, onClick = { onThicknessChange(t); expandedThickness = false }) }
            }
        }
        Spacer(Modifier.width(8.dp))
        IconToggleButton(checked = showPoints, onCheckedChange = onShowPointsChange, modifier = Modifier.size(28.dp)) { Icon(if (showPoints) Icons.Default.Circle else Icons.Default.Lens, contentDescription = "Toggle Points", tint = if (showPoints) color else Color.Gray, modifier = Modifier.size(16.dp)) }
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
            modifier = Modifier.width(60.dp).height(24.dp).background(Color.White, RoundedCornerShape(4.dp)).border(1.dp, Color.Gray, RoundedCornerShape(4.dp)).wrapContentHeight(Alignment.CenterVertically)
        )
    }
}