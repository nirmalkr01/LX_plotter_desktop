import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

enum class PaperSize(val widthMm: Int, val heightMm: Int) {
    A0(841, 1189),
    A1(594, 841),
    A2(420, 594),
    A3(297, 420),
    A4(210, 297);
}

// --- CSV SAVING ---
fun saveCsv(data: List<RiverPoint>, file: File) {
    try {
        val sb = StringBuilder()
        sb.append("Chain_age,Distance,Pre_Monsoon,Post_Monsoon\n")
        data.forEach { p ->
            sb.append("${p.chainage},${p.originalDistance},${p.preMonsoon},${p.postMonsoon}\n")
        }
        file.writeText(sb.toString())
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// --- SPLIT GRAPH SAVING (High Fidelity) ---
fun saveSplitPage(
    data: List<RiverPoint>,
    file: File,
    row: Int, col: Int,
    type: String,
    paperSize: PaperSize,
    isLandscape: Boolean,
    hScale: Double, vScale: Double,
    chainage: Double,
    showPre: Boolean, showPost: Boolean,
    preColor: androidx.compose.ui.graphics.Color, postColor: androidx.compose.ui.graphics.Color,
    preDotted: Boolean, postDotted: Boolean,
    preWidth: Float, postWidth: Float,
    preShowPoints: Boolean, postShowPoints: Boolean,
    showGrid: Boolean
) {
    if (data.isEmpty()) return

    // Convert Compose Color to AWT Color
    val awtPreColor = Color(preColor.red, preColor.green, preColor.blue)
    val awtPostColor = Color(postColor.red, postColor.green, postColor.blue)

    // 1. Constants & Dimensions
    val IMG_PX_PER_CM = 38.0
    val mmToPx = IMG_PX_PER_CM / 10.0
    val pageWidthMm = if (isLandscape) paperSize.heightMm else paperSize.widthMm
    val pageHeightMm = if (isLandscape) paperSize.widthMm else paperSize.heightMm
    val pageW = (pageWidthMm * mmToPx).toInt()
    val pageH = (pageHeightMm * mmToPx).toInt()

    val xValues = if (type == "L-Section") data.map { it.chainage } else data.map { it.distance }
    val yValues = (if(showPre) data.map { it.preMonsoon } else emptyList()) + (if(showPost) data.map { it.postMonsoon } else emptyList())

    val minX = xValues.minOrNull() ?: 0.0
    val maxX = xValues.maxOrNull() ?: 10.0
    val minY = if(yValues.isNotEmpty()) floor(yValues.minOrNull()!!) - 1.0 else 0.0
    val maxY = if(yValues.isNotEmpty()) ceil(yValues.maxOrNull()!!) + 1.0 else 10.0

    val cmPerMeterX = 100.0 / max(hScale, 1.0)
    val cmPerMeterY = 100.0 / max(vScale, 1.0)
    val pxPerMeterX = cmPerMeterX * IMG_PX_PER_CM
    val pxPerMeterY = cmPerMeterY * IMG_PX_PER_CM

    val paddingLeft = 100.0 // Space for Y axis
    val tableRowHeight = 70.0
    val tableRows = 3 // Post, Pre, Offset/Chainage
    val tableH = tableRows * tableRowHeight
    val graphContentH = (maxY - minY) * pxPerMeterY
    val totalGraphAreaH = graphContentH + 50.0 // Padding above graph
    val totalH = (totalGraphAreaH + tableH).toInt()

    // Coordinate Mappers
    fun mapX(v: Double) = (paddingLeft + (v - minX) * pxPerMeterX).toInt()
    fun mapY(v: Double) = (totalGraphAreaH - (v - minY) * pxPerMeterY).toInt()

    val axisY = mapY(minY) // Bottom of graph area
    val axisX = mapX(minX) // Left of graph area

    // 2. Init Image
    val img = BufferedImage(pageW, pageH, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.color = Color.WHITE; g.fillRect(0, 0, pageW, pageH)

    // 3. Viewport Offsets (Scrolling camera)
    val offsetX = -col * pageW
    val offsetY = -row * pageH

    val tOriginal = g.transform

    // --- DRAWING GRAPH CONTENT ---
    g.translate(offsetX, offsetY)

    val sortedPoints = data.sortedBy { if(type=="L-Section") it.chainage else it.distance }

    // 1. Drop Lines (Grid from points to table)
    g.color = Color.LIGHT_GRAY
    g.stroke = BasicStroke(1f)
    sortedPoints.forEach { p ->
        val x = mapX(if (type == "L-Section") p.chainage else p.distance)
        // Optimization: Draw only if x is on this page (plus buffer)
        if (x + offsetX > -50 && x + offsetX < pageW + 50) {
            // Line from top of graph area down to bottom of table
            g.drawLine(x, 0, x, totalH)
        }
    }

    // 2. Data Lines
    fun drawPoly(getColor: (RiverPoint) -> Double, color: Color, isDotted: Boolean, width: Float) {
        g.color = color
        // Create dashed stroke if needed
        val stroke = if (isDotted)
            BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(10f, 10f), 0f)
        else
            BasicStroke(width)
        g.stroke = stroke

        var px = -1; var py = -1
        sortedPoints.forEach { p ->
            val x = mapX(if (type == "L-Section") p.chainage else p.distance)
            val y = mapY(getColor(p))

            if (px != -1) {
                // Optimization clip
                if ((x + offsetX > -100 && x + offsetX < pageW + 100) || (px + offsetX > -100 && px + offsetX < pageW + 100)) {
                    g.drawLine(px, py, x, y)
                }
            }
            px = x; py = y
        }
    }

    if (showPre) drawPoly({ it.preMonsoon }, awtPreColor, preDotted, preWidth)
    if (showPost) drawPoly({ it.postMonsoon }, awtPostColor, postDotted, postWidth)

    // 3. Table Structure (Horizontal Lines)
    g.color = Color.BLACK
    g.stroke = BasicStroke(2f)
    val tableStartY = totalGraphAreaH
    // Line 1: Top of table
    g.drawLine(0, tableStartY.toInt(), (paddingLeft + (maxX - minX) * pxPerMeterX).toInt(), tableStartY.toInt())
    // Line 2: After Post
    g.drawLine(0, (tableStartY + tableRowHeight).toInt(), (paddingLeft + (maxX - minX) * pxPerMeterX).toInt(), (tableStartY + tableRowHeight).toInt())
    // Line 3: After Pre
    g.drawLine(0, (tableStartY + 2 * tableRowHeight).toInt(), (paddingLeft + (maxX - minX) * pxPerMeterX).toInt(), (tableStartY + 2 * tableRowHeight).toInt())
    // Line 4: Bottom
    g.drawLine(0, (tableStartY + 3 * tableRowHeight).toInt(), (paddingLeft + (maxX - minX) * pxPerMeterX).toInt(), (tableStartY + 3 * tableRowHeight).toInt())

    // 4. Table Values (Vertical Text)
    val fontPlain = Font("Arial", Font.PLAIN, 10)
    g.font = fontPlain

    val baseTransform = g.transform
    sortedPoints.forEach { p ->
        val x = mapX(if (type == "L-Section") p.chainage else p.distance)
        if (x + offsetX > -50 && x + offsetX < pageW + 50) {
            val vals = listOf(
                String.format("%.3f", p.postMonsoon),
                String.format("%.3f", p.preMonsoon),
                String.format("%.1f", if(type=="L-Section") p.chainage else p.distance)
            )
            val colors = listOf(awtPostColor, awtPreColor, Color.BLACK)

            vals.forEachIndexed { i, txt ->
                val cellCenterY = tableStartY + (i * tableRowHeight) + (tableRowHeight / 2)
                val tx = AffineTransform()
                tx.translate(x.toDouble() + 4, cellCenterY) // Slightly offset right of grid line
                tx.rotate(-Math.PI / 2) // Rotate 90 deg counter-clockwise
                g.transform = baseTransform
                g.transform(tx)
                g.color = colors[i]
                g.drawString(txt, 0, 0)
            }
        }
    }

    g.transform = tOriginal // Reset to Page Coordinates

    // --- STICKY AXIS & HEADERS ---
    g.color = Color.BLACK
    g.stroke = BasicStroke(2f)

    // Sticky Background for Headers (to cover graph lines)
    g.color = Color.WHITE
    g.fillRect(0, 0, paddingLeft.toInt(), pageH)
    g.fillRect(0, totalGraphAreaH.toInt() + (offsetY).toInt(), paddingLeft.toInt(), tableH.toInt()) // Clip table headers area if scrolling vert? (Simplified: Just draw over)

    // Y Axis Line
    g.color = Color.BLACK
    g.drawLine(paddingLeft.toInt(), 0, paddingLeft.toInt(), totalGraphAreaH.toInt())

    // Y Axis Ticks & Labels (Datum)
    g.font = Font("Arial", Font.PLAIN, 10)
    for(i in 0..((maxY-minY).toInt())) {
        val yVal = minY + i
        val logicY = mapY(yVal)
        val screenY = logicY + offsetY
        if (screenY >= 0 && screenY <= totalGraphAreaH) {
            g.drawLine(paddingLeft.toInt() - 5, screenY.toInt(), paddingLeft.toInt(), screenY.toInt())
            g.drawString(String.format("%.1f", yVal), 5, screenY.toInt() + 5)
        }
    }
    // Datum Label
    val datumY = mapY(minY) + offsetY
    if(datumY > 0 && datumY < pageH) {
        g.drawString("DATUM ${minY}", 5, datumY.toInt() - 15)
    }

    // Table Headers (Sticky on Left)
    val headerLabels = listOf("POST RL", "PRE RL", if(type=="L-Section") "CHAINAGE" else "OFFSET")
    g.font = Font("Arial", Font.BOLD, 12)
    val stickyTableTop = totalGraphAreaH + offsetY

    // Only draw headers if table vertical area is in page
    if (stickyTableTop < pageH && stickyTableTop + tableH > 0) {
        // Draw Borders for headers
        g.stroke = BasicStroke(2f)
        g.color = Color.BLACK

        headerLabels.forEachIndexed { i, label ->
            val rowY = stickyTableTop + (i * tableRowHeight)
            val rowCenterY = rowY + (tableRowHeight / 2)

            if (rowY < pageH) {
                // Horizontal lines for headers
                g.drawLine(0, rowY.toInt(), paddingLeft.toInt(), rowY.toInt())
                g.drawLine(0, (rowY + tableRowHeight).toInt(), paddingLeft.toInt(), (rowY + tableRowHeight).toInt())

                // Text
                g.drawString(label, 10, rowCenterY.toInt() + 5)
            }
        }
        // Vertical line closing headers
        g.drawLine(paddingLeft.toInt(), stickyTableTop.toInt(), paddingLeft.toInt(), (stickyTableTop + tableH).toInt())
    }

    g.dispose()
    ImageIO.write(img, "png", file)
}