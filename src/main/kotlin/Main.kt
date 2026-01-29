import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.ui.text.style.TextAlign
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
const val CURRENT_APP_VERSION = "1.0.3"
const val UPDATE_JSON_URL = "https://lx-plotter-app-mxd1.vercel.app/version.json"

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "LX Plotter (Engineering Edition)") {
        window.minimumSize = Dimension(1024, 720)
        MaterialTheme(colorScheme = lightColorScheme()) { DesktopApp() }
    }
}

fun Color.toAwtColor(): java.awt.Color = java.awt.Color(this.red, this.green, this.blue, this.alpha)

// REDEFINED CONTROL GROUPS
enum class ControlGroup { PROFILE, VIEW, ADJUST }
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
    var activeControlGroup by remember { mutableStateOf(ControlGroup.PROFILE) }
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

    // Logic: Process Data and Initialize Start Chainage
    LaunchedEffect(useThalweg, rawRiverData, manualZeroOverrides) {
        if (rawRiverData.isNotEmpty()) {
            riverData = processAndCenterData(rawRiverData, useThalweg, manualZeroOverrides)
            if (riverData.isNotEmpty()) {
                minChainage = riverData.minOf { it.chainage }
                maxChainage = riverData.maxOf { it.chainage }

                // Initialize selection and Start Chainage if not set
                if(selectedChainage == 0.0) {
                    selectedChainage = minChainage
                    startChainage = minChainage // FIX: Initialize Start Ch to first chainage
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
                            onShowInstructions = { showInstructions = true },
                            zoomLevel = graphZoom,
                            onZoomChange = { graphZoom = it }
                        )

                        // 2. RIBBON CONTENT (Collapsible)
                        AnimatedVisibility(
                            visible = isRibbonOpen && riverData.isNotEmpty(),
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFFF8F9FA),
                                shadowElevation = 2.dp
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(90.dp).padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    when (activeControlGroup) {
                                        // GROUP 1: PROFILE (Graph Types, Reference, Series)
                                        ControlGroup.PROFILE -> {
                                            MainPanelRibbonGroup("Graph Type") {
                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        RadioButton(selected = selectedGraphType == "X-Section", onClick = { selectedGraphType = "X-Section" }, modifier = Modifier.size(16.dp), colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF2B579A)))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("X-Sec", fontSize = 11.sp, modifier = Modifier.clickable { selectedGraphType = "X-Section" })
                                                    }
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        RadioButton(selected = selectedGraphType == "L-Section", onClick = { selectedGraphType = "L-Section" }, modifier = Modifier.size(16.dp), colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF2B579A)))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("L-Sec", fontSize = 11.sp, modifier = Modifier.clickable { selectedGraphType = "L-Section" })
                                                    }
                                                }
                                            }

                                            VerticalDivider(Modifier.padding(vertical = 8.dp))

                                            MainPanelRibbonGroup("Reference") {
                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        RadioButton(selected = useThalweg, onClick = { useThalweg = true }, modifier = Modifier.size(16.dp), colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF2B579A)))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("Thalweg", fontSize = 11.sp, modifier = Modifier.clickable { useThalweg = true })
                                                    }
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        RadioButton(selected = !useThalweg, onClick = { useThalweg = false }, modifier = Modifier.size(16.dp), colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF2B579A)))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("Center", fontSize = 11.sp, modifier = Modifier.clickable { useThalweg = false })
                                                    }
                                                }
                                            }

                                            VerticalDivider(Modifier.padding(vertical = 8.dp))

                                            MainPanelRibbonGroup("Visible Series") {
                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Checkbox(checked = showPre, onCheckedChange = { showPre = it }, modifier = Modifier.size(16.dp), colors = CheckboxDefaults.colors(checkedColor = Color(0xFF2B579A)))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("Pre", fontSize = 11.sp)
                                                    }
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Checkbox(checked = showPost, onCheckedChange = { showPost = it }, modifier = Modifier.size(16.dp), colors = CheckboxDefaults.colors(checkedColor = Color(0xFF2B579A)))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("Post", fontSize = 11.sp)
                                                    }
                                                }
                                            }
                                        }

                                        // GROUP 2: VIEW (Visual Style of Graph)
                                        ControlGroup.VIEW -> {
                                            MainPanelRibbonGroup("Pre-Monsoon Style") {
                                                StyleSelector("Pre", preDotted, { preDotted=it }, preColor, { preColor=it }, preWidth, { preWidth=it }, preShowPoints, { preShowPoints=it })
                                            }
                                            VerticalDivider(Modifier.padding(vertical = 8.dp))
                                            MainPanelRibbonGroup("Post-Monsoon Style") {
                                                StyleSelector("Post", postDotted, { postDotted=it }, postColor, { postColor=it }, postWidth, { postWidth=it }, postShowPoints, { postShowPoints=it })
                                            }
                                            VerticalDivider(Modifier.padding(vertical = 8.dp))
                                            MainPanelRibbonGroup("Helpers") {
                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Checkbox(checked = showGrid, onCheckedChange = { showGrid = it }, modifier = Modifier.size(16.dp), colors = CheckboxDefaults.colors(checkedColor = Color(0xFF2B579A)))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("Grid", fontSize = 11.sp)
                                                    }
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Checkbox(checked = showRuler, onCheckedChange = { showRuler = it }, modifier = Modifier.size(16.dp), colors = CheckboxDefaults.colors(checkedColor = Color(0xFF2B579A)))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("Ruler", fontSize = 11.sp)
                                                    }
                                                }
                                            }
                                        }

                                        // GROUP 3: ADJUST (Scales & Ranges)
                                        ControlGroup.ADJUST -> {
                                            MainPanelRibbonGroup("Scale (1:X)") {
                                                if (selectedGraphType == "L-Section") {
                                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        ScaleInput("Horizontal:", lHScale) { lHScale = it }
                                                        ScaleInput("Vertical:", lVScale) { lVScale = it }
                                                    }
                                                } else {
                                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        ScaleInput("Horizontal:", xHScale) { xHScale = it }
                                                        ScaleInput("Vertical:", xVScale) { xVScale = it }
                                                    }
                                                }
                                            }

                                            VerticalDivider(Modifier.padding(vertical = 8.dp))

                                            MainPanelRibbonGroup("Navigation") {
                                                if (selectedGraphType == "L-Section") {
                                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        // FIX: These inputs now use initialized start/end chainages (often min/max)
                                                        // The component ScaleInput is updated to display '0' if value is 0.0
                                                        ScaleInput("Start Ch:", startChainage) { startChainage = it }
                                                        ScaleInput("End Ch:", endChainage) { endChainage = it }
                                                    }
                                                } else {
                                                    val uniqueChainages = remember(riverData) { riverData.map { it.chainage }.distinct().sorted() }
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            IconButton(onClick = { val idx = uniqueChainages.indexOf(selectedChainage); if(idx > 0) selectedChainage = uniqueChainages[idx - 1] }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Remove, null, modifier = Modifier.size(14.dp)) }
                                                            Text("CH: ${String.format("%.0f", selectedChainage)}", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(60.dp), textAlign = TextAlign.Center)
                                                            IconButton(onClick = { val idx = uniqueChainages.indexOf(selectedChainage); if(idx < uniqueChainages.size - 1) selectedChainage = uniqueChainages[idx + 1] }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp)) }
                                                        }
                                                        Slider(
                                                            value = selectedChainage.toFloat(),
                                                            onValueChange = { v -> selectedChainage = uniqueChainages.minByOrNull { abs(it - v) } ?: v.toDouble() },
                                                            valueRange = if(uniqueChainages.isNotEmpty()) uniqueChainages.first().toFloat()..uniqueChainages.last().toFloat() else 0f..100f,
                                                            modifier = Modifier.width(140.dp),
                                                            colors = SliderDefaults.colors(thumbColor = Color(0xFF2B579A), activeTrackColor = Color(0xFF2B579A))
                                                        )
                                                    }
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
                                            containerColor = Color(0xFFE1EDFD), // Light Blue
                                            elevation = FloatingActionButtonDefaults.elevation(2.dp)
                                        ) {
                                            Icon(
                                                if(showTable) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                                contentDescription = "Toggle Table",
                                                tint = Color(0xFF2B579A) // Professional Blue
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

// --- LOCAL RIBBON COMPONENTS FOR MAIN PANEL (Professional Look) ---
@Composable
private fun MainPanelRibbonGroup(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxHeight().padding(horizontal = 8.dp)
    ) {
        Box(modifier = Modifier.weight(1f).wrapContentWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.Start) { content() }
        }
        Text(text = label, fontSize = 9.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 4.dp))
    }
}