// FILE: D:\LX_plotter_desktop\src\main\kotlin\Download.kt
import androidx.compose.ui.geometry.Offset
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Desktop
import java.awt.Font
import java.awt.RenderingHints
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

// --- 1. SAVE RAW GRAPH (Legacy support for Panel 3) ---
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

    // Keep legacy resolution for raw export to avoid breaking existing workflow
    val IMG_PX_PER_CM = 72.0

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

    if (width <= 0 || height <= 0) return

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
    g.drawLine(padding, height - padding, width - padding, height - padding)
    g.drawLine(padding, padding, padding, height - padding)

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

// --- CORE RENDERER: Renders Page based on Millimeter Coordinates ---
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
    layoutType: PageLayoutType = PageLayoutType.BLANK,
    elements: List<ReportElement> = emptyList(),
    pageNumber: Int = 1,
    totalPageCount: Int = 1,
    annexureValue: String = "",
    b1Text: String = "",
    pxPerMm: Double = 11.81 // Defaults to High Res (300 DPI)
): BufferedImage? {

    val widthMm = if (isLandscape) paperSize.heightMm else paperSize.widthMm
    val heightMm = if (isLandscape) paperSize.widthMm else paperSize.heightMm

    val pageW = (widthMm * pxPerMm).toInt()
    val pageH = (heightMm * pxPerMm).toInt()

    if (pageW <= 0 || pageH <= 0) return null

    val img = BufferedImage(pageW, pageH, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()

    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

    g.color = Color.WHITE
    g.fillRect(0, 0, pageW, pageH)

    val mL = config.marginLeft * pxPerMm
    val mR = config.marginRight * pxPerMm
    val mT = config.marginTop * pxPerMm
    val mB = config.marginBottom * pxPerMm
    val gap = config.borderGap * pxPerMm

    val awtOuterColor = Color(config.outerColor.red, config.outerColor.green, config.outerColor.blue)
    val awtInnerColor = Color(config.innerColor.red, config.innerColor.green, config.innerColor.blue)

    g.color = awtOuterColor
    g.stroke = BasicStroke((config.outerThickness * (pxPerMm / 3.78)).toFloat())

    if (config.showOuterBorder) {
        g.drawRect(mL.toInt(), mT.toInt(), (pageW - mL - mR).toInt(), (pageH - mT - mB).toInt())

        if (config.showInnerBorder) {
            g.color = awtInnerColor
            g.stroke = BasicStroke((config.innerThickness * (pxPerMm / 3.78)).toFloat())
            g.drawRect((mL + gap).toInt(), (mT + gap).toInt(), (pageW - mL - mR - 2*gap).toInt(), (pageH - mT - mB - 2*gap).toInt())
        }
    }

    if(layoutType == PageLayoutType.ENGINEERING_STD) {
        drawAwtEngineeringLayout(g, pageW, pageH, mL, mT, mR, mB, awtOuterColor, annexureValue, b1Text, pageNumber, totalPageCount, hScale, vScale, config.legendType, preColor, postColor, pxPerMm)
    }

    val baseTx = g.transform
    elements.forEach { el ->
        g.transform = baseTx
        drawAwtElement(g, el, pxPerMm)
    }
    g.transform = baseTx

    textAnnotations.forEach { txt ->
        val x = (txt.xMm * pxPerMm).toInt()
        val y = (txt.yMm * pxPerMm).toInt()

        val c = txt.color
        g.color = Color(c.red, c.green, c.blue, c.alpha)

        val style = if (txt.isBold) Font.BOLD else Font.PLAIN
        val scaledFontSize = (txt.fontSize * (pxPerMm / 3.78)).toInt()
        g.font = Font("Arial", style, scaledFontSize)
        g.drawString(txt.text, x, y)
    }

    g.dispose()
    return img
}

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
    preColor: Color, postColor: Color,
    pxPerMm: Double
) {
    val bottom = pageH - mB
    val right = pageW - mR
    val top = mT

    val logoW = 31.0 * pxPerMm
    val logoH = 10.0 * pxPerMm
    val rowH = 10.0 * pxPerMm

    val colAW = 95.0 * pxPerMm
    val colBW = 92.0 * pxPerMm
    val colCW = 82.0 * pxPerMm
    val colDW = 62.0 * pxPerMm

    val xD = right - colDW
    val xC = xD - colCW
    val xB = xC - colBW
    val xA = xB - colAW

    val xASplit = xA + (54.0 * pxPerMm)
    val xDSplit = xD + (37.0 * pxPerMm)

    val yFooterTop = bottom - (3 * rowH)
    val yRowMid1 = bottom - (2 * rowH)
    val yRowMid2 = bottom - rowH
    val yD5Top = yFooterTop - rowH

    g.color = color
    g.stroke = BasicStroke((1.0f * (pxPerMm/3.78f)).toFloat())

    g.drawRect((right - logoW).toInt(), top.toInt(), logoW.toInt(), logoH.toInt())

    g.drawLine(xA.toInt(), yFooterTop.toInt(), xA.toInt(), bottom.toInt())
    g.drawLine(xD.toInt(), yD5Top.toInt(), xD.toInt(), bottom.toInt())
    g.drawLine(right.toInt(), yD5Top.toInt(), right.toInt(), bottom.toInt())

    g.drawLine(xB.toInt(), yFooterTop.toInt(), xB.toInt(), bottom.toInt())
    g.drawLine(xC.toInt(), yFooterTop.toInt(), xC.toInt(), bottom.toInt())
    g.drawLine(xASplit.toInt(), yFooterTop.toInt(), xASplit.toInt(), bottom.toInt())
    g.drawLine(xDSplit.toInt(), yFooterTop.toInt(), xDSplit.toInt(), bottom.toInt())

    g.drawLine(xA.toInt(), yFooterTop.toInt(), right.toInt(), yFooterTop.toInt())
    g.drawLine(xD.toInt(), yD5Top.toInt(), right.toInt(), yD5Top.toInt())

    g.drawLine(xA.toInt(), yRowMid1.toInt(), xB.toInt(), yRowMid1.toInt())
    g.drawLine(xD.toInt(), yRowMid1.toInt(), right.toInt(), yRowMid1.toInt())
    g.drawLine(xA.toInt(), yRowMid2.toInt(), xB.toInt(), yRowMid2.toInt())
    g.drawLine(xDSplit.toInt(), yRowMid2.toInt(), right.toInt(), yRowMid2.toInt())

    fun text(str: String, x1: Double, y1: Double, x2: Double, y2: Double, c: Color = Color.BLACK, bold: Boolean = false, scale: Double = 0.4) {
        val cx = x1 + (x2-x1)/2
        val cy = y1 + (y2-y1)/2
        val size = (10 * scale * (pxPerMm/3.78)).toInt()
        g.font = Font("Arial", if(bold) Font.BOLD else Font.PLAIN, size)
        val metrics = g.fontMetrics
        val tx = cx - metrics.stringWidth(str)/2
        val ty = cy + metrics.ascent/2 - 2
        g.color = c
        g.drawString(str, tx.toInt(), ty.toInt())
    }

    text("Annexure-$annexure", right-logoW, top, right, top+logoH)
    text("LEGEND:-", xA, yFooterTop-rowH, xB, yFooterTop)
    text("SCALE", xD, yFooterTop, xDSplit, yRowMid1, bold=true)
    text("Annexure-$annexure", xDSplit, yFooterTop, right, yRowMid1)
    text("1:${hScale.toInt()}(H) 1:${vScale.toInt()}(V)", xD, yRowMid1, xDSplit, bottom)
    text("SHEET NO.", xDSplit, yRowMid1, right, yRowMid2, bold=true)
    text("$page of $totalPages", xDSplit, yRowMid2, right, bottom, c=Color.BLUE, bold=true)

    text("Post Monsoon", xA, yRowMid1, xASplit, yRowMid2, c=postColor)
    text("Pre Monsoon", xA, yRowMid2, xASplit, bottom, c=preColor)

    g.color = postColor
    val postY = (yRowMid1 + yRowMid2)/2
    g.drawLine((xASplit+5).toInt(), postY.toInt(), (xB-5).toInt(), postY.toInt())

    g.color = preColor
    val preY = (yRowMid2 + bottom)/2
    g.drawLine((xASplit+5).toInt(), preY.toInt(), (xB-5).toInt(), preY.toInt())

    text(b1Text.replace("\n", " "), xB, yFooterTop, xC, bottom)
    text("Civil Eng Dept, IIT Roorkee", xC, yFooterTop, xD, bottom)
}

// --- HELPER: Draw Element (MM -> PX) ---
fun drawAwtElement(g: java.awt.Graphics2D, el: ReportElement, pxPerMm: Double) {
    val x = (el.xMm * pxPerMm).toInt()
    val y = (el.yMm * pxPerMm).toInt()
    val w = (el.widthMm * pxPerMm).toInt()
    val h = (el.heightMm * pxPerMm).toInt()

    if (w <= 0 || h <= 0) return

    val oldTx = g.transform
    g.rotate(Math.toRadians(el.rotation.toDouble()), (x + w/2).toDouble(), (y + h/2).toDouble())

    if (el.type == ElementType.GRAPH_IMAGE) {
        val savedClip = g.clip
        g.setClip(x, y, w, h)
        val graphTx = g.transform
        g.translate(x.toDouble(), y.toDouble())

        // Calculate internal graph scale mapping
        val mmToPx = pxPerMm
        val sortedData = el.graphData.sortedBy { if(el.graphType=="L-Section") it.chainage else it.distance }
            .distinctBy { if(el.graphType=="L-Section") it.chainage else it.distance }

        val xVals = if(el.graphType=="L-Section") sortedData.map{it.chainage} else sortedData.map{it.distance}
        val minX = xVals.minOrNull() ?: 0.0
        val maxX = xVals.maxOrNull() ?: 10.0

        val allY = (if(el.graphShowPre) sortedData.map{it.preMonsoon} else emptyList()) + (if(el.graphShowPost) sortedData.map{it.postMonsoon} else emptyList())
        val maxY = if(allY.isNotEmpty()) ceil(allY.maxOrNull()!!) else 10.0
        val minY = if(allY.isNotEmpty()) floor(allY.minOrNull()!!) else 0.0

        val mmPerMeterX = 1000.0 / max(el.graphHScale, 1.0)
        val mmPerMeterY = 1000.0 / max(el.graphVScale, 1.0)

        // SYNC WITH NEW CAD UI MARGINS
        val tableLeftWidthOffset = el.riverOffsets[-30]?.x ?: 0f
        val tableRightWidthOffset = el.riverOffsets[-31]?.x ?: 0f

        val padLeftMm = 25.0 + tableLeftWidthOffset
        val padTopMm = 5.0
        val rowHMm = 6.0
        val graphHMm = (maxY - minY) * mmPerMeterY
        val tableYStartMm = padTopMm + graphHMm + el.tableGap

        fun mX(v: Double) = ((padLeftMm + (v - minX) * mmPerMeterX) * mmToPx).toInt()
        fun mY(v: Double) = ((tableYStartMm - (v - minY) * mmPerMeterY) * mmToPx).toInt()

        val yTableStartPx = (tableYStartMm * mmToPx).toInt()
        val rowHPx = (rowHMm * mmToPx).toInt()
        val padLeftPx = (padLeftMm * mmToPx).toInt()
        val totalDrawH = ((tableYStartMm + 3 * rowHMm + 5.0) * mmToPx).toInt()

        val visualStrokeScale = 1.0f // Maintain 1.0f for High-Res PDF

        // 1. Grid
        if (el.graphShowGrid) {
            g.color = Color.LIGHT_GRAY
            g.stroke = BasicStroke(1f)
            sortedData.forEach { p ->
                val gx = mX(if(el.graphType=="L-Section") p.chainage else p.distance)
                if(gx > padLeftPx) g.drawLine(gx, 0, gx, totalDrawH)
            }
        }

        val dropColor = Color(Color.LIGHT_GRAY.red, Color.LIGHT_GRAY.green, Color.LIGHT_GRAY.blue, 153)
        val count = sortedData.size

        sortedData.forEachIndexed { index, p ->
            val ix = mX(if(el.graphType=="L-Section") p.chainage else p.distance)
            val isBank = el.graphType == "X-Section" && count > 1 && (index == 1 || index == count - 2)
            val lineColor = if (isBank) Color.BLUE else dropColor

            if (ix >= padLeftPx - 1) {
                // Base Drop Lines
                if (el.graphShowPre) {
                    g.color = dropColor
                    g.stroke = BasicStroke(1f)
                    g.drawLine(ix, mY(p.preMonsoon), ix, yTableStartPx)
                }
                if (el.graphShowPost) {
                    g.color = dropColor
                    g.stroke = BasicStroke(1f)
                    g.drawLine(ix, mY(p.postMonsoon), ix, yTableStartPx)
                }

                // Active Offset Lines
                if(!el.deletedBlueLineIndices.contains(index)) {
                    val lineOffset = el.blueLineOffsets[index] ?: Offset.Zero
                    val offXPx = (lineOffset.x * (mmToPx/3.78)).toInt()
                    val offYPx = (lineOffset.y * (mmToPx/3.78)).toInt()
                    g.color = lineColor
                    g.stroke = BasicStroke(1f)
                    if (el.graphShowPre) g.drawLine(ix + offXPx, mY(p.preMonsoon) + offYPx, ix + offXPx, yTableStartPx)
                    if (el.graphShowPost) g.drawLine(ix + offXPx, mY(p.postMonsoon) + offYPx, ix + offXPx, yTableStartPx)
                }

                // River Texts
                if (isBank && !el.deletedRiverIndices.contains(index)) {
                    val baseTextY = mY(maxY) - (10.0 * mmToPx).toInt()
                    val manualOffset = el.riverOffsets[index] ?: Offset.Zero
                    val offXPx = (manualOffset.x * (mmToPx/3.78)).toInt()
                    val offYPx = (manualOffset.y * (mmToPx/3.78)).toInt()

                    val drawX = ix + offXPx
                    val drawY = baseTextY + offYPx

                    g.color = Color.BLACK
                    g.font = Font("Arial", Font.BOLD, (el.riverTextSize * (mmToPx / 3.78)).toInt())

                    val origTx2 = g.transform
                    g.translate(drawX.toDouble(), drawY.toDouble())
                    g.rotate(Math.toRadians(-90.0))
                    val metrics = g.fontMetrics
                    g.drawString("RIVER", -metrics.stringWidth("RIVER") / 2, metrics.ascent / 2)
                    g.transform = origTx2
                }
            }
        }

        // 2. Series Lines
        fun drawSeries(getter: (RiverPoint)->Double, c: Color, isDotted: Boolean, width: Float) {
            g.color = c
            val sw = width * 2f * visualStrokeScale
            if (isDotted) {
                val dash = 10f * visualStrokeScale
                g.stroke = BasicStroke(sw, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(dash, dash), 0f)
            } else {
                g.stroke = BasicStroke(sw)
            }

            var px = -1; var py = -1
            sortedData.forEach { p ->
                val ix = mX(if(el.graphType=="L-Section") p.chainage else p.distance)
                val iy = mY(getter(p))
                if(px != -1) g.drawLine(px, py, ix, iy)
                px = ix; py = iy
            }
        }
        val preC = Color(el.graphPreColor.red, el.graphPreColor.green, el.graphPreColor.blue)
        val postC = Color(el.graphPostColor.red, el.graphPostColor.green, el.graphPostColor.blue)

        if(el.graphShowPre) drawSeries({it.preMonsoon}, preC, el.graphPreDotted, el.graphPreWidth)
        if(el.graphShowPost) drawSeries({it.postMonsoon}, postC, el.graphPostDotted, el.graphPostWidth)


        // 3. Axis Wipe & Lines
        val yAxisTop = mY(maxY)
        g.color = Color.WHITE
        g.fillRect(0, yAxisTop, padLeftPx, yTableStartPx - yAxisTop)

        g.color = Color.BLACK
        g.stroke = BasicStroke(2f * visualStrokeScale)
        g.drawLine(padLeftPx, yAxisTop, padLeftPx, yTableStartPx)

        g.font = Font("Arial", Font.PLAIN, (el.axisLabelSize * (mmToPx / 3.78)).toInt())
        val metrics = g.fontMetrics
        for(i in 1..((maxY-minY).toInt())) {
            val yVal = minY + i
            val yPos = mY(yVal)
            if (yPos in 0..yTableStartPx) {
                g.stroke = BasicStroke(1f * visualStrokeScale)
                g.drawLine(padLeftPx - (5 * (mmToPx/3.78)).toInt(), yPos, padLeftPx, yPos)
                val txt = String.format("%.1f", yVal)
                g.drawString(txt, padLeftPx - (8 * (mmToPx/3.78)).toInt() - metrics.stringWidth(txt), yPos + metrics.ascent / 2)
            }
        }

        val datumY = mY(minY)
        if (datumY > 0 && datumY < totalDrawH) {
            val txt = "DATUM=${minY}"
            g.font = Font("Arial", Font.PLAIN, (el.datumSize * (mmToPx / 3.78)).toInt())
            val datMetrics = g.fontMetrics
            g.drawString(txt, padLeftPx - (8 * (mmToPx/3.78)).toInt() - datMetrics.stringWidth(txt), datumY - (25 * (mmToPx/3.78)).toInt())
        }

        // 4. Table Borders
        val xEnd = mX(maxX) + (5.0 * mmToPx).toInt() + (tableRightWidthOffset * mmToPx).toInt()
        g.stroke = BasicStroke(2f * visualStrokeScale)
        g.drawLine(0, yTableStartPx, xEnd, yTableStartPx)
        for(i in 1..3) g.drawLine(0, yTableStartPx + i * rowHPx, xEnd, yTableStartPx + i * rowHPx)

        g.drawLine(0, yTableStartPx, 0, yTableStartPx + 3 * rowHPx)
        g.drawLine(padLeftPx, yTableStartPx, padLeftPx, yTableStartPx + 3 * rowHPx)
        g.drawLine(xEnd, yTableStartPx, xEnd, yTableStartPx + 3 * rowHPx)


        // 5. Table Content
        g.font = Font("Arial", Font.PLAIN, (el.tableTextSize * (mmToPx / 3.78)).toInt())
        val tableMetrics = g.fontMetrics
        sortedData.forEachIndexed { index, p ->
            val ix = mX(if(el.graphType=="L-Section") p.chainage else p.distance)
            val vals = listOf(String.format("%.3f", p.postMonsoon), String.format("%.3f", p.preMonsoon), String.format("%.1f", if(el.graphType=="L-Section") p.chainage else p.distance))
            val colors = listOf(postC, preC, Color.BLACK)

            vals.forEachIndexed { i, txt ->
                val cellCenterY = yTableStartPx + i * rowHPx + rowHPx/2
                val visualX = if (index == 0) ix + (8 * (mmToPx/3.78)).toInt() else ix
                g.color = colors[i]

                val origTx3 = g.transform
                g.translate(visualX.toDouble(), cellCenterY.toDouble())
                g.rotate(Math.toRadians(-90.0))
                g.drawString(txt, -tableMetrics.stringWidth(txt)/2, tableMetrics.ascent/2 - 2)
                g.transform = origTx3
            }
        }

        // 6. Row Headers
        val labels = if(el.graphType == "L-Section") listOf("POST MONSOON RL", "PRE MONSOON RL", "Chainage in mt.") else listOf("POST RL", "PRE RL", "OFFSET")
        g.font = Font("Arial", Font.BOLD, (el.tableTextSize * (mmToPx / 3.78)).toInt())
        val headerMetrics = g.fontMetrics
        labels.forEachIndexed { i, l ->
            val cy = yTableStartPx + i * rowHPx + rowHPx/2
            val cx = padLeftPx / 2
            g.color = Color.BLACK
            g.drawString(l, cx - headerMetrics.stringWidth(l)/2, cy + headerMetrics.ascent/2 - 2)
        }

        // 7. Chainage Label
        if (el.graphType == "X-Section" && sortedData.isNotEmpty() && !el.isChLabelDeleted) {
            val chainageVal = sortedData.first().chainage
            val chLabel = "CH:-${String.format("%.1f", chainageVal)}"
            val footerY = yTableStartPx + 3 * rowHPx + (10 * (mmToPx/3.78)).toInt()
            val tableCenter = xEnd / 2
            g.font = Font("SansSerif", Font.PLAIN, (el.chainageTextSize * (mmToPx / 3.78)).toInt())
            val chMetrics = g.fontMetrics

            val offX = (el.chLabelOffset.x * (mmToPx/3.78)).toInt()
            val offY = (el.chLabelOffset.y * (mmToPx/3.78)).toInt()

            val drawX = tableCenter - chMetrics.stringWidth(chLabel)/2 + offX
            val drawY = footerY + offY + chMetrics.ascent
            g.color = Color.BLACK
            g.drawString(chLabel, drawX, drawY)
        }

        g.transform = graphTx
        g.clip = savedClip

    } else {
        // Shapes
        g.color = Color(el.strokeColor.red, el.strokeColor.green, el.strokeColor.blue, el.strokeColor.alpha)
        g.stroke = BasicStroke((el.strokeWidth * (pxPerMm/3.8)).toFloat())
        val fill = if(el.fillColor.alpha > 0) Color(el.fillColor.red, el.fillColor.green, el.fillColor.blue, el.fillColor.alpha) else null

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
            else -> {}
        }
    }
    g.transform = oldTx
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
    pageElementData: Map<String, List<ReportElement>> = emptyMap(),
    pageLayoutTypes: Map<String, PageLayoutType> = emptyMap(),
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
            val layout = if(cfg.showOuterBorder) PageLayoutType.ENGINEERING_STD else PageLayoutType.BLANK

            val hScale = if (cfg.legendType == "L-Section") lHScale else xHScale
            val vScale = if (cfg.legendType == "L-Section") lVScale else xVScale

            val pageNumStr = pageNumberOverrides[item.id] ?: "${index + 1}"
            val pageNumInt = pageNumStr.toIntOrNull() ?: (index+1)

            // 1. Create High-Res Image (300 DPI)
            val exportPxPerMm = 11.81 // 300 DPI

            val bufferedImage = renderPageToImage(
                item.data, 0, 0, item.type,
                paperSize, isLandscape, hScale, vScale, cfg, 0.0,
                showPre, showPost, preColor, postColor, preDotted, postDotted,
                preWidth, postWidth, preShowPoints, postShowPoints, showGrid, txts,
                layout, elems, pageNumInt, reportItems.size,
                pageAnnexureValues[item.id] ?: "",
                pageB1Values[item.id] ?: "",
                exportPxPerMm
            )

            if (bufferedImage != null) {
                // 2. Define PDF Page Size (Points: 72 DPI)
                val ptPerMm = 72.0f / 25.4f
                val pWidth = (if (isLandscape) paperSize.heightMm else paperSize.widthMm) * ptPerMm
                val pHeight = (if (isLandscape) paperSize.widthMm else paperSize.heightMm) * ptPerMm

                val page = PDPage(PDRectangle(pWidth, pHeight))
                doc.addPage(page)

                // 3. Draw Image Scaled to Page
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