import java.awt.FileDialog
import java.awt.Frame
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

// ==========================================
// HISTORY LOGIC (Moved here to fix Unresolved Reference)
// ==========================================

fun getHistoryFile(): File {
    val userHome = System.getProperty("user.home")
    val appDir = File(userHome, ".lxplotter")
    if (!appDir.exists()) appDir.mkdirs()
    return File(appDir, "history.txt")
}

fun saveHistory(paths: List<String>) {
    try {
        getHistoryFile().writeText(paths.joinToString("\n"))
    } catch (e: Exception) { e.printStackTrace() }
}

fun loadHistory(): List<String> {
    val file = getHistoryFile()
    return if (file.exists()) file.readLines().filter { it.isNotBlank() } else emptyList()
}

// ==========================================
// DATA PROCESSING
// ==========================================

fun getCurrentViewData(allData: List<RiverPoint>, type: String, chainage: Double, startCh: Double, endCh: Double): List<RiverPoint> {
    return if (type == "L-Section") {
        // L-Section: Only points where distance == 0 (Center Line)
        val lSecData = allData.groupBy { it.chainage }
            .map { (_, pts) ->
                // Find point closest to 0
                pts.minByOrNull { abs(it.distance) } ?: pts.first()
            }
            .sortedBy { it.chainage }

        // Filter by user range
        lSecData.filter { it.chainage >= startCh && it.chainage <= endCh }
    } else {
        // X-Section: All points for specific chainage
        allData.filter { it.chainage == chainage }.sortedBy { it.distance }
    }
}

fun getGlobalYMin(allData: List<RiverPoint>, type: String, chainage: Double): Double {
    val relevantData = if(type == "L-Section") allData else allData.filter { it.chainage == chainage }
    val allY = relevantData.map { it.preMonsoon } + relevantData.map { it.postMonsoon }
    return if(allY.isNotEmpty()) floor(allY.minOrNull()!!) - 1.0 else 0.0
}

fun getGlobalYMax(allData: List<RiverPoint>, type: String, chainage: Double): Double {
    val relevantData = if(type == "L-Section") allData else allData.filter { it.chainage == chainage }
    val allY = relevantData.map { it.preMonsoon } + relevantData.map { it.postMonsoon }
    return if(allY.isNotEmpty()) ceil(allY.maxOrNull()!!) + 1.0 else 100.0
}

fun processAndCenterData(rawData: List<RawRiverPoint>): List<RiverPoint> {
    val grouped = rawData.groupBy { it.chainage }
    val finalPoints = mutableListOf<RiverPoint>()
    for ((_, points) in grouped) {
        val minD = points.minOf { it.distance }
        val maxD = points.maxOf { it.distance }
        val shift = (minD + maxD) / 2.0
        for (p in points) {
            finalPoints.add(RiverPoint(p.chainage, p.distance - shift, p.pre, p.post, p.distance))
        }
    }
    return finalPoints
}

fun parseCsvStrict(file: File): Pair<List<RawRiverPoint>, String?> {
    val lines = file.readLines()
    if (lines.isEmpty()) return Pair(emptyList(), "File is empty")
    val header = lines[0].lowercase().split(",").map { it.trim() }
    val idxChain = header.indexOf("chain_age")
    val idxDist = if(header.contains("distance")) header.indexOf("distance") else header.indexOf("offset")
    val idxPre = if(header.contains("pre_monsoon")) header.indexOf("pre_monsoon") else header.indexOf("pre monsoon")
    val idxPost = if(header.contains("post_monsoon")) header.indexOf("post_monsoon") else header.indexOf("post monsoon")

    if (idxChain == -1 || idxDist == -1 || idxPre == -1 || idxPost == -1)
        return Pair(emptyList(), "Invalid Headers! Need: Chain_age, distance, pre_Monsoon, post_Monsoon")

    val data = mutableListOf<RawRiverPoint>()
    for (i in 1 until lines.size) {
        val tokens = lines[i].split(",")
        try {
            if (tokens.size > max(max(idxChain, idxDist), max(idxPre, idxPost))) {
                data.add(RawRiverPoint(
                    tokens[idxChain].trim().toDouble(),
                    tokens[idxDist].trim().toDouble(),
                    tokens[idxPre].trim().toDouble(),
                    tokens[idxPost].trim().toDouble()
                ))
            }
        } catch (e: Exception) { }
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

// --- BATCH SAVE ---

fun performBatchSave(allData: List<RiverPoint>, folder: File, type: String, startCh: Double, endCh: Double, showPre: Boolean, showPost: Boolean, hScale: Double, vScale: Double, onProgress: (String) -> Unit) {
    if (type == "L-Section") {
        val data = getCurrentViewData(allData, type, 0.0, startCh, endCh)
        saveGraphWithTable(data, File(folder, "L-Section_${startCh.toInt()}-${endCh.toInt()}.png"), type, 0.0, showPre, showPost, hScale, vScale)
    } else {
        val uniqueCh = allData.map { it.chainage }.distinct().sorted()
        uniqueCh.forEach { ch ->
            val data = getCurrentViewData(allData, type, ch, 0.0, 0.0)
            saveGraphWithTable(data, File(folder, "X-Section_CH${ch.toInt()}.png"), type, ch, showPre, showPost, hScale, vScale)
            onProgress("Saved CH $ch")
        }
    }
}

// --- IMAGE SAVING ---

fun saveGraphWithTable(
    points: List<RiverPoint>, file: File, type: String, chainage: Double,
    showPre: Boolean, showPost: Boolean, hScale: Double, vScale: Double
) {
    if (points.isEmpty()) return
    val imgWidth = 2000; val graphHeight = 1000; val totalHeight = graphHeight + 350
    val img = BufferedImage(imgWidth, totalHeight, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()

    g.color = java.awt.Color.WHITE; g.fillRect(0, 0, imgWidth, totalHeight)
    g.color = java.awt.Color.BLACK; g.font = java.awt.Font("Arial", java.awt.Font.BOLD, 24)
    g.drawString(if(type == "L-Section") "L-Section Profile" else "X-Section @ Chainage $chainage", 50, 50)
    g.font = java.awt.Font("Arial", java.awt.Font.PLAIN, 16)
    g.drawString("Scale H 1:$hScale  V 1:$vScale", 50, 80)

    val padding = 80.0
    val gW = imgWidth - (2 * padding)
    val gH = graphHeight - (2 * padding)

    val xValues = if (type == "L-Section") points.map { it.chainage } else points.map { it.distance }
    val yValues = (if(showPre) points.map { it.preMonsoon } else emptyList()) + (if(showPost) points.map { it.postMonsoon } else emptyList())
    val minX = xValues.minOrNull() ?: 0.0; val maxX = xValues.maxOrNull() ?: 10.0
    val minY = (floor(yValues.minOrNull() ?: 0.0) - 1.0); val maxY = (ceil(yValues.maxOrNull() ?: 10.0) + 1.0)

    // Scaling
    fun mapX(v: Double) = (padding + ((v - minX) / max(maxX - minX, 1.0) * gW)).toInt()
    fun mapY(v: Double) = (padding + gH - ((v - minY) / max(maxY - minY, 1.0) * gH)).toInt()

    g.stroke = java.awt.BasicStroke(2f)
    g.drawLine(padding.toInt(), (padding + gH).toInt(), (padding + gW).toInt(), (padding + gH).toInt())
    g.drawLine(padding.toInt(), padding.toInt(), padding.toInt(), (padding + gH).toInt())

    if (type == "X-Section" && minX <= 0 && maxX >= 0) {
        val zX = mapX(0.0)
        g.color = java.awt.Color.LIGHT_GRAY; g.stroke = java.awt.BasicStroke(2f)
        g.drawLine(zX, padding.toInt(), zX, (padding + gH).toInt())
    }

    g.stroke = java.awt.BasicStroke(3f)
    if (showPre) { g.color = java.awt.Color.BLUE; var px = -1; var py = -1; points.forEach { p -> val x = mapX(if (type == "L-Section") p.chainage else p.distance); val y = mapY(p.preMonsoon); if (px != -1) g.drawLine(px, py, x, y); g.fillOval(x-4, y-4, 8, 8); px = x; py = y } }
    if (showPost) { g.color = java.awt.Color.RED; var px = -1; var py = -1; points.forEach { p -> val x = mapX(if (type == "L-Section") p.chainage else p.distance); val y = mapY(p.postMonsoon); if (px != -1) g.drawLine(px, py, x, y); g.fillOval(x-4, y-4, 8, 8); px = x; py = y } }

    val tableTop = graphHeight + 50; g.color = java.awt.Color.BLACK; g.stroke = java.awt.BasicStroke(1f); g.font = java.awt.Font("Arial", java.awt.Font.PLAIN, 12)
    val labels = listOf("Post-Monsoon", "Pre-Monsoon", if(type=="L-Section") "Chainage" else "Offset")
    labels.forEachIndexed { i, label -> val y = tableTop + (i + 1) * 30; g.drawString(label, padding.toInt(), y - 10); g.drawLine(padding.toInt(), y, (padding + gW).toInt(), y) }
    g.drawLine(padding.toInt(), tableTop, (padding + gW).toInt(), tableTop)

    val step = max(1, points.size / 40); var colX = padding.toInt() + 150
    for (i in points.indices step step) {
        val p = points[i]
        g.drawLine(colX, tableTop, colX, tableTop + 90)
        g.color = java.awt.Color.RED; g.drawString(String.format("%.1f", p.postMonsoon), colX + 5, tableTop + 20)
        g.color = java.awt.Color.BLUE; g.drawString(String.format("%.1f", p.preMonsoon), colX + 5, tableTop + 50)
        g.color = java.awt.Color.BLACK; val distVal = if(type=="L-Section") p.chainage else p.distance
        g.drawString(String.format("%.0f", distVal), colX + 5, tableTop + 80)
        colX += 50; if(colX > imgWidth - padding) break
    }
    g.dispose(); ImageIO.write(img, "png", file)
}