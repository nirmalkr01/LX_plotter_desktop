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
 * Intelligent Placement logic:
 * Finds the minimum required merged layout to fit the graph dimensions, updating the page partitions dynamically.
 */
fun fitGraphIntoPartition(
    graphWidthMm: Float,
    graphHeightMm: Float,
    selectedPartition: PartitionSlot,
    partitions: List<PartitionSlot>
): UpdatedPartitionLayout {

    // 1. If Graph Fits Perfectly -> Output Centered Location
    if (graphWidthMm <= selectedPartition.widthMm && graphHeightMm <= selectedPartition.heightMm) {
        val placement = FinalPlacementPosition(
            xMm = selectedPartition.xMm + (selectedPartition.widthMm - graphWidthMm) / 2f,
            yMm = selectedPartition.yMm + (selectedPartition.heightMm - graphHeightMm) / 2f
        )
        return UpdatedPartitionLayout(partitions, placement, selectedPartition)
    }

    // 2. Needs Auto-Reshape! Extract Canvas Boundaries
    val maxRight = partitions.maxOfOrNull { it.xMm + it.widthMm } ?: (selectedPartition.xMm + selectedPartition.widthMm)
    val maxBottom = partitions.maxOfOrNull { it.yMm + it.heightMm } ?: (selectedPartition.yMm + selectedPartition.heightMm)
    val minLeft = partitions.minOfOrNull { it.xMm } ?: selectedPartition.xMm
    val minTop = partitions.minOfOrNull { it.yMm } ?: selectedPartition.yMm

    // Required Minimum Physical Bounding Box
    val reqW = max(graphWidthMm, selectedPartition.widthMm)
    val reqH = max(graphHeightMm, selectedPartition.heightMm)

    // Initial Starting Points (Top Left of selected)
    var startX = selectedPartition.xMm
    var startY = selectedPartition.yMm

    // Push bounding box back Up/Left if extending Right/Down would clip outside Page margins
    if (startX + reqW > maxRight) {
        startX = max(minLeft, maxRight - reqW)
    }
    if (startY + reqH > maxBottom) {
        startY = max(minTop, maxBottom - reqH)
    }

    // Define the Spatial Footprint Needed
    var currentLeft = startX
    var currentTop = startY
    var currentRight = startX + reqW
    var currentBottom = startY + reqH

    val consumedSlots = mutableSetOf<String>()
    var expanding = true

    // 3. Spatially Intersect and expand till transitivity closure ends
    // This perfectly glues intersecting grids into one large rectangle without leaving stray slithers.
    while (expanding) {
        expanding = false
        for (slot in partitions) {
            if (slot.id in consumedSlots) continue

            val slotRight = slot.xMm + slot.widthMm
            val slotBottom = slot.yMm + slot.heightMm

            // Collision check with safety epsilon
            val overlaps = currentLeft < slotRight - 0.1f &&
                    currentRight > slot.xMm + 0.1f &&
                    currentTop < slotBottom - 0.1f &&
                    currentBottom > slot.yMm + 0.1f

            if (overlaps) {
                consumedSlots.add(slot.id)
                // Snap edges to bounding limits of consumed slots
                if (slot.xMm < currentLeft) { currentLeft = slot.xMm; expanding = true }
                if (slot.yMm < currentTop) { currentTop = slot.yMm; expanding = true }
                if (slotRight > currentRight) { currentRight = slotRight; expanding = true }
                if (slotBottom > currentBottom) { currentBottom = slotBottom; expanding = true }
            }
        }
    }

    // 4. Create Updated Partition Layout Configuration
    val mergedSlot = PartitionSlot(
        id = "merged_${System.currentTimeMillis()}",
        xMm = currentLeft,
        yMm = currentTop,
        widthMm = currentRight - currentLeft,
        heightMm = currentBottom - currentTop
    )

    // Remove the slots subsumed into the larger cell & append the massive slot layout
    val newPartitions = partitions.filterNot { it.id in consumedSlots }.toMutableList()
    newPartitions.add(mergedSlot)

    // Calculate strict center against merged layout bounds
    val placement = FinalPlacementPosition(
        xMm = currentLeft + (mergedSlot.widthMm - graphWidthMm) / 2f,
        yMm = currentTop + (mergedSlot.heightMm - graphHeightMm) / 2f
    )

    return UpdatedPartitionLayout(newPartitions, placement, mergedSlot)
}