import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

// --- SHARED DATA MODELS ---
data class ReportConfig(
    val marginTop: Float = 10f,
    val marginBottom: Float = 10f,
    val marginLeft: Float = 10f,
    val marginRight: Float = 10f,
    val showOuterBorder: Boolean = true,
    val outerThickness: Float = 1f,
    val outerColor: Color = Color.Black,
    val showInnerBorder: Boolean = false,
    val innerThickness: Float = 1f,
    val innerColor: Color = Color.Black,
    val borderGap: Float = 5f,
    // MODIFICATION: Added legendType to store user selection (Requirement 1)
    var legendType: String = "X-Section"
)

data class GraphDimensions(val width: Double, val height: Double)

// --- MAIN SCREEN ---
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
    onBack: () -> Unit
) {
    var selectedGraphType by remember { mutableStateOf(initialGraphType) }

    val availableGraphs = remember(selectedGraphType, riverData) {
        if (selectedGraphType == "L-Section") listOf(-1.0)
        else riverData.map { it.chainage }.distinct().sorted()
    }

    var activeGraphId by remember { mutableStateOf(if(availableGraphs.isNotEmpty()) availableGraphs.first() else -100.0) }
    val reportItems = remember { mutableStateListOf<ReportPageItem>() }
    var statusMsg by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF0F0F0))) {
        // Top Bar
        Surface(shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
            Row(modifier = Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Spacer(Modifier.width(16.dp))
                Text("Report Download Center", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                if(statusMsg.isNotEmpty()) Text(statusMsg, fontSize = 12.sp, color = Color(0xFF006400))
            }
        }

        // Content
        Row(modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
            // Panel 1: List
            Card(modifier = Modifier.weight(0.15f).fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("1. Select Graph", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    Row(modifier = Modifier.fillMaxWidth().height(32.dp).border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(if(selectedGraphType == "X-Section") MaterialTheme.colorScheme.primary else Color.White).clickable { selectedGraphType = "X-Section"; activeGraphId = if(availableGraphs.isNotEmpty()) availableGraphs.first() else -100.0 }, contentAlignment = Alignment.Center) {
                            Text("X-Sec", color = if(selectedGraphType == "X-Section") Color.White else MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                        }
                        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(if(selectedGraphType == "L-Section") MaterialTheme.colorScheme.primary else Color.White).clickable { selectedGraphType = "L-Section"; activeGraphId = -1.0 }, contentAlignment = Alignment.Center) {
                            Text("L-Sec", color = if(selectedGraphType == "L-Section") Color.White else MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(availableGraphs) { id ->
                            val label = if(id == -1.0) "L-Section Profile" else "Chainage ${id.toInt()} m"
                            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(if (activeGraphId == id) MaterialTheme.colorScheme.primaryContainer else Color.Transparent).clickable { activeGraphId = id }.padding(vertical = 8.dp, horizontal = 4.dp)) {
                                Text(label, fontSize = 12.sp, fontWeight = if(activeGraphId == id) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))

            // Panel 2: Image Panel
            Box(Modifier.weight(0.35f)) {
                ImagePanel(
                    riverData, activeGraphId, startCh, endCh, selectedGraphType, lHScale, lVScale, xHScale, xVScale,
                    showPre, showPost, preColor, postColor, preDotted, postDotted, preWidth, postWidth, preShowPoints, postShowPoints, showGrid,
                    onAddToReport = { reportItems.add(it) }, onStatusChange = { statusMsg = it }
                )
            }
            Spacer(Modifier.width(8.dp))

            // Panel 3: File Panel
            Box(Modifier.weight(0.5f)) {
                FilePanel(
                    reportItems, lHScale, lVScale, xHScale, xVScale,
                    showPre, showPost, preColor, postColor, preDotted, postDotted, preWidth, postWidth, preShowPoints, postShowPoints, showGrid,
                    onStatusChange = { statusMsg = it }
                )
            }
        }
    }
}

// --- SHARED HELPER FUNCTIONS ---

fun calculateGraphDimensions(data: List<RiverPoint>, type: String, hScale: Double, vScale: Double): GraphDimensions {
    if (data.isEmpty()) return GraphDimensions(100.0, 100.0)
    val IMG_PX_PER_CM = 38.0
    val xVals = if(type=="L-Section") data.map{it.chainage} else data.map{it.distance}
    val minX = xVals.minOrNull() ?: 0.0
    val maxX = xVals.maxOrNull() ?: 10.0
    val yVals = data.map{it.preMonsoon} + data.map{it.postMonsoon}
    val minY = if(yVals.isNotEmpty()) floor(yVals.minOrNull()!!) - 1.0 else 0.0
    val maxY = if(yVals.isNotEmpty()) ceil(yVals.maxOrNull()!!) + 1.0 else 10.0

    val pxPerMX = (100.0 / max(hScale, 1.0)) * IMG_PX_PER_CM
    val pxPerMY = (100.0 / max(vScale, 1.0)) * IMG_PX_PER_CM

    val paddingLeft = 100.0
    val tableH = 180.0 // 3 * 60
    val graphH = (maxY - minY) * pxPerMY
    val totalW = paddingLeft + (maxX - minX) * pxPerMX
    val totalH = 50.0 + graphH + tableH // 50 top pad
    return GraphDimensions(totalW, totalH)
}

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
    isTransparentOverlay: Boolean = false
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier) {
        if (size.width <= 0 || size.height <= 0) return@Canvas

        val IMG_PX_PER_CM = 38.0
        val mmToPx = IMG_PX_PER_CM / 10.0
        val pW_mm = if (isLandscape) paperSize.heightMm else paperSize.widthMm
        val pH_mm = if (isLandscape) paperSize.widthMm else paperSize.heightMm
        val pageW = pW_mm * mmToPx
        val pageH = pH_mm * mmToPx

        val scaleFit = if(isTransparentOverlay) {
            val dims = calculateGraphDimensions(data, type, hScale, vScale)
            minOf(size.width / dims.width.toFloat(), size.height / dims.height.toFloat())
        } else {
            minOf(size.width / pageW.toFloat(), size.height / pageH.toFloat())
        }

        if (scaleFit.isNaN() || scaleFit <= 0f) return@Canvas

        scale(scaleFit, pivot = Offset.Zero) {
            if (!isTransparentOverlay) {
                drawRect(Color.White, size = Size(pageW.toFloat(), pageH.toFloat()))
                if (!isRawView) {
                    val mL = (config.marginLeft * mmToPx).toFloat()
                    val mR = (config.marginRight * mmToPx).toFloat()
                    val mT = (config.marginTop * mmToPx).toFloat()
                    val mB = (config.marginBottom * mmToPx).toFloat()

                    if(config.showOuterBorder) {
                        drawRect(Color(config.outerColor.red, config.outerColor.green, config.outerColor.blue),
                            topLeft = Offset(mL, mT),
                            size = Size(pageW.toFloat()-mL-mR, pageH.toFloat()-mT-mB),
                            style = Stroke(width = config.outerThickness))
                    }
                }
            }

            val startX = if(isTransparentOverlay) 0f else {
                val mL = config.marginLeft * mmToPx
                val gap = config.borderGap * mmToPx
                (mL + (if(config.showInnerBorder) gap else 0.0)).toFloat()
            }
            val startY = if(isTransparentOverlay) 0f else {
                val mT = config.marginTop * mmToPx
                val gap = config.borderGap * mmToPx
                (mT + (if(config.showInnerBorder) gap else 0.0)).toFloat()
            }

            translate(left = startX, top = startY) {
                val xVals = if(type=="L-Section") data.map{it.chainage} else data.map{it.distance}
                val minX = xVals.minOrNull() ?: 0.0
                val maxX = xVals.maxOrNull() ?: 10.0
                val yVals = (if(showPre) data.map{it.preMonsoon} else emptyList()) + (if(showPost) data.map{it.postMonsoon} else emptyList())
                val minY = if(yVals.isNotEmpty()) floor(yVals.minOrNull()!!) - 1.0 else 0.0
                val maxY = if(yVals.isNotEmpty()) ceil(yVals.maxOrNull()!!) + 1.0 else 10.0

                val pxPerMX = (100.0/max(hScale, 1.0)) * IMG_PX_PER_CM
                val pxPerMY = (100.0/max(vScale, 1.0)) * IMG_PX_PER_CM

                val padLeft = 100.0f
                val tableRowH = 60.0f
                val graphH = ((maxY - minY) * pxPerMY).toFloat()
                val topPad = 50.0f
                val totAreaH = graphH + topPad
                val totalDrawH = totAreaH + (3 * tableRowH)

                fun mX(v: Double) = (padLeft + (v-minX)*pxPerMX).toFloat()
                fun mY(v: Double) = (totAreaH - (v-minY)*pxPerMY).toFloat()

                if (showGrid) {
                    data.sortedBy{if(type=="L-Section") it.chainage else it.distance}.forEach { p ->
                        val x = mX(if(type=="L-Section") p.chainage else p.distance)
                        drawLine(Color.LightGray, Offset(x, 0f), Offset(x, totalDrawH), strokeWidth = 1f)
                    }
                }

                val dropColor = Color.LightGray.copy(alpha=0.6f)
                data.sortedBy{if(type=="L-Section") it.chainage else it.distance}.forEach { p ->
                    val x = mX(if(type=="L-Section") p.chainage else p.distance)
                    if (showPre) drawLine(dropColor, Offset(x, mY(p.preMonsoon)), Offset(x, totAreaH), strokeWidth = 1f)
                    if (showPost) drawLine(dropColor, Offset(x, mY(p.postMonsoon)), Offset(x, totAreaH), strokeWidth = 1f)
                }

                fun drawSeries(getColor: (RiverPoint) -> Double, awtColor: java.awt.Color, isDotted: Boolean, width: Float, showPoints: Boolean) {
                    val color = Color(awtColor.red, awtColor.green, awtColor.blue)
                    val path = Path()
                    var first = true
                    data.sortedBy{if(type=="L-Section") it.chainage else it.distance}.forEach{p->
                        val x = mX(if(type=="L-Section") p.chainage else p.distance)
                        val y = mY(getColor(p))
                        if(first){ path.moveTo(x,y); first=false } else path.lineTo(x,y)
                        if(showPoints) drawCircle(color, radius = width * 2f, center = Offset(x, y))
                    }
                    val effect = if(isDotted) PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f) else null
                    drawPath(path, color, style = Stroke(width = width * 2, pathEffect = effect))
                }
                if(showPre) drawSeries({it.preMonsoon}, preColor, preDotted, preWidth, preShowPoints)
                if(showPost) drawSeries({it.postMonsoon}, postColor, postDotted, postWidth, postShowPoints)

                val xEnd = (padLeft + (maxX - minX) * pxPerMX).toFloat()
                drawLine(Color.Black, Offset(0f, totAreaH), Offset(xEnd, totAreaH), strokeWidth = 2f)
                drawLine(Color.Black, Offset(0f, totAreaH+tableRowH), Offset(xEnd, totAreaH+tableRowH), strokeWidth = 2f)
                drawLine(Color.Black, Offset(0f, totAreaH+2*tableRowH), Offset(xEnd, totAreaH+2*tableRowH), strokeWidth = 2f)
                drawLine(Color.Black, Offset(0f, totAreaH+3*tableRowH), Offset(xEnd, totAreaH+3*tableRowH), strokeWidth = 2f)

                data.sortedBy{if(type=="L-Section") it.chainage else it.distance}.forEach { p ->
                    val x = mX(if(type=="L-Section") p.chainage else p.distance)
                    val vals = listOf(String.format("%.3f", p.postMonsoon), String.format("%.3f", p.preMonsoon), String.format("%.1f", if(type=="L-Section") p.chainage else p.distance))
                    val colors = listOf(Color(postColor.red, postColor.green, postColor.blue), Color(preColor.red, preColor.green, preColor.blue), Color.Black)
                    vals.forEachIndexed { i, txt ->
                        val cellCenterY = totAreaH + i * tableRowH + tableRowH/2
                        val layoutResult = textMeasurer.measure(txt, style = TextStyle(fontSize = 10.sp, color = colors[i]))
                        rotate(-90f, pivot = Offset(x + 4f, cellCenterY)) {
                            drawText(layoutResult, topLeft = Offset(x + 4f - layoutResult.size.width/2, cellCenterY - layoutResult.size.height/2))
                        }
                    }
                }

                drawRect(Color.White, topLeft = Offset(0f, 0f), size = Size(padLeft, totalDrawH + 50f))
                drawLine(Color.Black, Offset(padLeft, 0f), Offset(padLeft, totAreaH), strokeWidth = 2f)
                for(i in 0..((maxY-minY).toInt())) {
                    val yVal = minY + i
                    val yPos = mY(yVal)
                    if (yPos >= 0 && yPos <= totAreaH) {
                        drawLine(Color.Black, Offset(padLeft - 5f, yPos), Offset(padLeft, yPos), strokeWidth = 1f)
                        val txt = String.format("%.1f", yVal)
                        val layout = textMeasurer.measure(txt, style = TextStyle(fontSize = 10.sp, color = Color.Black))
                        drawText(layout, topLeft = Offset(5f, yPos + 2f))
                    }
                }

                val datumY = mY(minY)
                if (datumY > 0 && datumY < totalDrawH) {
                    drawText(textMeasurer.measure("DATUM=${minY}", style = TextStyle(fontSize=10.sp)), topLeft = Offset(5f, datumY - 15f))
                }

                drawRect(Color.White, topLeft = Offset(0f, totAreaH), size = Size(padLeft, 3 * tableRowH))
                drawLine(Color.Black, Offset(padLeft, totAreaH), Offset(padLeft, totAreaH + 3 * tableRowH), strokeWidth = 2f)
                val labels = listOf("POST RL", "PRE RL", if(type=="L-Section") "CHAINAGE" else "OFFSET")
                labels.forEachIndexed { i, l ->
                    val y = totAreaH + i * tableRowH
                    drawLine(Color.Black, Offset(0f, y), Offset(padLeft, y), strokeWidth = 2f)
                    drawLine(Color.Black, Offset(0f, y + tableRowH), Offset(padLeft, y + tableRowH), strokeWidth = 2f)
                    drawText(textMeasurer.measure(l, style = TextStyle(fontSize=12.sp, fontWeight = FontWeight.Bold)), topLeft = Offset(5f, y + tableRowH/2 - 7f))
                }
            }
        }
    }
}

// --- COMPONENT HELPERS ---
@Composable
fun NumberInput(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(4.dp))
        BasicTextField(
            value = value.toString(),
            onValueChange = { onChange(it.toFloatOrNull() ?: value) },
            textStyle = TextStyle(fontSize = 11.sp, textAlign = TextAlign.Center),
            modifier = Modifier.width(30.dp).background(Color.White, RoundedCornerShape(4.dp)).border(1.dp, Color.Gray, RoundedCornerShape(4.dp)).padding(2.dp)
        )
    }
}

@Composable
fun ColorPickerButton(color: Color, onColorSelected: (Color) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(modifier = Modifier.size(16.dp).background(color, RoundedCornerShape(4.dp)).border(1.dp, Color.Black, RoundedCornerShape(4.dp)).clickable { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(Color.Black, Color.Red, Color.Blue, Color.Green, Color.Gray).forEach { c ->
                DropdownMenuItem(text = { Box(Modifier.size(20.dp).background(c)) }, onClick = { onColorSelected(c); expanded = false })
            }
        }
    }
}