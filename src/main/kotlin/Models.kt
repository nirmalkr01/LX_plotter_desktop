import java.util.UUID

data class RiverPoint(
    val id: String, // Added ID to track points
    val chainage: Double,
    val distance: Double,     // This is the Shifted/Centered Offset
    val preMonsoon: Double,
    val postMonsoon: Double,
    val originalDistance: Double, // Kept for reference
    val isZeroPoint: Boolean = false // Marker for the zero point
)

data class RawRiverPoint(
    val id: String = UUID.randomUUID().toString(),
    val chainage: Double,
    val distance: Double,
    val pre: Double,
    val post: Double
)