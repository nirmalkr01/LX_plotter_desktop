import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "LX Plotter (Engineering Edition)") {
        MaterialTheme(colorScheme = lightColorScheme()) { DesktopApp() }
    }
}

fun Color.toAwtColor(): java.awt.Color = java.awt.Color(this.red, this.green, this.blue, this.alpha)

@Composable
fun DesktopApp() {
    val scope = rememberCoroutineScope()
    var riverData by remember { mutableStateOf<List<RiverPoint>>(emptyList()) }
    var rawRiverData by remember { mutableStateOf<List<RawRiverPoint>>(emptyList()) }
    var statusMessage by remember { mutableStateOf("No file loaded") }
    var history by remember { mutableStateOf(loadHistory()) }

    // --- STATES ---
    var showInstructions by remember { mutableStateOf(false) }
    var useThalweg by remember { mutableStateOf(true) } // Default: With Thalweg

    var selectedGraphType by remember { mutableStateOf("X-Section") } // Default X-Section first
    var selectedChainage by remember { mutableStateOf(0.0) }
    var showPre by remember { mutableStateOf(true) }
    var showPost by remember { mutableStateOf(true) }

    // Styles
    var preColor by remember { mutableStateOf(Color.Blue) }
    var postColor by remember { mutableStateOf(Color.Red) }
    var preDotted by remember { mutableStateOf(true) }
    var postDotted by remember { mutableStateOf(false) }

    // INDEPENDENT SCALES
    var lHScale by remember { mutableStateOf(2000.0) }
    var lVScale by remember { mutableStateOf(100.0) }
    var xHScale by remember { mutableStateOf(1000.0) }
    var xVScale by remember { mutableStateOf(100.0) }

    // Limits
    var minChainage by remember { mutableStateOf(0.0) }
    var maxChainage by remember { mutableStateOf(0.0) }
    var startChainage by remember { mutableStateOf(0.0) }
    var endChainage by remember { mutableStateOf(0.0) }

    // Re-process data when Thalweg Toggle changes
    LaunchedEffect(useThalweg, rawRiverData) {
        if (rawRiverData.isNotEmpty()) {
            riverData = processAndCenterData(rawRiverData, useThalweg)
            if (riverData.isNotEmpty()) {
                // Calculate limits
                minChainage = riverData.minOf { it.chainage }
                maxChainage = riverData.maxOf { it.chainage }

                // COMMAND EXECUTION: Always start X-Section CH from minimum
                selectedChainage = minChainage

                startChainage = minChainage
                endChainage = maxChainage
            }
        }
    }

    fun loadFile(file: File) {
        if (!file.exists()) {
            statusMessage = "File not found"
            history = history.filter { it != file.absolutePath }
            saveHistory(history)
            return
        }
        val (data, error) = parseCsvStrict(file)
        if (error != null) {
            statusMessage = error
        } else {
            rawRiverData = data // Store raw data to re-process later
            statusMessage = "Loaded ${file.name}"
            val newHistory = (listOf(file.absolutePath) + history).distinct().take(15)
            history = newHistory
            saveHistory(newHistory)
        }
    }

    if (showInstructions) {
        InstructionDialog(onDismiss = { showInstructions = false })
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Row(modifier = Modifier.fillMaxSize()) {
            LeftPanel(
                history = history,
                onHistoryItemClick = { loadFile(File(it)) },
                onDeleteHistoryItem = { path ->
                    // COMMAND EXECUTION: Do not delete file from destination.
                    // Only delete from app history and promote user to new phase (Reset).

                    history = history.filter { it != path }
                    saveHistory(history)

                    // Reset State (Promote to "Upload CSV" phase)
                    riverData = emptyList()
                    rawRiverData = emptyList()
                    selectedChainage = 0.0
                    statusMessage = "Removed from History. Please load a file."
                }
            )

            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                AppHeader(
                    status = statusMessage,
                    onLoad = { pickFile()?.let { loadFile(it) } },
                    onSaveCurrent = {
                        if (riverData.isNotEmpty()) {
                            pickFolder()?.let { folder ->
                                scope.launch {
                                    statusMessage = "Saving..."
                                    try {
                                        val successMsg = withContext(Dispatchers.IO) {
                                            val data = getCurrentViewData(riverData, selectedGraphType, selectedChainage, startChainage, endChainage)
                                            val file = File(folder, "Graph_${System.currentTimeMillis()}.png")
                                            // Pass correct scale based on active view
                                            val h = if(selectedGraphType == "L-Section") lHScale else xHScale
                                            val v = if(selectedGraphType == "L-Section") lVScale else xVScale

                                            saveGraphWithTable(
                                                data, file, selectedGraphType, selectedChainage, showPre, showPost, h, v,
                                                preColor.toAwtColor(), postColor.toAwtColor(), preDotted, postDotted
                                            )
                                            "Saved to ${file.name}"
                                        }
                                        statusMessage = successMsg
                                    } catch (e: Exception) {
                                        statusMessage = "Error: ${e.message}"
                                    }
                                }
                            }
                        }
                    },
                    onBatchSave = {
                        if(riverData.isNotEmpty()) pickFolder()?.let { folder ->
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    // Use X-Scales for batch X-section saves, L-scales for L-section
                                    val h = if(selectedGraphType == "L-Section") lHScale else xHScale
                                    val v = if(selectedGraphType == "L-Section") lVScale else xVScale

                                    performBatchSave(riverData, folder, selectedGraphType, startChainage, endChainage, showPre, showPost, h, v, preColor.toAwtColor(), postColor.toAwtColor(), preDotted, postDotted) { statusMessage = it }
                                }
                            }
                        }
                    },
                    onGenerateReport = { /* Void */ },
                    onShowInstructions = { showInstructions = true }
                )

                Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                    if (riverData.isNotEmpty()) {
                        // 1. Controls
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = selectedGraphType == "X-Section", onClick = { selectedGraphType = "X-Section" }, label = { Text("X-Section") })
                                FilterChip(selected = selectedGraphType == "L-Section", onClick = { selectedGraphType = "L-Section" }, label = { Text("L-Section") })
                                Box(Modifier.width(1.dp).height(32.dp).background(Color.LightGray))
                                FilterChip(selected = showPre, onClick = { showPre = !showPre }, label = { Text("Pre") }, leadingIcon = { Icon(Icons.Default.Check, null, tint = preColor) })
                                FilterChip(selected = showPost, onClick = { showPost = !showPost }, label = { Text("Post") }, leadingIcon = { Icon(Icons.Default.Check, null, tint = postColor) })
                            }

                            // Reference Toggle (Thalweg vs Center)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Reference:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                FilterChip(
                                    selected = useThalweg,
                                    onClick = { useThalweg = true },
                                    label = { Text("Thalweg") }
                                )
                                FilterChip(
                                    selected = !useThalweg,
                                    onClick = { useThalweg = false },
                                    label = { Text("Center") }
                                )
                            }

                            // Independent Scales
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (selectedGraphType == "L-Section") {
                                    ScaleInput("L-H Scale:", lHScale) { lHScale = it }
                                    ScaleInput("L-V Scale:", lVScale) { lVScale = it }
                                } else {
                                    ScaleInput("X-H Scale:", xHScale) { xHScale = it }
                                    ScaleInput("X-V Scale:", xVScale) { xVScale = it }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // 2. Style & Range
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row {
                                StyleSelector("Pre:", preDotted, { preDotted = it }, preColor, { preColor = it })
                                StyleSelector("Post:", postDotted, { postDotted = it }, postColor, { postColor = it })
                            }

                            if (selectedGraphType == "L-Section") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Range:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    ScaleInput("Start", startChainage) { startChainage = it }
                                    ScaleInput("End", endChainage) { endChainage = it }

                                    if(startChainage < minChainage || endChainage > maxChainage || startChainage > endChainage) {
                                        Text("(Invalid!)", fontSize = 11.sp, color = Color.Red)
                                    } else {
                                        Text("(Min:$minChainage - Max:$maxChainage)", fontSize = 11.sp, color = Color.Gray)
                                    }
                                }
                            } else {
                                val uniqueChainages = remember(riverData) { riverData.map { it.chainage }.distinct().sorted() }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("CH: ${String.format("%.0f", selectedChainage)}", fontWeight = FontWeight.Bold)
                                    IconButton(onClick = {
                                        val idx = uniqueChainages.indexOf(selectedChainage)
                                        if(idx > 0) selectedChainage = uniqueChainages[idx - 1]
                                    }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Remove, null) }

                                    Slider(value = selectedChainage.toFloat(), onValueChange = { v -> selectedChainage = uniqueChainages.minByOrNull { abs(it - v) } ?: v.toDouble() }, valueRange = uniqueChainages.first().toFloat()..uniqueChainages.last().toFloat(), modifier = Modifier.width(200.dp))

                                    IconButton(onClick = {
                                        val idx = uniqueChainages.indexOf(selectedChainage)
                                        if(idx < uniqueChainages.size - 1) selectedChainage = uniqueChainages[idx + 1]
                                    }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Add, null) }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // 3. Graph Area
                        val dataToPlot = remember(selectedGraphType, selectedChainage, riverData, startChainage, endChainage) {
                            getCurrentViewData(riverData, selectedGraphType, selectedChainage, startChainage, endChainage)
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline).background(Color.White)) {
                            // Pass correct scales to Canvas based on active view
                            val h = if(selectedGraphType == "L-Section") lHScale else xHScale
                            val v = if(selectedGraphType == "L-Section") lVScale else xVScale

                            EngineeringCanvas(dataToPlot, selectedGraphType == "L-Section", showPre, showPost, h, v, preColor, postColor, preDotted, postDotted)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 4. Data Table
                        CompactDataTable(dataToPlot, selectedGraphType, preColor, postColor)
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Load a CSV file to begin", color = Color.Gray) }
                    }
                }
            }
        }
    }
}