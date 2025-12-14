data class RiverPoint(
    val chainage: Double,
    val distance: Double,     // Centered Distance
    val preMonsoon: Double,
    val postMonsoon: Double,
    val originalDistance: Double // Original Distance (for reference)
)

data class RawRiverPoint(
    val chainage: Double,
    val distance: Double,
    val pre: Double,
    val post: Double
)