import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
    // CHANGED DEFAULT TO A3 FOR ENGINEERING STANDARD
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
    // Note: For L-Section, we reuse riverOffsets with specific negative keys to persist data
    // -10: Pre Arrow, -11: Pre Text, -20: Post Arrow, -21: Post Text
    val riverOffsets = remember { mutableStateMapOf<Int, Offset>() }
    val blueLineOffsets = remember { mutableStateMapOf<Int, Offset>() }
    var chLabelOffset by remember { mutableStateOf(Offset.Zero) }
    val deletedRivers = remember { mutableStateListOf<Int>() }
    val deletedBlueLines = remember { mutableStateListOf<Int>() }
    var isChLabelDeleted by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<InteractiveItem?>(null) }
    var showItemDropdown by remember { mutableStateOf(false) }

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

    Card(modifier = Modifier.fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Column {
            // Header - REMOVED SAVE .PNG BUTTON
            Row(Modifier.fillMaxWidth().background(Color(0xFFE3F2FD)).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("2. Image View (Interactive)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
            }

            // --- L-SECTION AUTO SPLIT BAR ---
            if (selectedGraphType == "L-Section" && riverData.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().background(Color(0xFFFFF8E1)).padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("L-Section Tools:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF57F17))

                        Button(
                            onClick = {
                                // AUTO SPLIT LOGIC
                                if (selectedPartitionSlot == null) {
                                    onStatusChange("Please select a Grid/Slot in File Panel first to calculate fit.")
                                } else {
                                    // 1. Get Physical Paper Width (Landscape A3 usually 420mm)
                                    val paperW_mm = if (targetIsLandscape) targetPaperSize.heightMm else targetPaperSize.widthMm

                                    // 2. Get Slot Width in mm
                                    val slotW_mm = paperW_mm * selectedPartitionSlot.wPercent

                                    // 3. Deduct Margins/Padding inside the graph image
                                    val axisPadding_mm = 22.0

                                    // 4. EXCLUDE 2 UNITS (20mm) as requested
                                    val excludeUnits_mm = 20.0

                                    val usableW_mm = slotW_mm - (axisPadding_mm + excludeUnits_mm)

                                    if (usableW_mm > 0) {
                                        // 5. Calculate Meters per MM based on Scale
                                        val metersPerMm = lHScale / 1000.0

                                        // 6. Calculate Capacity
                                        val capacityMeters = usableW_mm * metersPerMm

                                        // Round down to nearest 100m for cleaner cuts
                                        val cleanCapacity = floor(capacityMeters / 100.0) * 100.0

                                        // 7. Generate Splits
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
                                        onStatusChange("Auto-Split: 2 Units excluded. ${newSplits.size} parts generated.")
                                    } else {
                                        onStatusChange("Selected slot is too small for graph.")
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57F17)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.CallSplit, null, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Auto Split", fontSize = 10.sp)
                        }
                    }

                    // Render Chips for Splits
                    if (generatedSplits.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        LazyColumn(modifier = Modifier.heightIn(max = 80.dp)) {
                            items(generatedSplits) { split ->
                                val isSelected = activeSplitIndex == split.index
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .background(if (isSelected) Color(0xFFFFE0B2) else Color.White, RoundedCornerShape(4.dp))
                                        .border(1.dp, if(isSelected) Color(0xFFF57F17) else Color.LightGray, RoundedCornerShape(4.dp))
                                        .clickable {
                                            activeSplitIndex = split.index
                                            currentStartCh = split.start
                                            currentEndCh = split.end
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Part ${split.index}", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFE65100))
                                    Spacer(Modifier.width(8.dp))
                                    Text("${split.start.toInt()}m to ${split.end.toInt()}m", fontSize = 11.sp)
                                    Spacer(Modifier.weight(1f))
                                    if(isSelected) Icon(Icons.Default.Check, null, tint = Color(0xFFF57F17), modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
                Divider()
            }

            // --- TOOLBAR ---
            Column(Modifier.fillMaxWidth().background(Color(0xFFF5F5F5)).padding(8.dp)) {

                // 1. General Sliders
                Text("Global Sizes:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Datum", fontSize = 9.sp)
                        NumberStepper(value = datumSize, onValueChange = { datumSize = it }, range = 5f..50f)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Axis", fontSize = 9.sp)
                        NumberStepper(value = axisSize, onValueChange = { axisSize = it }, range = 5f..50f)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Table", fontSize = 9.sp)
                        NumberStepper(value = tableTextSize, onValueChange = { tableTextSize = it }, range = 5f..50f)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Gap", fontSize = 9.sp)
                        NumberStepper(value = tableGap, onValueChange = { tableGap = it }, range = 0f..300f, step = 5f)
                    }
                }

                Divider(color = Color.LightGray, modifier = Modifier.padding(vertical = 4.dp))

                // 2. Selection & Edit Area
                Text("Select & Edit Element:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {

                    // DROPDOWN MENU
                    Box {
                        OutlinedButton(
                            onClick = { showItemDropdown = true },
                            modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            val label = when(val item = selectedItem) {
                                is InteractiveItem.RiverText -> if(item.isLeft) "Left River Text" else "Right River Text"
                                is InteractiveItem.BlueLine -> if(item.isLeft) "Left Blue Line" else "Right Blue Line"
                                is InteractiveItem.ChainageLabel -> "Chainage Label (CH:-)"
                                is InteractiveItem.LSecPreArrow -> "Left Elbow of Pre Monsoon"
                                is InteractiveItem.LSecPostArrow -> "Left Elbow of Post Monsoon"
                                is InteractiveItem.LSecPreText -> "L-section of Pre Monsoon"
                                is InteractiveItem.LSecPostText -> "L-section of Post Monsoon"
                                null -> "Select Item..."
                            }
                            Text(label, fontSize = 11.sp)
                            Icon(Icons.Default.ArrowDropDown, null)
                        }

                        DropdownMenu(expanded = showItemDropdown, onDismissRequest = { showItemDropdown = false }) {
                            DropdownMenuItem(text = { Text("None") }, onClick = { selectedItem = null; showItemDropdown = false })

                            if (selectedGraphType == "X-Section" && leftBankIndex != -1 && rightBankIndex != -1) {
                                Divider()
                                DropdownMenuItem(text = { Text("Left River Text") }, onClick = { selectedItem = InteractiveItem.RiverText(leftBankIndex, true); showItemDropdown = false })
                                DropdownMenuItem(text = { Text("Right River Text") }, onClick = { selectedItem = InteractiveItem.RiverText(rightBankIndex, false); showItemDropdown = false })
                                Divider()
                                DropdownMenuItem(text = { Text("Left Blue Line") }, onClick = { selectedItem = InteractiveItem.BlueLine(leftBankIndex, true); showItemDropdown = false })
                                DropdownMenuItem(text = { Text("Right Blue Line") }, onClick = { selectedItem = InteractiveItem.BlueLine(rightBankIndex, false); showItemDropdown = false })
                                Divider()
                                DropdownMenuItem(text = { Text("Chainage Label (CH:-)") }, onClick = { selectedItem = InteractiveItem.ChainageLabel; showItemDropdown = false })
                            }

                            // NEW: L-Section Options
                            if (selectedGraphType == "L-Section") {
                                Divider()
                                DropdownMenuItem(text = { Text("Left Elbow of Pre Monsoon") }, onClick = { selectedItem = InteractiveItem.LSecPreArrow; showItemDropdown = false })
                                DropdownMenuItem(text = { Text("L-section of Pre Monsoon") }, onClick = { selectedItem = InteractiveItem.LSecPreText; showItemDropdown = false })
                                Divider()
                                DropdownMenuItem(text = { Text("Left Elbow of Post Monsoon") }, onClick = { selectedItem = InteractiveItem.LSecPostArrow; showItemDropdown = false })
                                DropdownMenuItem(text = { Text("L-section of Post Monsoon") }, onClick = { selectedItem = InteractiveItem.LSecPostText; showItemDropdown = false })
                            }
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    // CONTEXTUAL STEPPER (Size)
                    if (selectedItem is InteractiveItem.RiverText) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Size:", fontSize = 10.sp)
                            Spacer(Modifier.width(4.dp))
                            NumberStepper(value = riverTextSize, onValueChange = { riverTextSize = it }, range = 5f..100f)
                        }
                    } else if (selectedItem is InteractiveItem.ChainageLabel) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Size:", fontSize = 10.sp)
                            Spacer(Modifier.width(4.dp))
                            NumberStepper(value = chainageTextSize, onValueChange = { chainageTextSize = it }, range = 5f..100f)
                        }
                    } else if (selectedItem is InteractiveItem.LSecPreArrow || selectedItem is InteractiveItem.LSecPostArrow || selectedItem is InteractiveItem.LSecPreText || selectedItem is InteractiveItem.LSecPostText) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Size:", fontSize = 10.sp)
                            Spacer(Modifier.width(4.dp))
                            NumberStepper(value = lSecItemSize, onValueChange = { lSecItemSize = it }, range = 5f..150f)
                        }
                    } else if (selectedItem != null) {
                        Text("(Drag on graph to move)", fontSize = 10.sp, color = Color.Gray, fontStyle = FontStyle.Italic)
                    }

                    Spacer(Modifier.weight(1f))

                    // DELETE BUTTON
                    IconButton(onClick = {
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
                    }, enabled = selectedItem != null, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, "Delete", tint = if(selectedItem!=null) Color.Red else Color.LightGray)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // --- ACTION BUTTONS (Reset and Add To Slot) ---
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    // Reset Button
                    TextButton(
                        onClick = {
                            riverOffsets.clear()
                            blueLineOffsets.clear()
                            chLabelOffset = Offset.Zero
                            deletedRivers.clear()
                            deletedBlueLines.clear()
                            isChLabelDeleted = false
                            selectedItem = null
                            tableGap = 0f
                            datumSize = 14f
                            tableTextSize = 14f
                            riverTextSize = 14f
                            chainageTextSize = 18f
                            lSecItemSize = 20f

                            if(selectedGraphType == "L-Section") {
                                currentStartCh = startCh
                                currentEndCh = endCh
                                generatedSplits = emptyList()
                                activeSplitIndex = -1
                            }
                        },
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reset All", fontSize = 11.sp)
                    }

                    // ADD TO SLOT BUTTON
                    Button(
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
                                    graphHScale = hS,
                                    graphVScale = vS,
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
                                    datumSize = datumSize,
                                    axisLabelSize = axisSize,
                                    tableTextSize = tableTextSize,
                                    tableGap = tableGap,
                                    riverTextSize = riverTextSize,
                                    chainageTextSize = chainageTextSize,
                                )
                                val finalElement = if(selectedGraphType == "L-Section") {
                                    newElement.copy(riverTextSize = lSecItemSize)
                                } else newElement

                                onAddToReport(finalElement)
                            } else {
                                onStatusChange("Select a Slot first!")
                            }
                        },
                        enabled = selectedPartitionSlot != null,
                        modifier = Modifier.height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if(selectedPartitionSlot != null) "Add to Slot >>" else "Select Slot First", fontSize = 12.sp)
                    }
                }
            }
            Divider()

            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color.White).padding(16.dp)) {
                if (activeGraphId == -100.0) {
                    Text("No Data Selected", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
                } else {
                    val hScale = if (selectedGraphType == "L-Section") lHScale else xHScale
                    val vScale = if (selectedGraphType == "L-Section") lVScale else xVScale

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
        }
    }
}

@Composable
fun NumberStepper(
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float> = 0f..100f,
    step: Float = 1f
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                .clickable {
                    val newValue = (value - step).coerceIn(range)
                    onValueChange(newValue)
                },
            contentAlignment = Alignment.Center
        ) {
            Text("-", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.width(4.dp))

        var text by remember(value) { mutableStateOf(String.format("%.1f", value).removeSuffix(".0")) }

        BasicTextField(
            value = text,
            onValueChange = { newText ->
                text = newText
                val floatVal = newText.toFloatOrNull()
                if (floatVal != null) {
                    onValueChange(floatVal.coerceIn(range))
                }
            },
            textStyle = TextStyle(fontSize = 11.sp, textAlign = TextAlign.Center),
            modifier = Modifier
                .width(30.dp)
                .background(Color.White, RoundedCornerShape(2.dp))
                .border(1.dp, Color.Gray, RoundedCornerShape(2.dp))
                .padding(vertical = 2.dp)
        )

        Spacer(Modifier.width(4.dp))

        Box(
            modifier = Modifier
                .size(20.dp)
                .background(Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                .clickable {
                    val newValue = (value + step).coerceIn(range)
                    onValueChange(newValue)
                },
            contentAlignment = Alignment.Center
        ) {
            Text("+", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}