import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.max

// Sealed class to identify what is selected
sealed class InteractiveItem {
    data class RiverText(val index: Int, val isLeft: Boolean) : InteractiveItem()
    data class BlueLine(val index: Int, val isLeft: Boolean) : InteractiveItem()
    data object ChainageLabel : InteractiveItem()

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
    startCh: Double, // Passed from parent
    endCh: Double,   // Passed from parent
    selectedGraphType: String,
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
    onStatusChange: (String) -> Unit
) {
    val scope = rememberCoroutineScope()

    // --- INTERNAL CHAINAGE STATE ---
    var currentStartCh by remember(startCh) { mutableStateOf(startCh) }
    var currentEndCh by remember(endCh) { mutableStateOf(endCh) }

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

    // --- INTERACTIVE ELEMENT STATE ---
    val riverOffsets = remember { mutableStateMapOf<Int, Offset>() }
    val blueLineOffsets = remember { mutableStateMapOf<Int, Offset>() }
    var chLabelOffset by remember { mutableStateOf(Offset.Zero) }
    val deletedRivers = remember { mutableStateListOf<Int>() }
    val deletedBlueLines = remember { mutableStateListOf<Int>() }
    var isChLabelDeleted by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<InteractiveItem?>(null) }

    // UI Logic State
    var activeTab by remember { mutableStateOf("Size") }
    var isRibbonOpen by remember { mutableStateOf(true) }

    // Scroll State for Canvas
    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()

    // Reset logic when graph type changes
    LaunchedEffect(activeGraphId, selectedGraphType) {
        selectedItem = null
        generatedSplits = emptyList()
        activeSplitIndex = -1
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

                Spacer(Modifier.width(8.dp))

                // Collapse Button
                ImagePanelIconButton(
                    onClick = { isRibbonOpen = !isRibbonOpen },
                    icon = if(isRibbonOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    tooltip = if(isRibbonOpen) "Collapse" else "Expand"
                )

                Spacer(Modifier.weight(1f))
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
                                            null -> "Select Object..."
                                        }
                                        Text(label, fontSize = 11.sp, maxLines = 1)
                                        Spacer(Modifier.width(4.dp))
                                        Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp))
                                    }
                                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                        DropdownMenuItem(text = { Text("None", fontSize = 11.sp) }, onClick = { selectedItem = null; expanded = false })

                                        if (selectedGraphType == "X-Section" && leftBankIndex != -1 && rightBankIndex != -1) {
                                            Divider()
                                            DropdownMenuItem(text = { Text("Left River Text", fontSize = 11.sp) }, onClick = { selectedItem = InteractiveItem.RiverText(leftBankIndex, true); expanded = false })
                                            DropdownMenuItem(text = { Text("Right River Text", fontSize = 11.sp) }, onClick = { selectedItem = InteractiveItem.RiverText(rightBankIndex, false); expanded = false })
                                            Divider()
                                            DropdownMenuItem(text = { Text("Left Blue Line", fontSize = 11.sp) }, onClick = { selectedItem = InteractiveItem.BlueLine(leftBankIndex, true); expanded = false })
                                            DropdownMenuItem(text = { Text("Right Blue Line", fontSize = 11.sp) }, onClick = { selectedItem = InteractiveItem.BlueLine(rightBankIndex, false); expanded = false })
                                            Divider()
                                            DropdownMenuItem(text = { Text("Chainage Label", fontSize = 11.sp) }, onClick = { selectedItem = InteractiveItem.ChainageLabel; expanded = false })
                                        }
                                        if (selectedGraphType == "L-Section") {
                                            Divider()
                                            DropdownMenuItem(text = { Text("Pre Arrow", fontSize = 11.sp) }, onClick = { selectedItem = InteractiveItem.LSecPreArrow; expanded = false })
                                            DropdownMenuItem(text = { Text("Pre Label", fontSize = 11.sp) }, onClick = { selectedItem = InteractiveItem.LSecPreText; expanded = false })
                                            Divider()
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

            Divider(color = Color(0xFFE0E0E0))

            // 3. CANVAS AREA (Scrollable)
            Box(
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

                    // Calculate required dimensions
                    val graphDims = remember(viewData, hScale, vScale) {
                        calculateGraphDimensions(viewData, selectedGraphType, hScale, vScale)
                    }

                    val contentWidth = maxOf(100.dp, graphDims.width.dp)
                    val contentHeight = maxOf(100.dp, graphDims.height.dp)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(horizontalScrollState)
                            .verticalScroll(verticalScrollState)
                    ) {
                        Box(modifier = Modifier.size(contentWidth, contentHeight)) {
                            GraphPageCanvas(
                                modifier = Modifier.fillMaxSize(), data = viewData, type = selectedGraphType,
                                paperSize = PaperSize.A4, isLandscape = true, hScale = hScale, vScale = vScale, config = rawConfig,
                                showPre = showPre, showPost = showPost, preColor = preColor, postColor = postColor,
                                preDotted = preDotted, postDotted = postDotted, preWidth = preWidth, postWidth = postWidth,
                                preShowPoints = preShowPoints, postShowPoints = postShowPoints, showGrid = showGrid, isRawView = true,

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
                                    when(item) {
                                        is InteractiveItem.RiverText -> riverOffsets[item.index] = (riverOffsets[item.index] ?: Offset.Zero) + dragAmount
                                        is InteractiveItem.BlueLine -> blueLineOffsets[item.index] = (blueLineOffsets[item.index] ?: Offset.Zero) + dragAmount
                                        is InteractiveItem.ChainageLabel -> chLabelOffset += dragAmount

                                        is InteractiveItem.LSecPreArrow -> riverOffsets[-10] = (riverOffsets[-10] ?: Offset.Zero) + dragAmount
                                        is InteractiveItem.LSecPreText -> riverOffsets[-11] = (riverOffsets[-11] ?: Offset.Zero) + dragAmount
                                        is InteractiveItem.LSecPostArrow -> riverOffsets[-20] = (riverOffsets[-20] ?: Offset.Zero) + dragAmount
                                        is InteractiveItem.LSecPostText -> riverOffsets[-21] = (riverOffsets[-21] ?: Offset.Zero) + dragAmount
                                    }
                                },
                            )
                        }
                    }

                    // Scrollbars
                    HorizontalScrollbar(
                        adapter = rememberScrollbarAdapter(horizontalScrollState),
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    )
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(verticalScrollState),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                    )
                }
            }

            // 3.5 SPLIT CHIPS BAR (Sub-section above footer)
            if (selectedGraphType == "L-Section" && generatedSplits.isNotEmpty()) {
                Divider()
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
                                riverOffsets.clear(); blueLineOffsets.clear(); chLabelOffset = Offset.Zero
                                deletedRivers.clear(); deletedBlueLines.clear(); isChLabelDeleted = false
                                selectedItem = null; tableGap = 0f; datumSize = 14f; tableTextSize = 14f
                                riverTextSize = 14f; chainageTextSize = 18f; lSecItemSize = 20f
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
                                when(val item = selectedItem) {
                                    is InteractiveItem.RiverText -> if(!deletedRivers.contains(item.index)) deletedRivers.add(item.index)
                                    is InteractiveItem.BlueLine -> if(!deletedBlueLines.contains(item.index)) deletedBlueLines.add(item.index)
                                    is InteractiveItem.ChainageLabel -> isChLabelDeleted = true
                                    is InteractiveItem.LSecPreArrow -> deletedRivers.add(-10)
                                    is InteractiveItem.LSecPreText -> deletedRivers.add(-11)
                                    is InteractiveItem.LSecPostArrow -> deletedRivers.add(-20)
                                    is InteractiveItem.LSecPostText -> deletedRivers.add(-21)
                                    null -> {}
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
                                        val slotW_mm = paperW_mm * selectedPartitionSlot.wPercent
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
                                    xPercent = selectedPartitionSlot.xPercent,
                                    yPercent = selectedPartitionSlot.yPercent,
                                    widthPercent = selectedPartitionSlot.wPercent,
                                    heightPercent = selectedPartitionSlot.hPercent,
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