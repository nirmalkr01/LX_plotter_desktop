import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.abs

data class AlignmentLine(
    val start: Offset,
    val end: Offset,
    val isVertical: Boolean
)

// Threshold for snapping/showing guides (in pixels)
const val SNAP_THRESHOLD = 5f

/**
 * Calculates alignment guides between the dragging item and other items/page boundaries.
 */
fun calculateAlignmentGuides(
    draggingRect: Rect,
    otherRects: List<Rect>,
    pageWidth: Float,
    pageHeight: Float
): List<AlignmentLine> {
    val lines = mutableListOf<AlignmentLine>()

    // Key points of the dragging item
    val dLeft = draggingRect.left
    val dRight = draggingRect.right
    val dTop = draggingRect.top
    val dBottom = draggingRect.bottom
    val dCenterX = draggingRect.center.x
    val dCenterY = draggingRect.center.y

    // Helper to add vertical line
    fun addVLine(x: Float) {
        lines.add(AlignmentLine(Offset(x, 0f), Offset(x, pageHeight), true))
    }

    // Helper to add horizontal line
    fun addHLine(y: Float) {
        lines.add(AlignmentLine(Offset(0f, y), Offset(pageWidth, y), false))
    }

    // 1. Page Center Alignment
    val pageCenterX = pageWidth / 2f
    val pageCenterY = pageHeight / 2f

    if (abs(dCenterX - pageCenterX) < SNAP_THRESHOLD) addVLine(pageCenterX)
    if (abs(dCenterY - pageCenterY) < SNAP_THRESHOLD) addHLine(pageCenterY)

    // 2. Object-to-Object Alignment
    // Collect interesting X and Y coordinates from other objects
    val interestingX = mutableListOf<Float>()
    val interestingY = mutableListOf<Float>()

    otherRects.forEach { r ->
        interestingX.add(r.left)
        interestingX.add(r.right)
        interestingX.add(r.center.x)
        interestingY.add(r.top)
        interestingY.add(r.bottom)
        interestingY.add(r.center.y)
    }

    // Check Vertical Alignments (Left, Right, Center against others)
    if (interestingX.any { abs(dLeft - it) < SNAP_THRESHOLD }) addVLine(dLeft)
    if (interestingX.any { abs(dRight - it) < SNAP_THRESHOLD }) addVLine(dRight)
    if (interestingX.any { abs(dCenterX - it) < SNAP_THRESHOLD }) addVLine(dCenterX)

    // Check Horizontal Alignments (Top, Bottom, Center against others)
    if (interestingY.any { abs(dTop - it) < SNAP_THRESHOLD }) addHLine(dTop)
    if (interestingY.any { abs(dBottom - it) < SNAP_THRESHOLD }) addHLine(dBottom)
    if (interestingY.any { abs(dCenterY - it) < SNAP_THRESHOLD }) addHLine(dCenterY)

    return lines
}

@Composable
fun AlignmentOverlay(lines: List<AlignmentLine>) {
    if (lines.isEmpty()) return

    Canvas(modifier = Modifier.fillMaxSize()) {
        val stroke = Stroke(
            width = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        )
        val color = Color(0xFFFF00FF) // Magenta for high visibility (Canva style)

        lines.forEach { line ->
            drawLine(
                color = color,
                start = line.start,
                end = line.end,
                strokeWidth = 1.dp.toPx(),
                pathEffect = stroke.pathEffect
            )
        }
    }
}