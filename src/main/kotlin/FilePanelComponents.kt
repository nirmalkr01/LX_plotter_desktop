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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
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
    var textAlign: TextAlign = TextAlign.Left
)

@Composable
fun EditablePageContainer(
    item: ReportPageItem,
    pageNumber: Int,
    paperSize: PaperSize,
    isLandscape: Boolean,
    config: ReportConfig,
    zoomLevel: Float,
    textAnnotations: MutableList<TextAnnotation>,
    hScale: Double, vScale: Double,
    showPre: Boolean, showPost: Boolean,
    preColor: java.awt.Color, postColor: java.awt.Color,
    preDotted: Boolean, postDotted: Boolean,
    preWidth: Float, postWidth: Float,
    preShowPoints: Boolean, postShowPoints: Boolean,
    showGrid: Boolean,
    currentTextColor: Color, currentTextSize: Float,
    isBold: Boolean, isItalic: Boolean, isUnderline: Boolean,
    currentTextAlign: TextAlign,
    isTextToolActive: Boolean,
    selectedAnnotationId: String?,
    onAnnotationSelected: (String?) -> Unit,
    onPageSelected: () -> Unit,
    onToolUsed: () -> Unit,
    onGraphPosChange: (Float, Float) -> Unit,
    onDeleteAnnotation: (String) -> Unit,
    onCopyAnnotation: (TextAnnotation) -> Unit,
    onPasteAnnotation: () -> Unit
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

    var showPageContextMenu by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .size(paperW_dp, paperH_dp)
            .background(Color.White)
            // 1) Logic: Tap on background -> Select Page. If Tool Active -> Create Text
            .pointerInput(isTextToolActive) {
                detectTapGestures(
                    onTap = { offset ->
                        onPageSelected()
                        if (isTextToolActive) {
                            val safeX = offset.x
                            val safeY = offset.y
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
                                textAlign = currentTextAlign
                            )
                            textAnnotations.add(newTxt)
                            onAnnotationSelected(newTxt.id)
                            onToolUsed()
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.buttons.isSecondaryPressed) {
                            // Page right click
                            onPageSelected()
                            showPageContextMenu = true
                        }
                    }
                }
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

            val pageStr = "$pageNumber"
            val textLayout = textMeasurer.measure(pageStr, style = TextStyle(fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold))
            drawText(textLayout, topLeft = Offset(paperW_px - textLayout.size.width - 20f, paperH_px - 25f))
        }

        // --- LAYER 2: GRAPH ---
        val fullPageW_imgPx = widthMm * (38.0/10.0)
        val graphScaleFactor = paperW_px / fullPageW_imgPx
        val graphDim = remember(item.data, hScale, vScale) {
            if (item.data.isEmpty()) GraphDimensions(0.0, 0.0) else calculateGraphDimensions(item.data, item.type, hScale, vScale)
        }
        val visualGraphW = (graphDim.width * graphScaleFactor).toFloat()
        val visualGraphH = (graphDim.height * graphScaleFactor).toFloat()

        val contentW_px = (widthMm * pxPerMm) - innerLeftPx - innerRightPx
        val contentH_px = (heightMm * pxPerMm) - innerTopPx - innerBottomPx

        Box(
            modifier = Modifier
                .padding(start = with(density){ innerLeftPx.toDp() }, top = with(density){ innerTopPx.toDp() })
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

        // PAGE CONTEXT MENU (Paste)
        DropdownMenu(expanded = showPageContextMenu, onDismissRequest = { showPageContextMenu = false }) {
            DropdownMenuItem(text = { Text("Paste") }, onClick = { onPasteAnnotation(); showPageContextMenu = false })
        }

        // --- LAYER 3: TEXT BOXES ---
        textAnnotations.forEachIndexed { index, txt ->
            val isSelected = txt.id == selectedAnnotationId
            var showContextMenu by remember { mutableStateOf(false) }

            val xPx = paperW_px * txt.xPercent
            val yPx = paperH_px * txt.yPercent
            val wPx = paperW_px * txt.widthPercent
            val hPx = paperH_px * txt.heightPercent

            Box(
                modifier = Modifier
                    .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
                    .size(with(density) { wPx.toDp() }, with(density) { hPx.toDp() })
                    .drawBehind {
                        if (isSelected) {
                            val stroke = Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                            )
                            drawRect(
                                color = Color.LightGray,
                                style = stroke
                            )
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onAnnotationSelected(txt.id) },
                            onLongPress = {
                                onAnnotationSelected(txt.id)
                                showContextMenu = true
                            }
                        )
                    }
                    // For Desktop Right Click (Secondary Click)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.buttons.isSecondaryPressed) {
                                    // Box right click
                                    onAnnotationSelected(txt.id)
                                    showContextMenu = true
                                    // Do NOT trigger page menu
                                    // This requires this pointerInput to handle it, potentially blocking parent
                                }
                            }
                        }
                    }
            ) {
                BasicTextField(
                    value = txt.text,
                    enabled = isSelected,
                    onValueChange = { str -> textAnnotations[index] = txt.copy(text = str) },
                    textStyle = TextStyle(
                        color = txt.color,
                        fontSize = (txt.fontSize * zoomLevel).sp,
                        fontWeight = if(txt.isBold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if(txt.isItalic) FontStyle.Italic else FontStyle.Normal,
                        textDecoration = if(txt.isUnderline) TextDecoration.Underline else TextDecoration.None,
                        textAlign = txt.textAlign
                    ),
                    modifier = Modifier.fillMaxSize().padding(4.dp)
                )

                // Text Box Context Menu (Copy/Delete)
                DropdownMenu(
                    expanded = showContextMenu,
                    onDismissRequest = { showContextMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Copy") },
                        onClick = {
                            onCopyAnnotation(txt)
                            showContextMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = Color.Red) },
                        onClick = {
                            onDeleteAnnotation(txt.id)
                            showContextMenu = false
                        }
                    )
                }

                // CONTROLS: Only visible when selected
                if (isSelected) {
                    // 1. DRAG ICON (Bottom Center) - Smaller, below line
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 12.dp) // Closer to line
                            .size(16.dp)
                            .background(Color(0xFFF0F0F0), CircleShape)
                            .clip(CircleShape)
                            .border(0.5.dp, Color.LightGray, CircleShape)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val currTxt = textAnnotations[index]
                                    val currX = currentPaperW * currTxt.xPercent
                                    val currY = currentPaperH * currTxt.yPercent

                                    val newX = currX + dragAmount.x
                                    val newY = currY + dragAmount.y

                                    textAnnotations[index] = currTxt.copy(
                                        xPercent = newX / currentPaperW,
                                        yPercent = newY / currentPaperH
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.DragIndicator, contentDescription = "Drag", tint = Color.Gray, modifier = Modifier.size(10.dp))
                    }

                    // 2. RESIZE HANDLES (Small dots on border lines)
                    val handleSize = 6.dp
                    val handleColor = Color.LightGray

                    @Composable
                    fun ResizeHandle(alignment: Alignment, onDrag: (Float, Float) -> Unit) {
                        Box(
                            modifier = Modifier
                                .align(alignment)
                                .size(handleSize)
                                .background(handleColor, CircleShape)
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        onDrag(dragAmount.x, dragAmount.y)
                                    }
                                }
                        )
                    }

                    fun updateResize(dx: Float, dy: Float, isLeft: Boolean, isTop: Boolean, lockX: Boolean = false, lockY: Boolean = false) {
                        val currTxt = textAnnotations[index]
                        val cx = currentPaperW * currTxt.xPercent
                        val cy = currentPaperH * currTxt.yPercent
                        val cw = currentPaperW * currTxt.widthPercent
                        val ch = currentPaperH * currTxt.heightPercent

                        var nx = cx
                        var ny = cy
                        var nw = cw
                        var nh = ch

                        if (!lockX) {
                            if (isLeft) { nx += dx; nw -= dx } else { nw += dx }
                        }
                        if (!lockY) {
                            if (isTop) { ny += dy; nh -= dy } else { nh += dy }
                        }

                        if (nw < 20f) { if (isLeft && !lockX) nx = cx; nw = 20f }
                        if (nh < 20f) { if (isTop && !lockY) ny = cy; nh = 20f }

                        textAnnotations[index] = currTxt.copy(
                            xPercent = nx / currentPaperW,
                            yPercent = ny / currentPaperH,
                            widthPercent = nw / currentPaperW,
                            heightPercent = nh / currentPaperH
                        )
                    }

                    // Handles on lines (Top, Bottom, Left, Right)
                    ResizeHandle(Alignment.TopCenter) { x, y -> updateResize(x, y, false, true, lockX = true) }
                    ResizeHandle(Alignment.BottomCenter) { x, y -> updateResize(x, y, false, false, lockX = true) }
                    ResizeHandle(Alignment.CenterStart) { x, y -> updateResize(x, y, true, false, lockY = true) }
                    ResizeHandle(Alignment.CenterEnd) { x, y -> updateResize(x, y, false, false, lockY = true) }

                    // Corner Handles
                    ResizeHandle(Alignment.TopStart) { x, y -> updateResize(x, y, true, true) }
                    ResizeHandle(Alignment.TopEnd) { x, y -> updateResize(x, y, false, true) }
                    ResizeHandle(Alignment.BottomStart) { x, y -> updateResize(x, y, true, false) }
                    ResizeHandle(Alignment.BottomEnd) { x, y -> updateResize(x, y, false, false) }
                }
            }
        }
    }
}

// --- RIBBON COMPONENTS ---

@Composable
fun RibbonTab(text: String, isActive: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, fontSize = 13.sp, fontWeight = if(isActive) FontWeight.Bold else FontWeight.Normal, color = if(isActive) Color(0xFF2B579A) else Color.Black)
        if (isActive) Box(Modifier.height(3.dp).width(40.dp).background(Color(0xFF2B579A), RoundedCornerShape(topStart=2.dp, topEnd=2.dp)))
    }
}

@Composable
fun RibbonGroup(label: String, content: @Composable RowScope.() -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxHeight().padding(horizontal = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, content = content)
        Text(label, fontSize = 10.sp, color = Color.Gray)
    }
}

@Composable
fun RibbonLargeButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color = Color(0xFF2B579A), onClick: () -> Unit) {
    Column(modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 11.sp, color = color)
    }
}

@Composable
fun RibboniconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, isActive: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .background(if(isActive) Color(0xFFE1EDFD) else Color.Transparent, RoundedCornerShape(4.dp))
            .border(1.dp, if(isActive) Color(0xFFA4C6F8) else Color.Transparent, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = if(isActive) Color(0xFF2B579A) else Color.Black)
        if(label.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 11.sp, color = if(isActive) Color(0xFF2B579A) else Color.Black)
        }
    }
}

@Composable
fun RibbonTextButton(text: String, isActive: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(if(isActive) Color(0xFFE1EDFD) else Color.Transparent, RoundedCornerShape(4.dp))
            .border(1.dp, if(isActive) Color(0xFFA4C6F8) else Color.Transparent, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if(isActive) Color(0xFF2B579A) else Color.Black)
    }
}

@Composable
fun RibbonDropdown(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, options: List<String>, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.border(1.dp, Color.LightGray, RoundedCornerShape(4.dp)).clip(RoundedCornerShape(4.dp)).clickable { expanded = true }.padding(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = Color(0xFF2B579A))
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 11.sp)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(14.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { idx, opt -> DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(idx); expanded = false }) }
        }
    }
}

// 2) Fraction and Empty Input Handling
@Composable
fun RibbonNumberInput(label: String, value: Float, enabled: Boolean = true, onChange: (Float) -> Unit) {
    var textValue by remember(value) { mutableStateOf(if(value == 0f) "" else value.toString()) }

    LaunchedEffect(value) {
        if(textValue.toFloatOrNull() != value) {
            textValue = if(value == 0f) "" else value.toString()
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.sp, color = if(enabled) Color.DarkGray else Color.LightGray, modifier = Modifier.width(30.dp))
        BasicTextField(
            value = textValue,
            enabled = enabled,
            onValueChange = { str ->
                textValue = str
                val num = str.toFloatOrNull()
                if (num != null) {
                    onChange(num)
                }
            },
            textStyle = TextStyle(fontSize = 11.sp, textAlign = TextAlign.Center),
            modifier = Modifier
                .width(40.dp)
                .background(if(enabled) Color.White else Color(0xFFEEEEEE), RoundedCornerShape(2.dp))
                .border(1.dp, Color.LightGray, RoundedCornerShape(2.dp))
                .padding(3.dp)
        )
    }
}