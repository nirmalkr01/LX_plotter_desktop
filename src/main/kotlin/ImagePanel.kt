import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.ceil

// Sealed class to identify what is selected
sealed class InteractiveItem {
    data class RiverText(val index: Int, val isLeft: Boolean) : InteractiveItem()
    data class BlueLine(val index: Int, val isLeft: Boolean) : InteractiveItem()
    data object ChainageLabel : InteractiveItem()
    data object TableRightBoundary : InteractiveItem()
    data object TableLeftBoundary : InteractiveItem()

    // NEW: L-Section Specific Items
    data object LSecPreArrow : InteractiveItem()
    data object LSecPreText : InteractiveItem()
    data object LSecPostArrow : InteractiveItem()
    data object LSecPostText : InteractiveItem()
}

// Data class for Split Segments
data class LSectionSplit(val index: Int, val start: Double, val end: Double)

@Composable
fun ImagePanel(
    riverData: List<RiverPoint>,
    activeGraphId: Double,
    onActiveGraphIdChange: (Double) -> Unit,
    startCh: Double,
    endCh: Double,
    selectedGraphType: String,
    onGraphTypeChange: (String) -> Unit,
    lHScale: Double, lVScale: Double,
    xHScale: Double, xVScale: Double,
    showPre: Boolean, showPost: Boolean,
    preColor: java.awt.Color, postColor: java.awt.Color,
    preDotted: Boolean, postDotted: Boolean,
    preWidth: Float, postWidth: Float,
    preShowPoints: Boolean, postShowPoints: Boolean,
    showGrid: Boolean,
    selectedPartitionSlot: PartitionSlot? = null,
    targetPaperSize: PaperSize = PaperSize.A3,
    targetIsLandscape: Boolean = true,
    onAddToReport: (ReportElement) -> Unit,
    onStatusChange: (String) -> Unit,

    // --- STATE HOISTED FROM MAIN.KT FOR PERSISTENCE & UNDO ---
    riverOffsets: MutableMap<Int, Offset>,
    blueLineOffsets: MutableMap<Int, Offset>,
    chLabelOffset: Offset,
    onChLabelOffsetChange: (Offset) -> Unit,
    deletedRivers: MutableList<Int>,
    deletedBlueLines: MutableList<Int>,
    isChLabelDeleted: Boolean,
    onChLabelDeleteChange: (Boolean) -> Unit,
    onInteractionStart: () -> Unit,
    onResetAllInteractive: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // --- INTERNAL CHAINAGE STATE ---
    var currentStartCh by remember(startCh) { mutableStateOf(startCh) }
    var currentEndCh by remember(endCh) { mutableStateOf(endCh) }

    // Use riverOffsets to store table resize offsets securely
    val tableLeftWidthOffset = riverOffsets[-30]?.x ?: 0f
    val tableRightWidthOffset = riverOffsets[-31]?.x ?: 0f

    // --- AUTO SPLIT STATE ---
    var generatedSplits by remember { mutableStateOf<List<LSectionSplit>>(emptyList()) }
    var activeSplitIndex by remember { mutableStateOf(-1) }

    val rawConfig = ReportConfig(
        marginTop = 2f, marginBottom = 2f, marginLeft = 2f, marginRight = 2f,
        showOuterBorder = false, showInnerBorder = false
    )

    // --- CALCULATE DATA VIEW ---
    val viewData = remember(riverData, activeGraphId, currentStartCh, currentEndCh, selectedGraphType) {
        if (activeGraphId == -1.0) getCurrentViewData(riverData, "L-Section", 0.0, currentStartCh, currentEndCh)
        else getCurrentViewData(riverData, "X-Section", activeGraphId, 0.0, 0.0)
    }

    // Determine Bank Indices for X-Section
    val sortedData = remember(viewData, selectedGraphType) {
        viewData.sortedBy { if(selectedGraphType=="L-Section") it.chainage else it.distance }
            .distinctBy { if(selectedGraphType=="L-Section") it.chainage else it.distance }
    }

    val leftBankIndex = if (sortedData.size > 1) 1 else -1
    val rightBankIndex = if (sortedData.size > 1) sortedData.size - 2 else -1

    // --- DYNAMIC SIZING STATE ---
    var datumSize by remember { mutableStateOf(14f) }
    var axisSize by remember { mutableStateOf(14f) }
    var tableTextSize by remember { mutableStateOf(14f) }
    var tableGap by remember { mutableStateOf(0f) }
    var riverTextSize by remember { mutableStateOf(14f) }
    var chainageTextSize by remember { mutableStateOf(18f) }

    // NEW: L-Section Specific Size (Arrow/Text) - Defaults to roughly "0.5" relative
    var lSecItemSize by remember { mutableStateOf(20f) }

    // --- ZOOM STATE ---
    var imageZoom by remember { mutableStateOf(1.0f) }
    var autoZoomPerformed by remember { mutableStateOf(false) }

    // --- INTERACTIVE ELEMENT STATE (LOCAL SELECTION ONLY) ---
    // Note: Actual data is now passed in via parameters
    var selectedItem by remember { mutableStateOf<InteractiveItem?>(null) }

    // UI Logic State
    var activeTab by remember { mutableStateOf("Size") }
    var isRibbonOpen by remember { mutableStateOf(true) }

    // Scroll State for Canvas
    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()

    // Reset logic when graph type changes
    LaunchedEffect(activeGraphId, selectedGraphType, riverData) { // Added riverData dependency
        selectedItem = null
        generatedSplits = emptyList()
        activeSplitIndex = -1
        autoZoomPerformed = false // Reset flag to trigger auto-zoom calculation

        // Reset table offsets stored in map when view changes
        riverOffsets.remove(-30)
        riverOffsets.remove(-31)

        // Reset to default limits
        if(selectedGraphType == "L-Section" && riverData.isNotEmpty()) {
            currentStartCh = riverData.minOf { it.chainage }
            currentEndCh = riverData.maxOf { it.chainage }
        }
    }

    // --- MAIN LAYOUT (Unified Boundary) ---
    Surface(
        modifier = Modifier.fillMaxHeight().fillMaxWidth(),
        color = Color(0xFFF3F2F1),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0)) // Unified Boundary
    ) {
        Column {

            // 1. RIBBON TABS
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ImagePanelRibbonTab("Size", activeTab == "Size") { activeTab = "Size"; if(!isRibbonOpen) isRibbonOpen = true }
                ImagePanelRibbonTab("Edit", activeTab == "Edit") { activeTab = "Edit"; if(!isRibbonOpen) isRibbonOpen = true }
                ImagePanelRibbonTab("Graph", activeTab == "Graph") { activeTab = "Graph"; if(!isRibbonOpen) isRibbonOpen = true }

                Spacer(Modifier.weight(1f))

                // Zoom Controls
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ImagePanelIconButton(
                        onClick = { imageZoom = (imageZoom - 0.1f).coerceAtLeast(0.1f) },
                        icon = Icons.Default.Remove,
                        tooltip = "Zoom Out"
                    )
                    Text(
                        text = "${(imageZoom * 100).toInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.Center
                    )
                    ImagePanelIconButton(
                        onClick = { imageZoom = (imageZoom + 0.1f).coerceAtMost(5.0f) },
                        icon = Icons.Default.Add,
                        tooltip = "Zoom In"
                    )
                }

                Spacer(Modifier.width(8.dp))
                Box(Modifier.width(1.dp).height(20.dp).background(Color.LightGray))
                Spacer(Modifier.width(8.dp))

                // Collapse Button
                ImagePanelIconButton(
                    onClick = { isRibbonOpen = !isRibbonOpen },
                    icon = if(isRibbonOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    tooltip = if(isRibbonOpen) "Collapse" else "Expand"
                )
            }

            // 2. RIBBON CONTENT
            AnimatedVisibility(
                visible = isRibbonOpen,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(90.dp),
                    color = Color(0xFFF8F9FA),
                    shadowElevation = 2.dp
                ) {
                    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {

                        if (activeTab == "Size") {
                            // --- SIZE TOOLS ---
                            ImagePanelRibbonGroup("Config") {
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ImagePanelNumberInput("Datum:", datumSize) { datumSize = it }
                                        ImagePanelNumberInput("Axis:", axisSize) { axisSize = it }
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ImagePanelNumberInput("Text:", tableTextSize) { tableTextSize = it }
                                        ImagePanelNumberInput("Gap:", tableGap) { tableGap = it }
                                    }
                                }
                            }
                        } else if (activeTab == "Graph") {
                            // --- GRAPH SELECTION TOOLS (SPLIT UP/DOWN) ---
                            ImagePanelRibbonGroup("Select Graph") {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.Start) {
                                    // TOP: X-SECTION ROW
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Button(
                                            onClick = { onGraphTypeChange("X-Section"); if(riverData.isNotEmpty()) onActiveGraphIdChange(riverData.minOf { it.chainage }) },
                                            modifier = Modifier.height(28.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = if(selectedGraphType == "X-Section") MaterialTheme.colorScheme.primary else Color.LightGray)
                                        ) {
                                            Text("X-Sec", fontSize = 10.sp)
                                        }
                                        Spacer(Modifier.width(8.dp))

                                        if(selectedGraphType == "X-Section") {
                                            val xGraphs = remember(riverData) { riverData.map { it.chainage }.distinct().sorted() }
                                            val currentIndex = xGraphs.indexOf(activeGraphId)
                                            val listState = rememberLazyListState()

                                            // Auto-scroll to selected item
                                            LaunchedEffect(activeGraphId) {
                                                if(currentIndex >= 0) listState.animateScrollToItem(max(0, currentIndex - 2))
                                            }

                                            // Previous Arrow
                                            IconButton(
                                                onClick = { if (currentIndex > 0) onActiveGraphIdChange(xGraphs[currentIndex - 1]) },
                                                enabled = currentIndex > 0,
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(Icons.Default.KeyboardArrowLeft, "Previous", tint = if(currentIndex > 0) Color.Black else Color.LightGray)
                                            }

                                            Spacer(Modifier.width(4.dp))

                                            // The List (Professional View)
                                            LazyRow(
                                                state = listState,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                items(xGraphs) { ch ->
                                                    val isSelected = activeGraphId == ch
                                                    Box(
                                                        modifier = Modifier
                                                            .height(24.dp)
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                                            .clickable { onActiveGraphIdChange(ch) }
                                                            .padding(horizontal = 8.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            "${ch.toInt()}",
                                                            fontSize = 11.sp,
                                                            fontWeight = if(isSelected) FontWeight.Bold else FontWeight.Normal,
                                                            color = if(isSelected) MaterialTheme.colorScheme.primary else Color.DarkGray
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(Modifier.width(4.dp))

                                            // Next Arrow
                                            IconButton(
                                                onClick = { if (currentIndex < xGraphs.size - 1) onActiveGraphIdChange(xGraphs[currentIndex + 1]) },
                                                enabled = currentIndex != -1 && currentIndex < xGraphs.size - 1,
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(Icons.Default.KeyboardArrowRight, "Next", tint = if(currentIndex < xGraphs.size - 1) Color.Black else Color.LightGray)
                                            }
                                        }
                                    }

                                    // BOTTOM: L-SECTION ROW
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Button(
                                            onClick = { onGraphTypeChange("L-Section"); onActiveGraphIdChange(-1.0) },
                                            modifier = Modifier.height(28.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = if(selectedGraphType == "L-Section") MaterialTheme.colorScheme.primary else Color.LightGray)
                                        ) {
                                            Text("L-Sec", fontSize = 10.sp)
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        if(selectedGraphType == "L-Section") {
                                            Text("L-Section Profile Selected", fontSize = 10.sp, color = Color.Gray)
                                        }
                                    }
                                }
                            }

                        } else {
                            // --- EDIT TOOLS ---
                            ImagePanelRibbonGroup("Selection") {
                                // Dropdown for item selection
                                var expanded by remember { mutableStateOf(false) }
                                Box {
                                    OutlinedButton(
                                        onClick = { expanded = true },
                                        modifier = Modifier.height(30.dp),
                                        shape = RoundedCornerShape(4.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        val label = when(val item = selectedItem) {
                                            is InteractiveItem.RiverText -> if(item.isLeft) "Left River Text" else "Right River Text"
                                            is InteractiveItem.BlueLine -> if(item.isLeft) "Left Blue Line" else "Right Blue Line"
                                            is InteractiveItem.ChainageLabel -> "Chainage Label"
                                            is InteractiveItem.LSecPreArrow -> "Pre Arrow"
                                            is InteractiveItem.LSecPostArrow -> "Post Arrow"
                                            is InteractiveItem.LSecPreText -> "Pre Label"
                                            is InteractiveItem.LSecPostText -> "Post Label"
                                            is InteractiveItem.TableRightBoundary -> "Table Right Edge"
                                            is InteractiveItem.TableLeftBoundary -> "Table Left Edge"
                                            null -> "Select Object..."
                                        }
                                        Text(label, fontSize = 11.sp, maxLines = 1)
                                        Spacer(Modifier.width(4.dp))
                                        Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp))
                                    }
                                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                        DropdownMenuItem(text = { Text("None", fontSize = 11.sp) }, onClick = { selectedItem = null; expanded = false })
                                        HorizontalDivider()
                                        DropdownMenuItem(text = { Text("Table Left Edge (Header)", fontSize = 11.sp) }, onClick = { selectedItem = InteractiveItem.TableLeftBoundary; expanded = false })
                                        DropdownMenuItem(text = { Text("Table Right Edge", fontSize = 11.sp) }, onClick = { selectedItem = InteractiveItem.TableRightBoundary; expanded = false })

                                        if (selectedGraphType == "X-Section" && leftBankIndex != -1 && rightBankIndex != -1) {
                                            HorizontalDivider()
                                            DropdownMenuItem(text = { Text("Left River Text", fontSize = 11.sp) }, onClick = { selectedItem = InteractiveItem.RiverText(leftBankIndex, true); expanded = false })
                                            DropdownMenuItem(text = { Text("Right River Text", fontSize = 11.sp) }, onClick = { selectedItem = InteractiveItem.RiverText(rightBankIndex, false); expanded = false })
                                            HorizontalDivider()
                                            DropdownMenuItem(text = { Text("Left Blue Line", fontSize = 11.sp) }, onClick = { selectedItem = InteractiveItem.BlueLine(leftBankIndex, true); expanded = false })
                                            DropdownMenuItem(text = { Text("Right Blue Line", fontSize = 11.sp) }, onClick = { selectedItem = InteractiveItem.BlueLine(rightBankIndex, false); expanded = false })
                                            HorizontalDivider()
                                            DropdownMenuItem(text = { Text("Chainage Label", fontSize = 11.sp) }, onClick = { selectedItem = InteractiveItem.ChainageLabel; expanded = false })
                                        }
                                        if (selectedGraphType == "L-Section") {
                                            HorizontalDivider()
                                            DropdownMenuItem(text = { Text("Pre Arrow", fontSize = 11.sp) }, onClick = { selectedItem = InteractiveItem.LSecPreArrow; expanded = false })
                                            DropdownMenuItem(text = { Text("Pre Label", fontSize = 11.sp) }, onClick = { selectedItem = InteractiveItem.LSecPreText; expanded = false })
                                            HorizontalDivider()
                                            DropdownMenuItem(text = { Text("Post Arrow", fontSize = 11.sp) }, onClick = { selectedItem = InteractiveItem.LSecPostArrow; expanded = false })
                                            DropdownMenuItem(text = { Text("Post Label", fontSize = 11.sp) }, onClick = { selectedItem = InteractiveItem.LSecPostText; expanded = false })
                                        }
                                    }
                                }
                            }

                            if (selectedItem != null) {
                                VerticalDivider(Modifier.padding(vertical = 8.dp))
                                ImagePanelRibbonGroup("Properties") {
                                    when(selectedItem) {
                                        is InteractiveItem.RiverText -> ImagePanelNumberInput("Size:", riverTextSize) { riverTextSize = it }
                                        is InteractiveItem.ChainageLabel -> ImagePanelNumberInput("Size:", chainageTextSize) { chainageTextSize = it }
                                        is InteractiveItem.LSecPreArrow, is InteractiveItem.LSecPostArrow,
                                        is InteractiveItem.LSecPreText, is InteractiveItem.LSecPostText -> ImagePanelNumberInput("Size:", lSecItemSize) { lSecItemSize = it }
                                        else -> {}
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3.5 SPLIT CHIPS BAR (Sub-section above footer)
            if (selectedGraphType == "L-Section" && generatedSplits.isNotEmpty()) {
                HorizontalDivider()
                Surface(
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    color = Color(0xFFFFF8E1)
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(generatedSplits) { split ->
                            val isSelected = activeSplitIndex == split.index
                            Box(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .background(if (isSelected) Color(0xFFFFE0B2) else Color.White, RoundedCornerShape(4.dp))
                                    .border(1.dp, if(isSelected) Color(0xFFF57F17) else Color.LightGray, RoundedCornerShape(4.dp))
                                    .clickable {
                                        activeSplitIndex = split.index
                                        currentStartCh = split.start
                                        currentEndCh = split.end
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Part ${split.index} (${split.start.toInt()}-${split.end.toInt()})", fontSize = 11.sp, color = Color.Black)
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = Color(0xFFE0E0E0))

            // 3. CANVAS AREA (Scrollable & Centered)
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.White)
            ) {
                if (activeGraphId == -100.0) {
                    Text("No Data Selected", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
                } else {
                    val hScale = if (selectedGraphType == "L-Section") lHScale else xHScale
                    val vScale = if (selectedGraphType == "L-Section") lVScale else xVScale

                    // Calculate dimension using correct values including table Gap and chainage MM offset
                    val chMm = chLabelOffset.y / (3.78f * density.density)
                    val graphDimsMm = remember(viewData, hScale, vScale, tableLeftWidthOffset, tableRightWidthOffset, tableGap, chMm) {
                        calculateGraphDimensionsMM(viewData, selectedGraphType, hScale, vScale, tableLeftWidthOffset, tableRightWidthOffset, tableGap, chMm)
                    }

                    // AUTO ZOOM LOGIC
                    val containerW = maxWidth.value
                    val containerH = maxHeight.value

                    LaunchedEffect(graphDimsMm, containerW, containerH, activeGraphId) {
                        if (!autoZoomPerformed && containerW > 0 && containerH > 0) {
                            val basePxPerMm = 3.78f * density.density
                            val contentWPx = (graphDimsMm.widthMm * basePxPerMm).toFloat()
                            val contentHPx = (graphDimsMm.heightMm * basePxPerMm).toFloat()

                            val contentWDp = contentWPx / density.density
                            val contentHDp = contentHPx / density.density

                            // Calculate ideal zoom to fit content in window
                            val zoomX = containerW / contentWDp
                            val zoomY = containerH / contentHDp
                            val idealZoom = min(zoomX, zoomY).coerceAtMost(1.0f) // Cap at 100% to avoid blur

                            // Apply slightly less than full fit for padding
                            imageZoom = (idealZoom * 0.9f).coerceAtLeast(0.1f)
                            autoZoomPerformed = true
                        }
                    }

                    // Fix 1: Establish a stable base resolution (1x zoom) for precise drawing without pixel rounding errors
                    val basePxPerMm = 3.78f * density.density

                    // Apply ImageZoom to the Pixel Scale here so the canvas physically grows, keeping high res.
                    val scaledPxPerMm = basePxPerMm * imageZoom

                    // Calculate Box content bounds dynamically
                    val contentWidthPx = (graphDimsMm.widthMm * scaledPxPerMm).toFloat()
                    val contentHeightPx = (graphDimsMm.heightMm * scaledPxPerMm).toFloat()

                    // Convert to DP
                    val contentWidthDp = (contentWidthPx / density.density).dp
                    val contentHeightDp = (contentHeightPx / density.density).dp

                    val viewportWidth = maxWidth
                    val viewportHeight = maxHeight
                    val buffer = 100.dp

                    val finalWidthDp = (contentWidthDp + buffer).coerceAtLeast(viewportWidth)
                    val finalHeightDp = (contentHeightDp + buffer).coerceAtLeast(viewportHeight)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(horizontalScrollState)
                            .verticalScroll(verticalScrollState)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(finalWidthDp)
                                .height(finalHeightDp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Transparent background applied here for Preview
                            Box(
                                modifier = Modifier
                                    .size(contentWidthDp, contentHeightDp)
                            ) {
                                GraphPageCanvas(
                                    modifier = Modifier.fillMaxSize(), data = viewData, type = selectedGraphType,
                                    paperSize = PaperSize.A4, isLandscape = true, hScale = hScale, vScale = vScale, config = rawConfig,
                                    showPre = showPre, showPost = showPost, preColor = preColor, postColor = postColor,
                                    preDotted = preDotted, postDotted = postDotted, preWidth = preWidth, postWidth = postWidth,
                                    preShowPoints = preShowPoints, postShowPoints = postShowPoints, showGrid = showGrid, isRawView = true,
                                    isTransparentOverlay = true,

                                    pxPerMm = scaledPxPerMm, // Pass the ZOOMED scale factor!

                                    datumSize = datumSize,
                                    axisLabelSize = axisSize,
                                    tableTextSize = tableTextSize,
                                    tableGap = tableGap,

                                    riverTextSize = if(selectedGraphType == "L-Section") lSecItemSize else riverTextSize,

                                    chainageTextSize = chainageTextSize,

                                    riverOffsets = riverOffsets,
                                    blueLineOffsets = blueLineOffsets,
                                    chLabelOffset = chLabelOffset,

                                    deletedRiverIndices = deletedRivers,
                                    deletedBlueLineIndices = deletedBlueLines,
                                    isChLabelDeleted = isChLabelDeleted,

                                    selectedItem = selectedItem,
                                    onSelectItem = { },
                                    onDragItem = { item, dragAmount ->
                                        // Un-zoom the drag amount so it's stored in true spatial scaling
                                        // NOW CORRECTLY DIVIDED BY pxPerMm SO IT STORES PERFECT MILLIMETERS!
                                        val mmDragX = dragAmount.x / scaledPxPerMm
                                        val mmDragY = dragAmount.y / scaledPxPerMm
                                        val mmDrag = Offset(mmDragX, mmDragY)

                                        when(item) {
                                            is InteractiveItem.RiverText -> riverOffsets[item.index] = (riverOffsets[item.index] ?: Offset.Zero) + mmDrag
                                            is InteractiveItem.BlueLine -> blueLineOffsets[item.index] = (blueLineOffsets[item.index] ?: Offset.Zero) + mmDrag
                                            is InteractiveItem.ChainageLabel -> onChLabelOffsetChange(chLabelOffset + mmDrag)

                                            is InteractiveItem.LSecPreArrow -> riverOffsets[-10] = (riverOffsets[-10] ?: Offset.Zero) + mmDrag
                                            is InteractiveItem.LSecPreText -> riverOffsets[-11] = (riverOffsets[-11] ?: Offset.Zero) + mmDrag
                                            is InteractiveItem.LSecPostArrow -> riverOffsets[-20] = (riverOffsets[-20] ?: Offset.Zero) + mmDrag
                                            is InteractiveItem.LSecPostText -> riverOffsets[-21] = (riverOffsets[-21] ?: Offset.Zero) + mmDrag

                                            // Store table edits straight into riverOffsets so they get passed natively into the element
                                            is InteractiveItem.TableLeftBoundary -> {
                                                val mmDelta = dragAmount.x / scaledPxPerMm
                                                val current = riverOffsets[-30]?.x ?: 0f
                                                // Prevent resizing too small
                                                if (current + mmDelta > -35f) {
                                                    riverOffsets[-30] = Offset(current + mmDelta, 0f)
                                                }
                                            }
                                            is InteractiveItem.TableRightBoundary -> {
                                                val mmDelta = dragAmount.x / scaledPxPerMm
                                                val current = riverOffsets[-31]?.x ?: 0f
                                                riverOffsets[-31] = Offset(current + mmDelta, 0f)
                                            }
                                        }
                                    },
                                    onInteractionStart = onInteractionStart
                                )
                            }
                        }
                    }
                }
            }

            // 4. FOOTER (Bottom Toolbar)
            Surface(
                modifier = Modifier.fillMaxWidth().height(40.dp),
                color = Color(0xFFF3F2F1),
                border = BorderStroke(1.dp, Color(0xFFE0E0E0))
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // LEFT: Action Icons
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Reset Button
                        ImagePanelIconButton(
                            onClick = {
                                onInteractionStart() // Snapshot before reset
                                onResetAllInteractive()
                                selectedItem = null; tableGap = 0f; datumSize = 14f; tableTextSize = 14f
                                riverTextSize = 14f; chainageTextSize = 18f; lSecItemSize = 20f
                                riverOffsets.remove(-30); riverOffsets.remove(-31) // Reset table size keys
                                if(selectedGraphType == "L-Section") {
                                    currentStartCh = startCh; currentEndCh = endCh
                                    generatedSplits = emptyList(); activeSplitIndex = -1
                                }
                            },
                            icon = Icons.Default.Refresh,
                            tooltip = "Reset All Changes"
                        )

                        // Delete Button
                        ImagePanelIconButton(
                            onClick = {
                                onInteractionStart() // Trigger Undo Snapshot before deleting
                                when(val item = selectedItem) {
                                    is InteractiveItem.RiverText -> if(!deletedRivers.contains(item.index)) deletedRivers.add(item.index)
                                    is InteractiveItem.BlueLine -> if(!deletedBlueLines.contains(item.index)) deletedBlueLines.add(item.index)
                                    is InteractiveItem.ChainageLabel -> onChLabelDeleteChange(true)
                                    is InteractiveItem.LSecPreArrow -> deletedRivers.add(-10)
                                    is InteractiveItem.LSecPreText -> deletedRivers.add(-11)
                                    is InteractiveItem.LSecPostArrow -> deletedRivers.add(-20)
                                    is InteractiveItem.LSecPostText -> deletedRivers.add(-21)
                                    null -> {}
                                    else -> {}
                                }
                                selectedItem = null
                            },
                            icon = Icons.Default.Delete,
                            tooltip = "Delete Selected",
                            tint = if(selectedItem != null) Color.Red else Color.LightGray,
                            enabled = selectedItem != null
                        )

                        // Auto Split (Only for L-Section)
                        if (selectedGraphType == "L-Section") {
                            ImagePanelIconButton(
                                onClick = {
                                    if (selectedPartitionSlot == null) {
                                        onStatusChange("Select a Grid/Slot first to calculate fit.")
                                    } else {
                                        // Auto Split Logic
                                        val paperW_mm = if (targetIsLandscape) targetPaperSize.heightMm else targetPaperSize.widthMm
                                        // Use MM Width from Slot
                                        val slotW_mm = selectedPartitionSlot.widthMm
                                        val axisPadding_mm = 22.0
                                        val excludeUnits_mm = 20.0
                                        val usableW_mm = slotW_mm - (axisPadding_mm + excludeUnits_mm)

                                        if (usableW_mm > 0) {
                                            val metersPerMm = lHScale / 1000.0
                                            val capacityMeters = usableW_mm * metersPerMm
                                            val cleanCapacity = floor(capacityMeters / 100.0) * 100.0
                                            val totalMin = riverData.minOf { it.chainage }
                                            val totalMax = riverData.maxOf { it.chainage }
                                            val newSplits = mutableListOf<LSectionSplit>()
                                            var cursor = totalMin
                                            var idx = 1
                                            while (cursor < totalMax) {
                                                var end = cursor + cleanCapacity
                                                if (end > totalMax) end = totalMax
                                                newSplits.add(LSectionSplit(idx, cursor, end))
                                                cursor = end
                                                idx++
                                            }
                                            generatedSplits = newSplits
                                            onStatusChange("Auto-Split: ${newSplits.size} parts.")
                                        } else {
                                            onStatusChange("Slot too small.")
                                        }
                                    }
                                },
                                icon = Icons.Default.CallSplit,
                                tooltip = "Auto-Split L-Section (Requires Grid Selection)",
                                tint = Color(0xFFF57F17),
                                enabled = selectedPartitionSlot != null
                            )
                        }
                    }

                    // RIGHT: Slot Add
                    ImagePanelIconButton(
                        onClick = {
                            if (activeGraphId != -100.0 && selectedPartitionSlot != null) {
                                val hS = if(selectedGraphType=="L-Section") lHScale else xHScale
                                val vS = if(selectedGraphType=="L-Section") lVScale else xVScale

                                val newElement = ReportElement(
                                    type = ElementType.GRAPH_IMAGE,
                                    // NO PERCENTS - Use MM coordinates from the Slot
                                    xMm = selectedPartitionSlot.xMm,
                                    yMm = selectedPartitionSlot.yMm,
                                    widthMm = selectedPartitionSlot.widthMm,
                                    heightMm = selectedPartitionSlot.heightMm,

                                    graphData = viewData,
                                    graphType = selectedGraphType,
                                    graphHScale = hS, graphVScale = vS,
                                    graphShowPre = showPre, graphShowPost = showPost,
                                    graphPreColor = Color(preColor.red, preColor.green, preColor.blue),
                                    graphPostColor = Color(postColor.red, postColor.green, postColor.blue),
                                    graphPreDotted = preDotted, graphPostDotted = postDotted,
                                    graphPreWidth = preWidth, graphPostWidth = postWidth,
                                    graphShowGrid = showGrid,
                                    riverOffsets = riverOffsets.toMap(),
                                    blueLineOffsets = blueLineOffsets.toMap(),
                                    chLabelOffset = chLabelOffset,
                                    deletedRiverIndices = deletedRivers.toList(),
                                    deletedBlueLineIndices = deletedBlueLines.toList(),
                                    isChLabelDeleted = isChLabelDeleted,
                                    datumSize = datumSize, axisLabelSize = axisSize,
                                    tableTextSize = tableTextSize, tableGap = tableGap,
                                    riverTextSize = riverTextSize, chainageTextSize = chainageTextSize,
                                )
                                val finalElement = if(selectedGraphType == "L-Section") newElement.copy(riverTextSize = lSecItemSize) else newElement
                                onAddToReport(finalElement)
                            } else {
                                onStatusChange("Select a Grid/Slot first!")
                            }
                        },
                        icon = Icons.Default.ArrowForward,
                        tooltip = "Add Graph on Page",
                        enabled = selectedPartitionSlot != null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// --- LOCAL UI HELPERS (To ensure independence and professional look) ---

@Composable
private fun ImagePanelRibbonTab(text: String, isActive: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (isActive) Color(0xFF2B579A) else Color.DarkGray,
            containerColor = if (isActive) Color.White else Color.Transparent
        ),
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        modifier = Modifier.height(32.dp)
    ) {
        Text(text, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal, fontSize = 11.sp)
    }
}

@Composable
private fun ImagePanelRibbonGroup(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxHeight().padding(horizontal = 4.dp)
    ) {
        Box(modifier = Modifier.weight(1f).wrapContentWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
        }
        Text(text = label, fontSize = 9.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 2.dp))
    }
}

@Composable
private fun ImagePanelNumberInput(label: String, value: Float, onChange: (Float) -> Unit) {
    var textValue by remember(value) { mutableStateOf(if(value == 0f) "" else value.toString().removeSuffix(".0")) }

    // If the external value changes to 0 (e.g. reset), update the text field
    LaunchedEffect(value) {
        if(value == 0f && textValue != "0") {
            textValue = "0"
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(24.dp)) {
        if(label.isNotEmpty()) Text(label, fontSize = 10.sp, color = Color.DarkGray, modifier = Modifier.width(45.dp))
        BasicTextField(
            value = textValue,
            onValueChange = { str ->
                textValue = str
                val num = str.toFloatOrNull()
                if (num != null) onChange(num)
            },
            textStyle = TextStyle(fontSize = 10.sp, textAlign = TextAlign.Center),
            singleLine = true,
            modifier = Modifier
                .width(40.dp)
                .fillMaxHeight()
                .background(Color.White, RoundedCornerShape(2.dp))
                .border(1.dp, Color.LightGray, RoundedCornerShape(2.dp))
                .padding(top = 5.dp) // Manual vertical center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImagePanelIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    tooltip: String,
    tint: Color = Color.Black,
    enabled: Boolean = true
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState()
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(32.dp)) {
            Icon(icon, contentDescription = tooltip, tint = if(enabled) tint else Color.LightGray, modifier = Modifier.size(18.dp))
        }
    }
}