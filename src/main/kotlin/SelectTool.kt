import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class SelectionRect(
    val start: Offset,
    val end: Offset
) {
    val topLeft: Offset get() = Offset(min(start.x, end.x), min(start.y, end.y))
    val size: Size get() = Size(abs(end.x - start.x), abs(end.y - start.y))
    val rect: Rect get() = Rect(topLeft, size)
}

// Logic to check if an item's bounds intersects or is contained by the selection rect
// Items are stored as percentages, so we need the paper size to convert to pixels for comparison
fun isItemInSelection(
    selRect: Rect,
    itemXPercent: Float,
    itemYPercent: Float,
    itemWPercent: Float,
    itemHPercent: Float,
    paperWidthPx: Float,
    paperHeightPx: Float
): Boolean {
    val itemX = itemXPercent * paperWidthPx
    val itemY = itemYPercent * paperHeightPx
    val itemW = itemWPercent * paperWidthPx
    val itemH = itemHPercent * paperHeightPx

    val itemRect = Rect(itemX, itemY, itemX + itemW, itemY + itemH)

    // Returns true if the selection rectangle overlaps the item rectangle
    return selRect.overlaps(itemRect)
}