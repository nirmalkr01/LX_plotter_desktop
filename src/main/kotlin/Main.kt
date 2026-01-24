import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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

enum class ControlGroup {
    VIEW,
    STYLE,
    SCALE,
    RANGE
}

enum class Screen {
    MAIN,
    REPORT_DOWNLOAD
}

@Composable
fun DesktopApp() {
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf(Screen.MAIN) }

    var riverData by remember { mutableStateOf<List<RiverPoint>>(emptyList()) }
    var rawRiverData by remember { mutableStateOf<List<RawRiverPoint>>(emptyList()) }
    var statusMessage by remember { mutableStateOf("No file loaded") }
    var history by remember { mutableStateOf(loadHistory()) }

    // --- STATES ---
    var showInstructions by remember { mutableStateOf(false) }

    // --- CSV MAPPING STATES ---
    var showCsvMapping by remember { mutableStateOf(false) }
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var csvHeaders by remember { mutableStateOf<List<String>>(emptyList()) }
    var csvPreviewRows by remember { mutableStateOf<List<List<String>>>(emptyList()) }

    var useThalweg by remember { mutableStateOf(true) } // Default: With Thalweg

    var selectedGraphType by remember { mutableStateOf("X-Section") }
    var selectedChainage by remember { mutableStateOf(0.0) }
    var showPre by remember { mutableStateOf(true) }
    var showPost by remember { mutableStateOf(true) }

    // Styles
    var preColor by remember { mutableStateOf(Color.Blue) }
    var postColor by remember { mutableStateOf(Color.Red) }
    var preDotted by remember { mutableStateOf(true) }
    var postDotted by remember { mutableStateOf(false) }

    // Thickness & Markers
    var preWidth by remember { mutableStateOf(2f) }
    var postWidth by remember { mutableStateOf(2f) }
    var preShowPoints by remember { mutableStateOf(true) }
    var postShowPoints by remember { mutableStateOf(true) }

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

    // RIBBON STATES
    var activeControlGroup by remember { mutableStateOf(ControlGroup.VIEW) }
    var isRibbonOpen by remember { mutableStateOf(true) }
    var showRuler by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(false) }

    // ZOOM STATE
    var graphZoom by remember { mutableStateOf(0.6f) } // Initial Zoom at 60%

    // MANUAL MODE STATES
    var isManualMode by remember { mutableStateOf(false) }
    var manualZeroOverrides by remember { mutableStateOf(mapOf<Double, String>()) }

    // --- RE-CALCULATE ERRORS (Pre > Post) ---
    val dataErrors = remember(riverData) {
        riverData.filter { it.preMonsoon > it.postMonsoon }
            .map { DataError(it.chainage, it.distance, it.preMonsoon, it.postMonsoon) }
    }

    // REACTIVE CORE: Re-process data when raw values, thalweg toggle, or manual 0 shifts change
    LaunchedEffect(useThalweg, rawRiverData, manualZeroOverrides) {
        if (rawRiverData.isNotEmpty()) {
            riverData = processAndCenterData(rawRiverData, useThalweg, manualZeroOverrides)
            if (riverData.isNotEmpty()) {
                minChainage = riverData.minOf { it.chainage }
                maxChainage = riverData.maxOf { it.chainage }

                if(selectedChainage == 0.0) {
                    selectedChainage = minChainage
                    startChainage = minChainage
                    endChainage = maxChainage
                }
            }
        }
    }

    // --- STEP 1: PREPARE FILE ---
    fun prepareFileLoad(file: File) {
        if (!file.exists()) {
            statusMessage = "File not found"
            return
        }
        val (headers, rows) = readCsvPreview(file)
        if (headers.isEmpty()) {
            statusMessage = "Empty or Invalid CSV"
            return
        }
        csvHeaders = headers
        csvPreviewRows = rows
        pendingFile = file
        showCsvMapping = true
    }

    // --- STEP 2: FINISH LOAD WITH MAPPING ---
    fun finishLoading(colMapping: Map<String, Int>) {
        val file = pendingFile ?: return
        val (data, error) = parseCsvMapped(file, colMapping)

        if (error != null) {
            statusMessage = error
        } else {
            rawRiverData = data
            manualZeroOverrides = emptyMap()
            isManualMode = false
            selectedChainage = 0.0
            statusMessage = "Loaded ${file.name}"
            val newHistory = (listOf(file.absolutePath) + history).distinct().take(15)
            history = newHistory
            saveHistory(newHistory)
        }
        showCsvMapping = false
        pendingFile = null
    }

    if (showInstructions) {
        InstructionDialog(onDismiss = { showInstructions = false })
    }

    if (showCsvMapping) {
        CsvMappingDialog(
            headers = csvHeaders,
            previewRows = csvPreviewRows,
            onDismiss = { showCsvMapping = false },
            onConfirm = { mapping -> finishLoading(mapping) }
        )
    }

    // --- SCREEN SWITCHING ---
    when (currentScreen) {
        Screen.REPORT_DOWNLOAD -> {
            ReportDownloadScreen(
                riverData = riverData,
                initialGraphType = selectedGraphType,
                initialChainage = selectedChainage,
                startCh = startChainage,
                endCh = endChainage,
                lHScale = lHScale, lVScale = lVScale,
                xHScale = xHScale, xVScale = xVScale,
                showPre = showPre, showPost = showPost,
                preColor = preColor.toAwtColor(), postColor = postColor.toAwtColor(),
                preDotted = preDotted, postDotted = postDotted,
                preWidth = preWidth, postWidth = postWidth,
                preShowPoints = preShowPoints, postShowPoints = postShowPoints,
                showGrid = showGrid,
                onBack = { currentScreen = Screen.MAIN }
            )
        }
        Screen.MAIN -> {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Row(modifier = Modifier.fillMaxSize()) {
                    LeftPanel(
                        history = history,
                        onHistoryItemClick = { prepareFileLoad(File(it)) },
                        onDeleteHistoryItem = { path ->
                            history = history.filter { it != path }
                            saveHistory(history)
                            if (pendingFile?.absolutePath == path) pendingFile = null
                        }
                    )

                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        AppHeader(
                            status = statusMessage,
                            errors = dataErrors,
                            onNavigateToError = { chainage ->
                                selectedGraphType = "X-Section"
                                selectedChainage = chainage
                            },
                            onLoad = { pickFile()?.let { prepareFileLoad(it) } },
                            onDownloadCsv = {
                                if(riverData.isNotEmpty()) pickSaveFile("modified_data.csv")?.let { file ->
                                    scope.launch(Dispatchers.IO) {
                                        saveCsv(riverData, file)
                                        statusMessage = "CSV Saved: ${file.name}"
                                    }
                                }
                            },
                            onGenerateReport = {
                                if (riverData.isNotEmpty()) {
                                    currentScreen = Screen.REPORT_DOWNLOAD
                                } else {
                                    statusMessage = "Load data first"
                                }
                            },
                            onShowInstructions = { showInstructions = true }
                        )

                        if (riverData.isNotEmpty()) {
                            // --- HANGING RIBBON CONTROL ---
                            Surface(
                                modifier = Modifier.fillMaxWidth().animateContentSize(),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shadowElevation = 4.dp,
                                shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (isRibbonOpen) {
                                        // 1. GROUP TABS
                                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), horizontalArrangement = Arrangement.Center) {
                                            ControlGroup.entries.forEach { group ->
                                                Spacer(Modifier.width(4.dp))
                                                FilterChip(
                                                    selected = activeControlGroup == group,
                                                    onClick = { activeControlGroup = group },
                                                    label = { Text(group.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }) }
                                                )
                                                Spacer(Modifier.width(4.dp))
                                            }
                                        }
                                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                                        // 2. ACTIVE CONTROLS ROW
                                        Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                            when (activeControlGroup) {
                                                ControlGroup.VIEW -> {
                                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text("Profile:", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(end=4.dp))
                                                            SegmentedButtonRow {
                                                                OutlinedButton(onClick = { selectedGraphType = "X-Section" }, shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp), colors = if(selectedGraphType=="X-Section") ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else ButtonDefaults.outlinedButtonColors()) { Text("X-Sec") }
                                                                OutlinedButton(onClick = { selectedGraphType = "L-Section" }, shape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp), colors = if(selectedGraphType=="L-Section") ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else ButtonDefaults.outlinedButtonColors()) { Text("L-Sec") }
                                                            }
                                                        }
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text("Series:", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(end=4.dp))
                                                            FilterChip(selected = showPre, onClick = { showPre = !showPre }, label = { Text("Pre") }, leadingIcon = { Icon(Icons.Default.Check, null, tint = preColor) })
                                                            Spacer(Modifier.width(4.dp))
                                                            FilterChip(selected = showPost, onClick = { showPost = !showPost }, label = { Text("Post") }, leadingIcon = { Icon(Icons.Default.Check, null, tint = postColor) })
                                                        }
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text("Ref:", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(end=4.dp))
                                                            FilterChip(selected = useThalweg, onClick = { useThalweg = true }, label = { Text("Thalweg") })
                                                            Spacer(Modifier.width(4.dp))
                                                            FilterChip(selected = !useThalweg, onClick = { useThalweg = false }, label = { Text("Center") })
                                                        }
                                                        Box(Modifier.width(1.dp).height(30.dp).background(Color.Gray))
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            IconToggleButton(checked = showRuler, onCheckedChange = { showRuler = it }) {
                                                                Icon(Icons.Default.Straighten, contentDescription = "Ruler", tint = if(showRuler) MaterialTheme.colorScheme.primary else Color.Gray)
                                                            }
                                                            IconToggleButton(checked = showGrid, onCheckedChange = { showGrid = it }) {
                                                                Icon(Icons.Default.GridOn, contentDescription = "Grid", tint = if(showGrid) MaterialTheme.colorScheme.primary else Color.Gray)
                                                            }
                                                        }
                                                        // --- ZOOM CONTROLS ---
                                                        Box(Modifier.width(1.dp).height(30.dp).background(Color.Gray))
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            IconButton(onClick = { graphZoom = (graphZoom - 0.1f).coerceAtLeast(0.2f) }, modifier = Modifier.size(32.dp)) {
                                                                Icon(Icons.Default.ZoomOut, "Zoom Out")
                                                            }
                                                            Text("${(graphZoom * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                                                            IconButton(onClick = { graphZoom = (graphZoom + 0.1f).coerceAtMost(5.0f) }, modifier = Modifier.size(32.dp)) {
                                                                Icon(Icons.Default.ZoomIn, "Zoom In")
                                                            }
                                                        }
                                                    }
                                                }
                                                ControlGroup.STYLE -> {
                                                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                                        StyleSelector(
                                                            label = "Pre:",
                                                            isDotted = preDotted, onDottedChange = { preDotted = it },
                                                            color = preColor, onColorChange = { preColor = it },
                                                            thickness = preWidth, onThicknessChange = { preWidth = it },
                                                            showPoints = preShowPoints, onShowPointsChange = { preShowPoints = it }
                                                        )
                                                        Box(Modifier.width(1.dp).height(30.dp).background(Color.Gray))
                                                        StyleSelector(
                                                            label = "Post:",
                                                            isDotted = postDotted, onDottedChange = { postDotted = it },
                                                            color = postColor, onColorChange = { postColor = it },
                                                            thickness = postWidth, onThicknessChange = { postWidth = it },
                                                            showPoints = postShowPoints, onShowPointsChange = { postShowPoints = it }
                                                        )
                                                    }
                                                }
                                                ControlGroup.SCALE -> {
                                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                                        Text("Scale Settings:", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterVertically))
                                                        if (selectedGraphType == "L-Section") {
                                                            ScaleInput("L-H Scale:", lHScale) { lHScale = it }
                                                            ScaleInput("L-V Scale:", lVScale) { lVScale = it }
                                                        } else {
                                                            ScaleInput("X-H Scale:", xHScale) { xHScale = it }
                                                            ScaleInput("X-V Scale:", xVScale) { xVScale = it }
                                                        }
                                                    }
                                                }
                                                ControlGroup.RANGE -> {
                                                    if (selectedGraphType == "L-Section") {
                                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                            Text("L-Section Range:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            Text("Chainage:", fontWeight = FontWeight.Bold)
                                                            IconButton(onClick = {
                                                                val idx = uniqueChainages.indexOf(selectedChainage)
                                                                if(idx > 0) selectedChainage = uniqueChainages[idx - 1]
                                                            }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Remove, null) }

                                                            Slider(value = selectedChainage.toFloat(), onValueChange = { v -> selectedChainage = uniqueChainages.minByOrNull { abs(it - v) } ?: v.toDouble() }, valueRange = uniqueChainages.first().toFloat()..uniqueChainages.last().toFloat(), modifier = Modifier.width(200.dp))

                                                            IconButton(onClick = {
                                                                val idx = uniqueChainages.indexOf(selectedChainage)
                                                                if(idx < uniqueChainages.size - 1) selectedChainage = uniqueChainages[idx + 1]
                                                            }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Add, null) }

                                                            Text("${String.format("%.0f", selectedChainage)}m", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // 3. COLLAPSE HANDLE
                                        IconButton(onClick = { isRibbonOpen = false }, modifier = Modifier.height(24.dp)) {
                                            Icon(Icons.Default.KeyboardArrowUp, null)
                                        }
                                    } else {
                                        // 4. EXPAND HANDLE
                                        IconButton(onClick = { isRibbonOpen = true }, modifier = Modifier.height(24.dp)) {
                                            Icon(Icons.Default.KeyboardArrowDown, null)
                                        }
                                    }
                                }
                            }
                        }

                        Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                            if (riverData.isNotEmpty()) {
                                // 3. Graph Area
                                val dataToPlot = remember(selectedGraphType, selectedChainage, riverData, startChainage, endChainage) {
                                    getCurrentViewData(riverData, selectedGraphType, selectedChainage, startChainage, endChainage)
                                }

                                Box(modifier = Modifier.weight(1f).fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline).background(Color.White)) {
                                    // Pass correct scales
                                    val h = if(selectedGraphType == "L-Section") lHScale else xHScale
                                    val v = if(selectedGraphType == "L-Section") lVScale else xVScale

                                    EngineeringCanvas(
                                        dataToPlot, selectedGraphType == "L-Section", showPre, showPost, h, v,
                                        preColor, postColor, preDotted, postDotted,
                                        preWidth, postWidth, preShowPoints, postShowPoints,
                                        showRuler, showGrid,
                                        zoomFactor = graphZoom
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // 4. Data Table (Updates trigger rawRiverData change, which triggers re-centering)
                                CompactDataTable(
                                    data = dataToPlot,
                                    type = selectedGraphType,
                                    preColor = preColor,
                                    postColor = postColor,
                                    isManualMode = isManualMode,
                                    hasManualZero = manualZeroOverrides.containsKey(selectedChainage),
                                    onManualModeToggle = { isManualMode = it },
                                    onUpdateValue = { id, newPre, newPost ->
                                        rawRiverData = rawRiverData.map {
                                            if (it.id == id) it.copy(pre = newPre, post = newPost) else it
                                        }
                                    },
                                    onSetZero = { id ->
                                        manualZeroOverrides = manualZeroOverrides + (selectedChainage to id)
                                    },
                                    onResetZero = {
                                        manualZeroOverrides = manualZeroOverrides.filterKeys { it != selectedChainage }
                                    }
                                )
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Load a CSV file to begin", color = Color.Gray) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SegmentedButtonRow(content: @Composable RowScope.() -> Unit) {
    Row(modifier = Modifier.height(32.dp), content = content)
}