import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

@Composable
fun EngineeringCanvas(
    points: List<RiverPoint>, isLSection: Boolean, showPre: Boolean, showPost: Boolean,
    hScale: Double, vScale: Double,
    preColor: Color, postColor: Color, preDotted: Boolean, postDotted: Boolean
) {
    if (points.isEmpty()) return

    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val padding = 50f

        val xValues = if (isLSection) points.map { it.chainage } else points.map { it.distance }
        val allYValues = points.map { it.preMonsoon } + points.map { it.postMonsoon }

        if (allYValues.isEmpty()) return@Canvas

        val minX = xValues.minOrNull() ?: 0.0
        val maxX = xValues.maxOrNull() ?: 100.0
        val minY = floor(allYValues.minOrNull() ?: 0.0) - 1.0
        val maxY = ceil(allYValues.maxOrNull() ?: 10.0) + 1.0

        val xRange = max((maxX - minX).toFloat(), 1f)
        val yRange = max((maxY - minY).toFloat(), 1f)

        fun mapX(v: Double) = padding + ((v - minX) / xRange * (width - 2 * padding)).toFloat()
        fun mapY(v: Double) = height - padding - ((v - minY) / yRange * (height - 2 * padding)).toFloat()

        val gridPathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)

        // Grid & Axes
        val ySteps = 5
        for (i in 0..ySteps) {
            val yVal = minY + (i * (yRange / ySteps))
            val yPos = mapY(yVal)
            drawLine(Color.LightGray, start = Offset(padding, yPos), end = Offset(width - padding, yPos), pathEffect = gridPathEffect)
            drawText(textMeasurer, String.format("%.1f", yVal), Offset(5f, yPos - 10f), TextStyle(fontSize = 10.sp, color = Color.Black))
        }
        val xSteps = 8
        for (i in 0..xSteps) {
            val xVal = minX + (i * (xRange / xSteps))
            val xPos = mapX(xVal)
            drawLine(Color.LightGray, start = Offset(xPos, padding), end = Offset(xPos, height - padding), pathEffect = gridPathEffect)
            drawText(textMeasurer, String.format("%.0f", xVal), Offset(xPos - 10f, height - padding + 5f), TextStyle(fontSize = 10.sp, color = Color.Black))
        }

        if (!isLSection && minX <= 0 && maxX >= 0) {
            val zeroPos = mapX(0.0)
            drawLine(Color.Black, start = Offset(zeroPos, padding), end = Offset(zeroPos, height - padding), strokeWidth = 2f)
        }

        drawLine(Color.Black, start = Offset(padding, height - padding), end = Offset(width - padding, height - padding), strokeWidth = 2f)
        drawLine(Color.Black, start = Offset(padding, padding), end = Offset(padding, height - padding), strokeWidth = 2f)

        fun drawSeries(getColor: (RiverPoint) -> Double, color: Color, isDotted: Boolean) {
            val path = Path()
            var first = true
            points.forEach { p ->
                val v = getColor(p)
                val x = mapX(if (isLSection) p.chainage else p.distance)
                val y = mapY(v)
                if (first) { path.moveTo(x, y); first = false } else { path.lineTo(x, y) }
                drawCircle(color, radius = 3f, center = Offset(x, y))
            }
            val effect = if(isDotted) PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f) else null
            drawPath(path, color, style = Stroke(width = 2f, pathEffect = effect))
        }

        if (showPre) drawSeries({ it.preMonsoon }, preColor, preDotted)
        if (showPost) drawSeries({ it.postMonsoon }, postColor, postDotted)
    }
}