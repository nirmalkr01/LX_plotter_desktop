// FILE: D:\LX_plotter_desktop\src\main\kotlin\FilePanel.kt
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class TextAnnotation(
    var id: String = UUID.randomUUID().toString(),
    var text: String = "",
    // --- COORDINATES IN MILLIMETERS (MM) ---
    var xMm: Float = 10f,
    var yMm: Float = 10f,
    var widthMm: Float = 50f,
    var heightMm: Float = 10f,

    var color: Color = Color.Black,
    var fontSize: Float = 12f,
    var isBold: Boolean = false,
    var isItalic: Boolean = false,
    var isUnderline: Boolean = false,
    var textAlign: TextAlign = TextAlign.Left,
    var fontFamily: String = "Arial",
    var displayId: String? = null
)

fun getFontFamily(name: String): FontFamily {
    return when(name) {
        "Times New Roman" -> FontFamily.Serif
        "Courier New" -> FontFamily.Monospace
        "Verdana" -> FontFamily.SansSerif
        "Georgia" -> FontFamily.Serif
        "Impact" -> FontFamily.SansSerif
        else -> FontFamily.Default
    }
}

@Composable
fun EditablePageContainer(
    item: ReportPageItem,
    pageNumber: Int,
    totalPageCount: Int,
    annexureValue: String,
    b1Text: String,
    paperSize: PaperSize,
    layoutType: PageLayoutType,
    isLandscape: Boolean,
    config: ReportConfig,
    zoomLevel: Float,
    textAnnotations: MutableList<TextAnnotation>,
    elements: MutableList<ReportElement>,
    hScale: Double, vScale: Double,
    showPre: Boolean, showPost: Boolean,
    preColor: java.awt.Color, postColor: java.awt.Color,
    preDotted: Boolean, postDotted: Boolean,
    preWidth: Float, postWidth: Float,
    preShowPoints: Boolean, postShowPoints: Boolean,
    showGrid: Boolean,
    showPageNumber: Boolean,
    currentTextColor: Color, currentTextSize: Float,
    isBold: Boolean, isItalic: Boolean, isUnderline: Boolean,
    currentTextAlign: TextAlign,
    currentFontFamily: String,
    isTextToolActive: Boolean,

    // SELECTION PARAMS
    isSelectToolActive: Boolean = false,
    selectedAnnotationId: String?,
    selectedElementId: String?,
    multiSelectedAnnotationIds: MutableList<String> = mutableListOf(),
    multiSelectedElementIds: MutableList<String> = mutableListOf(),

    canPaste: Boolean = false,
    onAnnotationSelected: (String?) -> Unit,
    onElementSelected: (String?) -> Unit,
    onPageSelected: () -> Unit,
    onToolUsed: () -> Unit,
    onGraphPosChange: (Float, Float) -> Unit,
    onDeleteAnnotation: (String) -> Unit,
    onDeleteElement: (String) -> Unit,
    onCopyAnnotation: (TextAnnotation) -> Unit,
    onPasteAnnotation: () -> Unit,

    // NEW CONTEXT MENU ACTIONS
    onContextMenuCopy: () -> Unit = {},
    onContextMenuPaste: () -> Unit = {},
    onContextMenuDelete: () -> Unit = {},
    hasClipboardContent: Boolean = false,

    hiddenAnnotationId: String? = null,
    hiddenElementId: String? = null,
    onGlobalDragStart: (TextAnnotation, Offset) -> Unit,
    onGlobalElementDragStart: (ReportElement, Offset) -> Unit,
    onGroupDragStart: (Offset, Rect) -> Unit,
    onGlobalDrag: (Offset) -> Unit,
    onGlobalDragEnd: () -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // --- 1. CALCULATE SCALE (MM -> SCREEN PX) ---
    // Standard screen is approx 96 DPI => 1 inch = 25.4mm = 96px => 1mm = 3.78px
    // We scale this base density by the User's Zoom Level
    val pxPerMm = 3.78f * density.density * (zoomLevel / 100f)

    // Calculate Paper Size in Screen Pixels
    val widthMm = if (isLandscape) paperSize.heightMm else paperSize.widthMm
    val heightMm = if (isLandscape) paperSize.widthMm else paperSize.heightMm

    val paperW_px = widthMm * pxPerMm
    val paperH_px = heightMm * pxPerMm

    val currentPaperW by rememberUpdatedState(paperW_px)
    val currentPaperH by rememberUpdatedState(paperH_px)

    // Selection Box State
    var selectionRect by remember { mutableStateOf<SelectionRect?>(null) }

    BoxWithConstraints(
        modifier = Modifier
            .size(with(density) { paperW_px.toDp() }, with(density) { paperH_px.toDp() })
            .background(Color.White)
            // INPUT HANDLING: MARQUEE SELECTION
            .pointerInput(isSelectToolActive) {
                if (isSelectToolActive) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            selectionRect = SelectionRect(offset, offset)
                            multiSelectedAnnotationIds.clear()
                            multiSelectedElementIds.clear()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val start = selectionRect?.start ?: change.position
                            val end = change.position
                            selectionRect = SelectionRect(start, end)
                        },
                        onDragEnd = {
                            if (selectionRect != null) {
                                val rect = selectionRect!!.rect
                                textAnnotations.forEach { txt ->
                                    if(isItemInSelection(rect, txt.xMm, txt.yMm, txt.widthMm, txt.heightMm, pxPerMm)) {
                                        if(!multiSelectedAnnotationIds.contains(txt.id)) multiSelectedAnnotationIds.add(txt.id)
                                    }
                                }
                                elements.forEach { el ->
                                    if(isItemInSelection(rect, el.xMm, el.yMm, el.widthMm, el.heightMm, pxPerMm)) {
                                        if(!multiSelectedElementIds.contains(el.id)) multiSelectedElementIds.add(el.id)
                                    }
                                }
                                selectionRect = null
                            }
                        }
                    )
                }
            }
            // INPUT HANDLING: Tap for Tool / Focus
            .pointerInput(isTextToolActive, currentTextColor, currentTextSize, isBold, isItalic, isUnderline, currentTextAlign, currentFontFamily) {
                detectTapGestures(
                    onTap = { offset ->
                        onPageSelected()
                        if (isTextToolActive) {
                            // CONVERT CLICK (PX) -> MM
                            val safeX_mm = offset.x / pxPerMm
                            val safeY_mm = offset.y / pxPerMm
                            val nextIdNum = textAnnotations.size + 1
                            val newTxt = TextAnnotation(
                                xMm = safeX_mm,
                                yMm = safeY_mm,
                                widthMm = 50f, // Default Width 50mm
                                heightMm = 10f, // Default Height 10mm
                                color = currentTextColor,
                                fontSize = currentTextSize,
                                isBold = isBold,
                                isItalic = isItalic,
                                isUnderline = isUnderline,
                                textAlign = currentTextAlign,
                                fontFamily = currentFontFamily,
                                displayId = "ID-$nextIdNum"
                            )
                            textAnnotations.add(newTxt)
                            onAnnotationSelected(newTxt.id)
                            onToolUsed()
                        }
                    }
                )
            }
    ) {
        // --- LAYER 1: LAYOUT & BORDERS ---
        Canvas(modifier = Modifier.fillMaxSize()) {
            val mLeft = config.marginLeft * pxPerMm
            val mTop = config.marginTop * pxPerMm
            val mRight = config.marginRight * pxPerMm
            val mBottom = config.marginBottom * pxPerMm
            val gap = config.borderGap * pxPerMm

            if (config.showOuterBorder) {
                drawRect(Color(config.outerColor.red, config.outerColor.green, config.outerColor.blue),
                    topLeft = Offset(mLeft, mTop),
                    size = Size(paperW_px - mLeft - mRight, paperH_px - mTop - mBottom),
                    style = Stroke(width = config.outerThickness))
            }

            if (config.showInnerBorder && config.showOuterBorder) {
                val iL = mLeft + gap
                val iT = mTop + gap
                val iR = mRight + gap
                val iB = mBottom + gap
                drawRect(Color(config.innerColor.red, config.innerColor.green, config.innerColor.blue),
                    topLeft = Offset(iL, iT),
                    size = Size(paperW_px - mLeft - mRight - 2*gap, paperH_px - mTop - mBottom - 2*gap),
                    style = Stroke(width = config.innerThickness))
            }

            val layoutML = if (config.showInnerBorder) mLeft + gap else mLeft
            val layoutMT = if (config.showInnerBorder) mTop + gap else mTop
            val layoutMR = if (config.showInnerBorder) mRight + gap else mRight
            val layoutMB = if (config.showInnerBorder) mBottom + gap else mBottom

            drawPageLayout(
                type = layoutType,
                paperWidthPx = paperW_px,
                paperHeightPx = paperH_px,
                marginLeftPx = layoutML,
                marginTopPx = layoutMT,
                marginRightPx = layoutMR,
                marginBottomPx = layoutMB,
                textMeasurer = textMeasurer,
                borderColor = Color(config.outerColor.red, config.outerColor.green, config.outerColor.blue),
                borderThickness = config.outerThickness,
                pageNumber = pageNumber,
                totalPageCount = totalPageCount,
                annexureValue = annexureValue,
                b1Text = b1Text,
                legendType = config.legendType,
                preColor = Color(preColor.red, preColor.green, preColor.blue),
                postColor = Color(postColor.red, postColor.green, postColor.blue),
                preDotted = preDotted,
                postDotted = postDotted,
                hScale = hScale,
                vScale = vScale,
                pxPerMm = pxPerMm // Pass scale factor
            )

            if (layoutType == PageLayoutType.BLANK && showPageNumber) {
                val pageStr = "$pageNumber"
                val textLayout = textMeasurer.measure(pageStr, style = TextStyle(fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold))
                drawText(textLayout, topLeft = Offset(paperW_px - textLayout.size.width - 20f, paperH_px - 25f))
            }

            // DRAW SELECTION BOX
            if (selectionRect != null) {
                drawRect(
                    color = Color.Blue.copy(alpha = 0.2f),
                    topLeft = selectionRect!!.topLeft,
                    size = selectionRect!!.size
                )
                drawRect(
                    color = Color.Blue,
                    topLeft = selectionRect!!.topLeft,
                    size = selectionRect!!.size,
                    style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                )
            }
        }

        // --- LAYER 2: BACKGROUND GRAPH (Legacy Panning Logic) ---
        // Note: For legacy compatibility, we treat xOffset/yOffset as raw pixels but scale them by density
        // Ideally, this should also move to MM, but we'll adapt it to the new Container.
        val contentLeft = if(config.showInnerBorder) (config.marginLeft + config.borderGap) * pxPerMm else config.marginLeft * pxPerMm
        val contentTop = if(config.showInnerBorder) (config.marginTop + config.borderGap) * pxPerMm else config.marginTop * pxPerMm
        val contentRight = if(config.showInnerBorder) (config.marginRight + config.borderGap) * pxPerMm else config.marginRight * pxPerMm
        val contentBottom = if(config.showInnerBorder) (config.marginBottom + config.borderGap) * pxPerMm else config.marginBottom * pxPerMm

        val contentW_px = paperW_px - contentLeft - contentRight
        val contentH_px = paperH_px - contentTop - contentBottom

        Box(
            modifier = Modifier
                .padding(start = with(density){ contentLeft.toDp() }, top = with(density){ contentTop.toDp() })
                .size(with(density){ contentW_px.toDp() }, with(density){ contentH_px.toDp() })
                .clipToBounds()
        ) {
            if(item.data.isNotEmpty()){
                Box(
                    modifier = Modifier
                        .offset { IntOffset(item.xOffset.roundToInt(), item.yOffset.roundToInt()) }
                        // Background graph uses fillMaxSize logic inside GraphPageCanvas, so size here matters less
                        // providing we give it enough space or use explicit size.
                        // Using a large generic size so it doesn't clip immediately.
                        .size(with(density){ paperW_px.toDp() }, with(density){ paperH_px.toDp() })
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                onGraphPosChange(item.xOffset + dragAmount.x, item.yOffset + dragAmount.y)
                            }
                        }
                ) {
                    GraphPageCanvas(Modifier.fillMaxSize(), item.data, item.type, PaperSize.A4, true, hScale, vScale, config, showPre, showPost, preColor, postColor, preWidth, postWidth, preDotted, postDotted, preShowPoints, postShowPoints, showGrid, isTransparentOverlay = true, pxPerMm = pxPerMm)
                }
            }
        }

        // --- GROUP SELECTION BOX LOGIC ---
        if (isSelectToolActive && (multiSelectedAnnotationIds.isNotEmpty() || multiSelectedElementIds.isNotEmpty())) {
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE
            var hasSelection = false

            textAnnotations.forEach { txt ->
                if (multiSelectedAnnotationIds.contains(txt.id)) {
                    val x = txt.xMm * pxPerMm
                    val y = txt.yMm * pxPerMm
                    val w = txt.widthMm * pxPerMm
                    val h = txt.heightMm * pxPerMm
                    minX = min(minX, x)
                    minY = min(minY, y)
                    maxX = max(maxX, x + w)
                    maxY = max(maxY, y + h)
                    hasSelection = true
                }
            }
            elements.forEach { el ->
                if (multiSelectedElementIds.contains(el.id)) {
                    val x = el.xMm * pxPerMm
                    val y = el.yMm * pxPerMm
                    val w = el.widthMm * pxPerMm
                    val h = el.heightMm * pxPerMm
                    minX = min(minX, x)
                    minY = min(minY, y)
                    maxX = max(maxX, x + w)
                    maxY = max(maxY, y + h)
                    hasSelection = true
                }
            }

            if (hasSelection) {
                val groupRect = Rect(minX, minY, maxX, maxY)
                var showGroupMenu by remember { mutableStateOf(false) }
                var absolutePosition by remember { mutableStateOf(Offset.Zero) }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(minX.roundToInt(), minY.roundToInt()) }
                        .size(with(density) { (maxX - minX).toDp() }, with(density) { (maxY - minY).toDp() })
                        .border(1.dp, Color.Blue, RoundedCornerShape(4.dp))
                        .drawBehind {
                            drawRect(
                                color = Color.Blue,
                                style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                            )
                        }
                        .onGloballyPositioned { absolutePosition = it.positionInWindow() }
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.buttons.isSecondaryPressed && event.changes.any { it.pressed }) {
                                        event.changes.forEach { it.consume() }
                                        showGroupMenu = true
                                    }
                                }
                            }
                        }
                ) {
                    DropdownMenu(expanded = showGroupMenu, onDismissRequest = { showGroupMenu = false }) {
                        DropdownMenuItem(text = { Text("Copy Group") }, onClick = { onContextMenuCopy(); showGroupMenu = false })
                        if(hasClipboardContent) {
                            DropdownMenuItem(text = { Text("Paste") }, onClick = { onContextMenuPaste(); showGroupMenu = false })
                        }
                        DropdownMenuItem(text = { Text("Delete Group", color = Color.Red) }, onClick = { onContextMenuDelete(); showGroupMenu = false })
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 20.dp)
                            .size(24.dp)
                            .background(Color.White, CircleShape)
                            .border(1.dp, Color.Blue, CircleShape)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { onGroupDragStart(absolutePosition, groupRect) },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        onGlobalDrag(dragAmount)
                                    },
                                    onDragEnd = { onGlobalDragEnd() }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.DragIndicator, null, tint = Color.Blue, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        fun shouldShowDragHandle(itemId: String, isSelected: Boolean): Boolean {
            if (!isSelected) return false
            if (isSelectToolActive && (multiSelectedAnnotationIds.size + multiSelectedElementIds.size > 1)) return false
            return true
        }

        // --- LAYER 3: ELEMENTS (SHAPES & FLOATING GRAPHS) ---
        elements.forEachIndexed { index, el ->
            key(el.id) {
                val isSelected = el.id == selectedElementId || (isSelectToolActive && multiSelectedElementIds.contains(el.id))
                var showContextMenu by remember { mutableStateOf(false) }

                // CONVERT MM -> SCREEN PX FOR DISPLAY
                val xPx = el.xMm * pxPerMm
                val yPx = el.yMm * pxPerMm
                val wPx = el.widthMm * pxPerMm
                val hPx = el.heightMm * pxPerMm

                var absolutePosition by remember { mutableStateOf(Offset.Zero) }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
                        .size(with(density) { wPx.toDp() }, with(density) { hPx.toDp() })
                        .onGloballyPositioned { layoutCoordinates -> absolutePosition = layoutCoordinates.positionInWindow() }
                        .alpha(if (hiddenElementId == el.id && !isSelectToolActive) 0f else 1f)
                        .drawBehind {
                            if (isSelected) {
                                val stroke = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f))
                                drawRect(color = Color.LightGray, style = stroke)
                            }
                        }
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.buttons.isSecondaryPressed && event.changes.any { it.pressed }) {
                                        event.changes.forEach { it.consume() }
                                        onElementSelected(el.id)
                                        showContextMenu = true
                                    }
                                }
                            }
                        }
                        .pointerInput(Unit) { detectTapGestures(onTap = {
                            if(isSelectToolActive) {
                                if(!multiSelectedElementIds.contains(el.id)) multiSelectedElementIds.add(el.id)
                            } else {
                                onElementSelected(el.id)
                            }
                        }) }
                ) {
                    if (el.type == ElementType.GRAPH_IMAGE) {
                        // Render Floating Graph
                        val awtPreColor = java.awt.Color(el.graphPreColor.red, el.graphPreColor.green, el.graphPreColor.blue)
                        val awtPostColor = java.awt.Color(el.graphPostColor.red, el.graphPostColor.green, el.graphPostColor.blue)

                        // Pass pxPerMm for correct internal scaling
                        GraphPageCanvas(
                            modifier = Modifier.fillMaxSize(),
                            data = el.graphData,
                            type = el.graphType,
                            paperSize = paperSize, // Uses parent paper size context
                            isLandscape = isLandscape,
                            hScale = el.graphHScale,
                            vScale = el.graphVScale,
                            config = ReportConfig(), // No border for inner element
                            showPre = el.graphShowPre,
                            showPost = el.graphShowPost,
                            preColor = awtPreColor,
                            postColor = awtPostColor,
                            preWidth = el.graphPreWidth,
                            postWidth = el.graphPostWidth,
                            preDotted = el.graphPreDotted,
                            postDotted = el.graphPostDotted,
                            preShowPoints = true,
                            postShowPoints = true,
                            showGrid = el.graphShowGrid,
                            isTransparentOverlay = true,
                            pxPerMm = pxPerMm, // VITAL: Ensures scale matches container

                            // IMPORTANT: Pass the interactive state stored in ReportElement
                            riverOffsets = el.riverOffsets,
                            blueLineOffsets = el.blueLineOffsets,
                            chLabelOffset = el.chLabelOffset,
                            deletedRiverIndices = el.deletedRiverIndices,
                            deletedBlueLineIndices = el.deletedBlueLineIndices,
                            isChLabelDeleted = el.isChLabelDeleted,
                            datumSize = el.datumSize,
                            axisLabelSize = el.axisLabelSize,
                            tableTextSize = el.tableTextSize,
                            tableGap = el.tableGap,
                            riverTextSize = el.riverTextSize,
                            chainageTextSize = el.chainageTextSize
                        )
                    } else {
                        // Render Vector Shape
                        ElementRenderer(el)
                    }

                    DropdownMenu(expanded = showContextMenu, onDismissRequest = { showContextMenu = false }) {
                        if (isSelectToolActive || selectedElementId != null) {
                            DropdownMenuItem(text = { Text("Copy") }, onClick = { onContextMenuCopy(); showContextMenu = false })
                        }
                        if (hasClipboardContent) {
                            DropdownMenuItem(text = { Text("Paste") }, onClick = { onContextMenuPaste(); showContextMenu = false })
                        }
                        DropdownMenuItem(text = { Text("Delete", color = Color.Red) }, onClick = { onContextMenuDelete(); showContextMenu = false })
                    }

                    if (shouldShowDragHandle(el.id, isSelected)) {
                        Box(modifier = Modifier.align(Alignment.BottomCenter).offset(y = 20.dp).size(18.dp).background(Color(0xFFF0F0F0), CircleShape).clip(CircleShape).border(0.5.dp, Color.LightGray, CircleShape).pointerInput(Unit) {
                            detectDragGestures(onDragStart = { onGlobalElementDragStart(el, absolutePosition) },
                                onDrag = { change, dragAmount ->
                                    change.consume();
                                    // Update Drag in MM
                                    el.xMm += dragAmount.x / pxPerMm
                                    el.yMm += dragAmount.y / pxPerMm
                                    onGlobalDrag(dragAmount)
                                },
                                onDragEnd = { onGlobalDragEnd() })
                        }, contentAlignment = Alignment.Center) { Icon(Icons.Default.DragIndicator, null, tint = Color.Gray, modifier = Modifier.size(12.dp)) }

                        if (!isSelectToolActive) {
                            val handleSize = 6.dp
                            val handleColor = Color.Blue
                            @Composable fun Handle(align: Alignment, onDrag: (Float, Float) -> Unit) {
                                Box(modifier = Modifier.align(align).size(handleSize).background(handleColor, CircleShape).pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        onDrag(dragAmount.x, dragAmount.y)
                                    }
                                })
                            }

                            fun resize(dx: Float, dy: Float, left: Boolean = false, top: Boolean = false) {
                                val currentEl = elements[index]

                                // CALCULATE IN MM
                                var cx = currentEl.xMm
                                var cy = currentEl.yMm
                                var cw = currentEl.widthMm
                                var ch = currentEl.heightMm

                                val dxMm = dx / pxPerMm
                                val dyMm = dy / pxPerMm

                                if (left) { cx += dxMm; cw -= dxMm } else cw += dxMm
                                if (top) { cy += dyMm; ch -= dyMm } else ch += dyMm

                                val minSizeMm = 5f
                                if(cw < minSizeMm) { if(left) cx -= (minSizeMm - cw); cw = minSizeMm }
                                if(ch < minSizeMm) { if(top) cy -= (minSizeMm - ch); ch = minSizeMm }

                                elements[index] = currentEl.copy(xMm = cx, yMm = cy, widthMm = cw, heightMm = ch)
                            }

                            Handle(Alignment.BottomEnd) { x, y -> resize(x, y) }
                            Handle(Alignment.BottomStart) { x, y -> resize(x, y, left = true) }
                            Handle(Alignment.TopEnd) { x, y -> resize(x, y, top = true) }
                            Handle(Alignment.TopStart) { x, y -> resize(x, y, left = true, top = true) }
                        }
                    }
                }
            }
        }

        // --- LAYER 4: TEXT BOXES ---
        textAnnotations.forEachIndexed { index, txt ->
            key(txt.id) {
                val isSelected = txt.id == selectedAnnotationId || (isSelectToolActive && multiSelectedAnnotationIds.contains(txt.id))
                var showContextMenu by remember { mutableStateOf(false) }

                // CONVERT MM -> SCREEN PX
                val xPx = txt.xMm * pxPerMm
                val yPx = txt.yMm * pxPerMm
                val wPx = txt.widthMm * pxPerMm
                val hPx = txt.heightMm * pxPerMm

                var absolutePosition by remember { mutableStateOf(Offset.Zero) }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
                        .size(with(density) { wPx.toDp() }, with(density) { hPx.toDp() })
                        .onGloballyPositioned { layoutCoordinates -> absolutePosition = layoutCoordinates.positionInWindow() }
                        .alpha(if (hiddenAnnotationId == txt.id && !isSelectToolActive) 0f else 1f)
                        .drawBehind {
                            if (isSelected) {
                                val stroke = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f))
                                drawRect(color = Color.LightGray, style = stroke)
                            }
                        }
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.buttons.isSecondaryPressed && event.changes.any { it.pressed }) {
                                        event.changes.forEach { it.consume() }
                                        onAnnotationSelected(txt.id)
                                        showContextMenu = true
                                    }
                                }
                            }
                        }
                        .pointerInput(Unit) { detectTapGestures(onTap = {
                            if(isSelectToolActive) {
                                if(!multiSelectedAnnotationIds.contains(txt.id)) multiSelectedAnnotationIds.add(txt.id)
                            } else {
                                onAnnotationSelected(txt.id)
                            }
                        }) }
                ) {
                    BasicTextField(
                        value = txt.text,
                        enabled = isSelected && !isSelectToolActive,
                        onValueChange = { str -> textAnnotations[index] = txt.copy(text = str) },
                        // Scale Font: txt.fontSize (Points) -> Pixels approximately
                        textStyle = TextStyle(color = txt.color, fontSize = (txt.fontSize * (pxPerMm / 3.78f)).sp, fontWeight = if (txt.isBold) FontWeight.Bold else FontWeight.Normal, fontStyle = if (txt.isItalic) FontStyle.Italic else FontStyle.Normal, textDecoration = if (txt.isUnderline) TextDecoration.Underline else TextDecoration.None, textAlign = txt.textAlign, fontFamily = getFontFamily(txt.fontFamily)),
                        modifier = Modifier.fillMaxSize().padding(4.dp)
                    )

                    DropdownMenu(expanded = showContextMenu, onDismissRequest = { showContextMenu = false }) {
                        if (isSelectToolActive || selectedAnnotationId != null) {
                            DropdownMenuItem(text = { Text("Copy") }, onClick = { onContextMenuCopy(); showContextMenu = false })
                        }
                        if (hasClipboardContent) {
                            DropdownMenuItem(text = { Text("Paste") }, onClick = { onContextMenuPaste(); showContextMenu = false })
                        }
                        DropdownMenuItem(text = { Text("Delete", color = Color.Red) }, onClick = { onContextMenuDelete(); showContextMenu = false })
                    }

                    if (shouldShowDragHandle(txt.id, isSelected)) {
                        Box(modifier = Modifier.align(Alignment.BottomCenter).offset(y = 20.dp).size(18.dp).background(Color(0xFFF0F0F0), CircleShape).clip(CircleShape).border(0.5.dp, Color.LightGray, CircleShape).pointerInput(Unit) {
                            detectDragGestures(onDragStart = { onGlobalDragStart(txt, absolutePosition) },
                                onDrag = { change, dragAmount ->
                                    change.consume();
                                    // Update Drag in MM
                                    txt.xMm += dragAmount.x / pxPerMm
                                    txt.yMm += dragAmount.y / pxPerMm
                                    onGlobalDrag(dragAmount)
                                },
                                onDragEnd = { onGlobalDragEnd() })
                        }, contentAlignment = Alignment.Center) { Icon(Icons.Default.DragIndicator, contentDescription = "Drag", tint = Color.Gray, modifier = Modifier.size(12.dp)) }

                        if(!isSelectToolActive) {
                            val handleSize = 6.dp
                            val handleColor = Color.LightGray
                            @Composable fun ResizeHandle(alignment: Alignment, onDrag: (Float, Float) -> Unit) { Box(modifier = Modifier.align(alignment).size(handleSize).background(handleColor, CircleShape).pointerInput(Unit) { detectDragGestures { change, dragAmount -> change.consume(); onDrag(dragAmount.x, dragAmount.y) } }) }

                            fun updateResize(dx: Float, dy: Float, isLeft: Boolean, isTop: Boolean, lockX: Boolean = false, lockY: Boolean = false) {
                                val currTxt = textAnnotations[index]
                                var cx = currTxt.xMm
                                var cy = currTxt.yMm
                                var cw = currTxt.widthMm
                                var ch = currTxt.heightMm

                                val dxMm = dx / pxPerMm
                                val dyMm = dy / pxPerMm

                                if (!lockX) { if (isLeft) { cx += dxMm; cw -= dxMm } else { cw += dxMm } }
                                if (!lockY) { if (isTop) { cy += dyMm; ch -= dyMm } else { ch += dyMm } }

                                val minSize = 5f
                                if(cw < minSize) { if(isLeft) cx -= (minSize - cw); cw = minSize }
                                if(ch < minSize) { if(isTop) cy -= (minSize - ch); ch = minSize }

                                textAnnotations[index] = currTxt.copy(xMm = cx, yMm = cy, widthMm = cw, heightMm = ch)
                            }

                            ResizeHandle(Alignment.TopCenter) { x, y -> updateResize(x, y, false, true, lockX = true) }
                            ResizeHandle(Alignment.BottomCenter) { x, y -> updateResize(x, y, false, false, lockX = true) }
                            ResizeHandle(Alignment.CenterStart) { x, y -> updateResize(x, y, true, false, lockY = true) }
                            ResizeHandle(Alignment.CenterEnd) { x, y -> updateResize(x, y, false, false, lockY = true) }
                            ResizeHandle(Alignment.TopStart) { x, y -> updateResize(x, y, true, true) }
                            ResizeHandle(Alignment.TopEnd) { x, y -> updateResize(x, y, false, true) }
                            ResizeHandle(Alignment.BottomStart) { x, y -> updateResize(x, y, true, false) }
                            ResizeHandle(Alignment.BottomEnd) { x, y -> updateResize(x, y, false, false) }
                        }
                    }
                }
            }
        }
    }
}