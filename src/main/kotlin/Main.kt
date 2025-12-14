import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
        MaterialTheme { DesktopApp() }
    }
}

@Composable
fun DesktopApp() {
    val scope = rememberCoroutineScope()
    var isDarkMode by remember { mutableStateOf(false) }
    var riverData by remember { mutableStateOf<List<RiverPoint>>(emptyList()) }
    var statusMessage by remember { mutableStateOf("No file loaded") }
    var history by remember { mutableStateOf(loadHistory()) }

    var selectedGraphType by remember { mutableStateOf("L-Section") }
    var selectedChainage by remember { mutableStateOf(0.0) }
    var showPre by remember { mutableStateOf(true) }
    var showPost by remember { mutableStateOf(true) }

    var hScale by remember { mutableStateOf(100.0) }
    var vScale by remember { mutableStateOf(100.0) }
    var startChainage by remember { mutableStateOf(0.0) }
    var endChainage by remember { mutableStateOf(0.0) }

    fun loadFile(file: File) {
        if (!file.exists()) {
            statusMessage = "File not found: ${file.name}"
            return
        }
        val (data, error) = parseCsvStrict(file)
        if (error != null) {
            statusMessage = "Error: $error"
        } else {
            riverData = processAndCenterData(data)
            if (riverData.isNotEmpty()) {
                selectedChainage = riverData.first().chainage
                startChainage = riverData.minOf { it.chainage }
                endChainage = riverData.maxOf { it.chainage }
            }
            statusMessage = "Loaded ${file.name}"
            val newHistory = (listOf(file.absolutePath) + history).distinct().take(10)
            history = newHistory
            saveHistory(newHistory)
        }
    }

    val colorScheme = if (isDarkMode) darkColorScheme() else lightColorScheme()

    MaterialTheme(colorScheme = colorScheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Row(modifier = Modifier.fillMaxSize()) {
                LeftPanel(history, { loadFile(File(it)) }, { path -> history = history.filter { it != path }; saveHistory(history) })

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    AppHeader(statusMessage, { pickFile()?.let { loadFile(it) } },
                        { if(riverData.isNotEmpty()) pickFolder()?.let { folder ->
                            scope.launch {
                                val data = getCurrentViewData(riverData, selectedGraphType, selectedChainage, startChainage, endChainage)
                                saveGraphWithTable(data, File(folder, "Graph_Current.png"), selectedGraphType, selectedChainage, showPre, showPost, hScale, vScale)
                            } } },
                        { if(riverData.isNotEmpty()) pickFolder()?.let { folder ->
                            scope.launch { withContext(Dispatchers.IO) { performBatchSave(riverData, folder, selectedGraphType, startChainage, endChainage, showPre, showPost, hScale, vScale) { statusMessage = it } } } } },
                        {}, { isDarkMode = !isDarkMode }, isDarkMode)

                    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                        if (riverData.isNotEmpty()) {
                            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(selected = selectedGraphType == "L-Section", onClick = { selectedGraphType = "L-Section" }, label = { Text("L-Section") })
                                    FilterChip(selected = selectedGraphType == "X-Section", onClick = { selectedGraphType = "X-Section" }, label = { Text("X-Section") })
                                    Box(Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.outline))
                                    FilterChip(selected = showPre, onClick = { showPre = !showPre }, label = { Text("Pre-Mon") }, leadingIcon = { Icon(Icons.Default.Check, null, tint = Color.Blue) })
                                    FilterChip(selected = showPost, onClick = { showPost = !showPost }, label = { Text("Post-Mon") }, leadingIcon = { Icon(Icons.Default.Check, null, tint = Color.Red) })
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ScaleInput("H-Scale (1:)", hScale) { hScale = it }
                                    ScaleInput("V-Scale (1:)", vScale) { vScale = it }
                                }
                            }

                            if (selectedGraphType == "L-Section") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Range (m):")
                                    ScaleInput("Start", startChainage) { startChainage = it }
                                    ScaleInput("End", endChainage) { endChainage = it }
                                    Text("(Total: ${riverData.minOf { it.chainage }} - ${riverData.maxOf { it.chainage }})", fontSize = 10.sp, color = Color.Gray)
                                }
                            } else {
                                val uniqueChainages = remember(riverData) { riverData.map { it.chainage }.distinct().sorted() }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("CH: ${String.format("%.0f", selectedChainage)}", fontWeight = FontWeight.Bold)
                                    Slider(value = selectedChainage.toFloat(), onValueChange = { v -> selectedChainage = uniqueChainages.minByOrNull { abs(it - v) } ?: v.toDouble() }, valueRange = uniqueChainages.first().toFloat()..uniqueChainages.last().toFloat(), modifier = Modifier.width(300.dp))
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            val dataToPlot = remember(selectedGraphType, selectedChainage, riverData, startChainage, endChainage) {
                                getCurrentViewData(riverData, selectedGraphType, selectedChainage, startChainage, endChainage)
                            }

                            Box(modifier = Modifier.weight(1f).fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline).background(Color.White).padding(16.dp)) {
                                EngineeringCanvas(dataToPlot, selectedGraphType == "L-Section", showPre, showPost, hScale, vScale)
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Data Table", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Row(modifier = Modifier.height(100.dp).fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline).background(Color.White).horizontalScroll(rememberScrollState())) {
                                Column {
                                    TableCell("Param", isHeader = true)
                                    TableCell("Post", color = Color.Red)
                                    TableCell("Pre", color = Color.Blue)
                                }
                                dataToPlot.forEach { point ->
                                    Column {
                                        val label = if (selectedGraphType == "L-Section") point.chainage else point.distance
                                        TableCell(String.format("%.0f", label), isHeader = true)
                                        TableCell(String.format("%.2f", point.postMonsoon))
                                        TableCell(String.format("%.2f", point.preMonsoon))
                                    }
                                }
                            }
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Welcome! Please load a CSV file.", color = MaterialTheme.colorScheme.onBackground) }
                        }
                    }
                }
            }
        }
    }
}