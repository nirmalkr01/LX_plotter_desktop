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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Sealed class to identify what is selected
sealed class InteractiveItem {
    data class RiverText(val index: Int, val isLeft: Boolean) : InteractiveItem()
    data class BlueLine(val index: Int, val isLeft: Boolean) : InteractiveItem()
    data object ChainageLabel : InteractiveItem()
}

@Composable
fun ImagePanel(
    riverData: List<RiverPoint>,
    activeGraphId: Double,
    startCh: Double,
    endCh: Double,
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
    // Note: targetPaperSize is kept for compatibility but logic now fits to slot
    targetPaperSize: PaperSize = PaperSize.A4,
    targetIsLandscape: Boolean = true,
    onAddToReport: (ReportElement) -> Unit,
    onStatusChange: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val rawConfig = ReportConfig(
        marginTop = 2f, marginBottom = 2f, marginLeft = 2f, marginRight = 2f,
        showOuterBorder = false, showInnerBorder = false
    )

    // --- CALCULATE DATA VIEW FIRST ---
    val viewData = remember(riverData, activeGraphId, startCh, endCh, selectedGraphType) {
        if (activeGraphId == -1.0) getCurrentViewData(riverData, "L-Section", 0.0, startCh, endCh)
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

    // NEW: Specific sizes for interactive elements
    var riverTextSize by remember { mutableStateOf(14f) }
    var chainageTextSize by remember { mutableStateOf(18f) }

    // --- INTERACTIVE ELEMENT STATE ---
    // Offsets
    val riverOffsets = remember { mutableStateMapOf<Int, Offset>() }
    val blueLineOffsets = remember { mutableStateMapOf<Int, Offset>() }
    var chLabelOffset by remember { mutableStateOf(Offset.Zero) }

    // Deletions
    val deletedRivers = remember { mutableStateListOf<Int>() }
    val deletedBlueLines = remember { mutableStateListOf<Int>() }
    var isChLabelDeleted by remember { mutableStateOf(false) }

    // Selection
    var selectedItem by remember { mutableStateOf<InteractiveItem?>(null) }
    var showItemDropdown by remember { mutableStateOf(false) }

    // Reset selection if graph changes
    LaunchedEffect(activeGraphId) {
        selectedItem = null
    }

    Card(modifier = Modifier.fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Column {
            // Header
            Row(Modifier.fillMaxWidth().background(Color(0xFFE3F2FD)).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("2. Image View (Interactive)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)

                Button(onClick = {
                    if (activeGraphId != -100.0) {
                        pickFolder()?.let { folder ->
                            scope.launch {
                                onStatusChange("Saving Image...")
                                val h = if(selectedGraphType=="L-Section") lHScale else xHScale
                                val v = if(selectedGraphType=="L-Section") lVScale else xVScale
                                withContext(Dispatchers.IO) {
                                    saveRawGraph(viewData, File(folder, "Graph_${if(activeGraphId==-1.0)"LSec" else "CH${activeGraphId.toInt()}"}.png"), selectedGraphType, h, v, showPre, showPost, preColor, postColor, preDotted, postDotted, preWidth, postWidth, preShowPoints, postShowPoints)
                                }
                                onStatusChange("Image Saved!")
                            }
                        }
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary), contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(28.dp)) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save .PNG", fontSize = 10.sp)
                }
            }

            // --- TOOLBAR ---
            Column(Modifier.fillMaxWidth().background(Color(0xFFF5F5F5)).padding(8.dp)) {

                // 1. General Sliders
                Text("Global Sizes:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Datum", fontSize = 9.sp)
                        Slider(value = datumSize, onValueChange = { datumSize = it }, valueRange = 8f..30f, modifier = Modifier.width(60.dp))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Axis", fontSize = 9.sp)
                        Slider(value = axisSize, onValueChange = { axisSize = it }, valueRange = 8f..30f, modifier = Modifier.width(60.dp))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Table", fontSize = 9.sp)
                        Slider(value = tableTextSize, onValueChange = { tableTextSize = it }, valueRange = 8f..30f, modifier = Modifier.width(60.dp))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Gap", fontSize = 9.sp)
                        Slider(value = tableGap, onValueChange = { tableGap = it }, valueRange = 0f..150f, modifier = Modifier.width(60.dp))
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
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    // CONTEXTUAL SLIDER (Size)
                    if (selectedItem is InteractiveItem.RiverText) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Size:", fontSize = 10.sp)
                            Slider(value = riverTextSize, onValueChange = { riverTextSize = it }, valueRange = 8f..40f, modifier = Modifier.width(80.dp))
                        }
                    } else if (selectedItem is InteractiveItem.ChainageLabel) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Size:", fontSize = 10.sp)
                            Slider(value = chainageTextSize, onValueChange = { chainageTextSize = it }, valueRange = 8f..40f, modifier = Modifier.width(80.dp))
                        }
                    } else if (selectedItem != null) {
                        Text("(Drag on graph to move)", fontSize = 10.sp, color = Color.Gray, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    }

                    Spacer(Modifier.weight(1f))

                    // DELETE BUTTON
                    IconButton(onClick = {
                        when(val item = selectedItem) {
                            is InteractiveItem.RiverText -> if(!deletedRivers.contains(item.index)) deletedRivers.add(item.index)
                            is InteractiveItem.BlueLine -> if(!deletedBlueLines.contains(item.index)) deletedBlueLines.add(item.index)
                            is InteractiveItem.ChainageLabel -> isChLabelDeleted = true
                            null -> {}
                        }
                        selectedItem = null
                    }, enabled = selectedItem != null, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, "Delete", tint = if(selectedItem!=null) Color.Red else Color.LightGray)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // --- ACTION BUTTONS (Reset and Add To Slot) ---
                // Using a Column here to ensure enough vertical space and alignment
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
                        },
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reset All", fontSize = 11.sp)
                    }

                    // ADD TO SLOT BUTTON (Ensured Visibility)
                    Button(
                        onClick = {
                            if (activeGraphId != -100.0 && selectedPartitionSlot != null) {
                                // --- FIT TO SLOT LOGIC ---
                                // Instead of calculating real-world mm and overflowing,
                                // we force the element to adopt the Slot's dimensions exactly.
                                // The internal GraphPageCanvas will use 'scaleFit' to ensure the graph fits inside.

                                val hS = if(selectedGraphType=="L-Section") lHScale else xHScale
                                val vS = if(selectedGraphType=="L-Section") lVScale else xVScale

                                val newElement = ReportElement(
                                    type = ElementType.GRAPH_IMAGE,
                                    // Use PartitionSlot coordinates EXACTLY
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

                                    // Pass Interactive State
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
                                    chainageTextSize = chainageTextSize
                                )
                                onAddToReport(newElement)
                            } else {
                                onStatusChange("Select a Slot first!")
                            }
                        },
                        enabled = selectedPartitionSlot != null,
                        modifier = Modifier.height(40.dp), // Taller button
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

                        // --- INTERACTIVE STATE ---
                        datumSize = datumSize,
                        axisLabelSize = axisSize,
                        tableTextSize = tableTextSize,
                        tableGap = tableGap,
                        riverTextSize = riverTextSize,
                        chainageTextSize = chainageTextSize,

                        riverOffsets = riverOffsets,
                        blueLineOffsets = blueLineOffsets,
                        chLabelOffset = chLabelOffset,

                        deletedRiverIndices = deletedRivers,
                        deletedBlueLineIndices = deletedBlueLines,
                        isChLabelDeleted = isChLabelDeleted,

                        selectedItem = selectedItem,
                        onSelectItem = { /* Disabled tap selection */ },
                        onDragItem = { item, dragAmount ->
                            when(item) {
                                is InteractiveItem.RiverText -> riverOffsets[item.index] = (riverOffsets[item.index] ?: Offset.Zero) + dragAmount
                                is InteractiveItem.BlueLine -> blueLineOffsets[item.index] = (blueLineOffsets[item.index] ?: Offset.Zero) + dragAmount
                                is InteractiveItem.ChainageLabel -> chLabelOffset += dragAmount
                            }
                        }
                    )
                }
            }
        }
    }
}