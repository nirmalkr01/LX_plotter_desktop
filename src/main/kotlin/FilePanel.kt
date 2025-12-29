import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

data class TextAnnotation(
    var id: String = java.util.UUID.randomUUID().toString(),
    var text: String = "",
    var xPercent: Float = 0f,
    var yPercent: Float = 0f,
    var widthPercent: Float = 0.2f,
    var heightPercent: Float = 0.05f,
    var color: Color = Color.Black,
    var fontSize: Float = 12f,
    var isBold: Boolean = false,
    var isItalic: Boolean = false,
    var isUnderline: Boolean = false,
    var textAlign: TextAlign = TextAlign.Left,
    var fontFamily: String = "Arial",
    // New property for faded placeholder ID
    var displayId: String? = null
)

// Helper to map string names to Compose FontFamilies
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
    elements: MutableList<ReportElement>, // NEW: List of elements
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
    selectedAnnotationId: String?,
    selectedElementId: String?, // NEW
    canPaste: Boolean,
    onAnnotationSelected: (String?) -> Unit,
    onElementSelected: (String?) -> Unit, // NEW
    onPageSelected: () -> Unit,
    onToolUsed: () -> Unit,
    onGraphPosChange: (Float, Float) -> Unit,
    onDeleteAnnotation: (String) -> Unit,
    onDeleteElement: (String) -> Unit, // NEW
    onCopyAnnotation: (TextAnnotation) -> Unit,
    onPasteAnnotation: () -> Unit,
    // --- CALLBACKS FOR GLOBAL DRAG ---
    hiddenAnnotationId: String? = null,
    onGlobalDragStart: (TextAnnotation, Offset) -> Unit,
    onGlobalDrag: (Offset) -> Unit,
    onGlobalDragEnd: () -> Unit
) {
    val textMeasurer = rememberTextMeasurer()

    // 1. Dimensions
    val widthMm = if (isLandscape) paperSize.heightMm else paperSize.widthMm
    val heightMm = if (isLandscape) paperSize.widthMm else paperSize.heightMm
    val pxPerMm = 1.5f * zoomLevel
    val paperW_dp = (widthMm * pxPerMm).dp
    val paperH_dp = (heightMm * pxPerMm).dp

    // 2. Margins & Borders (Pixels)
    val mLeftPx = config.marginLeft * pxPerMm
    val mTopPx = config.marginTop * pxPerMm
    val mRightPx = config.marginRight * pxPerMm
    val mBottomPx = config.marginBottom * pxPerMm
    val gapPx = config.borderGap * pxPerMm

    val innerLeftPx = mLeftPx + gapPx
    val innerTopPx = mTopPx + gapPx
    val innerRightPx = mRightPx + gapPx
    val innerBottomPx = mBottomPx + gapPx

    val density = LocalDensity.current
    val paperW_px = with(density) { paperW_dp.toPx() }
    val paperH_px = with(density) { paperH_dp.toPx() }

    val currentPaperW by rememberUpdatedState(paperW_px)
    val currentPaperH by rememberUpdatedState(paperH_px)

    BoxWithConstraints(
        modifier = Modifier
            .size(paperW_dp, paperH_dp)
            .background(Color.White)
            // 1) Logic: Tap on background -> Select Page. If Tool Active -> Create Text
            .pointerInput(isTextToolActive, currentTextColor, currentTextSize, isBold, isItalic, isUnderline, currentTextAlign, currentFontFamily) {
                detectTapGestures(
                    onTap = { offset ->
                        onPageSelected()
                        if (isTextToolActive) {
                            val safeX = offset.x
                            val safeY = offset.y
                            // Generate a simple sequential ID for display like "Txt-1"
                            val nextIdNum = textAnnotations.size + 1
                            val newTxt = TextAnnotation(
                                xPercent = safeX / currentPaperW,
                                yPercent = safeY / currentPaperH,
                                widthPercent = 0.2f,
                                heightPercent = 0.05f,
                                color = currentTextColor,
                                fontSize = currentTextSize,
                                isBold = isBold,
                                isItalic = isItalic,
                                isUnderline = isUnderline,
                                textAlign = currentTextAlign,
                                fontFamily = currentFontFamily,
                                displayId = "ID-$nextIdNum" // Assign ID
                            )
                            textAnnotations.add(newTxt)
                            onAnnotationSelected(newTxt.id)
                            onToolUsed()
                        }
                    }
                )
            }
    ) {
        // --- LAYER 1: CANVAS ---
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (config.showOuterBorder) {
                drawRect(Color(config.outerColor.red, config.outerColor.green, config.outerColor.blue),
                    topLeft = Offset(mLeftPx, mTopPx),
                    size = Size(paperW_px - mLeftPx - mRightPx, paperH_px - mTopPx - mBottomPx),
                    style = Stroke(width = config.outerThickness))
            }

            if (config.showInnerBorder && config.showOuterBorder) {
                drawRect(Color(config.innerColor.red, config.innerColor.green, config.innerColor.blue),
                    topLeft = Offset(innerLeftPx, innerTopPx),
                    size = Size(paperW_px - innerLeftPx - innerRightPx, paperH_px - innerTopPx - innerBottomPx),
                    style = Stroke(width = config.innerThickness))
            }

            // --- DRAW SPECIFIC PAGE LAYOUT (A1 Style etc) ---
            val layoutML = if (config.showInnerBorder) innerLeftPx else mLeftPx
            val layoutMT = if (config.showInnerBorder) innerTopPx else mTopPx
            val layoutMR = if (config.showInnerBorder) innerRightPx else mRightPx
            val layoutMB = if (config.showInnerBorder) innerBottomPx else mBottomPx

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
                vScale = vScale
            )

            if (layoutType == PageLayoutType.BLANK && showPageNumber) {
                val pageStr = "$pageNumber"
                val textLayout = textMeasurer.measure(pageStr, style = TextStyle(fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold))
                drawText(textLayout, topLeft = Offset(paperW_px - textLayout.size.width - 20f, paperH_px - 25f))
            }
        }

        // --- LAYER 2: GRAPH ---
        val fullPageW_imgPx = widthMm * (38.0/10.0)
        val graphScaleFactor = paperW_px / fullPageW_imgPx
        val graphDim = remember(item.data, hScale, vScale) {
            if (item.data.isEmpty()) GraphDimensions(0.0, 0.0) else calculateGraphDimensions(item.data, item.type, hScale, vScale)
        }
        val visualGraphW = (graphDim.width * graphScaleFactor).toFloat()
        val visualGraphH = (graphDim.height * graphScaleFactor).toFloat()
        val contentLeft = if(config.showInnerBorder) innerLeftPx else mLeftPx
        val contentTop = if(config.showInnerBorder) innerTopPx else mTopPx
        val contentRight = if(config.showInnerBorder) innerRightPx else mRightPx
        val contentBottom = if(config.showInnerBorder) innerBottomPx else mBottomPx
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
                        .offset { IntOffset((item.xOffset * graphScaleFactor).roundToInt(), (item.yOffset * graphScaleFactor).roundToInt()) }
                        .size(with(density){ visualGraphW.toDp() }, with(density){ visualGraphH.toDp() })
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val graphScaleFactorF = graphScaleFactor.toFloat()
                                val dx = dragAmount.x / graphScaleFactorF
                                val dy = dragAmount.y / graphScaleFactorF
                                onGraphPosChange(item.xOffset + dx, item.yOffset + dy)
                            }
                        }
                ) {
                    GraphPageCanvas(Modifier.fillMaxSize(), item.data, item.type, PaperSize.A4, true, hScale, vScale, config, showPre, showPost, preColor, postColor, preWidth, postWidth, preDotted, postDotted, preShowPoints, postShowPoints, showGrid, isTransparentOverlay = true)
                }
            }
        }

        // --- LAYER 3: ELEMENTS (SHAPES) ---
        elements.forEachIndexed { index, el ->
            key(el.id) {
                val isSelected = el.id == selectedElementId
                var showContextMenu by remember { mutableStateOf(false) }

                val xPx = paperW_px * el.xPercent
                val yPx = paperH_px * el.yPercent
                val wPx = paperW_px * el.widthPercent
                val hPx = paperH_px * el.heightPercent

                Box(
                    modifier = Modifier
                        .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
                        .size(with(density) { wPx.toDp() }, with(density) { hPx.toDp() })
                        .drawBehind {
                            if (isSelected) {
                                val stroke = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f))
                                drawRect(color = Color.Blue, style = stroke)
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
                        .pointerInput(Unit) { detectTapGestures(onTap = { onElementSelected(el.id) }) }
                ) {
                    ElementRenderer(el)

                    DropdownMenu(expanded = showContextMenu, onDismissRequest = { showContextMenu = false }) {
                        DropdownMenuItem(text = { Text("Delete", color = Color.Red) }, onClick = { onDeleteElement(el.id); showContextMenu = false })
                    }

                    if (isSelected) {
                        // DRAG ICON
                        Box(modifier = Modifier.align(Alignment.BottomCenter).offset(y = 20.dp).size(18.dp).background(Color(0xFFF0F0F0), CircleShape).clip(CircleShape).border(0.5.dp, Color.LightGray, CircleShape).pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val nx = (paperW_px * el.xPercent) + dragAmount.x
                                val ny = (paperH_px * el.yPercent) + dragAmount.y
                                elements[index] = el.copy(xPercent = nx / paperW_px, yPercent = ny / paperH_px)
                            }
                        }, contentAlignment = Alignment.Center) { Icon(Icons.Default.DragIndicator, null, tint = Color.Gray, modifier = Modifier.size(12.dp)) }

                        // RESIZE HANDLES
                        val handleSize = 6.dp
                        val handleColor = Color.Blue
                        @Composable fun Handle(align: Alignment, onDrag: (Float, Float) -> Unit) {
                            Box(modifier = Modifier.align(align).size(handleSize).background(handleColor, CircleShape).pointerInput(Unit) { detectDragGestures { change, drag -> change.consume(); onDrag(drag.x, drag.y) } })
                        }

                        fun resize(dx: Float, dy: Float, left: Boolean = false, top: Boolean = false) {
                            var cx = paperW_px * el.xPercent
                            var cy = paperH_px * el.yPercent
                            var cw = paperW_px * el.widthPercent
                            var ch = paperH_px * el.heightPercent
                            if (left) { cx += dx; cw -= dx } else cw += dx
                            if (top) { cy += dy; ch -= dy } else ch += dy
                            if(cw < 10f) cw = 10f; if(ch < 10f) ch = 10f
                            elements[index] = el.copy(xPercent = cx / paperW_px, yPercent = cy / paperH_px, widthPercent = cw / paperW_px, heightPercent = ch / paperH_px)
                        }

                        Handle(Alignment.BottomEnd) { x, y -> resize(x, y) }
                        Handle(Alignment.BottomStart) { x, y -> resize(x, y, left = true) }
                        Handle(Alignment.TopEnd) { x, y -> resize(x, y, top = true) }
                        Handle(Alignment.TopStart) { x, y -> resize(x, y, left = true, top = true) }
                    }
                }
            }
        }

        // --- LAYER 4: TEXT BOXES ---
        textAnnotations.forEachIndexed { index, txt ->
            key(txt.id) {
                val isSelected = txt.id == selectedAnnotationId
                var showContextMenu by remember { mutableStateOf(false) }

                val xPx = paperW_px * txt.xPercent
                val yPx = paperH_px * txt.yPercent
                val wPx = paperW_px * txt.widthPercent
                val hPx = paperH_px * txt.heightPercent

                var absolutePosition by remember { mutableStateOf(Offset.Zero) }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
                        .size(with(density) { wPx.toDp() }, with(density) { hPx.toDp() })
                        .onGloballyPositioned { layoutCoordinates -> absolutePosition = layoutCoordinates.positionInWindow() }
                        .alpha(if (hiddenAnnotationId == txt.id) 0f else 1f)
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
                        .pointerInput(Unit) { detectTapGestures(onTap = { onAnnotationSelected(txt.id) }) }
                ) {
                    BasicTextField(
                        value = txt.text,
                        enabled = isSelected,
                        onValueChange = { str -> textAnnotations[index] = txt.copy(text = str) },
                        textStyle = TextStyle(color = txt.color, fontSize = (txt.fontSize * zoomLevel).sp, fontWeight = if (txt.isBold) FontWeight.Bold else FontWeight.Normal, fontStyle = if (txt.isItalic) FontStyle.Italic else FontStyle.Normal, textDecoration = if (txt.isUnderline) TextDecoration.Underline else TextDecoration.None, textAlign = txt.textAlign, fontFamily = getFontFamily(txt.fontFamily)),
                        modifier = Modifier.fillMaxSize().padding(4.dp)
                    )

                    DropdownMenu(expanded = showContextMenu, onDismissRequest = { showContextMenu = false }) {
                        DropdownMenuItem(text = { Text("Delete", color = Color.Red) }, onClick = { onDeleteAnnotation(txt.id); showContextMenu = false })
                    }

                    if (isSelected) {
                        Box(modifier = Modifier.align(Alignment.BottomCenter).offset(y = 20.dp).size(18.dp).background(Color(0xFFF0F0F0), CircleShape).clip(CircleShape).border(0.5.dp, Color.LightGray, CircleShape).pointerInput(Unit) { detectDragGestures(onDragStart = { onGlobalDragStart(txt, absolutePosition) }, onDrag = { change, dragAmount -> change.consume(); onGlobalDrag(dragAmount) }, onDragEnd = { onGlobalDragEnd() }) }, contentAlignment = Alignment.Center) { Icon(Icons.Default.DragIndicator, contentDescription = "Drag", tint = Color.Gray, modifier = Modifier.size(12.dp)) }

                        val handleSize = 6.dp
                        val handleColor = Color.LightGray
                        @Composable fun ResizeHandle(alignment: Alignment, onDrag: (Float, Float) -> Unit) { Box(modifier = Modifier.align(alignment).size(handleSize).background(handleColor, CircleShape).pointerInput(Unit) { detectDragGestures { change, dragAmount -> change.consume(); onDrag(dragAmount.x, dragAmount.y) } }) }

                        fun updateResize(dx: Float, dy: Float, isLeft: Boolean, isTop: Boolean, lockX: Boolean = false, lockY: Boolean = false) {
                            val currTxt = textAnnotations[index]
                            val cx = currentPaperW * currTxt.xPercent
                            val cy = currentPaperH * currTxt.yPercent
                            val cw = currentPaperW * currTxt.widthPercent
                            val ch = currentPaperH * currTxt.heightPercent
                            var nx = cx; var ny = cy; var nw = cw; var nh = ch

                            if (!lockX) { if (isLeft) { nx += dx; nw -= dx } else { nw += dx } }
                            if (!lockY) { if (isTop) { ny += dy; nh -= dy } else { nh += dy } }

                            if (nw < 20f) { if (isLeft && !lockX) nx = cx; nw = 20f }
                            if (nh < 20f) { if (isTop && !lockY) ny = cy; nh = 20f }

                            textAnnotations[index] = currTxt.copy(xPercent = nx / currentPaperW, yPercent = ny / currentPaperH, widthPercent = nw / currentPaperW, heightPercent = nh / currentPaperH)
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