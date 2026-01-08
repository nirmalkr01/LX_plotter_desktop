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
    // This aligns with PageLayout.kt scale logic to respect the borders/footer size
    val scale = paperWidthPx / 42.0f

    // The gap between content and the border lines
    val unitGap = 0.1f * scale

    var safeTop: Float
    var safeBottom: Float
    var safeLeft: Float
    var safeRight: Float

    // --- BOUNDARY CALCULATION (Where can we draw?) ---
    if (layoutType == PageLayoutType.ENGINEERING_STD) {
        // Engineering (Std) Layout Logic
        // Top Limit: Bottom of Annexure Box (Height ~1.0 unit)
        val annexureHeight = 1.0f * scale
        safeTop = marginTopPx + annexureHeight + unitGap

        // Bottom Limit: Top of "All dimensions in meters" (D5) box
        // The Footer stack height is roughly 4.0 scale units from the bottom margin
        val footerIntrusion = 4.0f * scale
        safeBottom = (paperHeightPx - marginBottomPx) - footerIntrusion - unitGap

        // Left/Right: Margins + Gap
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

            // 2. Adjust for Inner Border
            if (config.showInnerBorder) {
                val gap = config.borderGap * pxPerMm
                val innerTh = config.innerThickness
                currentL += (gap + innerTh)
                currentT += (gap + innerTh)
                currentR -= (gap + innerTh)
                currentB -= (gap + innerTh)
            }
        }

        // 3. Apply the gap
        safeLeft = currentL + unitGap
        safeTop = currentT + unitGap
        safeRight = currentR - unitGap
        safeBottom = currentB - unitGap
    }

    val safeW = safeRight - safeLeft
    val safeH = safeBottom - safeTop

    // If space is invalid, return empty
    if (safeW <= 20f || safeH <= 20f) return emptyList()

    // --- GRID GENERATION (FIXED SIZE LOGIC) ---
    // Instead of aspect ratio, we use fixed physical target sizes (in mm)
    // to ensure A3 fits more graphs than A4, and A2 fits more than A3.

    // Convert safe dimensions to mm
    val safeWidthMm = safeW / pxPerMm
    val safeHeightMm = safeH / pxPerMm

    val rows: Int
    val cols: Int

    if (graphType == "L-Section") {
        // L-Sections take full width, split vertically
        // Target height ~75mm per strip
        cols = 1
        val targetHeightMm = 75.0f
        rows = max(1, floor(safeHeightMm / targetHeightMm).toInt())
    } else {
        // X-Section logic
        // Target Box Size: Width ~180mm (A4 width), Height ~75mm
        // This ensures on A4 (W~180) we get 1 col, on A3 (W~390) we get 2 cols.
        val targetWidthMm = 180.0f
        val targetHeightMm = 75.0f

        cols = max(1, floor(safeWidthMm / targetWidthMm).toInt())
        rows = max(1, floor(safeHeightMm / targetHeightMm).toInt())
    }

    // --- SLOT CREATION ---
    val slots = mutableListOf<PartitionSlot>()

    // Divide the actual available space evenly among the calculated rows/cols
    val cellW = safeW / cols
    val cellH = safeH / rows

    for (r in 0 until rows) {
        for (c in 0 until cols) {
            val pxX = safeLeft + (c * cellW)
            val pxY = safeTop + (r * cellH)

            // Calculate final slot rect
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