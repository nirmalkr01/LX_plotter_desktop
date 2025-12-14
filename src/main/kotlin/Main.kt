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
    var statusMessage by remember { mutableStateOf("No file loaded") }
    var history by remember { mutableStateOf(loadHistory()) }

    var selectedGraphType by remember { mutableStateOf("L-Section") }
    var selectedChainage by remember { mutableStateOf(0.0) }
    var showPre by remember { mutableStateOf(true) }
    var showPost by remember { mutableStateOf(true) }

    // Styles
    var preColor by remember { mutableStateOf(Color.Blue) }
    var postColor by remember { mutableStateOf(Color.Red) }
    var preDotted by remember { mutableStateOf(true) }
    var postDotted by remember { mutableStateOf(false) }

    var hScale by remember { mutableStateOf(100.0) }
    var vScale by remember { mutableStateOf(100.0) }

    // Limits & Range
    var minChainage by remember { mutableStateOf(0.0) }
    var maxChainage by remember { mutableStateOf(0.0) }
    var startChainage by remember { mutableStateOf(0.0) }
    var endChainage by remember { mutableStateOf(0.0) }

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
            riverData = processAndCenterData(data)
            if (riverData.isNotEmpty()) {
                selectedChainage = riverData.first().chainage
                minChainage = riverData.minOf { it.chainage }
                maxChainage = riverData.maxOf { it.chainage }
                startChainage = minChainage
                endChainage = maxChainage
            }
            statusMessage = "Loaded ${file.name}"
            val newHistory = (listOf(file.absolutePath) + history).distinct().take(15)
            history = newHistory
            saveHistory(newHistory)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Row(modifier = Modifier.fillMaxSize()) {
            LeftPanel(history, { loadFile(File(it)) }, { path -> history = history.filter { it != path }; saveHistory(history) })

            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                AppHeader(
                    status = statusMessage,
                    onLoad = { pickFile()?.let { loadFile(it) } },
                    onSaveCurrent = { if(riverData.isNotEmpty()) pickFolder()?.let { folder -> scope.launch { val data = getCurrentViewData(riverData, selectedGraphType, selectedChainage, startChainage, endChainage); saveGraphWithTable(data, File(folder, "Graph_View.png"), selectedGraphType, selectedChainage, showPre, showPost, hScale, vScale, preColor.toAwtColor(), postColor.toAwtColor(), preDotted, postDotted); statusMessage = "Saved" } } },
                    onBatchSave = { if(riverData.isNotEmpty()) pickFolder()?.let { folder -> scope.launch { withContext(Dispatchers.IO) { performBatchSave(riverData, folder, selectedGraphType, startChainage, endChainage, showPre, showPost, hScale, vScale, preColor.toAwtColor(), postColor.toAwtColor(), preDotted, postDotted) { statusMessage = it } } } } },
                    onGenerateReport = { /* Void for now */ }
                )

                Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                    if (riverData.isNotEmpty()) {
                        // 1. Controls
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = selectedGraphType == "L-Section", onClick = { selectedGraphType = "L-Section" }, label = { Text("L-Section") })
                                FilterChip(selected = selectedGraphType == "X-Section", onClick = { selectedGraphType = "X-Section" }, label = { Text("X-Section") })
                                Box(Modifier.width(1.dp).height(32.dp).background(Color.LightGray))
                                FilterChip(selected = showPre, onClick = { showPre = !showPre }, label = { Text("Pre") }, leadingIcon = { Icon(Icons.Default.Check, null, tint = preColor) })
                                FilterChip(selected = showPost, onClick = { showPost = !showPost }, label = { Text("Post") }, leadingIcon = { Icon(Icons.Default.Check, null, tint = postColor) })
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ScaleInput("H-Scale 1:", hScale) { hScale = it }
                                ScaleInput("V-Scale 1:", vScale) { vScale = it }
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
                                    Text("Range (Min:${minChainage.toInt()} Max:${maxChainage.toInt()}):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    // Validation Logic
                                    ScaleInput("Start", startChainage) { if(it >= minChainage && it <= maxChainage) startChainage = it }
                                    ScaleInput("End", endChainage) { if(it >= minChainage && it <= maxChainage) endChainage = it }
                                }
                            } else {
                                val uniqueChainages = remember(riverData) { riverData.map { it.chainage }.distinct().sorted() }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("CH: ${String.format("%.0f", selectedChainage)}", fontWeight = FontWeight.Bold)
                                    // Stepper -
                                    IconButton(onClick = {
                                        val idx = uniqueChainages.indexOf(selectedChainage)
                                        if(idx > 0) selectedChainage = uniqueChainages[idx - 1]
                                    }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Remove, null) }

                                    Slider(
                                        value = selectedChainage.toFloat(),
                                        onValueChange = { v -> selectedChainage = uniqueChainages.minByOrNull { abs(it - v) } ?: v.toDouble() },
                                        valueRange = uniqueChainages.first().toFloat()..uniqueChainages.last().toFloat(),
                                        modifier = Modifier.width(200.dp)
                                    )

                                    // Stepper +
                                    IconButton(onClick = {
                                        val idx = uniqueChainages.indexOf(selectedChainage)
                                        if(idx < uniqueChainages.size - 1) selectedChainage = uniqueChainages[idx + 1]
                                    }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Add, null) }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // 3. Graph
                        val dataToPlot = remember(selectedGraphType, selectedChainage, riverData, startChainage, endChainage) {
                            getCurrentViewData(riverData, selectedGraphType, selectedChainage, startChainage, endChainage)
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline).background(Color.White).padding(16.dp)) {
                            EngineeringCanvas(dataToPlot, selectedGraphType == "L-Section", showPre, showPost, hScale, vScale, preColor, postColor, preDotted, postDotted)
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