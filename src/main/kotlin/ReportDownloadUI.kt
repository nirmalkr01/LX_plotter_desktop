import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class ReportConfig(
    val marginTop: Float = 10f, // MM
    val marginBottom: Float = 10f, // MM
    val marginLeft: Float = 10f, // MM
    val marginRight: Float = 10f, // MM
    val showOuterBorder: Boolean = true,
    val outerThickness: Float = 1f, // Px/Pt
    val outerColor: Color = Color.Black,
    val showInnerBorder: Boolean = false,
    val innerThickness: Float = 1f,
    val innerColor: Color = Color.Black,
    val borderGap: Float = 5f, // MM
    var legendType: String = "X-Section"
)

// Changed from Pixel Dimensions to Millimeter Dimensions
data class GraphDimensions(val widthMm: Double, val heightMm: Double)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDownloadScreen(
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
    onBack: () -> Unit,

    // --- STATES HOISTED FOR PERSISTENCE & UNDO ---
    riverOffsets: MutableMap<Int, Offset>,
    blueLineOffsets: MutableMap<Int, Offset>,
    chLabelOffset: Offset,
    onChLabelOffsetChange: (Offset) -> Unit,
    deletedRivers: MutableList<Int>,
    deletedBlueLines: MutableList<Int>,
    isChLabelDeleted: Boolean,
    onChLabelDeleteChange: (Boolean) -> Unit,
    onInteractionStart: () -> Unit, // Snapshot trigger for Undo
    onResetAllInteractive: () -> Unit
) {
    val density = LocalDensity.current
    var selectedGraphType by remember { mutableStateOf(initialGraphType) }
    val availableGraphs = remember(selectedGraphType, riverData) {
        if (selectedGraphType == "L-Section") listOf(-1.0)
        else riverData.map { it.chainage }.distinct().sorted()
    }

    var activeGraphId by remember { mutableStateOf(
        if(initialGraphType == "L-Section") -1.0
        else if(availableGraphs.isNotEmpty()) availableGraphs.first() else -100.0
    )}

    val reportItems = remember { mutableStateListOf<ReportPageItem>() }
    var statusMsg by remember { mutableStateOf("") }
    val pageElementData = remember { mutableStateMapOf<String, MutableList<ReportElement>>() }
    var activeFilePanelPageIndex by remember { mutableStateOf(0) }
    var selectedPartition by remember { mutableStateOf<PartitionSlot?>(null) }
    var isPartitionModeEnabled by remember { mutableStateOf(false) }

    // NEW: Persistent Map tracking generated grid layout rules per page ID
    val pagePartitionsData = remember { mutableStateMapOf<String, List<PartitionSlot>>() }

    // Panel Visibility State
    var isMiddlePanelVisible by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF0F0F0))) {
        Row(modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {

            // MIDDLE PANEL: IMAGE VIEW
            if (isMiddlePanelVisible) {
                Box(Modifier.weight(0.35f).fillMaxHeight()) {
                    Column {
                        ImagePanel(
                            riverData, activeGraphId,
                            onActiveGraphIdChange = { activeGraphId = it },
                            startCh, endCh,
                            selectedGraphType,
                            onGraphTypeChange = { selectedGraphType = it },
                            lHScale, lVScale, xHScale, xVScale,
                            showPre, showPost, preColor, postColor, preDotted, postDotted, preWidth, postWidth, preShowPoints, postShowPoints, showGrid,
                            selectedPartitionSlot = selectedPartition,
                            onAddToReport = { newElement ->
                                if (isPartitionModeEnabled && selectedPartition != null) {
                                    if (reportItems.isNotEmpty() && activeFilePanelPageIndex < reportItems.size) {
                                        val activePageId = reportItems[activeFilePanelPageIndex].id
                                        if (!pageElementData.containsKey(activePageId)) pageElementData[activePageId] = mutableStateListOf()

                                        // Extract table width offsets stored securely in riverOffsets
                                        val tLeft = newElement.riverOffsets[-30]?.x ?: 0f
                                        val tRight = newElement.riverOffsets[-31]?.x ?: 0f

                                        val chMm = newElement.chLabelOffset.y / (3.78f * density.density)

                                        // 1. Calculate Physical Dimensions in MM including added table offsets
                                        val graphDimsMm = calculateGraphDimensionsMM(
                                            newElement.graphData,
                                            newElement.graphType,
                                            newElement.graphHScale,
                                            newElement.graphVScale,
                                            tLeft,
                                            tRight,
                                            newElement.tableGap,
                                            chMm
                                        )

                                        // 2. Fetch the partitions mapped by FilePanel
                                        val currentPartitions = pagePartitionsData[activePageId]

                                        if (currentPartitions != null) {
                                            val slot = currentPartitions.find { it.id == selectedPartition!!.id }

                                            if (slot != null) {
                                                // INTELLIGENT MERGING EXECUTION: Returns newly adapted grids and precise position
                                                val placementResult = fitGraphIntoPartition(
                                                    graphWidthMm = graphDimsMm.widthMm.toFloat(),
                                                    graphHeightMm = graphDimsMm.heightMm.toFloat(),
                                                    selectedPartition = slot,
                                                    partitions = currentPartitions
                                                )

                                                // Update Active Page's Grid Layout visually
                                                pagePartitionsData[activePageId] = placementResult.partitions

                                                // Apply Absolute Placement Coordinates dynamically (Scale Untouched)
                                                val tightElement = newElement.copy(
                                                    xMm = placementResult.placement.xMm,
                                                    yMm = placementResult.placement.yMm,
                                                    widthMm = graphDimsMm.widthMm.toFloat(),
                                                    heightMm = graphDimsMm.heightMm.toFloat()
                                                )

                                                pageElementData[activePageId]?.add(tightElement)
                                                selectedPartition = null
                                                statusMsg = "Added Graph to Page ${activeFilePanelPageIndex + 1} (Merged if needed)"
                                            } else {
                                                statusMsg = "Selected slot no longer exists. Please re-select."
                                            }
                                        } else {
                                            statusMsg = "Grid data not initialized. Please view the page layout grid first."
                                        }
                                    } else {
                                        statusMsg = "No active page! Create a page in File View first."
                                    }
                                } else {
                                    statusMsg = "Enable Grid Mode & Select a Slot First!"
                                }
                            },
                            onStatusChange = { statusMsg = it },

                            // PASS THROUGH STATE TO IMAGE PANEL
                            riverOffsets = riverOffsets,
                            blueLineOffsets = blueLineOffsets,
                            chLabelOffset = chLabelOffset,
                            onChLabelOffsetChange = onChLabelOffsetChange,
                            deletedRivers = deletedRivers,
                            deletedBlueLines = deletedBlueLines,
                            isChLabelDeleted = isChLabelDeleted,
                            onChLabelDeleteChange = onChLabelDeleteChange,
                            onInteractionStart = onInteractionStart,
                            onResetAllInteractive = onResetAllInteractive
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
            }

            // RIGHT PANEL: REPORT DESIGNER (FilePanel)
            Box(Modifier.weight(0.5f).fillMaxHeight()) {
                FilePanel(
                    reportItems, lHScale, lVScale, xHScale, xVScale,
                    showPre, showPost, preColor, postColor, preDotted, postDotted, preWidth, postWidth, preShowPoints, postShowPoints, showGrid,
                    onStatusChange = { statusMsg = it },
                    externalPageElementData = pageElementData,
                    pagePartitionsData = pagePartitionsData, // PASS STATE DOWN TO UI
                    onActivePageChanged = { activeFilePanelPageIndex = it },
                    selectedPartitionSlot = selectedPartition,
                    onPartitionSelected = { selectedPartition = it },
                    activeGraphType = selectedGraphType,
                    isPartitionModeEnabled = isPartitionModeEnabled,
                    onPartitionModeToggle = { isPartitionModeEnabled = it; if(!it) selectedPartition = null },

                    // NEW PARAMS for Navigation & Toggles
                    onBack = onBack,
                    isMiddlePanelVisible = isMiddlePanelVisible,
                    onMiddlePanelToggle = { isMiddlePanelVisible = !isMiddlePanelVisible },
                    isLeftPanelVisible = false,
                    onLeftPanelToggle = {}
                )
            }
        }
    }
}

// --- LOGIC: Calculate Dimensions in Millimeters ---
fun calculateGraphDimensionsMM(
    data: List<RiverPoint>,
    type: String,
    hScale: Double,
    vScale: Double,
    tableLeftWidthOffset: Float = 0f,
    tableRightWidthOffset: Float = 0f,
    tableGap: Float = 0f,
    chLabelOffsetYMm: Float = 0f
): GraphDimensions {
    if (data.isEmpty()) return GraphDimensions(100.0, 100.0)

    val xVals = if(type=="L-Section") data.map{it.chainage} else data.map{it.distance}
    val minX = xVals.minOrNull() ?: 0.0
    val maxX = xVals.maxOrNull() ?: 10.0
    val yVals = data.map{it.preMonsoon} + data.map{it.postMonsoon}

    // Exact floor and ceil bounds without artificial extra 1.0 padding
    val minY = if(yVals.isNotEmpty()) floor(yVals.minOrNull()!!) else 0.0
    val maxY = if(yVals.isNotEmpty()) ceil(yVals.maxOrNull()!!) else 10.0

    // SCALE LOGIC: 1:100 means 100 units real = 1 unit paper.
    // 1m real = 1000mm. 1000mm / 100 = 10mm paper.
    val mmPerMeterX = 1000.0 / max(hScale, 1.0)
    val mmPerMeterY = 1000.0 / max(vScale, 1.0)

    // CAD-OPTIMIZED Layout Constants in MM
    val paddingLeftMm = 25.0 + tableLeftWidthOffset // Reduced from 45.0 to match CAD
    val rightPadMm = 5.0 + tableRightWidthOffset    // Reduced from 15.0
    val topPadMm = 5.0                              // Reduced from 15.0
    val tableRowHMm = 6.0                           // Reduced from 10.0 (3 rows = 18mm total)
    val baseFooterBufferMm = 5.0                    // Reduced from 15.0
    val extraFooterMm = max(0.0, chLabelOffsetYMm.toDouble())
    val footerBufferMm = baseFooterBufferMm + extraFooterMm

    val graphContentHMm = (maxY - minY) * mmPerMeterY
    val tableTotalHMm = 3 * tableRowHMm

    val totalWidthMm = paddingLeftMm + ((maxX - minX) * mmPerMeterX) + rightPadMm
    val totalHeightMm = topPadMm + graphContentHMm + tableGap + tableTotalHMm + footerBufferMm

    return GraphDimensions(totalWidthMm, totalHeightMm)
}

// Wrapper for compatibility if older code calls this
fun calculateGraphDimensions(data: List<RiverPoint>, type: String, hScale: Double, vScale: Double) = calculateGraphDimensionsMM(data, type, hScale, vScale)

@Composable
fun GraphPageCanvas(
    modifier: Modifier,
    data: List<RiverPoint>,
    type: String,
    paperSize: PaperSize,
    isLandscape: Boolean,
    hScale: Double, vScale: Double,
    config: ReportConfig,
    showPre: Boolean, showPost: Boolean,
    preColor: java.awt.Color, postColor: java.awt.Color,
    preWidth: Float, postWidth: Float,
    preDotted: Boolean, postDotted: Boolean,
    preShowPoints: Boolean, postShowPoints: Boolean,
    showGrid: Boolean,
    isRawView: Boolean = false,
    isTransparentOverlay: Boolean = false,
    pxPerMm: Float = 3.78f, // Default Screen Scale (approx 96 DPI)
    datumSize: Float = 14f,
    axisLabelSize: Float = 14f,
    tableTextSize: Float = 14f,
    tableGap: Float = 0f,
    riverTextSize: Float = 14f,
    chainageTextSize: Float = 18f,
    riverOffsets: Map<Int, Offset> = emptyMap(),
    blueLineOffsets: Map<Int, Offset> = emptyMap(),
    chLabelOffset: Offset = Offset.Zero,
    deletedRiverIndices: List<Int> = emptyList(),
    deletedBlueLineIndices: List<Int> = emptyList(),
    isChLabelDeleted: Boolean = false,
    selectedItem: InteractiveItem? = null,
    onSelectItem: (InteractiveItem?) -> Unit = {},
    onDragItem: (InteractiveItem, Offset) -> Unit = { _, _ -> },
    onInteractionStart: () -> Unit = {}
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // Calculate dynamic zoom factor based on the passed pxPerMm versus standard 96 DPI
    // Standard 96 DPI = 3.78 px/mm
    val zoomFactor = pxPerMm / (3.78f * density.density)

    // Visual Scaling for Stroke Width and Text
    val visualStrokeScale = zoomFactor.coerceAtLeast(1f) // Ensure lines don't vanish
    val scaledFontSize = { size: Float -> (size * zoomFactor).sp }

    // Compose Colors for L-Section
    val cPre = Color(preColor.red, preColor.green, preColor.blue)
    val cPost = Color(postColor.red, postColor.green, postColor.blue)

    // Remember the lambdas so pointerInput doesn't restart when offsets change
    val currentOnDragItem by rememberUpdatedState(onDragItem)
    val currentOnInteractionStart by rememberUpdatedState(onInteractionStart)

    Canvas(modifier = modifier.pointerInput(selectedItem) {
        detectDragGestures(
            onDragStart = {
                if (selectedItem != null) {
                    currentOnInteractionStart()
                }
            },
            onDrag = { change, dragAmount ->
                change.consume()
                if (selectedItem != null) {
                    currentOnDragItem(selectedItem, dragAmount)
                }
            }
        )
    }) {
        if (size.width <= 0 || size.height <= 0) return@Canvas

        // Render everything using this multiplier: [Value in MM] * [pxPerMm]
        val mmToPx = pxPerMm

        // Retrieve width changes directly from riverOffsets native map
        val tableLeftWidthOffset = riverOffsets[-30]?.x ?: 0f
        val tableRightWidthOffset = riverOffsets[-31]?.x ?: 0f

        // --- 2. PREPARE DATA ---
        val sortedData = data.sortedBy { if(type=="L-Section") it.chainage else it.distance }
            .distinctBy { if(type=="L-Section") it.chainage else it.distance }
        val xVals = if(type=="L-Section") sortedData.map{it.chainage} else sortedData.map{it.distance}
        val minX = xVals.minOrNull() ?: 0.0
        val maxX = xVals.maxOrNull() ?: 10.0
        val yVals = (if(showPre) sortedData.map{it.preMonsoon} else emptyList()) + (if(showPost) sortedData.map{it.postMonsoon} else emptyList())
        val minY = if(yVals.isNotEmpty()) floor(yVals.minOrNull()!!) else 0.0
        val maxY = if(yVals.isNotEmpty()) ceil(yVals.maxOrNull()!!) else 10.0

        val mmPerMeterX = 1000.0 / max(hScale, 1.0)
        val mmPerMeterY = 1000.0 / max(vScale, 1.0)

        // Layout Constants (MM)
        val padLeftMm = 25.0 + tableLeftWidthOffset
        val padTopMm = 5.0
        val rowHMm = 6.0
        val graphHMm = (maxY - minY) * mmPerMeterY
        val tableYStartMm = padTopMm + graphHMm + tableGap

        // Mapping Functions (Real Value -> MM -> Screen PX)
        fun mX(v: Double) = ((padLeftMm + (v - minX) * mmPerMeterX) * mmToPx).toFloat()
        fun mY(v: Double) = ((tableYStartMm - (v - minY) * mmPerMeterY) * mmToPx).toFloat()

        val totalDrawH = ((tableYStartMm + 3 * rowHMm + 5.0) * mmToPx).toFloat()

        // --- 3. DRAWING ---

        // Grid
        if (showGrid) {
            sortedData.forEach { p ->
                val x = mX(if(type=="L-Section") p.chainage else p.distance)
                if (x > (padLeftMm * mmToPx)) drawLine(Color.LightGray, Offset(x, 0f), Offset(x, totalDrawH), strokeWidth = 1f * visualStrokeScale)
            }
        }

        val dropColor = Color.LightGray.copy(alpha=0.6f)
        val count = sortedData.size
        sortedData.forEachIndexed { index, p ->
            val x = mX(if(type=="L-Section") p.chainage else p.distance)
            val isBank = type == "X-Section" && count > 1 && (index == 1 || index == count - 2)
            val isLineSelected = selectedItem is InteractiveItem.BlueLine && (selectedItem as InteractiveItem.BlueLine).index == index
            val baseLineColor = if (isBank) Color.Blue else dropColor
            val lineColor = if(isLineSelected) Color.Red else baseLineColor

            if (x >= (padLeftMm * mmToPx) - 1f) {
                // Base Lines
                if (showPre) drawLine(dropColor, Offset(x, mY(p.preMonsoon)), Offset(x, (tableYStartMm * mmToPx).toFloat()), strokeWidth = 1f * visualStrokeScale)
                if (showPost) drawLine(dropColor, Offset(x, mY(p.postMonsoon)), Offset(x, (tableYStartMm * mmToPx).toFloat()), strokeWidth = 1f * visualStrokeScale)

                // Active Lines (Blue/Red)
                if(!deletedBlueLineIndices.contains(index)) {
                    val lineOffset = blueLineOffsets[index] ?: Offset.Zero
                    // Apply visual scale to offsets to ensure they stick to the point visually
                    val scaledOffset = Offset(lineOffset.x * zoomFactor, lineOffset.y * zoomFactor)

                    val lineX = x + scaledOffset.x
                    if (showPre) drawLine(lineColor, Offset(lineX, mY(p.preMonsoon) + scaledOffset.y), Offset(lineX, (tableYStartMm * mmToPx).toFloat()), strokeWidth = (if(isLineSelected) 3f else 1f) * visualStrokeScale)
                    if (showPost) drawLine(lineColor, Offset(lineX, mY(p.postMonsoon) + scaledOffset.y), Offset(lineX, (tableYStartMm * mmToPx).toFloat()), strokeWidth = (if(isLineSelected) 3f else 1f) * visualStrokeScale)
                }

                if (isBank && !deletedRiverIndices.contains(index)) {
                    val baseTextY = mY(maxY) - (10f * visualStrokeScale)
                    val manualOffset = riverOffsets[index] ?: Offset.Zero
                    val scaledOffset = Offset(manualOffset.x * zoomFactor, manualOffset.y * zoomFactor)

                    val drawX = x + scaledOffset.x
                    val drawY = baseTextY + scaledOffset.y
                    val isTextSelected = selectedItem is InteractiveItem.RiverText && (selectedItem as InteractiveItem.RiverText).index == index
                    val textColor = if(isTextSelected) Color.Red else Color.Black
                    val riverLayout = textMeasurer.measure("RIVER", style = TextStyle(fontSize = scaledFontSize(riverTextSize), color = textColor, fontWeight = FontWeight.Bold))
                    rotate(-90f, pivot = Offset(drawX, drawY)) { drawText(riverLayout, topLeft = Offset(drawX - riverLayout.size.width / 2, drawY)) }
                }
            }
        }

        // Data Series (Visually Scaled)
        fun drawSeries(getter: (RiverPoint) -> Double, awtColor: java.awt.Color, isDotted: Boolean, width: Float, showPoints: Boolean) {
            val color = Color(awtColor.red, awtColor.green, awtColor.blue)
            val path = Path()
            var first = true
            sortedData.forEach{p->
                val x = mX(if(type=="L-Section") p.chainage else p.distance)
                val y = mY(getter(p))
                if(first){ path.moveTo(x,y); first=false } else path.lineTo(x,y)

                // FIX: Only draw points if we are in RawView (Image Panel)
                // and the user has enabled them. This prevents dots on the final report page.
                if(isRawView && showPoints) drawCircle(color, radius = (width * 2f) * visualStrokeScale, center = Offset(x, y))
            }
            // Scale Dash Effect
            val dashLen = 10f * visualStrokeScale
            val gapLen = 10f * visualStrokeScale
            val effect = if(isDotted) PathEffect.dashPathEffect(floatArrayOf(dashLen, gapLen), 0f) else null

            // Visual Width = Base Width * Zoom
            val visualWidth = width * 2f * visualStrokeScale

            drawPath(path, color, style = Stroke(width = visualWidth, pathEffect = effect))
        }
        if(showPre) drawSeries({it.preMonsoon}, preColor, preDotted, preWidth, preShowPoints)
        if(showPost) drawSeries({it.postMonsoon}, postColor, postDotted, postWidth, postShowPoints)

        // L-Section Annotations
        if (type == "L-Section" && sortedData.isNotEmpty()) {
            val midIndex = sortedData.size / 2
            val midPoint = sortedData.getOrElse(midIndex) { sortedData.first() }
            val defX = mX(midPoint.chainage)

            if (showPre) {
                val defY = mY(midPoint.preMonsoon)
                if (!deletedRiverIndices.contains(-10)) {
                    val userOffset = riverOffsets[-10] ?: Offset.Zero
                    val scaledOffset = Offset(userOffset.x * zoomFactor, userOffset.y * zoomFactor)
                    val tipX = defX + scaledOffset.x
                    val tipY = defY + scaledOffset.y
                    val size = (riverTextSize * 2.5f) * visualStrokeScale
                    val isSelected = selectedItem is InteractiveItem.LSecPreArrow
                    val color = if(isSelected) Color.Magenta else cPre
                    val path = Path().apply { moveTo(tipX + size, tipY - size); lineTo(tipX, tipY - size); lineTo(tipX, tipY) }
                    drawPath(path, color, style = Stroke(width = 3f * visualStrokeScale))
                    val headPath = Path().apply { moveTo(tipX, tipY); lineTo(tipX - size * 0.2f, tipY - size * 0.2f); lineTo(tipX + size * 0.2f, tipY - size * 0.2f); close() }
                    drawPath(headPath, color)
                }
                if (!deletedRiverIndices.contains(-11)) {
                    val userOffset = riverOffsets[-11] ?: Offset.Zero
                    val scaledOffset = Offset(userOffset.x * zoomFactor, userOffset.y * zoomFactor)
                    val size = (riverTextSize * 2.5f) * visualStrokeScale
                    val textX = defX + size + (5f * visualStrokeScale) + scaledOffset.x
                    val textY = defY - size - (10f * visualStrokeScale) + scaledOffset.y
                    val isSelected = selectedItem is InteractiveItem.LSecPreText
                    val textColor = if(isSelected) Color.Magenta else cPre
                    val txtLayout = textMeasurer.measure("L-section of Pre Monsoon", style = TextStyle(fontFamily = FontFamily.Serif, fontSize = scaledFontSize(riverTextSize), color = textColor, fontWeight = FontWeight.Bold))
                    drawText(txtLayout, topLeft = Offset(textX, textY))
                }
            }
            if (showPost) {
                val defY = mY(midPoint.postMonsoon)
                if (!deletedRiverIndices.contains(-20)) {
                    val userOffset = riverOffsets[-20] ?: Offset.Zero
                    val scaledOffset = Offset(userOffset.x * zoomFactor, userOffset.y * zoomFactor)
                    val tipX = defX + (50f * visualStrokeScale) + scaledOffset.x
                    val tipY = defY + scaledOffset.y
                    val size = (riverTextSize * 2.5f) * visualStrokeScale
                    val isSelected = selectedItem is InteractiveItem.LSecPostArrow
                    val color = if(isSelected) Color.Magenta else cPost
                    val path = Path().apply { moveTo(tipX + size, tipY - size); lineTo(tipX, tipY - size); lineTo(tipX, tipY) }
                    drawPath(path, color, style = Stroke(width = 3f * visualStrokeScale))
                    val headPath = Path().apply { moveTo(tipX, tipY); lineTo(tipX - size * 0.2f, tipY - size * 0.2f); lineTo(tipX + size * 0.2f, tipY - size * 0.2f); close() }
                    drawPath(headPath, color)
                }
                if (!deletedRiverIndices.contains(-21)) {
                    val userOffset = riverOffsets[-21] ?: Offset.Zero
                    val scaledOffset = Offset(userOffset.x * zoomFactor, userOffset.y * zoomFactor)
                    val size = (riverTextSize * 2.5f) * visualStrokeScale
                    val textX = defX + (50f * visualStrokeScale) + size + (5f * visualStrokeScale) + scaledOffset.x
                    val textY = defY - size - (10f * visualStrokeScale) + scaledOffset.y
                    val isSelected = selectedItem is InteractiveItem.LSecPostText
                    val textColor = if(isSelected) Color.Magenta else cPost
                    val txtLayout = textMeasurer.measure("L-section of Post Monsoon", style = TextStyle(fontFamily = FontFamily.Serif, fontSize = scaledFontSize(riverTextSize), color = textColor, fontWeight = FontWeight.Bold))
                    drawText(txtLayout, topLeft = Offset(textX, textY))
                }
            }
        }

        // --- FIXED TABLE RENDERING ORDER ---
        // 1. Wipe Axis Area
        val yAxisTop = mY(maxY)
        if (!isTransparentOverlay) {
            drawRect(Color.White, topLeft = Offset(0f, yAxisTop), size = Size((padLeftMm * mmToPx).toFloat(), (tableYStartMm * mmToPx).toFloat() - yAxisTop))
        }

        // 2. Draw Axis Lines & Ticks (on top of wipe)
        drawLine(Color.Black, Offset((padLeftMm * mmToPx).toFloat(), yAxisTop), Offset((padLeftMm * mmToPx).toFloat(), (tableYStartMm * mmToPx).toFloat()), strokeWidth = 2f * visualStrokeScale)

        for(i in 1..((maxY-minY).toInt())) {
            val yVal = minY + i
            val yPos = mY(yVal)
            if (yPos >= 0 && yPos <= (tableYStartMm * mmToPx).toFloat()) {
                drawLine(Color.Black, Offset((padLeftMm * mmToPx).toFloat() - (5f * visualStrokeScale), yPos), Offset((padLeftMm * mmToPx).toFloat(), yPos), strokeWidth = 1f * visualStrokeScale)
                val txt = String.format("%.1f", yVal)
                val layout = textMeasurer.measure(txt, style = TextStyle(fontSize = scaledFontSize(axisLabelSize), color = Color.Black))
                drawText(layout, topLeft = Offset((padLeftMm * mmToPx).toFloat() - (8f * visualStrokeScale) - layout.size.width, yPos - layout.size.height / 2))
            }
        }
        val datumY = mY(minY)
        if (datumY > 0 && datumY < totalDrawH) {
            val txt = "DATUM=${minY}"
            val layout = textMeasurer.measure(txt, style = TextStyle(fontSize = scaledFontSize(datumSize)))
            // Multiply the pixel offset by zoomFactor so it doesn't visually shift away when zooming
            drawText(layout, topLeft = Offset((padLeftMm * mmToPx).toFloat() - (8f * visualStrokeScale) - layout.size.width, datumY - (25f * zoomFactor)))
        }

        // 3. Draw Table Borders
        val xEnd = mX(maxX) + (5.0 * mmToPx).toFloat() + (tableRightWidthOffset * mmToPx) // Extension pad
        val yTableStart = (tableYStartMm * mmToPx).toFloat()
        val rowH = (rowHMm * mmToPx).toFloat()

        drawLine(Color.Black, Offset(0f, yTableStart), Offset(xEnd, yTableStart), strokeWidth = 2f * visualStrokeScale)
        for(i in 1..3) drawLine(Color.Black, Offset(0f, yTableStart + i * rowH), Offset(xEnd, yTableStart + i * rowH), strokeWidth = 2f * visualStrokeScale)

        // Vertical lines
        drawLine(Color.Black, Offset(0f, yTableStart), Offset(0f, yTableStart + 3 * rowH), strokeWidth = 2f * visualStrokeScale)
        drawLine(Color.Black, Offset((padLeftMm * mmToPx).toFloat(), yTableStart), Offset((padLeftMm * mmToPx).toFloat(), yTableStart + 3 * rowH), strokeWidth = 2f * visualStrokeScale)
        drawLine(Color.Black, Offset(xEnd, yTableStart), Offset(xEnd, yTableStart + 3 * rowH), strokeWidth = 2f * visualStrokeScale)

        // Draw Interactive Drag Handle for Table Right Edge
        if (selectedItem is InteractiveItem.TableRightBoundary) {
            drawCircle(Color.Blue, radius = 6f * visualStrokeScale, center = Offset(xEnd, yTableStart + 1.5f * rowH))
        }

        // Draw Interactive Drag Handle for Table Left Edge (Header Width)
        if (selectedItem is InteractiveItem.TableLeftBoundary) {
            val leftX = (padLeftMm * mmToPx).toFloat()
            drawCircle(Color.Blue, radius = 6f * visualStrokeScale, center = Offset(leftX, yTableStart + 1.5f * rowH))
        }

        // Table Content
        sortedData.forEachIndexed { index, p ->
            val x = mX(if(type=="L-Section") p.chainage else p.distance)
            val vals = listOf(String.format("%.3f", p.postMonsoon), String.format("%.3f", p.preMonsoon), String.format("%.1f", if(type=="L-Section") p.chainage else p.distance))
            val colors = listOf(Color(postColor.red, postColor.green, postColor.blue), Color(preColor.red, preColor.green, preColor.blue), Color.Black)
            vals.forEachIndexed { i, txt ->
                val cellCenterY = yTableStart + i * rowH + rowH/2
                val layoutResult = textMeasurer.measure(txt, style = TextStyle(fontSize = scaledFontSize(tableTextSize), color = colors[i]))
                val visualX = if (index == 0) x + 8f * visualStrokeScale else x
                rotate(-90f, pivot = Offset(visualX, cellCenterY)) { drawText(layoutResult, topLeft = Offset(visualX - layoutResult.size.width/2, cellCenterY - layoutResult.size.height/2)) }
            }
        }

        // Row Headers
        val labels = if(type == "L-Section") listOf("POST MONSOON RL", "PRE MONSOON RL", "Chainage in mt.") else listOf("POST RL", "PRE RL", "OFFSET")
        labels.forEachIndexed { i, l ->
            val y = yTableStart + i * rowH
            val cx = (padLeftMm * mmToPx).toFloat() / 2
            val cy = y + rowH/2
            val textLayout = textMeasurer.measure(l, style = TextStyle(fontSize = scaledFontSize(tableTextSize), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
            drawText(textLayout, topLeft = Offset(cx - textLayout.size.width/2, cy - textLayout.size.height/2))
        }

        if (type == "X-Section" && sortedData.isNotEmpty() && !isChLabelDeleted) {
            val chainageVal = sortedData.first().chainage
            val chLabel = "CH:-${String.format("%.1f", chainageVal)}"
            val footerY = yTableStart + 3 * rowH + (10f * visualStrokeScale)
            val tableCenter = xEnd / 2
            val isChSelected = selectedItem is InteractiveItem.ChainageLabel
            val chColor = if(isChSelected) Color.Red else Color.Black
            val chLayout = textMeasurer.measure(chLabel, style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = scaledFontSize(chainageTextSize), color = chColor))
            val drawX = tableCenter - chLayout.size.width/2 + (chLabelOffset.x * zoomFactor)
            val drawY = footerY + (chLabelOffset.y * zoomFactor)
            drawText(chLayout, topLeft = Offset(drawX, drawY))
        }
    }
}