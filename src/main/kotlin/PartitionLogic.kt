import androidx.compose.ui.geometry.Rect
import kotlin.math.max

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
    // This aligns with PageLayout.kt scale logic
    val scale = paperWidthPx / 42.0f

    // The requested 0.1 unit gap
    val unitGap = 0.1f * scale

    var safeTop: Float
    var safeBottom: Float
    var safeLeft: Float
    var safeRight: Float

    // --- BOUNDARY CALCULATION ---
    if (layoutType == PageLayoutType.ENGINEERING_STD) {
        // Engineering (Std) Logic
        // Top Limit: Bottom of Annexure Box
        // Annexure box height is 1.0 unit (from PageLayout.kt logic)
        val annexureHeight = 1.0f * scale
        safeTop = marginTopPx + annexureHeight + unitGap

        // Bottom Limit: Top of "All dimensions in meters" (D5) box
        // D5 sits above the 3-row footer.
        // Footer = 3.0 units. D5 = 1.0 unit.
        // The D5 top edge is 4.0 units from the bottom margin.
        val footerIntrusion = 4.0f * scale
        safeBottom = (paperHeightPx - marginBottomPx) - footerIntrusion - unitGap

        // Left/Right: Margins + Gap
        safeLeft = marginLeftPx + unitGap
        safeRight = (paperWidthPx - marginRightPx) - unitGap

    } else {
        // BLANK LAYOUT Logic
        // Determine the innermost active border
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

            // 2. Adjust for Inner Border (only if outer is on)
            if (config.showInnerBorder) {
                val gap = config.borderGap * pxPerMm
                val innerTh = config.innerThickness
                currentL += (gap + innerTh)
                currentT += (gap + innerTh)
                currentR -= (gap + innerTh)
                currentB -= (gap + innerTh)
            }
        }

        // 3. Apply the 0.1 unit gap from the calculated boundary
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
    val rows: Int
    val cols: Int

    if (graphType == "L-Section") {
        // L-Sections take full width, split vertically into 3
        rows = 3
        cols = 1
    } else {
        // X-Sections: Dynamic grid based on aspect ratio
        val aspectRatio = safeW / safeH
        if (aspectRatio > 1.2) {
            // Landscape-ish area -> 2 Columns x 3 Rows
            cols = 2
            rows = 3
        } else {
            // Portrait-ish area -> 1 Column x 4 Rows
            cols = 1
            rows = 4
        }
    }

    // --- SLOT CREATION ---
    val slots = mutableListOf<PartitionSlot>()
    val cellW = safeW / cols
    val cellH = safeH / rows

    for (r in 0 until rows) {
        for (c in 0 until cols) {
            val pxX = safeLeft + (c * cellW)
            val pxY = safeTop + (r * cellH)

            // No internal padding between slots, just strict math
            // (The unitGap handles the outer boundary separation)
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