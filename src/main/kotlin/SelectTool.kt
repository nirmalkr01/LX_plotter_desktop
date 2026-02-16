// FILE: D:\LX_plotter_desktop\src\main\kotlin\SelectTool.kt
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.max

data class SelectionRect(
    val start: Offset,
    val end: Offset
) {
    val topLeft: Offset get() = Offset(min(start.x, end.x), min(start.y, end.y))
    val size: Size get() = Size(abs(end.x - start.x), abs(end.y - start.y))
    val rect: Rect get() = Rect(topLeft, size)
}

// Logic to check if an item's bounds intersects or is contained by the selection rect
// Items are stored in MM, Selection is in PX. Needs pxPerMm.
fun isItemInSelection(
    selRect: Rect,
    itemXMm: Float,
    itemYMm: Float,
    itemWMm: Float,
    itemHMm: Float,
    pxPerMm: Float
): Boolean {
    val itemX = itemXMm * pxPerMm
    val itemY = itemYMm * pxPerMm
    val itemW = itemWMm * pxPerMm
    val itemH = itemHMm * pxPerMm

    val itemRect = Rect(itemX, itemY, itemX + itemW, itemY + itemH)

    // Returns true if the selection rectangle overlaps the item rectangle
    return selRect.overlaps(itemRect)
}