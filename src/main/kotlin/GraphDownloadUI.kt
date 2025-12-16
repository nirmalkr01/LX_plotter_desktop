import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphDownloadScreen(
    riverData: List<RiverPoint>,
    initialGraphType: String,
    initialChainage: Double,
    startCh: Double,
    endCh: Double,
    lHScale: Double, lVScale: Double,
    xHScale: Double, xVScale: Double,
    showPre: Boolean, showPost: Boolean,
    preColor: java.awt.Color, postColor: java.awt.Color,
    preDotted: Boolean, postDotted: Boolean,
    preWidth: Float, postWidth: Float,
    preShowPoints: Boolean, postShowPoints: Boolean,
    showGrid: Boolean,
    onBack: () -> Unit
) {
    // --- STATE ---
    var selectedGraphType by remember { mutableStateOf(initialGraphType) }
    var selectedPaperSize by remember { mutableStateOf(PaperSize.A4) }
    var isLandscape by remember { mutableStateOf(true) }

    // Graph Selection
    val availableGraphs = remember(selectedGraphType, riverData) {
        if (selectedGraphType == "L-Section") listOf(-1.0)
        else riverData.map { it.chainage }.distinct().sorted()
    }
    val initialSelection = if (selectedGraphType == "L-Section") -1.0 else initialChainage
    var selectedGraphIds by remember { mutableStateOf(setOf(initialSelection)) }

    // Large Preview State: Stores <GraphID, Row, Col>
    var zoomedPage by remember { mutableStateOf<Triple<Double, Int, Int>?>(null) }

    val scope = rememberCoroutineScope()
    var statusMsg by remember { mutableStateOf("") }

    // --- ZOOM DIALOG ---
    if (zoomedPage != null) {
        Dialog(
            onDismissRequest = { zoomedPage = null },
            properties = DialogProperties(usePlatformDefaultWidth = false) // Full screen
        ) {
            val (id, r, c) = zoomedPage!!
            Surface(modifier = Modifier.fillMaxSize().padding(24.dp), shape = RoundedCornerShape(8.dp)) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Preview: ${if(id==-1.0) "L-Section" else "CH $id"} - Page ${r+1}-${c+1}", fontWeight = FontWeight.Bold)
                        IconButton(onClick = { zoomedPage = null }) { Icon(Icons.Default.Close, "Close") }
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
                        // Re-calculate data for this specific graph
                        val dataForGraph = remember(id, riverData) {
                            if (id == -1.0) getCurrentViewData(riverData, "L-Section", 0.0, startCh, endCh)
                            else getCurrentViewData(riverData, "X-Section", id, 0.0, 0.0)
                        }
                        val hScale = if (selectedGraphType == "L-Section") lHScale else xHScale
                        val vScale = if (selectedGraphType == "L-Section") lVScale else xVScale

                        // Reuse the preview component but passing a modifier to fill size
                        GraphPageCanvas(
                            modifier = Modifier.fillMaxSize(),
                            data = dataForGraph,
                            row = r, col = c,
                            type = selectedGraphType,
                            paperSize = selectedPaperSize,
                            isLandscape = isLandscape,
                            hScale = hScale, vScale = vScale,
                            showPre = showPre, showPost = showPost,
                            preColor = preColor, postColor = postColor,
                            preWidth = preWidth, postWidth = postWidth
                        )
                    }
                }
            }
        }
    }

    // --- MAIN LAYOUT ---
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        // 1. TOP PANEL (Options)
        Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
            Column {
                TopAppBar(
                    title = { Text("Download Graph", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Type Selector
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Type:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = selectedGraphType == "X-Section",
                            onClick = { selectedGraphType = "X-Section"; selectedGraphIds = emptySet() },
                            label = { Text("X-Section") }
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = selectedGraphType == "L-Section",
                            onClick = { selectedGraphType = "L-Section"; selectedGraphIds = setOf(-1.0) },
                            label = { Text("L-Section") }
                        )
                    }

                    // Size Selector
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Size:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(Modifier.width(8.dp))
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp), modifier = Modifier.height(32.dp)) {
                                Text(selectedPaperSize.name)
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                PaperSize.entries.forEach { size ->
                                    DropdownMenuItem(text = { Text(size.name) }, onClick = { selectedPaperSize = size; expanded = false })
                                }
                            }
                        }
                    }

                    // Orientation Selector
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Orientation:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(Modifier.width(8.dp))
                        FilterChip(selected = isLandscape, onClick = { isLandscape = true }, label = { Text("Landscape") })
                        Spacer(Modifier.width(8.dp))
                        FilterChip(selected = !isLandscape, onClick = { isLandscape = false }, label = { Text("Portrait") })
                    }

                    Spacer(Modifier.weight(1f))
                    if(statusMsg.isNotEmpty()) {
                        Text(statusMsg, fontSize = 12.sp, color = if(statusMsg.startsWith("Error")) Color.Red else Color(0xFF006400))
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxSize()) {
            // 2. LEFT PANEL (Graph List)
            Card(
                modifier = Modifier.width(280.dp).fillMaxHeight().padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Select Graphs", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            if (selectedGraphIds.size == availableGraphs.size) selectedGraphIds = emptySet()
                            else selectedGraphIds = availableGraphs.toSet()
                        }) {
                            Text(if (selectedGraphIds.size == availableGraphs.size) "Clear" else "All")
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(availableGraphs) { id ->
                            val label = if(id == -1.0) "Full L-Section Profile" else "Chainage ${id.toInt()} m"
                            val isSelected = selectedGraphIds.contains(id)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .clickable {
                                        selectedGraphIds = if (isSelected) selectedGraphIds - id else selectedGraphIds + id
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(label, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // 3. CENTER PANEL (Page Preview)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFFE0E0E0))
                    .padding(16.dp)
            ) {
                val scrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    if (selectedGraphIds.isEmpty()) {
                        Box(Modifier.height(200.dp), contentAlignment = Alignment.Center) {
                            Text("Select graphs from the left panel to preview", color = Color.Gray)
                        }
                    }

                    selectedGraphIds.sorted().forEach { id ->
                        val dataForGraph = remember(id, riverData) {
                            if (id == -1.0) getCurrentViewData(riverData, "L-Section", 0.0, startCh, endCh)
                            else getCurrentViewData(riverData, "X-Section", id, 0.0, 0.0)
                        }

                        val hScale = if (selectedGraphType == "L-Section") lHScale else xHScale
                        val vScale = if (selectedGraphType == "L-Section") lVScale else xVScale

                        // Renders the list of pages for this graph
                        GraphLayoutPreview(
                            data = dataForGraph,
                            label = if(id == -1.0) "L-Section" else "CH $id",
                            type = selectedGraphType,
                            paperSize = selectedPaperSize,
                            isLandscape = isLandscape,
                            hScale = hScale, vScale = vScale,
                            showPre = showPre, showPost = showPost,
                            preColor = preColor, postColor = postColor,
                            preWidth = preWidth, postWidth = postWidth,
                            onZoom = { r, c -> zoomedPage = Triple(id, r, c) },
                            onDownloadPage = { r, c ->
                                pickFolder()?.let { folder ->
                                    scope.launch {
                                        statusMsg = "Saving..."
                                        withContext(Dispatchers.IO) {
                                            saveSplitPage(
                                                data = dataForGraph,
                                                file = File(folder, "${if(id==-1.0) "LSec" else "CH${id.toInt()}"}_Page_${r+1}_${c+1}.png"),
                                                row = r, col = c,
                                                type = selectedGraphType,
                                                paperSize = selectedPaperSize,
                                                isLandscape = isLandscape,
                                                hScale = hScale, vScale = vScale,
                                                chainage = if(id==-1.0) 0.0 else id,
                                                showPre = showPre, showPost = showPost,
                                                preColor = Color(preColor.red, preColor.green, preColor.blue), postColor = Color(postColor.red, postColor.green, postColor.blue),
                                                preDotted = preDotted, postDotted = postDotted,
                                                preWidth = preWidth, postWidth = postWidth,
                                                preShowPoints = preShowPoints, postShowPoints = postShowPoints,
                                                showGrid = showGrid
                                            )
                                        }
                                        statusMsg = "Saved Page ${r+1}-${c+1}"
                                    }
                                }
                            }
                        )
                    }
                }

                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(scrollState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                )
            }
        }
    }
}

@Composable
fun GraphLayoutPreview(
    data: List<RiverPoint>,
    label: String,
    type: String,
    paperSize: PaperSize,
    isLandscape: Boolean,
    hScale: Double, vScale: Double,
    showPre: Boolean, showPost: Boolean,
    preColor: java.awt.Color, postColor: java.awt.Color,
    preWidth: Float, postWidth: Float,
    onZoom: (Int, Int) -> Unit,
    onDownloadPage: (Int, Int) -> Unit
) {
    if(data.isEmpty()) return

    // Calculate dimensions to determine grid
    val IMG_PX_PER_CM = 38.0
    val mmToPx = IMG_PX_PER_CM / 10.0
    val pageWidthMm = if (isLandscape) paperSize.heightMm else paperSize.widthMm
    val pageHeightMm = if (isLandscape) paperSize.widthMm else paperSize.heightMm
    val pageW = (pageWidthMm * mmToPx).toInt()
    val pageH = (pageHeightMm * mmToPx).toInt()

    val xValues = if (type == "L-Section") data.map { it.chainage } else data.map { it.distance }
    val minX = xValues.minOrNull() ?: 0.0
    val maxX = xValues.maxOrNull() ?: 10.0
    val cmPerMeterX = 100.0 / max(hScale, 1.0)
    val pxPerMeterX = cmPerMeterX * IMG_PX_PER_CM

    // Height calculation (approx)
    val totalH = pageH // Assuming 1 row for simplicity in calculation, real logic in Download.kt

    val graphContentW = (maxX - minX) * pxPerMeterX
    val totalW = (graphContentW + 200).toInt()

    val cols = ceil(totalW.toDouble() / pageW).toInt()
    val rows = 1 // Simplified for preview UI grid, code supports multi-row if height exceeds

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("$label ($cols pages)", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (c in 0 until cols) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Card(
                        modifier = Modifier
                            .size(200.dp, (200.0 * pageH / pageW).dp)
                            .border(1.dp, Color.Gray),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            GraphPageCanvas(
                                modifier = Modifier.fillMaxSize(),
                                data = data,
                                row = 0, col = c,
                                type = type,
                                paperSize = paperSize,
                                isLandscape = isLandscape,
                                hScale = hScale, vScale = vScale,
                                showPre = showPre, showPost = showPost,
                                preColor = preColor, postColor = postColor,
                                preWidth = preWidth, postWidth = postWidth
                            )

                            // Overlay Buttons
                            Row(modifier = Modifier.align(Alignment.Center)) {
                                FilledIconButton(onClick = { onZoom(0, c) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.ZoomIn, "Zoom", modifier = Modifier.size(18.dp))
                                }
                            }

                            Text("Page ${c+1}", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.align(Alignment.BottomCenter).padding(4.dp))
                        }
                    }
                    IconButton(onClick = { onDownloadPage(0, c) }) {
                        Icon(Icons.Default.Download, "Download Page", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun GraphPageCanvas(
    modifier: Modifier,
    data: List<RiverPoint>,
    row: Int, col: Int,
    type: String,
    paperSize: PaperSize,
    isLandscape: Boolean,
    hScale: Double, vScale: Double,
    showPre: Boolean, showPost: Boolean,
    preColor: java.awt.Color, postColor: java.awt.Color,
    preWidth: Float, postWidth: Float
) {
    Canvas(modifier = modifier) {
        val IMG_PX_PER_CM = 38.0
        val mmToPx = IMG_PX_PER_CM / 10.0
        val pageWidthMm = if (isLandscape) paperSize.heightMm else paperSize.widthMm
        val pageHeightMm = if (isLandscape) paperSize.widthMm else paperSize.heightMm
        val pageW = (pageWidthMm * mmToPx).toFloat()
        val pageH = (pageHeightMm * mmToPx).toFloat()

        val scaleFactor = size.width / pageW

        scale(scaleFactor, pivot = Offset.Zero) {
            val offsetX = -col * pageW
            val offsetY = -row * pageH

            translate(offsetX, offsetY) {
                // Background
                drawRect(Color.White, size = Size(pageW * 10, pageH)) // Big white canvas

                // Basic Graph Drawing Logic (Simplified for UI Preview speed)
                val xValues = if (type == "L-Section") data.map { it.chainage } else data.map { it.distance }
                val yValues = (if(showPre) data.map { it.preMonsoon } else emptyList()) + (if(showPost) data.map { it.postMonsoon } else emptyList())

                val minX = xValues.minOrNull() ?: 0.0
                val minY = if(yValues.isNotEmpty()) floor(yValues.minOrNull()!!) - 1.0 else 0.0
                val maxY = if(yValues.isNotEmpty()) ceil(yValues.maxOrNull()!!) + 1.0 else 10.0

                val cmPerMeterX = 100.0 / max(hScale, 1.0)
                val cmPerMeterY = 100.0 / max(vScale, 1.0)
                val pxPerMeterX = cmPerMeterX * IMG_PX_PER_CM
                val pxPerMeterY = cmPerMeterY * IMG_PX_PER_CM
                val padding = 100.0
                val tableH = 350.0
                val graphH = (maxY - minY) * pxPerMeterY
                val totalH = graphH + 2 * padding + tableH

                fun mapX(v: Double) = (padding + (v - minX) * pxPerMeterX).toFloat()
                fun mapY(v: Double) = ((totalH - tableH) - padding - (v - minY) * pxPerMeterY).toFloat()

                // Draw Data
                if (showPre) {
                    val path = Path()
                    var first = true
                    data.sortedBy { if(type=="L-Section") it.chainage else it.distance }.forEach { p ->
                        val x = mapX(if (type == "L-Section") p.chainage else p.distance)
                        val y = mapY(p.preMonsoon)
                        if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
                    }
                    drawPath(path, Color(preColor.red, preColor.green, preColor.blue), style = Stroke(width = preWidth * 2))
                }
                if (showPost) {
                    val path = Path()
                    var first = true
                    data.sortedBy { if(type=="L-Section") it.chainage else it.distance }.forEach { p ->
                        val x = mapX(if (type == "L-Section") p.chainage else p.distance)
                        val y = mapY(p.postMonsoon)
                        if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
                    }
                    drawPath(path, Color(postColor.red, postColor.green, postColor.blue), style = Stroke(width = postWidth * 2))
                }
            }

            // Sticky Axis Overlay (Visual indication only for UI)
            drawRect(Color.Black, style = Stroke(width = 2f), size = Size(pageW, pageH))
        }
    }
}