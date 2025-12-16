import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

// --- DATA PROCESSING ---
fun getCurrentViewData(allData: List<RiverPoint>, type: String, chainage: Double, startCh: Double, endCh: Double): List<RiverPoint> {
    return if (type == "L-Section") {
        val lSecData = allData.groupBy { it.chainage }
            .map { (_, pts) ->
                // For L-Section, closest point to 0 offset (which is derived from Thalweg or Center)
                pts.minByOrNull { abs(it.distance) } ?: pts.first()
            }
            .sortedBy { it.chainage }
        lSecData.filter { it.chainage >= startCh && it.chainage <= endCh }
    } else {
        allData.filter { it.chainage == chainage }.sortedBy { it.distance }
    }
}

// THALWEG / CENTER / MANUAL LOGIC
fun processAndCenterData(
    rawData: List<RawRiverPoint>,
    useThalweg: Boolean,
    manualZeroOverrides: Map<Double, String> // Map<Chainage, PointID>
): List<RiverPoint> {
    val grouped = rawData.groupBy { it.chainage }
    val finalPoints = mutableListOf<RiverPoint>()

    for ((chainage, points) in grouped) {
        // 1. Sort by original distance (ascending)
        val sortedPoints = points.sortedBy { it.distance }

        // 2. Determine the "Zero" Index
        val zeroIndex: Int = if (manualZeroOverrides.containsKey(chainage)) {
            val overrideId = manualZeroOverrides[chainage]
            val foundIndex = sortedPoints.indexOfFirst { it.id == overrideId }
            if (foundIndex != -1) foundIndex else 0 // Fallback
        } else if (useThalweg) {
            // MODIFIED THALWEG LOGIC
            if (sortedPoints.isEmpty()) {
                0
            } else {
                val minPre = sortedPoints.minOf { it.pre }
                val minIndices = sortedPoints.mapIndexedNotNull { index, point ->
                    if (point.pre == minPre) index else null
                }
                minIndices[minIndices.size / 2]
            }
        } else {
            // Case 7.1: Without Thalweg (Center)
            val size = sortedPoints.size
            if (size % 2 != 0) {
                size / 2
            } else {
                val mid1 = (size / 2) - 1
                val mid2 = size / 2
                if (sortedPoints[mid1].pre <= sortedPoints[mid2].pre) mid1 else mid2
            }
        }

        // 3. Get the reference distance
        val refDistance = if(sortedPoints.isNotEmpty()) sortedPoints[zeroIndex].distance else 0.0

        // 4. Calculate new distances
        for (p in sortedPoints) {
            val newDist = p.distance - refDistance
            val cleanDist = round(newDist * 100) / 100.0

            finalPoints.add(RiverPoint(
                id = p.id,
                chainage = p.chainage,
                distance = cleanDist,
                preMonsoon = p.pre,
                postMonsoon = p.post,
                originalDistance = p.distance,
                isZeroPoint = (p.id == sortedPoints[zeroIndex].id)
            ))
        }
    }
    return finalPoints
}

// --- CSV HELPERS FOR MAPPING ---

// 1. Just read the headers and first few rows for preview
fun readCsvPreview(file: File): Pair<List<String>, List<List<String>>> {
    val lines = file.readLines().take(6) // Header + 5 rows
    if (lines.isEmpty()) return Pair(emptyList(), emptyList())

    val headers = lines[0].split(",").map { it.trim() }
    val rows = lines.drop(1).map { line ->
        line.split(",").map { it.trim() }
    }
    return Pair(headers, rows)
}

// 2. Parse using specific indices
fun parseCsvMapped(file: File, colIndices: Map<String, Int>): Pair<List<RawRiverPoint>, String?> {
    val idxChain = colIndices["chainage"] ?: -1
    val idxDist = colIndices["distance"] ?: -1
    val idxPre = colIndices["pre"] ?: -1
    val idxPost = colIndices["post"] ?: -1

    if (idxChain == -1 || idxDist == -1 || idxPre == -1 || idxPost == -1)
        return Pair(emptyList(), "Missing Column Mapping")

    val lines = file.readLines()
    val data = mutableListOf<RawRiverPoint>()

    // Start from 1 to skip header
    for (i in 1 until lines.size) {
        val tokens = lines[i].split(",").map { it.trim() }
        try {
            // Ensure the line has enough columns for the max index we need
            val maxIdx = max(max(idxChain, idxDist), max(idxPre, idxPost))
            if (tokens.size > maxIdx) {
                data.add(RawRiverPoint(
                    chainage = tokens[idxChain].toDouble(),
                    distance = tokens[idxDist].toDouble(),
                    pre = tokens[idxPre].toDouble(),
                    post = tokens[idxPost].toDouble()
                ))
            }
        } catch (e: Exception) {
            // Skip bad rows silently or log
        }
    }
    return Pair(data, null)
}

// --- FILE HELPERS ---
fun pickFile(): File? {
    val d = FileDialog(null as Frame?, "Select CSV", FileDialog.LOAD)
    d.setFile("*.csv")
    d.isVisible = true
    return if (d.file != null) File(d.directory, d.file) else null
}

fun pickFolder(): File? {
    val d = FileDialog(null as Frame?, "Select Output Folder", FileDialog.SAVE)
    d.isVisible = true
    return if (d.directory != null) File(d.directory) else null
}

fun pickSaveFile(defaultName: String): File? {
    val d = FileDialog(null as Frame?, "Save CSV", FileDialog.SAVE)
    d.setFile(defaultName)
    d.isVisible = true
    return if (d.file != null) File(d.directory, d.file) else null
}

fun getHistoryFile(): File {
    val userHome = System.getProperty("user.home")
    val appDir = File(userHome, ".lxplotter")
    if (!appDir.exists()) appDir.mkdirs()
    return File(appDir, "history.txt")
}

fun saveHistory(paths: List<String>) {
    try { getHistoryFile().writeText(paths.joinToString("\n")) } catch (e: Exception) { }
}

fun loadHistory(): List<String> {
    val file = getHistoryFile()
    return if (file.exists()) file.readLines().filter { it.isNotBlank() } else emptyList()
}