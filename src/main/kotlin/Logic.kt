import java.awt.FileDialog
import java.awt.Frame
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
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

// THALWEG / CENTER LOGIC
fun processAndCenterData(rawData: List<RawRiverPoint>, useThalweg: Boolean): List<RiverPoint> {
    val grouped = rawData.groupBy { it.chainage }
    val finalPoints = mutableListOf<RiverPoint>()

    for ((_, points) in grouped) {
        // 1. Sort by original distance (ascending)
        val sortedPoints = points.sortedBy { it.distance }

        // 2. Determine the "Zero" Index
        val zeroIndex: Int = if (useThalweg) {
            // Case 6.1: Deepest pre-monsoon value.
            // If multiple same values, minByOrNull picks first one (acceptable by prompt).
            sortedPoints.indexOf(sortedPoints.minByOrNull { it.pre } ?: sortedPoints.first())
        } else {
            // Case 7.1: Without Thalweg (Center)
            val size = sortedPoints.size
            if (size % 2 != 0) {
                // Odd: Exact middle
                size / 2
            } else {
                // Even: Check two center values, pick deepest
                val mid1 = (size / 2) - 1
                val mid2 = size / 2
                if (sortedPoints[mid1].pre <= sortedPoints[mid2].pre) mid1 else mid2
            }
        }

        // 3. Get the reference distance
        val refDistance = sortedPoints[zeroIndex].distance

        // 4. Calculate new distances
        for (p in sortedPoints) {
            val newDist = p.distance - refDistance
            // Round to avoid floating point artifacts (e.g. -0.000004 -> 0.0)
            val cleanDist = round(newDist * 100) / 100.0

            finalPoints.add(RiverPoint(
                chainage = p.chainage,
                distance = cleanDist,
                preMonsoon = p.pre,
                postMonsoon = p.post,
                originalDistance = p.distance
            ))
        }
    }
    return finalPoints
}

fun parseCsvStrict(file: File): Pair<List<RawRiverPoint>, String?> {
    val lines = file.readLines()
    if (lines.isEmpty()) return Pair(emptyList(), "File is empty")

    val header = lines[0].lowercase().split(",").map { it.trim() }

    val idxChain = header.indexOf("chain_age")
    val idxDist = header.indexOf("distance")
    val idxPre = header.indexOf("pre_monsoon")
    val idxPost = header.indexOf("post_monsoon")

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

// --- BATCH SAVE ---
fun performBatchSave(allData: List<RiverPoint>, folder: File, type: String, startCh: Double, endCh: Double, showPre: Boolean, showPost: Boolean, hScale: Double, vScale: Double, preColor: java.awt.Color, postColor: java.awt.Color, preDotted: Boolean, postDotted: Boolean, onProgress: (String) -> Unit) {
    if (type == "L-Section") {
        val data = getCurrentViewData(allData, type, 0.0, startCh, endCh)
        saveGraphWithTable(data, File(folder, "L-Section_${startCh.toInt()}-${endCh.toInt()}.png"), type, 0.0, showPre, showPost, hScale, vScale, preColor, postColor, preDotted, postDotted)
    } else {
        val uniqueCh = allData.map { it.chainage }.distinct().sorted()
        uniqueCh.forEach { ch ->
            val data = getCurrentViewData(allData, type, ch, 0.0, 0.0)
            saveGraphWithTable(data, File(folder, "X-Section_CH${ch.toInt()}.png"), type, ch, showPre, showPost, hScale, vScale, preColor, postColor, preDotted, postDotted)
            onProgress("Saved CH $ch")
        }
    }
}

// --- IMAGE SAVING ---
fun saveGraphWithTable(
    points: List<RiverPoint>, file: File, type: String, chainage: Double,
    showPre: Boolean, showPost: Boolean, hScale: Double, vScale: Double,
    preColor: java.awt.Color, postColor: java.awt.Color, preDotted: Boolean, postDotted: Boolean
) {
    try {
        if (points.isEmpty()) return

        val xValues = if (type == "L-Section") points.map { it.chainage } else points.map { it.distance }
        val yValues = (if(showPre) points.map { it.preMonsoon } else emptyList()) + (if(showPost) points.map { it.postMonsoon } else emptyList())

        val minX = xValues.minOrNull() ?: 0.0
        val maxX = xValues.maxOrNull() ?: 10.0
        val minY = if(yValues.isNotEmpty()) floor(yValues.minOrNull()!!) - 1.0 else 0.0
        val maxY = if(yValues.isNotEmpty()) ceil(yValues.maxOrNull()!!) + 1.0 else 10.0

        val IMG_PX_PER_CM = 50.0

        val cmPerMeterX = 100.0 / max(hScale, 1.0)
        val cmPerMeterY = 100.0 / max(vScale, 1.0)

        val pxPerMeterX = cmPerMeterX * IMG_PX_PER_CM
        val pxPerMeterY = cmPerMeterY * IMG_PX_PER_CM

        val padding = 100.0

        val graphW = (maxX - minX) * pxPerMeterX
        val graphH = (maxY - minY) * pxPerMeterY

        val imgWidth = (graphW + 2 * padding).toInt().coerceAtMost(10000)
        val imgHeight = (graphH + 2 * padding).toInt().coerceAtMost(10000)
        val tableHeight = 350
        val totalHeight = imgHeight + tableHeight

        val img = BufferedImage(imgWidth, totalHeight, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()

        g.color = java.awt.Color.WHITE
        g.fillRect(0, 0, imgWidth, totalHeight)

        g.color = java.awt.Color.BLACK; g.font = java.awt.Font("Arial", java.awt.Font.BOLD, 24)
        g.drawString(if(type == "L-Section") "L-Section Profile" else "X-Section @ Chainage $chainage", 50, 50)
        g.font = java.awt.Font("Arial", java.awt.Font.PLAIN, 16)
        g.drawString("Scale H: 1:$hScale  V: 1:$vScale", 50, 80)

        fun mapX(v: Double) = (padding + (v - minX) * pxPerMeterX).toInt()
        fun mapY(v: Double) = (imgHeight - padding - (v - minY) * pxPerMeterY).toInt()

        val axisY = mapY(minY)
        val axisX = mapX(minX)

        g.stroke = java.awt.BasicStroke(2f)
        g.color = java.awt.Color.BLACK
        g.drawLine(padding.toInt(), axisY, (imgWidth - padding).toInt(), axisY)
        g.drawLine(axisX, padding.toInt(), axisX, axisY)

        g.stroke = java.awt.BasicStroke(1f)
        val fadedColor = java.awt.Color(200, 200, 200)

        val distinctX = xValues.distinct().sorted()
        g.font = java.awt.Font("Arial", java.awt.Font.PLAIN, 10)
        distinctX.forEach { xVal ->
            val x = mapX(xVal)
            if (x < imgWidth - padding) {
                g.color = java.awt.Color.BLACK
                g.drawLine(x, axisY, x, axisY + 5)
                g.drawString(String.format("%.1f", xVal), x - 10, axisY + 20)

                points.filter { (if(type == "L-Section") it.chainage else it.distance) == xVal }.forEach { p ->
                    if(showPre) {
                        val y = mapY(p.preMonsoon)
                        g.color = fadedColor
                        g.drawLine(x, y, x, axisY)
                    }
                    if(showPost) {
                        val y = mapY(p.postMonsoon)
                        g.color = fadedColor
                        g.drawLine(x, y, x, axisY)
                    }
                }
            }
        }

        for(i in 0..((maxY-minY).toInt())) {
            val yVal = minY + i
            val y = mapY(yVal)
            g.color = java.awt.Color.BLACK
            g.drawLine(axisX - 5, y, axisX, y)
            g.drawString(String.format("%.1f", yVal), axisX - 35, y + 5)
        }

        fun drawSeries(getColor: (RiverPoint) -> Double, color: java.awt.Color, isDotted: Boolean) {
            g.color = color
            val stroke = if(isDotted) java.awt.BasicStroke(3f, java.awt.BasicStroke.CAP_BUTT, java.awt.BasicStroke.JOIN_MITER, 10f, floatArrayOf(10f, 10f), 0f) else java.awt.BasicStroke(3f)
            g.stroke = stroke

            var px = -1; var py = -1
            val sortedP = points.sortedBy { if(type=="L-Section") it.chainage else it.distance }

            sortedP.forEach { p ->
                val x = mapX(if (type == "L-Section") p.chainage else p.distance)
                val y = mapY(getColor(p))
                if (px != -1) g.drawLine(px, py, x, y)
                g.fillOval(x-4, y-4, 8, 8)
                px = x; py = y
            }
        }

        if (showPre) drawSeries({ it.preMonsoon }, preColor, preDotted)
        if (showPost) drawSeries({ it.postMonsoon }, postColor, postDotted)

        val tableTop = imgHeight + 20
        g.color = java.awt.Color.BLACK; g.stroke = java.awt.BasicStroke(1f); g.font = java.awt.Font("Arial", java.awt.Font.PLAIN, 12)
        val labels = listOf("Post Monsoon:", "Pre Monsoon:", if(type=="L-Section") "Chainage in mt:" else "Offset in mt:")
        labels.forEachIndexed { i, label ->
            val y = tableTop + (i + 1) * 30
            g.drawString(label, padding.toInt(), y - 10)
            g.drawLine(padding.toInt(), y, (imgWidth - padding).toInt(), y)
        }
        g.drawLine(padding.toInt(), tableTop, (imgWidth - padding).toInt(), tableTop)

        val step = max(1, points.size / 50)
        var colX = padding.toInt() + 150
        for (i in points.indices step step) {
            val p = points[i]
            g.drawLine(colX, tableTop, colX, tableTop + 90)
            g.color = postColor; g.drawString(String.format("%.2f", p.postMonsoon), colX + 5, tableTop + 20)
            g.color = preColor; g.drawString(String.format("%.2f", p.preMonsoon), colX + 5, tableTop + 50)
            g.color = java.awt.Color.BLACK; val distVal = if(type=="L-Section") p.chainage else p.distance
            g.drawString(String.format("%.1f", distVal), colX + 5, tableTop + 80)
            colX += 50; if(colX > imgWidth - padding) break
        }

        g.dispose(); ImageIO.write(img, "png", file)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}