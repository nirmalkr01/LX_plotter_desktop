import androidx.compose.ui.geometry.Rect
import kotlin.math.max
import kotlin.math.floor

// Represents a slot where a graph can go
data class PartitionSlot(
    val id: String,
    val xMm: Float,
    val yMm: Float,
    val widthMm: Float,
    val heightMm: Float
) {
    // Helper to get screen rect for a specific Zoom level (pxPerMm)
    fun getScreenRect(pxPerMm: Float): Rect {
        val l = xMm * pxPerMm
        val t = yMm * pxPerMm
        val r = (xMm + widthMm) * pxPerMm
        val b = (yMm + heightMm) * pxPerMm
        return Rect(l, t, r, b)
    }
}

// Data structures for Placement Output
data class FinalPlacementPosition(val xMm: Float, val yMm: Float)

data class UpdatedPartitionLayout(
    val partitions: List<PartitionSlot>,
    val placement: FinalPlacementPosition,
    val mergedSlot: PartitionSlot
)

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

    val unitGapMm = 1.0f
    var safeTopMm: Float
    var safeBottomMm: Float
    var safeLeftMm: Float
    var safeRightMm: Float

    // --- BOUNDARY CALCULATION (IN MM) ---
    if (layoutType == PageLayoutType.ENGINEERING_STD) {
        val scaleRatio = paperWidthMm / 420.0f
        val annexureHeight = 10.0f * scaleRatio
        val footerHeight = 40.0f * scaleRatio

        safeTopMm = marginTopMm + annexureHeight + unitGapMm
        safeBottomMm = (paperHeightMm - marginBottomMm) - footerHeight - unitGapMm
        safeLeftMm = marginLeftMm + unitGapMm
        safeRightMm = (paperWidthMm - marginRightMm) - unitGapMm
    } else {
        var currentL = marginLeftMm
        var currentT = marginTopMm
        var currentR = paperWidthMm - marginRightMm
        var currentB = paperHeightMm - marginBottomMm

        if (config.showOuterBorder) {
            val th = 0.5f
            currentL += th
            currentT += th
            currentR -= th
            currentB -= th
            if (config.showInnerBorder) {
                val gap = config.borderGap
                val innerTh = 0.5f
                currentL += (gap + innerTh)
                currentT += (gap + innerTh)
                currentR -= (gap + innerTh)
                currentB -= (gap + innerTh)
            }
        }
        safeLeftMm = currentL + unitGapMm
        safeTopMm = currentT + unitGapMm
        safeRightMm = currentR - unitGapMm
        safeBottomMm = currentB - unitGapMm
    }

    val safeWMm = safeRightMm - safeLeftMm
    val safeHMm = safeBottomMm - safeTopMm

    if (safeWMm <= 10f || safeHMm <= 10f) return emptyList()

    // --- GRID GENERATION ---
    val rows: Int
    val cols: Int

    if (graphType == "L-Section") {
        cols = 1
        val targetHeightMm = 75.0f
        rows = max(1, floor(safeHMm / targetHeightMm).toInt())
    } else {
        // MATCH CAD LAYOUT: For X-Section, A3 is typically 2 columns x 3 rows (6 graphs)
        when {
            paperWidthMm >= 1100f -> { cols = 4; rows = 5 } // A0
            paperWidthMm >= 800f -> { cols = 3; rows = 4 }  // A1
            paperWidthMm >= 550f -> { cols = 3; rows = 3 }  // A2
            paperWidthMm >= 400f -> { cols = 2; rows = 3 }  // A3 (Fits 6 X-Sections perfectly)
            else -> { cols = 1; rows = 3 } // A4
        }
    }

    val slots = mutableListOf<PartitionSlot>()
    val cellWMm = safeWMm / cols
    val cellHMm = safeHMm / rows

    for (r in 0 until rows) {
        for (c in 0 until cols) {
            slots.add(
                PartitionSlot(
                    id = "slot_${r}_${c}",
                    xMm = safeLeftMm + (c * cellWMm),
                    yMm = safeTopMm + (r * cellHMm),
                    widthMm = cellWMm,
                    heightMm = cellHMm
                )
            )
        }
    }
    return slots
}

/**
 * Strict CAD Viewport Placement logic:
 * Centers the graph perfectly in the selected partition.
 * Disables automatic merging to preserve the 6-grid A3 Layout.
 */
fun fitGraphIntoPartition(
    graphWidthMm: Float,
    graphHeightMm: Float,
    selectedPartition: PartitionSlot,
    partitions: List<PartitionSlot>
): UpdatedPartitionLayout {

    // ALWAYS center the graph within the user's chosen partition
    val placement = FinalPlacementPosition(
        xMm = selectedPartition.xMm + (selectedPartition.widthMm - graphWidthMm) / 2f,
        yMm = selectedPartition.yMm + (selectedPartition.heightMm - graphHeightMm) / 2f
    )

    // STRICT GRID MODE: We no longer merge or delete adjacent slots.
    // This guarantees the 2x3 (6 image) layout stays perfectly intact,
    // just like standard CAD viewports, allowing you to fill all 6 slots.
    return UpdatedPartitionLayout(partitions, placement, selectedPartition)
}