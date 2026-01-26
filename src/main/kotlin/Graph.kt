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
// FIX: Reduced Max Size to prevent Constraints Crash on Skiko/AWT
const val MAX_CANVAS_SIZE = 20000.0

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

        if (containerWidth <= 0 || containerHeight <= 0) return@BoxWithConstraints

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
            val finalW = max(graphRequiredWidth, containerWidth.toDouble()).coerceAtLeast(10.0).dp
            val finalH = max(graphRequiredHeight, containerHeight.toDouble()).coerceAtLeast(10.0).dp

            Canvas(modifier = Modifier.size(finalW, finalH)) {
                val h = size.height
                val w = size.width

                fun mapX(v: Double) = (padding + (v - minX) * pxPerMeterX).toFloat()
                fun mapY(v: Double) = (h - padding - (v - minY) * pxPerMeterY).toFloat()

                val zeroY = mapY(minY)
                val zeroX = mapX(minX)

                if (zeroY.isNaN() || zeroX.isNaN() || !zeroY.isFinite() || !zeroX.isFinite()) return@Canvas

                // Main Axes
                drawLine(Color.Black, Offset(padding.toFloat(), zeroY), Offset((w - padding).toFloat(), zeroY), 2f)
                drawLine(Color.Black, Offset(zeroX, padding.toFloat()), Offset(zeroX, h - padding.toFloat()), 2f)

                val gridColor = Color.LightGray.copy(alpha = 0.5f)

                if (showGrid) {
                    val axisStartX = padding.toFloat()
                    val axisEndX = (w - padding).toFloat()
                    val axisStartY = zeroY
                    val axisEndY = padding.toFloat()
                    val visualGridStep = currentPxPerCm.toFloat()

                    if (visualGridStep > 1.5f) {
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
                }

                val distinctX = xValues.distinct().sorted()
                distinctX.forEach { xVal ->
                    val xPos = mapX(xVal)
                    if (xPos >= padding && xPos <= w - padding) {
                        drawLine(Color.Black, Offset(xPos, zeroY), Offset(xPos, zeroY + 5f), 1f)

                        val labelX = xPos - 15f
                        val labelY = zeroY + 10f

                        // CRITICAL FIX: Ensure the label coordinates don't force negative layout widths
                        if (labelX >= 0f && labelY >= 0f && labelX < w - 20f && labelY < h - 15f) {
                            try {
                                drawText(
                                    textMeasurer,
                                    String.format("%.1f", xVal),
                                    Offset(labelX, labelY),
                                    TextStyle(fontSize = 10.sp, color = Color.Black)
                                )
                            } catch (e: Exception) { /* Silently fail to prevent crash */ }
                        }

                        val fadedColor = Color.LightGray.copy(alpha = 0.5f)
                        points.filter { (if (isLSection) it.chainage else it.distance) == xVal }.forEach { p ->
                            val yPre = mapY(p.preMonsoon)
                            val yPost = mapY(p.postMonsoon)
                            if (showPre && yPre.isFinite() && yPre >= 0) drawLine(fadedColor, Offset(xPos, yPre), Offset(xPos, zeroY), strokeWidth = 1f)
                            if (showPost && yPost.isFinite() && yPost >= 0) drawLine(fadedColor, Offset(xPos, yPost), Offset(xPos, zeroY), strokeWidth = 1f)
                        }
                    }
                }

                val ySteps = ((maxY - minY)).toInt()
                for(i in 0..ySteps) {
                    val yVal = minY + i
                    val yPos = mapY(yVal)
                    if (yPos >= padding && yPos <= h - padding) {
                        drawLine(Color.Black, Offset(zeroX - 5f, yPos), Offset(zeroX, yPos), 1f)

                        val labelX = zeroX - 35f
                        val labelY = yPos - 6f
                        // CRITICAL FIX: Similar boundary check for Y axis text
                        if (labelX >= 0f && labelY >= 0f && labelX < w - 40f && labelY < h - 10f) {
                            try {
                                drawText(
                                    textMeasurer,
                                    String.format("%.1f", yVal),
                                    Offset(labelX, labelY),
                                    TextStyle(fontSize = 10.sp, color = Color.Black)
                                )
                            } catch (e: Exception) { /* Silently fail to prevent crash */ }
                        }
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

                        if (x.isFinite() && y.isFinite()) {
                            if (first) { path.moveTo(x, y); first = false } else { path.lineTo(x, y) }
                            if (showPoints) drawCircle(color, radius = strokeW + 1.5f, center = Offset(x, y))
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
                    val visualGridStep = currentPxPerCm.toFloat()
                    val axisOriginX = padding.toFloat()

                    drawRect(rulerBg, topLeft = Offset(0f, 0f), size = Size(w, rulerWidth))
                    drawLine(Color.Gray, Offset(0f, rulerWidth), Offset(w, rulerWidth))

                    if (visualGridStep > 5f) {
                        val totalCmX = ((w - axisOriginX) / visualGridStep).toInt().coerceAtMost(2000)
                        for (i in 0..totalCmX) {
                            val xPos = axisOriginX + (i * visualGridStep)
                            if (xPos <= w - 5f && xPos >= 0) {
                                drawLine(Color.Black, Offset(xPos, 0f), Offset(xPos, rulerWidth * 0.6f), 1.5f)
                                if (xPos < w - 20f) {
                                    drawText(textMeasurer, "$i", Offset(xPos + 3f, 2f), TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black))
                                }
                            }
                        }

                        val axisOriginY = zeroY
                        drawRect(rulerBg, topLeft = Offset(0f, 0f), size = Size(rulerWidth, h))
                        drawLine(Color.Gray, Offset(rulerWidth, 0f), Offset(rulerWidth, h))

                        val totalCmY = ((axisOriginY - padding) / visualGridStep).toInt().coerceAtMost(1000)
                        for (i in 0..totalCmY) {
                            val yPos = (axisOriginY - (i * visualGridStep)).toFloat()
                            if (yPos >= 0 && yPos <= h) {
                                drawLine(Color.Black, Offset(0f, yPos), Offset(rulerWidth * 0.6f, yPos), 1.5f)
                                if (yPos > 10f) {
                                    drawText(textMeasurer, "$i", Offset(2f, yPos + 2f), TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black))
                                }
                            }
                        }
                    }
                    drawRect(Color.White, topLeft = Offset(0f,0f), size = Size(rulerWidth, rulerWidth))
                    drawText(textMeasurer, "cm", Offset(8f, 8f), TextStyle(fontSize=10.sp, fontWeight = FontWeight.Bold))
                }
            }
        }

        if (enableVScroll) VerticalScrollbar(adapter = rememberScrollbarAdapter(verticalScroll), modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        if (enableHScroll) HorizontalScrollbar(adapter = rememberScrollbarAdapter(horizontalScroll), modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth())
    }
}