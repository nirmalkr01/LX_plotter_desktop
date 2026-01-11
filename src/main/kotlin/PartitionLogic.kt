import androidx.compose.ui.geometry.Rect
import kotlin.math.max
import kotlin.math.floor

// Represents a slot where a graph can go
data class PartitionSlot(
    val id: String,
    val rect: Rect, // In Pixels
    val xPercent: Float,
    val yPercent: Float,
    val wPercent: Float,
    val hPercent: Float
)

fun calculatePartitions(
    paperWidthPx: Float,
    paperHeightPx: Float,
    marginTopPx: Float,
    marginBottomPx: Float,
    marginLeftPx: Float,
    marginRightPx: Float,
    layoutType: PageLayoutType,
    graphType: String, // "X-Section" or "L-Section"
    config: ReportConfig,
    pxPerMm: Float
): List<PartitionSlot> {

    // 1. Calculate Scale Factor based on A3 Landscape Width (42.0 cm)
    val scale = paperWidthPx / 42.0f
    // The visual gap unit used in layout drawing
    val unitGap = 0.1f * scale

    var safeTop: Float
    var safeBottom: Float
    var safeLeft: Float
    var safeRight: Float

    // --- BOUNDARY CALCULATION ---
    if (layoutType == PageLayoutType.ENGINEERING_STD) {
        // Engineering (Std) Layout Logic
        val annexureHeight = 1.0f * scale
        val annexureBottom = marginTopPx + annexureHeight
        safeTop = annexureBottom + unitGap

        // Bottom Limit: Above Footer Stack
        val rowH = 1.0f * scale
        val footerStackHeight = 4.0f * rowH
        val footerTopY = (paperHeightPx - marginBottomPx) - footerStackHeight
        safeBottom = footerTopY - unitGap

        // Left/Right: Standard Margins
        safeLeft = marginLeftPx + unitGap
        safeRight = (paperWidthPx - marginRightPx) - unitGap

    } else {
        // BLANK LAYOUT Logic
        var currentL = marginLeftPx
        var currentT = marginTopPx
        var currentR = paperWidthPx - marginRightPx
        var currentB = paperHeightPx - marginBottomPx

        // 1. Adjust for Outer Border
        if (config.showOuterBorder) {
            val th = config.outerThickness
            currentL += th
            currentT += th
            currentR -= th
            currentB -= th

            // 2. Adjust for Inner Border (RESTORED AS REQUESTED)
            if (config.showInnerBorder) {
                val gap = config.borderGap * pxPerMm
                val innerTh = config.innerThickness
                currentL += (gap + innerTh)
                currentT += (gap + innerTh)
                currentR -= (gap + innerTh)
                currentB -= (gap + innerTh)
            }
        }

        // 3. Apply the minimal unit gap
        safeLeft = currentL + unitGap
        safeTop = currentT + unitGap
        safeRight = currentR - unitGap
        safeBottom = currentB - unitGap
    }

    val safeW = safeRight - safeLeft
    val safeH = safeBottom - safeTop

    // If space is invalid, return empty
    if (safeW <= 20f || safeH <= 20f) return emptyList()

    // --- GRID GENERATION ---
    val safeWidthMm = safeW / pxPerMm
    val safeHeightMm = safeH / pxPerMm

    val rows: Int
    val cols: Int

    if (graphType == "L-Section") {
        cols = 1
        val targetHeightMm = 75.0f
        rows = max(1, floor(safeHeightMm / targetHeightMm).toInt())
    } else {
        val targetWidthMm = 180.0f
        val targetHeightMm = 75.0f
        cols = max(1, floor(safeWidthMm / targetWidthMm).toInt())
        rows = max(1, floor(safeHeightMm / targetHeightMm).toInt())
    }

    // --- SLOT CREATION ---
    val slots = mutableListOf<PartitionSlot>()
    val cellW = safeW / cols
    val cellH = safeH / rows

    for (r in 0 until rows) {
        for (c in 0 until cols) {
            val pxX = safeLeft + (c * cellW)
            val pxY = safeTop + (r * cellH)
            val finalX = pxX
            val finalY = pxY
            val finalW = cellW
            val finalH = cellH

            slots.add(
                PartitionSlot(
                    id = "slot_${r}_${c}",
                    rect = Rect(finalX, finalY, finalX + finalW, finalY + finalH),
                    xPercent = finalX / paperWidthPx,
                    yPercent = finalY / paperHeightPx,
                    wPercent = finalW / paperWidthPx,
                    hPercent = finalH / paperHeightPx
                )
            )
        }
    }
    return slots
}