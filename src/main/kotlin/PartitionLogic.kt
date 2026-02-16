// FILE: D:\LX_plotter_desktop\src\main\kotlin\PartitionLogic.kt
import androidx.compose.ui.geometry.Rect
import kotlin.math.max
import kotlin.math.floor

// Represents a slot where a graph can go
// UPDATED: Now uses Millimeters (MM) as the source of truth
data class PartitionSlot(
    val id: String,
    val xMm: Float,
    val yMm: Float,
    val widthMm: Float,
    val heightMm: Float
) {
    // Helper to get screen rect for a specific Zoom level (pxPerMm)
    // This allows the UI to draw it correctly regardless of zoom
    fun getScreenRect(pxPerMm: Float): Rect {
        val l = xMm * pxPerMm
        val t = yMm * pxPerMm
        val r = (xMm + widthMm) * pxPerMm
        val b = (yMm + heightMm) * pxPerMm
        return Rect(l, t, r, b)
    }
}

fun calculatePartitions(
    paperWidthMm: Float, // Input in MM
    paperHeightMm: Float, // Input in MM
    marginTopMm: Float,
    marginBottomMm: Float,
    marginLeftMm: Float,
    marginRightMm: Float,
    layoutType: PageLayoutType,
    graphType: String, // "X-Section" or "L-Section"
    config: ReportConfig
): List<PartitionSlot> {

    // The visual gap unit (1mm)
    val unitGapMm = 1.0f

    var safeTopMm: Float
    var safeBottomMm: Float
    var safeLeftMm: Float
    var safeRightMm: Float

    // --- BOUNDARY CALCULATION (IN MM) ---
    if (layoutType == PageLayoutType.ENGINEERING_STD) {
        // Engineering (Std) Layout Logic
        // Defined relative to A3 width (420mm) standard
        val scaleRatio = paperWidthMm / 420.0f

        val annexureHeight = 10.0f * scaleRatio
        val footerHeight = 40.0f * scaleRatio

        safeTopMm = marginTopMm + annexureHeight + unitGapMm
        safeBottomMm = (paperHeightMm - marginBottomMm) - footerHeight - unitGapMm

        safeLeftMm = marginLeftMm + unitGapMm
        safeRightMm = (paperWidthMm - marginRightMm) - unitGapMm

    } else {
        // BLANK LAYOUT Logic
        var currentL = marginLeftMm
        var currentT = marginTopMm
        var currentR = paperWidthMm - marginRightMm
        var currentB = paperHeightMm - marginBottomMm

        // 1. Adjust for Outer Border
        if (config.showOuterBorder) {
            val th = 0.5f // Approx border thickness in mm
            currentL += th
            currentT += th
            currentR -= th
            currentB -= th

            // 2. Adjust for Inner Border
            if (config.showInnerBorder) {
                val gap = config.borderGap // Config value is treated as MM
                val innerTh = 0.5f
                currentL += (gap + innerTh)
                currentT += (gap + innerTh)
                currentR -= (gap + innerTh)
                currentB -= (gap + innerTh)
            }
        }

        // 3. Apply the minimal unit gap
        safeLeftMm = currentL + unitGapMm
        safeTopMm = currentT + unitGapMm
        safeRightMm = currentR - unitGapMm
        safeBottomMm = currentB - unitGapMm
    }

    val safeWMm = safeRightMm - safeLeftMm
    val safeHMm = safeBottomMm - safeTopMm

    // If space is invalid, return empty
    if (safeWMm <= 10f || safeHMm <= 10f) return emptyList()

    // --- GRID GENERATION ---
    val rows: Int
    val cols: Int

    if (graphType == "L-Section") {
        cols = 1
        val targetHeightMm = 75.0f
        rows = max(1, floor(safeHMm / targetHeightMm).toInt())
    } else {
        val targetWidthMm = 180.0f // Approx width for X-Sec
        val targetHeightMm = 75.0f
        cols = max(1, floor(safeWMm / targetWidthMm).toInt())
        rows = max(1, floor(safeHMm / targetHeightMm).toInt())
    }

    // --- SLOT CREATION ---
    val slots = mutableListOf<PartitionSlot>()
    val cellWMm = safeWMm / cols
    val cellHMm = safeHMm / rows

    for (r in 0 until rows) {
        for (c in 0 until cols) {
            val finalX = safeLeftMm + (c * cellWMm)
            val finalY = safeTopMm + (r * cellHMm)

            slots.add(
                PartitionSlot(
                    id = "slot_${r}_${c}",
                    xMm = finalX,
                    yMm = finalY,
                    widthMm = cellWMm,
                    heightMm = cellHMm
                )
            )
        }
    }
    return slots
}