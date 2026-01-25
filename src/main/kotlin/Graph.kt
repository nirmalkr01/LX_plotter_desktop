import androidx.compose.foundation.Canvas
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

const val PX_PER_CM = 120.0
const val MAX_CANVAS_SIZE = 50000.0

@Composable
fun EngineeringCanvas(
    points: List<RiverPoint>,
    isLSection: Boolean,
    showPre: Boolean,
    showPost: Boolean,
    hScale: Double,
    vScale: Double,
    preColor: Color,
    postColor: Color,
    preDotted: Boolean,
    postDotted: Boolean,
    preWidth: Float,
    postWidth: Float,
    preShowPoints: Boolean,
    postShowPoints: Boolean,
    showRuler: Boolean,
    showGrid: Boolean,
    zoomFactor: Float = 1f
) {
    if (points.isEmpty()) return

    val xValues = if (isLSection) points.map { it.chainage } else points.map { it.distance }
    val allYValues = (if(showPre) points.map { it.preMonsoon } else emptyList()) +
            (if(showPost) points.map { it.postMonsoon } else emptyList())

    val minX = xValues.minOrNull() ?: 0.0
    val maxX = xValues.maxOrNull() ?: 100.0
    val minY = if(allYValues.isNotEmpty()) floor(allYValues.minOrNull()!!) - 1.0 else 0.0
    val maxY = if(allYValues.isNotEmpty()) ceil(allYValues.maxOrNull()!!) + 1.0 else 10.0

    val safeHScale = if (hScale <= 0.001) 0.001 else hScale
    val safeVScale = if (vScale <= 0.001) 0.001 else vScale

    val cmPerMeterX = 100.0 / safeHScale
    val cmPerMeterY = 100.0 / safeVScale

    val currentPxPerCm = PX_PER_CM * zoomFactor
    val pxPerMeterX = cmPerMeterX * currentPxPerCm
    val pxPerMeterY = cmPerMeterY * currentPxPerCm

    val padding = 60.0

    val rawGraphW = (maxX - minX) * pxPerMeterX + (2 * padding)
    val rawGraphH = (maxY - minY) * pxPerMeterY + (2 * padding)

    val graphRequiredWidth = rawGraphW.coerceAtMost(MAX_CANVAS_SIZE)
    val graphRequiredHeight = rawGraphH.coerceAtMost(MAX_CANVAS_SIZE)

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(Color.White)
    ) {
        val containerWidth = constraints.maxWidth.toFloat()
        val containerHeight = constraints.maxHeight.toFloat()

        // FIX: Zoom Crash - If container is smaller than padding or negative, skip drawing
        if (containerWidth <= padding || containerHeight <= padding) return@BoxWithConstraints

        val enableHScroll = graphRequiredWidth > containerWidth
        val enableVScroll = graphRequiredHeight > containerHeight

        val horizontalScroll = rememberScrollState()
        val verticalScroll = rememberScrollState()
        val textMeasurer = rememberTextMeasurer()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (enableHScroll) Modifier.horizontalScroll(horizontalScroll) else Modifier)
                .then(if (enableVScroll) Modifier.verticalScroll(verticalScroll) else Modifier)
        ) {
            // FIX: Zoom Crash - Ensure size is always positive
            val finalW = max(graphRequiredWidth, containerWidth.toDouble()).coerceAtLeast(100.0).dp
            val finalH = max(graphRequiredHeight, containerHeight.toDouble()).coerceAtLeast(100.0).dp

            Canvas(modifier = Modifier.size(finalW, finalH)) {
                val h = size.height

                fun mapX(v: Double) = (padding + (v - minX) * pxPerMeterX).toFloat()
                fun mapY(v: Double) = (h - padding - (v - minY) * pxPerMeterY).toFloat()

                val zeroY = mapY(minY)
                drawLine(Color.Black, Offset(padding.toFloat(), zeroY), Offset((size.width - padding).toFloat(), zeroY), 2f)

                val zeroX = mapX(minX)
                drawLine(Color.Black, Offset(zeroX, padding.toFloat()), Offset(zeroX, h - padding.toFloat()), 2f)

                val gridColor = Color.LightGray.copy(alpha = 0.5f)

                if (showGrid) {
                    val axisStartX = padding.toFloat()
                    val axisEndX = (size.width - padding).toFloat()
                    val axisStartY = zeroY
                    val axisEndY = padding.toFloat()
                    val visualGridStep = currentPxPerCm.toFloat()

                    var xGrid = axisStartX + visualGridStep
                    while (xGrid < axisEndX) {
                        drawLine(gridColor, Offset(xGrid, axisStartY), Offset(xGrid, axisEndY), 1f)
                        xGrid += visualGridStep
                    }

                    var yGrid = axisStartY - visualGridStep
                    while (yGrid > axisEndY) {
                        drawLine(gridColor, Offset(axisStartX, yGrid), Offset(axisEndX, yGrid), 1f)
                        yGrid -= visualGridStep
                    }
                }

                val distinctX = xValues.distinct().sorted()
                distinctX.forEach { xVal ->
                    val xPos = mapX(xVal)
                    if (xPos >= 0 && xPos <= size.width) {
                        drawLine(Color.Black, Offset(xPos, zeroY), Offset(xPos, zeroY + 5f), 1f)
                        drawText(
                            textMeasurer,
                            String.format("%.1f", xVal),
                            Offset(xPos - 15f, zeroY + 10f),
                            TextStyle(fontSize = 10.sp, color = Color.Black)
                        )

                        val fadedColor = Color.LightGray.copy(alpha = 0.5f)
                        points.filter { (if (isLSection) it.chainage else it.distance) == xVal }.forEach { p ->
                            if (showPre) {
                                val yPre = mapY(p.preMonsoon)
                                drawLine(fadedColor, Offset(xPos, yPre), Offset(xPos, zeroY), strokeWidth = 1f)
                            }
                            if (showPost) {
                                val yPost = mapY(p.postMonsoon)
                                drawLine(fadedColor, Offset(xPos, yPost), Offset(xPos, zeroY), strokeWidth = 1f)
                            }
                        }
                    }
                }

                val ySteps = ((maxY - minY)).toInt()
                for(i in 0..ySteps) {
                    val yVal = minY + i
                    val yPos = mapY(yVal)
                    if (yPos >= 0 && yPos <= size.height) {
                        drawLine(Color.Black, Offset(zeroX - 5f, yPos), Offset(zeroX, yPos), 1f)
                        drawText(
                            textMeasurer,
                            String.format("%.1f", yVal),
                            Offset(zeroX - 35f, yPos - 6f),
                            TextStyle(fontSize = 10.sp, color = Color.Black)
                        )
                    }
                }

                fun drawSeries(getColor: (RiverPoint) -> Double, color: Color, isDotted: Boolean, strokeW: Float, showPoints: Boolean) {
                    val path = Path()
                    var first = true
                    val sortedPoints = points.sortedBy { if (isLSection) it.chainage else it.distance }

                    sortedPoints.forEach { p ->
                        val v = getColor(p)
                        val x = mapX(if (isLSection) p.chainage else p.distance)
                        val y = mapY(v)

                        if (x <= size.width && y >= 0) {
                            if (first) { path.moveTo(x, y); first = false } else { path.lineTo(x, y) }
                            if (showPoints) {
                                drawCircle(color, radius = strokeW + 2f, center = Offset(x, y))
                            }
                        }
                    }
                    val effect = if(isDotted) PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f) else null
                    drawPath(path, color, style = Stroke(width = strokeW, pathEffect = effect))
                }

                if (showPre) drawSeries({ it.preMonsoon }, preColor, preDotted, preWidth, preShowPoints)
                if (showPost) drawSeries({ it.postMonsoon }, postColor, postDotted, postWidth, postShowPoints)

                if (showRuler) {
                    val rulerWidth = 35f
                    val rulerBg = Color(0xFFF5F5F5)
                    val tickColor = Color.Black
                    val visualGridStep = currentPxPerCm.toFloat()
                    val axisOriginX = padding.toFloat()

                    drawRect(rulerBg, topLeft = Offset(0f, 0f), size = Size(size.width, rulerWidth))
                    drawLine(Color.Gray, Offset(0f, rulerWidth), Offset(size.width, rulerWidth))

                    val totalCmX = ((size.width - axisOriginX) / visualGridStep).toInt()

                    for (i in 0..totalCmX) {
                        val xPos = axisOriginX + (i * visualGridStep)
                        if (xPos <= size.width) {
                            drawLine(tickColor, Offset(xPos, 0f), Offset(xPos, rulerWidth * 0.6f), 1.5f)
                            drawText(textMeasurer, "$i", Offset(xPos + 3f, 2f), TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black))
                            for (sub in 1..9) {
                                val xSub = xPos + (sub * (visualGridStep / 10.0f))
                                if (xSub <= size.width) {
                                    val hLine = if (sub == 5) rulerWidth * 0.4f else rulerWidth * 0.25f
                                    drawLine(tickColor, Offset(xSub, 0f), Offset(xSub, hLine), 1f)
                                }
                            }
                        }
                    }

                    val axisOriginY = zeroY
                    drawRect(rulerBg, topLeft = Offset(0f, 0f), size = Size(rulerWidth, size.height))
                    drawLine(Color.Gray, Offset(rulerWidth, 0f), Offset(rulerWidth, size.height))

                    val totalCmY = ((axisOriginY - padding) / visualGridStep).toInt()

                    for (i in 0..totalCmY) {
                        val yPos = axisOriginY - (i * visualGridStep)
                        if (yPos >= 0) {
                            drawLine(tickColor, Offset(0f, yPos), Offset(rulerWidth * 0.6f, yPos), 1.5f)
                            drawText(textMeasurer, "$i", Offset(2f, yPos + 2f), TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black))
                            for (sub in 1..9) {
                                val ySub = yPos - (sub * (visualGridStep / 10.0f))
                                if (ySub >= 0) {
                                    val wLine = if (sub == 5) rulerWidth * 0.4f else rulerWidth * 0.25f
                                    drawLine(tickColor, Offset(0f, ySub), Offset(wLine, ySub), 1f)
                                }
                            }
                        }
                    }
                    drawRect(Color.White, topLeft = Offset(0f,0f), size = Size(rulerWidth, rulerWidth))
                    drawText(textMeasurer, "cm", Offset(8f, 8f), TextStyle(fontSize=10.sp, fontWeight = FontWeight.Bold))
                }
            }
        }

        if (enableVScroll) {
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(verticalScroll),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
            )
        }
        if (enableHScroll) {
            HorizontalScrollbar(
                adapter = rememberScrollbarAdapter(horizontalScroll),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            )
        }
    }
}