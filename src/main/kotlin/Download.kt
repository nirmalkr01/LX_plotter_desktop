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

// --- 1. SAVE RAW GRAPH (For Panel 3 - Image View) ---
fun saveRawGraph(
    data: List<RiverPoint>,
    file: File,
    type: String,
    hScale: Double, vScale: Double,
    showPre: Boolean, showPost: Boolean,
    preColor: Color, postColor: Color, // Using java.awt.Color directly
    preDotted: Boolean, postDotted: Boolean,
    preWidth: Float, postWidth: Float,
    preShowPoints: Boolean, postShowPoints: Boolean
) {
    if (data.isEmpty()) return

    val IMG_PX_PER_CM = 38.0

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

    val padding = 50
    val graphW = ((maxX - minX) * pxPerMeterX).toInt()
    val graphH = ((maxY - minY) * pxPerMeterY).toInt()

    val width = graphW + 2 * padding
    val height = graphH + 2 * padding

    val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.color = Color.WHITE
    g.fillRect(0, 0, width, height)

    fun mapX(v: Double) = (padding + (v - minX) * pxPerMeterX).toInt()
    fun mapY(v: Double) = (height - padding - (v - minY) * pxPerMeterY).toInt()

    g.color = Color.LIGHT_GRAY
    g.stroke = BasicStroke(1f)
    val sortedPoints = data.sortedBy { if(type=="L-Section") it.chainage else it.distance }
    sortedPoints.forEach { p ->
        val x = mapX(if (type == "L-Section") p.chainage else p.distance)
        g.drawLine(x, padding, x, height - padding)
    }

    g.color = Color.BLACK
    g.stroke = BasicStroke(2f)
    g.drawLine(padding, height - padding, width - padding, height - padding) // X-Axis
    g.drawLine(padding, padding, padding, height - padding) // Y-Axis

    fun drawPoly(getColor: (RiverPoint) -> Double, color: Color, isDotted: Boolean, strokeW: Float, showPoints: Boolean) {
        g.color = color
        val stroke = if (isDotted) BasicStroke(strokeW, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(10f, 10f), 0f) else BasicStroke(strokeW)
        g.stroke = stroke
        var px = -1; var py = -1
        sortedPoints.forEach { p ->
            val x = mapX(if (type == "L-Section") p.chainage else p.distance)
            val y = mapY(getColor(p))
            if (px != -1) g.drawLine(px, py, x, y)
            px = x; py = y

            if (showPoints) {
                val r = (strokeW * 2).toInt().coerceAtLeast(4)
                g.fillOval(x - r/2, y - r/2, r, r)
            }
        }
    }

    if (showPre) drawPoly({ it.preMonsoon }, preColor, preDotted, preWidth, preShowPoints)
    if (showPost) drawPoly({ it.postMonsoon }, postColor, postDotted, postWidth, postShowPoints)

    g.dispose()
    ImageIO.write(img, "png", file)
}

// --- 2. SAVE REPORT PAGE (For Panel 4 - File View) ---
fun saveSplitPage(
    data: List<RiverPoint>,
    file: File,
    row: Int, col: Int,
    type: String,
    paperSize: PaperSize,
    isLandscape: Boolean,
    hScale: Double, vScale: Double,
    config: ReportConfig,
    chainage: Double,
    showPre: Boolean, showPost: Boolean,
    preColor: Color, postColor: Color,
    preDotted: Boolean, postDotted: Boolean,
    preWidth: Float, postWidth: Float,
    preShowPoints: Boolean, postShowPoints: Boolean,
    showGrid: Boolean,
    textAnnotations: List<TextAnnotation> = emptyList() // Added Parameter for Text
) {
    if (data.isEmpty()) return

    // Colors
    val awtOuterColor = Color(config.outerColor.red, config.outerColor.green, config.outerColor.blue)
    val awtInnerColor = Color(config.innerColor.red, config.innerColor.green, config.innerColor.blue)

    val IMG_PX_PER_CM = 38.0
    val mmToPx = IMG_PX_PER_CM / 10.0
    val pW_mm = if (isLandscape) paperSize.heightMm else paperSize.widthMm
    val pH_mm = if (isLandscape) paperSize.widthMm else paperSize.heightMm
    val pageW = (pW_mm * mmToPx).toInt()
    val pageH = (pH_mm * mmToPx).toInt()

    val mL = (config.marginLeft * mmToPx).toDouble()
    val mR = (config.marginRight * mmToPx).toDouble()
    val mT = (config.marginTop * mmToPx).toDouble()
    val mB = (config.marginBottom * mmToPx).toDouble()
    val gap = (config.borderGap * mmToPx).toDouble()

    val totalML = mL + if(config.showInnerBorder) gap else 0.0
    val totalMT = mT + if(config.showInnerBorder) gap else 0.0
    val totalMR = mR + if(config.showInnerBorder) gap else 0.0
    val totalMB = mB + if(config.showInnerBorder) gap else 0.0

    val contentW = pageW - totalML - totalMR
    val contentH = pageH - totalMT - totalMB

    if (contentW <= 0 || contentH <= 0) return

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

    val paddingLeft = 100.0
    val tableRowHeight = 60.0
    val tableRows = 3
    val tableH = tableRows * tableRowHeight
    val graphContentH = (maxY - minY) * pxPerMeterY
    val totalGraphAreaH = graphContentH + 50.0
    val totalDrawH = (totalGraphAreaH + tableH).toInt()

    fun mapX(v: Double) = (paddingLeft + (v - minX) * pxPerMeterX).toInt()
    fun mapY(v: Double) = (totalGraphAreaH - (v - minY) * pxPerMeterY).toInt()

    val img = BufferedImage(pageW, pageH, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.color = Color.WHITE; g.fillRect(0, 0, pageW, pageH)

    // Draw Borders
    g.stroke = BasicStroke(config.outerThickness)
    g.color = awtOuterColor
    if (config.showOuterBorder) g.drawRect(mL.toInt(), mT.toInt(), (pageW - mL - mR).toInt(), (pageH - mT - mB).toInt())

    g.stroke = BasicStroke(config.innerThickness)
    g.color = awtInnerColor
    if (config.showInnerBorder) g.drawRect((mL + gap).toInt(), (mT + gap).toInt(), (pageW - mL - mR - 2*gap).toInt(), (pageH - mT - mB - 2*gap).toInt())

    // --- DRAW GRAPH CONTENT ---
    g.setClip(totalML.toInt(), totalMT.toInt(), contentW.toInt(), contentH.toInt())
    val baseTransform = g.transform
    g.translate(totalML, totalMT)

    val offsetX = -col * contentW
    val offsetY = -row * contentH
    g.translate(offsetX, offsetY)

    val sortedPoints = data.sortedBy { if(type=="L-Section") it.chainage else it.distance }

    if (showGrid) {
        g.color = Color.LIGHT_GRAY
        g.stroke = BasicStroke(1f)
        sortedPoints.forEach { p ->
            val x = mapX(if (type == "L-Section") p.chainage else p.distance)
            if (x + offsetX > -50 && x + offsetX < contentW + 50) {
                g.drawLine(x, 0, x, totalDrawH)
            }
        }
    }

    fun drawPoly(getColor: (RiverPoint) -> Double, color: Color, isDotted: Boolean, width: Float) {
        g.color = color
        val stroke = if (isDotted) BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(10f, 10f), 0f) else BasicStroke(width)
        g.stroke = stroke
        var px = -1; var py = -1
        sortedPoints.forEach { p ->
            val x = mapX(if (type == "L-Section") p.chainage else p.distance)
            val y = mapY(getColor(p))
            if (px != -1) {
                if ((x + offsetX > -100 && x + offsetX < contentW + 100)) g.drawLine(px, py, x, y)
            }
            px = x; py = y
        }
    }
    if (showPre) drawPoly({ it.preMonsoon }, preColor, preDotted, preWidth)
    if (showPost) drawPoly({ it.postMonsoon }, postColor, postDotted, postWidth)

    // Draw Table
    g.color = Color.BLACK
    g.stroke = BasicStroke(2f)
    val xEnd = (paddingLeft + (maxX - minX) * pxPerMeterX).toInt()
    val tableY0 = totalGraphAreaH.toInt()
    g.drawLine(0, tableY0, xEnd, tableY0)
    for(i in 1..3) {
        val y = (tableY0 + i * tableRowHeight).toInt()
        g.drawLine(0, y, xEnd, y)
    }

    val fontPlain = Font("Arial", Font.PLAIN, 10)
    g.font = fontPlain
    val graphTx = g.transform

    sortedPoints.forEach { p ->
        val x = mapX(if (type == "L-Section") p.chainage else p.distance)
        if (x + offsetX > -50 && x + offsetX < contentW + 50) {
            val vals = listOf(
                String.format("%.3f", p.postMonsoon),
                String.format("%.3f", p.preMonsoon),
                String.format("%.1f", if(type=="L-Section") p.chainage else p.distance)
            )
            val colors = listOf(postColor, preColor, Color.BLACK)
            vals.forEachIndexed { i, txt ->
                val centerY = tableY0 + (i * tableRowHeight) + (tableRowHeight / 2)
                val tx = AffineTransform()
                tx.translate(x.toDouble() + 4, centerY)
                tx.rotate(-Math.PI / 2)
                g.transform = graphTx
                g.transform(tx)
                g.color = colors[i]
                g.drawString(txt, 0, 0)
            }
        }
    }

    // Reset for Labels
    g.transform = baseTransform // Go back to (0,0) of clip (inside margin)
    g.translate(totalML, totalMT) // Move to margin start

    g.color = Color.WHITE
    g.fillRect(0, 0, paddingLeft.toInt(), contentH.toInt())

    g.color = Color.BLACK
    g.stroke = BasicStroke(2f)
    g.drawLine(paddingLeft.toInt(), 0, paddingLeft.toInt(), totalGraphAreaH.toInt())

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

    val datumScreenY = mapY(minY) + offsetY
    if(datumScreenY > 0 && datumScreenY < contentH) {
        g.drawString("DATUM=${minY}", 5, datumScreenY.toInt() - 5)
    }

    val stickyTableTop = totalGraphAreaH + offsetY
    if (stickyTableTop < contentH && stickyTableTop + tableH > 0) {
        g.color = Color.WHITE
        g.fillRect(0, stickyTableTop.toInt().coerceAtLeast(0), paddingLeft.toInt(), tableH.toInt())
        g.color = Color.BLACK
        g.stroke = BasicStroke(2f)

        val headerLabels = listOf("POST RL", "PRE RL", if(type=="L-Section") "CHAINAGE" else "OFFSET")
        g.font = Font("Arial", Font.BOLD, 12)

        headerLabels.forEachIndexed { i, label ->
            val rowY = stickyTableTop + i * tableRowHeight
            val rowCenterY = rowY + tableRowHeight/2
            if(rowY < contentH) {
                g.drawLine(0, rowY.toInt(), paddingLeft.toInt(), rowY.toInt())
                g.drawLine(0, (rowY+tableRowHeight).toInt(), paddingLeft.toInt(), (rowY+tableRowHeight).toInt())
                g.drawString(label, 5, rowCenterY.toInt() + 5)
            }
        }
        g.drawLine(paddingLeft.toInt(), stickyTableTop.toInt(), paddingLeft.toInt(), (stickyTableTop+tableH).toInt())
    }

    // --- DRAW TEXT ANNOTATIONS ---
    // Reset to absolute coordinates to draw text anywhere on page (even over borders)
    g.transform = AffineTransform()

    textAnnotations.forEach { txt ->
        val x = (pageW * txt.xPercent).toInt()
        val y = (pageH * txt.yPercent).toInt()

        // Convert Compose Color to AWT Color
        val c = txt.color
        g.color = Color(c.red, c.green, c.blue, c.alpha)

        val style = if (txt.isBold) Font.BOLD else Font.PLAIN
        // Scale Font: Image resolution is ~2.5x higher than screen view 100%
        val scaledFontSize = (txt.fontSize * 2.5f).toInt()
        g.font = Font("Arial", style, scaledFontSize)

        g.drawString(txt.text, x, y)
    }

    g.dispose()
    ImageIO.write(img, "png", file)
}