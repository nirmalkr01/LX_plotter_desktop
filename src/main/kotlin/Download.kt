import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.apache.pdfbox.pdmodel.font.PDType1Font
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Desktop
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.GeneralPath
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

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
    preColor: Color, postColor: Color,
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

// --- INTERNAL RENDERER (Used by both PNG and PDF) ---
fun renderPageToImage(
    data: List<RiverPoint>,
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
    textAnnotations: List<TextAnnotation> = emptyList(),
    // NEW PARAMETERS FOR FULL RENDERING
    layoutType: PageLayoutType = PageLayoutType.BLANK,
    elements: List<ReportElement> = emptyList(),
    pageNumber: Int = 1,
    totalPageCount: Int = 1,
    annexureValue: String = "",
    b1Text: String = ""
): BufferedImage? {

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

    if (contentW <= 0 || contentH <= 0) return null

    val img = BufferedImage(pageW, pageH, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

    g.color = Color.WHITE; g.fillRect(0, 0, pageW, pageH)

    // 1. Draw Borders
    g.stroke = BasicStroke(config.outerThickness)
    g.color = awtOuterColor
    if (config.showOuterBorder) g.drawRect(mL.toInt(), mT.toInt(), (pageW - mL - mR).toInt(), (pageH - mT - mB).toInt())

    g.stroke = BasicStroke(config.innerThickness)
    g.color = awtInnerColor
    if (config.showInnerBorder) g.drawRect((mL + gap).toInt(), (mT + gap).toInt(), (pageW - mL - mR - 2*gap).toInt(), (pageH - mT - mB - 2*gap).toInt())

    // 2. Draw Page Layout (Engineering Standard)
    if(layoutType == PageLayoutType.ENGINEERING_STD) {
        drawAwtEngineeringLayout(g, pageW, pageH, mL, mT, mR, mB, awtOuterColor, annexureValue, b1Text, pageNumber, totalPageCount, hScale, vScale, config.legendType, preColor, postColor)
    }

    // 3. Draw Background Graph (If data exists)
    if(data.isNotEmpty()) {
        drawAwtGraph(g, data, type, hScale, vScale, showPre, showPost, preColor, postColor, preDotted, postDotted, preWidth, postWidth, preShowPoints, postShowPoints, showGrid,
            totalML, totalMT, contentW, contentH, row, col)
    }

    // 4. Draw Floating Elements (Shapes & Graphs)
    val baseTx = g.transform
    elements.forEach { el ->
        g.transform = baseTx // Reset transform for each element
        drawAwtElement(g, el, pageW, pageH, isLandscape, paperSize)
    }
    g.transform = baseTx // Reset

    // 5. Draw Text Annotations
    textAnnotations.forEach { txt ->
        val x = (pageW * txt.xPercent).toInt()
        val y = (pageH * txt.yPercent).toInt()

        val c = txt.color
        g.color = Color(c.red, c.green, c.blue, c.alpha)

        val style = if (txt.isBold) Font.BOLD else Font.PLAIN
        val scaledFontSize = (txt.fontSize * 1.0f).toInt() // Adjusted for visual match
        g.font = Font("Arial", style, scaledFontSize)
        g.drawString(txt.text, x, y)
    }

    g.dispose()
    return img
}

// --- HELPER: Draw The Engineering Layout (AWT Version) ---
fun drawAwtEngineeringLayout(
    g: java.awt.Graphics2D,
    pageW: Int, pageH: Int,
    mL: Double, mT: Double, mR: Double, mB: Double,
    color: Color,
    annexure: String,
    b1Text: String,
    page: Int, totalPages: Int,
    hScale: Double, vScale: Double,
    legendType: String,
    preColor: Color, postColor: Color
) {
    val left = mL
    val top = mT
    val right = pageW - mR
    val bottom = pageH - mB
    val scale = pageW / 42.0f // Scale relative to 42cm width
    val rowH = 1.0f * scale
    val logoW = 3.1f * scale
    val logoH = 1.0f * scale

    val colA_W = (5.4f + 4.1f) * scale
    val colB_W = 9.2f * scale
    val colC_W = 8.2f * scale
    val colD_W = (3.7f + 2.5f) * scale

    val x_D_Start = right - colD_W
    val x_C_Start = x_D_Start - colC_W
    val x_B_Start = x_C_Start - colB_W
    val x_A_Start = x_B_Start - colA_W

    val x_A_Split = x_A_Start + (5.4f * scale)
    val x_D_Split = x_D_Start + (3.7f * scale)

    val y_Footer_Top = bottom - (3 * rowH)
    val y_Row_Mid1 = bottom - (2 * rowH)
    val y_Row_Mid2 = bottom - rowH
    val y_D5_Top = y_Footer_Top - rowH

    g.color = color
    g.stroke = BasicStroke(1.0f)

    // 1. Logo Box (Annexure)
    g.drawRect((right - logoW).toInt(), top.toInt(), logoW.toInt(), logoH.toInt())
    drawCenteredString(g, "Annexure-$annexure", (right - logoW).toInt(), top.toInt(), logoW.toInt(), logoH.toInt(), (scale*0.35f).toInt(), false)

    // 2. Footer Outline
    g.drawLine(x_A_Start.toInt(), y_Footer_Top.toInt(), x_A_Start.toInt(), bottom.toInt()) // Left
    g.drawLine(x_A_Start.toInt(), y_Footer_Top.toInt(), x_D_Start.toInt(), y_Footer_Top.toInt()) // Top Main
    g.drawLine(x_D_Start.toInt(), y_D5_Top.toInt(), x_D_Start.toInt(), bottom.toInt()) // D-Col Left
    g.drawLine(x_D_Start.toInt(), y_D5_Top.toInt(), right.toInt(), y_D5_Top.toInt()) // D5 Top
    g.drawLine(x_D_Start.toInt(), y_Footer_Top.toInt(), right.toInt(), y_Footer_Top.toInt()) // D1 Top

    // Verticals
    g.drawLine(x_B_Start.toInt(), y_Footer_Top.toInt(), x_B_Start.toInt(), bottom.toInt())
    g.drawLine(x_C_Start.toInt(), y_Footer_Top.toInt(), x_C_Start.toInt(), bottom.toInt())
    g.drawLine(x_A_Split.toInt(), y_Footer_Top.toInt(), x_A_Split.toInt(), bottom.toInt())
    g.drawLine(x_D_Split.toInt(), y_Footer_Top.toInt(), x_D_Split.toInt(), bottom.toInt())

    // Horizontals
    g.drawLine(x_A_Start.toInt(), y_Row_Mid1.toInt(), x_B_Start.toInt(), y_Row_Mid1.toInt())
    g.drawLine(x_D_Start.toInt(), y_Row_Mid1.toInt(), right.toInt(), y_Row_Mid1.toInt())
    g.drawLine(x_A_Start.toInt(), y_Row_Mid2.toInt(), x_B_Start.toInt(), y_Row_Mid2.toInt())
    g.drawLine(x_D_Split.toInt(), y_Row_Mid2.toInt(), right.toInt(), y_Row_Mid2.toInt())

    // Text Content
    drawCenteredString(g, "LEGEND:-", x_A_Start.toInt(), (y_Footer_Top - rowH).toInt(), (x_B_Start-x_A_Start).toInt(), rowH.toInt(), (scale*0.35f).toInt(), false)
    drawCenteredString(g, "All dimensions in meters(m)", x_D_Start.toInt(), y_D5_Top.toInt(), colD_W.toInt(), rowH.toInt(), (scale*0.35f).toInt(), false)

    drawCenteredString(g, "SCALE", x_D_Start.toInt(), y_Footer_Top.toInt(), (x_D_Split-x_D_Start).toInt(), rowH.toInt(), (scale*0.35f).toInt(), true)
    drawCenteredString(g, "Annexure-$annexure", x_D_Split.toInt(), y_Footer_Top.toInt(), (right-x_D_Split).toInt(), rowH.toInt(), (scale*0.35f).toInt(), false)

    val scaleStr = "1:${hScale.toInt()} (H)  1:${vScale.toInt()} (V)"
    drawCenteredString(g, scaleStr, x_D_Start.toInt(), y_Row_Mid1.toInt(), (x_D_Split-x_D_Start).toInt(), (bottom-y_Row_Mid1).toInt(), (scale*0.3f).toInt(), false)

    drawCenteredString(g, "SHEET NO.", x_D_Split.toInt(), y_Row_Mid1.toInt(), (right-x_D_Split).toInt(), rowH.toInt(), (scale*0.35f).toInt(), true)

    g.color = Color.BLUE
    drawCenteredString(g, "$page of $totalPages", x_D_Split.toInt(), y_Row_Mid2.toInt(), (right-x_D_Split).toInt(), rowH.toInt(), (scale*0.35f).toInt(), true)
    g.color = color

    // C1 Text
    val c1Lines = "Civil Engineering Department\nIndian Institute of Technology Roorkee\nRoorkee -247667".split("\n")
    drawMultiLineString(g, c1Lines, x_C_Start.toInt(), y_Footer_Top.toInt(), colC_W.toInt(), (bottom-y_Footer_Top).toInt(), (scale*0.3f).toInt())

    // B1 Text
    val b1Lines = b1Text.split("\n")
    drawMultiLineString(g, b1Lines, x_B_Start.toInt(), y_Footer_Top.toInt(), colB_W.toInt(), (bottom-y_Footer_Top).toInt(), (scale*0.3f).toInt())

    // Legend A Cols
    drawCenteredString(g, "DESCRIPTION", x_A_Start.toInt(), y_Footer_Top.toInt(), (x_A_Split-x_A_Start).toInt(), rowH.toInt(), (scale*0.3f).toInt(), true)
    drawCenteredString(g, "SYMBOL", x_A_Split.toInt(), y_Footer_Top.toInt(), (x_B_Start-x_A_Split).toInt(), rowH.toInt(), (scale*0.3f).toInt(), true)

    g.color = postColor
    drawCenteredString(g, "Post Monsoon", x_A_Start.toInt(), y_Row_Mid1.toInt(), (x_A_Split-x_A_Start).toInt(), rowH.toInt(), (scale*0.3f).toInt(), false)
    val postY = (y_Row_Mid1 + y_Row_Mid2)/2
    g.drawLine(x_A_Split.toInt() + 5, postY.toInt(), x_B_Start.toInt() - 5, postY.toInt())

    g.color = preColor
    drawCenteredString(g, "Pre Monsoon", x_A_Start.toInt(), y_Row_Mid2.toInt(), (x_A_Split-x_A_Start).toInt(), rowH.toInt(), (scale*0.3f).toInt(), false)
    val preY = (y_Row_Mid2 + bottom)/2
    g.drawLine(x_A_Split.toInt() + 5, preY.toInt(), x_B_Start.toInt() - 5, preY.toInt())
}

fun drawCenteredString(g: java.awt.Graphics2D, text: String, x: Int, y: Int, w: Int, h: Int, size: Int, bold: Boolean) {
    val style = if (bold) Font.BOLD else Font.PLAIN
    g.font = Font("Arial", style, size)
    val metrics = g.fontMetrics
    val textX = x + (w - metrics.stringWidth(text)) / 2
    val textY = y + ((h - metrics.height) / 2) + metrics.ascent
    g.drawString(text, textX, textY)
}

fun drawMultiLineString(g: java.awt.Graphics2D, lines: List<String>, x: Int, y: Int, w: Int, h: Int, size: Int) {
    g.font = Font("Arial", Font.BOLD, size)
    val metrics = g.fontMetrics
    val lineHeight = metrics.height
    val totalHeight = lineHeight * lines.size
    val startY = y + (h - totalHeight) / 2 + metrics.ascent

    lines.forEachIndexed { i, line ->
        val textX = x + (w - metrics.stringWidth(line)) / 2
        g.drawString(line, textX, startY + (i * lineHeight))
    }
}

// --- HELPER: Draw Graph (Background) ---
fun drawAwtGraph(
    g: java.awt.Graphics2D,
    data: List<RiverPoint>,
    type: String,
    hScale: Double, vScale: Double,
    showPre: Boolean, showPost: Boolean,
    preColor: Color, postColor: Color,
    preDotted: Boolean, postDotted: Boolean,
    preWidth: Float, postWidth: Float,
    preShowPoints: Boolean, postShowPoints: Boolean,
    showGrid: Boolean,
    totalML: Double, totalMT: Double, contentW: Double, contentH: Double,
    row: Int, col: Int
) {
    val oldClip = g.clip
    g.setClip(totalML.toInt(), totalMT.toInt(), contentW.toInt(), contentH.toInt())
    val baseTx = g.transform
    g.translate(totalML, totalMT)
    val offsetX = -col * contentW
    val offsetY = -row * contentH
    g.translate(offsetX, offsetY)

    val IMG_PX_PER_CM = 38.0 // Keep internal consistent

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
    val graphContentH = (maxY - minY) * pxPerMeterY
    val totalGraphAreaH = graphContentH + 50.0
    val tableY0 = totalGraphAreaH.toInt()

    fun mapX(v: Double) = (paddingLeft + (v - minX) * pxPerMeterX).toInt()
    fun mapY(v: Double) = (totalGraphAreaH - (v - minY) * pxPerMeterY).toInt()

    val sortedPoints = data.sortedBy { if(type=="L-Section") it.chainage else it.distance }

    // Grid
    if (showGrid) {
        g.color = Color.LIGHT_GRAY
        g.stroke = BasicStroke(1f)
        sortedPoints.forEach { p ->
            val x = mapX(if (type == "L-Section") p.chainage else p.distance)
            if (x + offsetX > -50 && x + offsetX < contentW + 50) {
                g.drawLine(x, 0, x, tableY0 + 180)
            }
        }
    }

    // Polylines
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

    // Table Lines
    g.color = Color.BLACK
    g.stroke = BasicStroke(2f)
    val xEnd = (paddingLeft + (maxX - minX) * pxPerMeterX).toInt()
    g.drawLine(0, tableY0, xEnd, tableY0)
    for(i in 1..3) g.drawLine(0, tableY0 + i * tableRowHeight.toInt(), xEnd, tableY0 + i * tableRowHeight.toInt())

    // Table Text
    val fontPlain = Font("Arial", Font.PLAIN, 10)
    g.font = fontPlain
    val graphTx = g.transform
    sortedPoints.forEach { p ->
        val x = mapX(if (type == "L-Section") p.chainage else p.distance)
        if (x + offsetX > -50 && x + offsetX < contentW + 50) {
            val vals = listOf(String.format("%.3f", p.postMonsoon), String.format("%.3f", p.preMonsoon), String.format("%.1f", if(type=="L-Section") p.chainage else p.distance))
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

    // Reset and Draw Axis/Headers
    g.transform = baseTx
    g.setClip(oldClip) // Draw headers outside clip logic if needed, but usually we mask.
    // Re-clip to content area for headers
    g.setClip(totalML.toInt(), totalMT.toInt(), contentW.toInt(), contentH.toInt())

    // Axis background
    g.color = Color.WHITE
    g.fillRect(totalML.toInt(), totalMT.toInt(), paddingLeft.toInt(), contentH.toInt())

    g.color = Color.BLACK
    g.stroke = BasicStroke(2f)
    g.drawLine((totalML + paddingLeft).toInt(), totalMT.toInt(), (totalML + paddingLeft).toInt(), (totalMT + tableY0).toInt())

    // Y Ticks
    g.font = Font("Arial", Font.PLAIN, 10)
    for(i in 0..((maxY-minY).toInt())) {
        val yVal = minY + i
        val logicY = mapY(yVal)
        val screenY = logicY + offsetY + totalMT
        if (screenY >= totalMT && screenY <= totalMT + tableY0) {
            g.drawLine((totalML + paddingLeft - 5).toInt(), screenY.toInt(), (totalML + paddingLeft).toInt(), screenY.toInt())
            g.drawString(String.format("%.1f", yVal), (totalML + 5).toInt(), screenY.toInt() + 5)
        }
    }

    // Table Headers (Sticky)
    val stickyTableTop = totalMT + offsetY + tableY0
    if (stickyTableTop < totalMT + contentH) {
        g.color = Color.WHITE
        g.fillRect(totalML.toInt(), stickyTableTop.toInt().coerceAtLeast(totalMT.toInt()), paddingLeft.toInt(), (3*tableRowHeight).toInt())
        g.color = Color.BLACK
        g.stroke = BasicStroke(2f)
        val headers = listOf("POST RL", "PRE RL", if(type=="L-Section") "CHAINAGE" else "OFFSET")
        g.font = Font("Arial", Font.BOLD, 12)
        headers.forEachIndexed { i, label ->
            val y = stickyTableTop + i * tableRowHeight
            val cy = y + tableRowHeight/2
            if(y < totalMT + contentH) {
                g.drawLine(totalML.toInt(), y.toInt(), (totalML + paddingLeft).toInt(), y.toInt())
                g.drawLine(totalML.toInt(), (y+tableRowHeight).toInt(), (totalML + paddingLeft).toInt(), (y+tableRowHeight).toInt())
                g.drawString(label, (totalML+5).toInt(), cy.toInt() + 5)
            }
        }
        g.drawLine((totalML+paddingLeft).toInt(), stickyTableTop.toInt(), (totalML+paddingLeft).toInt(), (stickyTableTop + 3*tableRowHeight).toInt())
    }
}

// --- HELPER: Draw Shapes & Floating Graphs (AWT) ---
fun drawAwtElement(
    g: java.awt.Graphics2D,
    el: ReportElement,
    pageW: Int, pageH: Int,
    isLandscape: Boolean,
    paperSize: PaperSize
) {
    val x = (pageW * el.xPercent).toInt()
    val y = (pageH * el.yPercent).toInt()
    val w = (pageW * el.widthPercent).toInt()
    val h = (pageH * el.heightPercent).toInt()

    if (w <= 0 || h <= 0) return

    val oldTx = g.transform
    g.rotate(Math.toRadians(el.rotation.toDouble()), (x + w/2).toDouble(), (y + h/2).toDouble())

    if (el.type == ElementType.GRAPH_IMAGE) {
        // Recursively draw graph in the box
        // Map Colors
        val preC = Color(el.graphPreColor.red, el.graphPreColor.green, el.graphPreColor.blue)
        val postC = Color(el.graphPostColor.red, el.graphPostColor.green, el.graphPostColor.blue)

        // Temporarily adjust scale logic for the small box
        // We reuse drawAwtGraph but treat the box as the "content area"
        drawAwtGraph(g, el.graphData, el.graphType, el.graphHScale, el.graphVScale,
            el.graphShowPre, el.graphShowPost, preC, postC, el.graphPreDotted, el.graphPostDotted,
            el.graphPreWidth, el.graphPostWidth, true, true, el.graphShowGrid,
            x.toDouble(), y.toDouble(), w.toDouble(), h.toDouble(), 0, 0)

    } else {
        // Shapes
        g.color = Color(el.strokeColor.red, el.strokeColor.green, el.strokeColor.blue, el.strokeColor.alpha)
        g.stroke = BasicStroke(el.strokeWidth)

        // FIX: Replaced "Color.Transparent" with check for alpha
        val fill = if(el.fillColor.alpha > 0f) Color(el.fillColor.red, el.fillColor.green, el.fillColor.blue, el.fillColor.alpha) else null

        when (el.type) {
            ElementType.SQUARE -> {
                if (fill != null) { g.color = fill; g.fillRect(x, y, w, h) }
                g.color = Color(el.strokeColor.red, el.strokeColor.green, el.strokeColor.blue, el.strokeColor.alpha)
                g.drawRect(x, y, w, h)
            }
            ElementType.CIRCLE -> {
                if (fill != null) { g.color = fill; g.fillOval(x, y, w, h) }
                g.color = Color(el.strokeColor.red, el.strokeColor.green, el.strokeColor.blue, el.strokeColor.alpha)
                g.drawOval(x, y, w, h)
            }
            ElementType.LINE -> g.drawLine(x, y + h/2, x + w, y + h/2)
            ElementType.ARROW_RIGHT -> {
                val p = GeneralPath()
                p.moveTo(x.toDouble(), (y + h/2).toDouble())
                p.lineTo((x + w).toDouble(), (y + h/2).toDouble()) // Line
                // Head
                p.moveTo((x + w).toDouble(), (y + h/2).toDouble())
                p.lineTo((x + w*0.8).toDouble(), (y + h*0.3).toDouble())
                p.lineTo((x + w*0.8).toDouble(), (y + h*0.7).toDouble())
                p.closePath()

                if(fill != null) { g.color = fill; g.fill(p) }
                g.color = Color(el.strokeColor.red, el.strokeColor.green, el.strokeColor.blue, el.strokeColor.alpha)
                g.draw(p)
            }
            // ... Add other shapes similarly if needed, typically basic ones suffice for now ...
            else -> {}
        }
    }
    g.transform = oldTx
}

// --- 2. SAVE IMAGE (Wrapper) ---
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
    textAnnotations: List<TextAnnotation> = emptyList(),
    // Forwarded Params
    layoutType: PageLayoutType = PageLayoutType.BLANK,
    elements: List<ReportElement> = emptyList(),
    pageNumber: Int = 1,
    totalPageCount: Int = 1,
    annexureValue: String = "",
    b1Text: String = ""
) {
    val img = renderPageToImage(
        data, row, col, type, paperSize, isLandscape, hScale, vScale, config, chainage,
        showPre, showPost, preColor, postColor, preDotted, postDotted, preWidth, postWidth,
        preShowPoints, postShowPoints, showGrid, textAnnotations,
        layoutType, elements, pageNumber, totalPageCount, annexureValue, b1Text
    )
    if (img != null) {
        ImageIO.write(img, "png", file)
    }
}

// --- 3. SAVE FULL PDF REPORT ---
fun saveReportToPdf(
    reportItems: List<ReportPageItem>,
    file: File,
    paperSize: PaperSize,
    isLandscape: Boolean,
    lHScale: Double, lVScale: Double,
    xHScale: Double, xVScale: Double,
    pageConfigs: Map<String, ReportConfig>,
    pageTextData: Map<String, List<TextAnnotation>>,
    // NEW: Page Elements & Layout Data Maps
    pageElementData: Map<String, List<ReportElement>> = emptyMap(),
    pageLayoutTypes: Map<String, PageLayoutType> = emptyMap(), // Needs to be passed or derived
    pageAnnexureValues: Map<String, String> = emptyMap(),
    pageB1Values: Map<String, String> = emptyMap(),
    pageNumberOverrides: Map<String, String> = emptyMap(),

    showPre: Boolean, showPost: Boolean,
    preColor: Color, postColor: Color,
    preDotted: Boolean, postDotted: Boolean,
    preWidth: Float, postWidth: Float,
    preShowPoints: Boolean, postShowPoints: Boolean,
    showGrid: Boolean
) {
    val doc = PDDocument()
    try {
        reportItems.forEachIndexed { index, item ->
            val cfg = pageConfigs[item.id] ?: ReportConfig()
            val txts = pageTextData[item.id] ?: emptyList()
            val elems = pageElementData[item.id] ?: emptyList()
            // We assume layout is stored in config or passed. Since config doesn't have layoutType, we assume Engineering if text fields present or default
            // Ideally should be passed. For now, defaulting to standard if not found
            val layout = if(cfg.showOuterBorder) PageLayoutType.ENGINEERING_STD else PageLayoutType.BLANK // Inference heuristic

            val hScale = if (cfg.legendType == "L-Section") lHScale else xHScale
            val vScale = if (cfg.legendType == "L-Section") lVScale else xVScale

            val pageNumStr = pageNumberOverrides[item.id] ?: "${index + 1}"
            val pageNumInt = pageNumStr.toIntOrNull() ?: (index+1)

            val bufferedImage = renderPageToImage(
                item.data, item.xOffset.toInt(), item.yOffset.toInt(), item.type,
                paperSize, isLandscape, hScale, vScale, cfg, 0.0,
                showPre, showPost, preColor, postColor, preDotted, postDotted,
                preWidth, postWidth, preShowPoints, postShowPoints, showGrid, txts,
                layout, elems, pageNumInt, reportItems.size,
                pageAnnexureValues[item.id] ?: "",
                pageB1Values[item.id] ?: ""
            )

            if (bufferedImage != null) {
                val pointsPerMm = 72.0f / 25.4f
                val pWidth = (if (isLandscape) paperSize.heightMm else paperSize.widthMm) * pointsPerMm
                val pHeight = (if (isLandscape) paperSize.widthMm else paperSize.heightMm) * pointsPerMm

                val page = PDPage(PDRectangle(pWidth, pHeight))
                doc.addPage(page)

                val pdImage = LosslessFactory.createFromImage(doc, bufferedImage)
                PDPageContentStream(doc, page).use { contentStream ->
                    contentStream.drawImage(pdImage, 0f, 0f, pWidth, pHeight)
                }
            }
        }
        doc.save(file)
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        doc.close()
    }

    try {
        if (file.exists() && file.length() > 0) {
            Desktop.getDesktop().open(file)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}