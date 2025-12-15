data class RiverPoint(
    val chainage: Double,
    val distance: Double,     // This is the Shifted/Centered Offset
    val preMonsoon: Double,
    val postMonsoon: Double,
    val originalDistance: Double // Kept for reference
)

data class RawRiverPoint(
    val chainage: Double,
    val distance: Double,
    val pre: Double,
    val post: Double
)