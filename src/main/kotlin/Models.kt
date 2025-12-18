import java.util.UUID

data class RiverPoint(
    val id: String,
    val chainage: Double,
    val distance: Double,     // Shifted/Centered Offset
    val preMonsoon: Double,
    val postMonsoon: Double,
    val originalDistance: Double,
    val isZeroPoint: Boolean = false
)

data class RawRiverPoint(
    val id: String = UUID.randomUUID().toString(),
    val chainage: Double,
    val distance: Double,
    val pre: Double,
    val post: Double
)

data class ReportPageItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val graphId: Double,
    val type: String,
    val data: List<RiverPoint>,
    // Position of the graph relative to the top-left margin of the page
    val xOffset: Float = 0f,
    val yOffset: Float = 0f
)