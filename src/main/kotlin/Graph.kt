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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

// --- PHYSICAL CONSTANTS ---
const val PX_PER_CM = 38.0
const val MAX_CANVAS_SIZE = 30000.0

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
    postDotted: Boolean
) {
    if (points.isEmpty()) return

    val xValues = if (isLSection) points.map { it.chainage } else points.map { it.distance }
    val allYValues = (if(showPre) points.map { it.preMonsoon } else emptyList()) +
            (if(showPost) points.map { it.postMonsoon } else emptyList())

    val minX = xValues.minOrNull() ?: 0.0
    val maxX = xValues.maxOrNull() ?: 100.0
    val minY = if(allYValues.isNotEmpty()) floor(allYValues.minOrNull()!!) - 1.0 else 0.0
    val maxY = if(allYValues.isNotEmpty()) ceil(allYValues.maxOrNull()!!) + 1.0 else 10.0

    // ENGINEERING SCALING LOGIC
    val safeHScale = if (hScale <= 0.001) 0.001 else hScale
    val safeVScale = if (vScale <= 0.001) 0.001 else vScale

    val cmPerMeterX = 100.0 / safeHScale
    val cmPerMeterY = 100.0 / safeVScale

    val pxPerMeterX = cmPerMeterX * PX_PER_CM
    val pxPerMeterY = cmPerMeterY * PX_PER_CM

    val padding = 60.0

    // Total required size in pixels
    val rawGraphW = (maxX - minX) * pxPerMeterX + (2 * padding)
    val rawGraphH = (maxY - minY) * pxPerMeterY + (2 * padding)

    val graphRequiredWidth = rawGraphW.coerceAtMost(MAX_CANVAS_SIZE)
    val graphRequiredHeight = rawGraphH.coerceAtMost(MAX_CANVAS_SIZE)

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(Color.White)
    ) {
        val containerWidth = constraints.maxWidth.toFloat()
        val containerHeight = constraints.maxHeight.toFloat()

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
            val finalW = max(graphRequiredWidth, containerWidth.toDouble()).dp
            val finalH = max(graphRequiredHeight, containerHeight.toDouble()).dp

            Canvas(modifier = Modifier.size(finalW, finalH)) {
                val h = size.height

                // COORDINATE MAPPING
                fun mapX(v: Double) = (padding + (v - minX) * pxPerMeterX).toFloat()
                fun mapY(v: Double) = (h - padding - (v - minY) * pxPerMeterY).toFloat()

                // Draw X-Axis Line (at Y=minY)
                val zeroY = mapY(minY)
                drawLine(Color.Black, Offset(padding.toFloat(), zeroY), Offset((size.width - padding).toFloat(), zeroY), 2f)

                // Draw Y-Axis Line (at X=minX)
                val zeroX = mapX(minX)
                drawLine(Color.Black, Offset(zeroX, padding.toFloat()), Offset(zeroX, h - padding.toFloat()), 2f)

                // Draw X-Axis Ticks & Labels
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

                        // Drop Lines
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

                // Draw Y-Axis Ticks
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

                // Draw Data Series
                fun drawSeries(getColor: (RiverPoint) -> Double, color: Color, isDotted: Boolean) {
                    val path = Path()
                    var first = true
                    val sortedPoints = points.sortedBy { if (isLSection) it.chainage else it.distance }

                    sortedPoints.forEach { p ->
                        val v = getColor(p)
                        val x = mapX(if (isLSection) p.chainage else p.distance)
                        val y = mapY(v)

                        if (x <= size.width && y >= 0) {
                            if (first) { path.moveTo(x, y); first = false } else { path.lineTo(x, y) }
                            drawCircle(color, radius = 3f, center = Offset(x, y))
                        }
                    }
                    val effect = if(isDotted) PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f) else null
                    drawPath(path, color, style = Stroke(width = 2f, pathEffect = effect))
                }

                if (showPre) drawSeries({ it.preMonsoon }, preColor, preDotted)
                if (showPost) drawSeries({ it.postMonsoon }, postColor, postDotted)
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