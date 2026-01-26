import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.awt.Dimension
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.net.URL
import kotlin.math.abs

// --- CONFIGURATION ---
const val CURRENT_APP_VERSION = "1.0.2"
const val UPDATE_JSON_URL = "https://lx-plotter-app-mxd1.vercel.app/version.json"

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "LX Plotter (Engineering Edition)") {
        window.minimumSize = Dimension(1024, 720)
        MaterialTheme(colorScheme = lightColorScheme()) { DesktopApp() }
    }
}

fun Color.toAwtColor(): java.awt.Color = java.awt.Color(this.red, this.green, this.blue, this.alpha)

enum class ControlGroup { VIEW, STYLE, SCALE, RANGE }
enum class Screen { MAIN, REPORT_DOWNLOAD }

@Composable
fun DesktopApp() {
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf(Screen.MAIN) }

    // Data States
    var riverData by remember { mutableStateOf<List<RiverPoint>>(emptyList()) }
    var rawRiverData by remember { mutableStateOf<List<RawRiverPoint>>(emptyList()) }
    var statusMessage by remember { mutableStateOf("No file loaded") }
    var history by remember { mutableStateOf(loadHistory()) }

    // Update States
    var updateAvailable by remember { mutableStateOf(false) }
    var updateUrl by remember { mutableStateOf("") }
    var updateNotes by remember { mutableStateOf("") }

    // Layout States
    var showHistory by remember { mutableStateOf(false) } // Initially Hidden
    var showTable by remember { mutableStateOf(true) }    // Initially Visible
    var isRibbonOpen by remember { mutableStateOf(true) } // Ribbon initially open

    // Check for updates
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val jsonStr = URL("$UPDATE_JSON_URL?t=${System.currentTimeMillis()}").readText()
                val json = JSONObject(jsonStr)
                val serverVersion = json.getString("version")
                if (serverVersion != CURRENT_APP_VERSION) {
                    updateUrl = json.getString("msiUrl")
                    updateNotes = json.optString("notes", "New features available.")
                    updateAvailable = true
                }
            } catch (e: Exception) { println("Update check failed: ${e.message}") }
        }
    }

    // UI States
    var showInstructions by remember { mutableStateOf(false) }
    var showCsvMapping by remember { mutableStateOf(false) }
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var csvHeaders by remember { mutableStateOf<List<String>>(emptyList()) }
    var csvPreviewRows by remember { mutableStateOf<List<List<String>>>(emptyList()) }

    // Store current mapping to enable re-saving preferences when style changes
    var currentColMapping by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    var useThalweg by remember { mutableStateOf(true) }
    var selectedGraphType by remember { mutableStateOf("X-Section") }
    var selectedChainage by remember { mutableStateOf(0.0) }

    // Series & Style - MODIFIED DEFAULTS (1px, No Points)
    var showPre by remember { mutableStateOf(true) }
    var showPost by remember { mutableStateOf(true) }
    var preColor by remember { mutableStateOf(Color.Blue) }
    var postColor by remember { mutableStateOf(Color.Red) }
    var preDotted by remember { mutableStateOf(true) }
    var postDotted by remember { mutableStateOf(false) }
    var preWidth by remember { mutableStateOf(1f) } // Default 1px
    var postWidth by remember { mutableStateOf(1f) } // Default 1px
    var preShowPoints by remember { mutableStateOf(false) } // Default No Points
    var postShowPoints by remember { mutableStateOf(false) } // Default No Points

    // Scales & Limits
    var lHScale by remember { mutableStateOf(2000.0) }
    var lVScale by remember { mutableStateOf(100.0) }
    var xHScale by remember { mutableStateOf(1000.0) }
    var xVScale by remember { mutableStateOf(100.0) }
    var minChainage by remember { mutableStateOf(0.0) }
    var maxChainage by remember { mutableStateOf(0.0) }
    var startChainage by remember { mutableStateOf(0.0) }
    var endChainage by remember { mutableStateOf(0.0) }

    // Ribbon & View
    var activeControlGroup by remember { mutableStateOf(ControlGroup.VIEW) }
    var showRuler by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(false) }
    var graphZoom by remember { mutableStateOf(0.6f) }

    // Manual Mode
    var isManualMode by remember { mutableStateOf(false) }
    var manualZeroOverrides by remember { mutableStateOf(mapOf<Double, String>()) }

    val dataErrors = remember(riverData) {
        riverData.filter { it.preMonsoon > it.postMonsoon }
            .map { DataError(it.chainage, it.distance, it.preMonsoon, it.postMonsoon) }
    }

    // Logic: Process Data
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

    fun finishLoading(colMapping: Map<String, Int>) {
        val file = pendingFile ?: return
        val (data, error) = parseCsvMapped(file, colMapping)
        if (error != null) { statusMessage = error } else {
            rawRiverData = data
            manualZeroOverrides = emptyMap(); isManualMode = false; selectedChainage = 0.0
            statusMessage = "Loaded ${file.name}"
            val newHistory = (listOf(file.absolutePath) + history).distinct().take(15)
            history = newHistory; saveHistory(newHistory)

            // Store mapping state
            currentColMapping = colMapping

            // Save initial preferences for this file
            saveFilePrefs(
                file.absolutePath, colMapping,
                showPre, showPost, preDotted, postDotted, preWidth, postWidth, preShowPoints, postShowPoints,
                lHScale, lVScale, xHScale, xVScale, preColor.toArgb(), postColor.toArgb()
            )
        }
        showCsvMapping = false; pendingFile = null
    }

    fun prepareFileLoad(file: File) {
        if (!file.exists()) { statusMessage = "File not found"; return }

        // CHECK IF PREFS EXIST
        val savedPrefs = loadFilePrefs(file.absolutePath)

        pendingFile = file // Set pending file first

        if (savedPrefs != null) {
            // Restore Mapping
            val mapObj = savedPrefs.getJSONObject("mapping")
            val mapping = mapOf(
                "chainage" to mapObj.getInt("chainage"),
                "distance" to mapObj.getInt("distance"),
                "pre" to mapObj.getInt("pre"),
                "post" to mapObj.getInt("post")
            )

            // Restore View Settings
            if (savedPrefs.has("view")) {
                val v = savedPrefs.getJSONObject("view")
                showPre = v.getBoolean("showPre")
                showPost = v.getBoolean("showPost")
                preDotted = v.getBoolean("preDotted")
                postDotted = v.getBoolean("postDotted")
                preWidth = v.getDouble("preWidth").toFloat()
                postWidth = v.getDouble("postWidth").toFloat()
                preShowPoints = v.getBoolean("preShowPoints")
                postShowPoints = v.getBoolean("postShowPoints")
                lHScale = v.getDouble("lHScale")
                lVScale = v.getDouble("lVScale")
                xHScale = v.getDouble("xHScale")
                xVScale = v.getDouble("xVScale")
                preColor = Color(v.getInt("preColor"))
                postColor = Color(v.getInt("postColor"))
            }

            // Skip dialog and load directly
            finishLoading(mapping)
        } else {
            // Standard Load (Defaults applied by initialization)
            // Reset to defaults if opening a new file without history
            preWidth = 1f; postWidth = 1f
            preShowPoints = false; postShowPoints = false

            val (headers, rows) = readCsvPreview(file)
            if (headers.isEmpty()) { statusMessage = "Empty or Invalid CSV"; return }
            csvHeaders = headers; csvPreviewRows = rows; showCsvMapping = true
        }
    }

    // Auto-save Preferences whenever style/view settings change, IF we have a valid mapping loaded
    LaunchedEffect(
        showPre, showPost, preDotted, postDotted, preWidth, postWidth,
        preShowPoints, postShowPoints, lHScale, lVScale, xHScale, xVScale, preColor, postColor
    ) {
        // Only save if we have an active file and valid mapping (implies file is loaded)
        if (history.isNotEmpty() && currentColMapping.isNotEmpty()) {
            // The currently loaded file is technically history[0] if we just loaded it
            val currentPath = history.firstOrNull()
            if (currentPath != null) {
                saveFilePrefs(
                    currentPath, currentColMapping,
                    showPre, showPost, preDotted, postDotted, preWidth, postWidth, preShowPoints, postShowPoints,
                    lHScale, lVScale, xHScale, xVScale, preColor.toArgb(), postColor.toArgb()
                )
            }
        }
    }

    if (showInstructions) InstructionDialog(onDismiss = { showInstructions = false })
    if (showCsvMapping) CsvMappingDialog(headers = csvHeaders, previewRows = csvPreviewRows, onDismiss = { showCsvMapping = false }, onConfirm = { finishLoading(it) })

    // --- MAIN UI ---
    Column(Modifier.fillMaxSize()) {

        if (updateAvailable) {
            Surface(color = Color(0xFFFFF3E0), modifier = Modifier.fillMaxWidth().height(40.dp).clickable {
                if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(updateUrl))
            }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.SystemUpdateAlt, null, tint = Color(0xFFE65100))
                    Spacer(Modifier.width(8.dp))
                    Text("New Update Available! Click here to download ($updateNotes)", color = Color(0xFFE65100), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        Box(Modifier.weight(1f)) {
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
                    Column(modifier = Modifier.fillMaxSize()) {

                        // 1. UNIFIED HEADER (MS Word Style)
                        UnifiedAppHeader(
                            status = statusMessage,
                            errors = dataErrors,
                            activeGroup = activeControlGroup,
                            isRibbonOpen = isRibbonOpen,
                            onToggleRibbon = { isRibbonOpen = !isRibbonOpen },
                            onGroupSelected = {
                                activeControlGroup = it
                                // If ribbon was closed, open it. If click same tab, maintain state.
                                if(!isRibbonOpen) isRibbonOpen = true
                            },
                            onToggleHistory = { showHistory = !showHistory },
                            isHistoryVisible = showHistory,
                            onNavigateToError = { selectedGraphType = "X-Section"; selectedChainage = it },
                            onLoad = { pickFile()?.let { prepareFileLoad(it) } },
                            onDownloadCsv = {
                                if(riverData.isNotEmpty()) pickSaveFile("modified_data.csv")?.let { file ->
                                    scope.launch(Dispatchers.IO) { saveCsv(riverData, file); statusMessage = "CSV Saved: ${file.name}" }
                                }
                            },
                            onGenerateReport = { if (riverData.isNotEmpty()) currentScreen = Screen.REPORT_DOWNLOAD else statusMessage = "Load data first" },
                            onShowInstructions = { showInstructions = true }
                        )

                        // 2. RIBBON CONTENT (Collapsible)
                        AnimatedVisibility(
                            visible = isRibbonOpen && riverData.isNotEmpty(),
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shadowElevation = 4.dp,
                                shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
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
                                                    IconToggleButton(checked = showRuler, onCheckedChange = { showRuler = it }) { Icon(Icons.Default.Straighten, null, tint = if(showRuler) MaterialTheme.colorScheme.primary else Color.Gray) }
                                                    IconToggleButton(checked = showGrid, onCheckedChange = { showGrid = it }) { Icon(Icons.Default.GridOn, null, tint = if(showGrid) MaterialTheme.colorScheme.primary else Color.Gray) }
                                                }
                                                Box(Modifier.width(1.dp).height(30.dp).background(Color.Gray))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    IconButton(onClick = { graphZoom = (graphZoom - 0.1f).coerceAtLeast(0.2f) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.ZoomOut, null) }
                                                    Text("${(graphZoom * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                                                    IconButton(onClick = { graphZoom = (graphZoom + 0.1f).coerceAtMost(5.0f) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.ZoomIn, null) }
                                                }
                                            }
                                        }
                                        ControlGroup.STYLE -> {
                                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                                StyleSelector("Pre:", preDotted, { preDotted=it }, preColor, { preColor=it }, preWidth, { preWidth=it }, preShowPoints, { preShowPoints=it })
                                                Box(Modifier.width(1.dp).height(30.dp).background(Color.Gray))
                                                StyleSelector("Post:", postDotted, { postDotted=it }, postColor, { postColor=it }, postWidth, { postWidth=it }, postShowPoints, { postShowPoints=it })
                                            }
                                        }
                                        ControlGroup.SCALE -> {
                                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                                Text("Scale:", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterVertically))
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
                                                    if(startChainage < minChainage || endChainage > maxChainage || startChainage > endChainage) Text("(Invalid!)", fontSize = 11.sp, color = Color.Red)
                                                    else Text("(Min:$minChainage - Max:$maxChainage)", fontSize = 11.sp, color = Color.Gray)
                                                }
                                            } else {
                                                val uniqueChainages = remember(riverData) { riverData.map { it.chainage }.distinct().sorted() }
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text("Chainage:", fontWeight = FontWeight.Bold)
                                                    IconButton(onClick = { val idx = uniqueChainages.indexOf(selectedChainage); if(idx > 0) selectedChainage = uniqueChainages[idx - 1] }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Remove, null) }
                                                    Slider(value = selectedChainage.toFloat(), onValueChange = { v -> selectedChainage = uniqueChainages.minByOrNull { abs(it - v) } ?: v.toDouble() }, valueRange = uniqueChainages.first().toFloat()..uniqueChainages.last().toFloat(), modifier = Modifier.width(200.dp))
                                                    IconButton(onClick = { val idx = uniqueChainages.indexOf(selectedChainage); if(idx < uniqueChainages.size - 1) selectedChainage = uniqueChainages[idx + 1] }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Add, null) }
                                                    Text("${String.format("%.0f", selectedChainage)}m", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 3. MAIN WORKSPACE (Split Left Panel / Graph / Table)
                        Row(modifier = Modifier.fillMaxSize()) {

                            // History Panel (Collapsible)
                            AnimatedVisibility(
                                visible = showHistory,
                                enter = expandHorizontally(),
                                exit = shrinkHorizontally()
                            ) {
                                LeftPanel(
                                    history = history,
                                    onHistoryItemClick = { prepareFileLoad(File(it)) },
                                    onDeleteHistoryItem = { path ->
                                        history = history.filter { it != path }; saveHistory(history)
                                        if (pendingFile?.absolutePath == path) pendingFile = null
                                    }
                                )
                            }

                            // Center Content
                            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                if (riverData.isNotEmpty()) {
                                    val dataToPlot = remember(selectedGraphType, selectedChainage, riverData, startChainage, endChainage) { getCurrentViewData(riverData, selectedGraphType, selectedChainage, startChainage, endChainage) }

                                    // Graph Box
                                    Box(modifier = Modifier.weight(1f).fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline).background(Color.White)) {
                                        val h = if(selectedGraphType == "L-Section") lHScale else xHScale
                                        val v = if(selectedGraphType == "L-Section") lVScale else xVScale
                                        EngineeringCanvas(dataToPlot, selectedGraphType == "L-Section", showPre, showPost, h, v, preColor, postColor, preDotted, postDotted, preWidth, postWidth, preShowPoints, postShowPoints, showRuler, showGrid, zoomFactor = graphZoom)

                                        // Floating Toggle for Table visibility
                                        FloatingActionButton(
                                            onClick = { showTable = !showTable },
                                            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).size(36.dp),
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            elevation = FloatingActionButtonDefaults.elevation(2.dp)
                                        ) {
                                            Icon(
                                                if(showTable) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                                contentDescription = "Toggle Table",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }

                                    // Collapsible Data Table
                                    AnimatedVisibility(
                                        visible = showTable,
                                        enter = expandVertically(),
                                        exit = shrinkVertically()
                                    ) {
                                        Column {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            CompactDataTable(data = dataToPlot, type = selectedGraphType, preColor = preColor, postColor = postColor, isManualMode = isManualMode, hasManualZero = manualZeroOverrides.containsKey(selectedChainage), onManualModeToggle = { isManualMode = it }, onUpdateValue = { id, newPre, newPost -> rawRiverData = rawRiverData.map { if (it.id == id) it.copy(pre = newPre, post = newPost) else it } }, onSetZero = { id -> manualZeroOverrides = manualZeroOverrides + (selectedChainage to id) }, onResetZero = { manualZeroOverrides = manualZeroOverrides.filterKeys { it != selectedChainage } })
                                        }
                                    }
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
}

@Composable
fun SegmentedButtonRow(content: @Composable RowScope.() -> Unit) {
    Row(modifier = Modifier.height(32.dp), content = content)
}