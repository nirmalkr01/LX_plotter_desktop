import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
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
    currentTextColor: Color, currentTextSize: Float, isBold: Boolean, currentTextAlign: TextAlign,
    isTextToolActive: Boolean,
    selectedAnnotationId: String?,
    onAnnotationSelected: (String?) -> Unit,
    onPageSelected: () -> Unit,
    onToolUsed: () -> Unit,
    onGraphPosChange: (Float, Float) -> Unit
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

    // 7) Strict Safe Zone Logic
    val safeZone: Rect = remember(config, paperW_px, paperH_px) {
        if (!config.showOuterBorder) {
            Rect(0f, 0f, paperW_px, paperH_px) // Full Freedom
        } else if (!config.showInnerBorder) {
            Rect(mLeftPx, mTopPx, paperW_px - mRightPx, paperH_px - mBottomPx)
        } else {
            Rect(innerLeftPx, innerTopPx, paperW_px - innerRightPx, paperH_px - innerBottomPx)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .size(paperW_dp, paperH_dp)
            .background(Color.White)
            // 1) Logic: Tap on background -> Select Page. If Tool Active -> Create Text
            .pointerInput(isTextToolActive) {
                detectTapGestures { offset ->
                    onPageSelected() // Select Page on ANY click

                    if (isTextToolActive) {
                        if (safeZone.contains(offset)) {
                            val defW = paperW_px * 0.2f
                            val defH = paperH_px * 0.05f
                            val safeX = offset.x.coerceIn(safeZone.left, safeZone.right - defW)
                            val safeY = offset.y.coerceIn(safeZone.top, safeZone.bottom - defH)

                            val newTxt = TextAnnotation(
                                xPercent = safeX / paperW_px,
                                yPercent = safeY / paperH_px,
                                widthPercent = 0.2f,
                                heightPercent = 0.05f,
                                color = currentTextColor,
                                fontSize = currentTextSize,
                                isBold = isBold,
                                textAlign = currentTextAlign
                            )
                            textAnnotations.add(newTxt)
                            onAnnotationSelected(newTxt.id)
                            onToolUsed()
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

        // Graph Bounds (Approximate to margin)
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

                                onGraphPosChange(
                                    item.xOffset + dx,
                                    item.yOffset + dy
                                )
                            }
                        }
                ) {
                    GraphPageCanvas(Modifier.fillMaxSize(), item.data, item.type, PaperSize.A4, true, hScale, vScale, config, showPre, showPost, preColor, postColor, preWidth, postWidth, preDotted, postDotted, preShowPoints, postShowPoints, showGrid, isTransparentOverlay = true)
                }
            }
        }

        // --- LAYER 3: TEXT BOXES ---
        textAnnotations.forEachIndexed { index, txt ->
            val isSelected = txt.id == selectedAnnotationId

            val xPx = paperW_px * txt.xPercent
            val yPx = paperH_px * txt.yPercent
            val wPx = paperW_px * txt.widthPercent
            val hPx = paperH_px * txt.heightPercent

            Box(
                modifier = Modifier
                    .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
                    .size(with(density) { wPx.toDp() }, with(density) { hPx.toDp() })
                    .border(
                        if (isSelected) 1.dp else 0.dp,
                        if (isSelected) Color.Blue else Color.Transparent,
                        RoundedCornerShape(2.dp)
                    )
                    // 4) Independent Dragging + Page Selection on Tap
                    .pointerInput(isSelected, safeZone) {
                        detectTapGestures(onTap = { onAnnotationSelected(txt.id) })
                    }
                    .pointerInput(isSelected, safeZone) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            // 4) Move anywhere inside Safe Zone
                            val newX = (xPx + dragAmount.x).coerceIn(safeZone.left, safeZone.right - wPx)
                            val newY = (yPx + dragAmount.y).coerceIn(safeZone.top, safeZone.bottom - hPx)

                            textAnnotations[index] = txt.copy(
                                xPercent = newX / paperW_px,
                                yPercent = newY / paperH_px
                            )
                            onAnnotationSelected(txt.id) // Ensure selected while dragging
                        }
                    }
            ) {
                BasicTextField(
                    value = txt.text,
                    onValueChange = { str -> textAnnotations[index] = txt.copy(text = str) },
                    textStyle = TextStyle(
                        color = txt.color,
                        fontSize = (txt.fontSize * zoomLevel).sp,
                        fontWeight = if(txt.isBold) FontWeight.Bold else FontWeight.Normal,
                        textAlign = txt.textAlign
                    ),
                    modifier = Modifier.fillMaxSize().padding(2.dp)
                )

                // 3) Resizing Handle (Blue Square)
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(12.dp)
                            .background(Color.Blue)
                            .pointerInput(safeZone) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    // Calculate new size
                                    val newW = (wPx + dragAmount.x).coerceIn(30f, safeZone.right - xPx)
                                    val newH = (hPx + dragAmount.y).coerceIn(20f, safeZone.bottom - yPx)

                                    textAnnotations[index] = txt.copy(
                                        widthPercent = newW / paperW_px,
                                        heightPercent = newH / paperH_px
                                    )
                                }
                            }
                    )
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
    // Local state to hold exact user typing (including dots and empty)
    var textValue by remember(value) { mutableStateOf(if(value == 0f) "" else value.toString()) }

    // Keep textValue synced if value changes externally (e.g., page switch)
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
                // Only update parent if valid number, otherwise just hold text
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