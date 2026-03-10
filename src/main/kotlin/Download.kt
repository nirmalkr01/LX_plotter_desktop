// FILE: D:\LX_plotter_desktop\src\main\kotlin\Download.kt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.awt.BasicStroke
import java.awt.Desktop
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

// --- 1. SAVE RAW GRAPH (Legacy support for Panel 3) ---
fun saveRawGraph(
    data: List<RiverPoint>,
    file: File,
    type: String,
    hScale: Double, vScale: Double,
    showPre: Boolean, showPost: Boolean,
    preColor: java.awt.Color, postColor: java.awt.Color,
    preDotted: Boolean, postDotted: Boolean,
    preWidth: Float, postWidth: Float,
    preShowPoints: Boolean, postShowPoints: Boolean
) {
    if (data.isEmpty()) return

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
    g.color = java.awt.Color.WHITE
    g.fillRect(0, 0, width, height)

    fun mapX(v: Double) = (padding + (v - minX) * pxPerMeterX).toInt()
    fun mapY(v: Double) = (height - padding - (v - minY) * pxPerMeterY).toInt()

    g.color = java.awt.Color.LIGHT_GRAY
    g.stroke = BasicStroke(1f)
    val sortedPoints = data.sortedBy { if(type=="L-Section") it.chainage else it.distance }
    sortedPoints.forEach { p ->
        val x = mapX(if (type == "L-Section") p.chainage else p.distance)
        g.drawLine(x, padding, x, height - padding)
    }

    g.color = java.awt.Color.BLACK
    g.stroke = BasicStroke(2f)
    g.drawLine(padding, height - padding, width - padding, height - padding)
    g.drawLine(padding, padding, padding, height - padding)

    fun drawPoly(getColor: (RiverPoint) -> Double, color: java.awt.Color, isDotted: Boolean, strokeW: Float, showPoints: Boolean) {
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

// --- CORE RENDERER: The High-Res Snapshot Component ---
@Composable
fun PrintablePage(
    item: ReportPageItem,
    paperSize: PaperSize,
    isLandscape: Boolean,
    layoutType: PageLayoutType,
    config: ReportConfig,
    textAnnotations: List<TextAnnotation>,
    elements: List<ReportElement>,
    hScale: Double, vScale: Double,
    showPre: Boolean, showPost: Boolean,
    preColor: Color, postColor: Color,
    preDotted: Boolean, postDotted: Boolean,
    preWidth: Float, postWidth: Float,
    preShowPoints: Boolean, postShowPoints: Boolean,
    showGrid: Boolean,
    pageNumber: Int, totalPageCount: Int,
    annexureValue: String, b1Text: String,
    pxPerMm: Float
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val widthMm = if (isLandscape) paperSize.heightMm else paperSize.widthMm
    val heightMm = if (isLandscape) paperSize.widthMm else paperSize.heightMm

    val paperWPx = widthMm * pxPerMm
    val paperHPx = heightMm * pxPerMm

    Box(
        modifier = Modifier
            .size(with(density) { paperWPx.toDp() }, with(density) { paperHPx.toDp() })
            .background(Color.White)
    ) {
        // 1. Layout Borders
        Canvas(modifier = Modifier.fillMaxSize()) {
            val mLeft = config.marginLeft * pxPerMm
            val mTop = config.marginTop * pxPerMm
            val mRight = config.marginRight * pxPerMm
            val mBottom = config.marginBottom * pxPerMm
            val gap = config.borderGap * pxPerMm

            if (config.showOuterBorder) {
                drawRect(
                    color = config.outerColor,
                    topLeft = Offset(mLeft, mTop),
                    size = Size(paperWPx - mLeft - mRight, paperHPx - mTop - mBottom),
                    style = Stroke(width = config.outerThickness * (pxPerMm / 3.78f))
                )
            }

            if (config.showInnerBorder && config.showOuterBorder) {
                val iL = mLeft + gap
                val iT = mTop + gap
                val iR = mRight + gap
                val iB = mBottom + gap
                drawRect(
                    color = config.innerColor,
                    topLeft = Offset(iL, iT),
                    size = Size(paperWPx - mLeft - mRight - 2 * gap, paperHPx - mTop - mBottom - 2 * gap),
                    style = Stroke(width = config.innerThickness * (pxPerMm / 3.78f))
                )
            }

            val layoutML = if (config.showInnerBorder) mLeft + gap else mLeft
            val layoutMT = if (config.showInnerBorder) mTop + gap else mTop
            val layoutMR = if (config.showInnerBorder) mRight + gap else mRight
            val layoutMB = if (config.showInnerBorder) mBottom + gap else mBottom

            drawPageLayout(
                type = layoutType,
                paperWidthPx = paperWPx,
                paperHeightPx = paperHPx,
                marginLeftPx = layoutML,
                marginTopPx = layoutMT,
                marginRightPx = layoutMR,
                marginBottomPx = layoutMB,
                borderColor = config.outerColor,
                borderThickness = config.outerThickness * (pxPerMm / 3.78f),
                textMeasurer = textMeasurer,
                pageNumber = pageNumber,
                totalPageCount = totalPageCount,
                annexureValue = annexureValue,
                b1Text = b1Text,
                legendType = config.legendType,
                preColor = preColor,
                postColor = postColor,
                preDotted = preDotted,
                postDotted = postDotted,
                hScale = hScale,
                vScale = vScale,
                pxPerMm = pxPerMm
            )
        }

        // 2. Elements (Graphs & Vector Shapes)
        elements.forEach { el ->
            val xPx = el.xMm * pxPerMm
            val yPx = el.yMm * pxPerMm
            val wPx = el.widthMm * pxPerMm
            val hPx = el.heightMm * pxPerMm

            Box(
                modifier = Modifier
                    .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
                    .size(with(density) { wPx.toDp() }, with(density) { hPx.toDp() })
            ) {
                if (el.type == ElementType.GRAPH_IMAGE) {
                    GraphPageCanvas(
                        modifier = Modifier.fillMaxSize(),
                        data = el.graphData,
                        type = el.graphType,
                        paperSize = paperSize,
                        isLandscape = isLandscape,
                        hScale = el.graphHScale,
                        vScale = el.graphVScale,
                        config = ReportConfig(),
                        showPre = el.graphShowPre, showPost = el.graphShowPost,
                        preColor = java.awt.Color(el.graphPreColor.red, el.graphPreColor.green, el.graphPreColor.blue, el.graphPreColor.alpha),
                        postColor = java.awt.Color(el.graphPostColor.red, el.graphPostColor.green, el.graphPostColor.blue, el.graphPostColor.alpha),
                        preWidth = el.graphPreWidth, postWidth = el.graphPostWidth,
                        preDotted = el.graphPreDotted, postDotted = el.graphPostDotted,
                        preShowPoints = preShowPoints, postShowPoints = postShowPoints, // FIX: Pass down global visibility toggle
                        showGrid = el.graphShowGrid,
                        isRawView = true, isTransparentOverlay = true, pxPerMm = pxPerMm,
                        datumSize = el.datumSize, axisLabelSize = el.axisLabelSize,
                        tableTextSize = el.tableTextSize, tableGap = el.tableGap,
                        riverTextSize = el.riverTextSize, chainageTextSize = el.chainageTextSize,
                        riverOffsets = el.riverOffsets, blueLineOffsets = el.blueLineOffsets,
                        chLabelOffset = el.chLabelOffset, deletedRiverIndices = el.deletedRiverIndices,
                        deletedBlueLineIndices = el.deletedBlueLineIndices, isChLabelDeleted = el.isChLabelDeleted
                    )
                } else {
                    ElementRenderer(el.copy(strokeWidth = el.strokeWidth * (pxPerMm / 3.78f)))
                }
            }
        }

        // 3. Texts
        textAnnotations.forEach { txt ->
            val xPx = txt.xMm * pxPerMm
            val yPx = txt.yMm * pxPerMm
            val wPx = txt.widthMm * pxPerMm
            val hPx = txt.heightMm * pxPerMm

            Box(
                modifier = Modifier
                    .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
                    .size(with(density) { wPx.toDp() }, with(density) { hPx.toDp() })
                    .padding(4.dp) // FIX: Removed scaling math, handled natively by Scene Density
            ) {
                Text(
                    text = txt.text,
                    color = txt.color,
                    fontSize = txt.fontSize.sp, // FIX: Let Scene Density scale this natively
                    fontWeight = if (txt.isBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (txt.isItalic) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = if (txt.isUnderline) TextDecoration.Underline else TextDecoration.None,
                    textAlign = txt.textAlign,
                    fontFamily = getFontFamily(txt.fontFamily)
                )
            }
        }
    }
}

// --- 3. SAVE FULL PDF REPORT USING COMPOSE WYSIWYG ---
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
    // FIX: Safety protocol to avoid "File Locked" Corruptions from Adobe Acrobat
    var targetFile = file
    if (targetFile.exists()) {
        var canWrite = false
        try {
            java.io.FileOutputStream(targetFile, true).close()
            canWrite = true
        } catch (e: Exception) {}

        if (!canWrite) {
            // File is locked! Find the next available sequential name.
            var counter = 1
            while (true) {
                val newName = "${file.nameWithoutExtension}_$counter.${file.extension}"
                val candidate = File(file.parent, newName)
                var candidateCanWrite = false
                if (!candidate.exists()) {
                    candidateCanWrite = true
                } else {
                    try {
                        java.io.FileOutputStream(candidate, true).close()
                        candidateCanWrite = true
                    } catch (e: Exception) {}
                }
                if (candidateCanWrite) {
                    targetFile = candidate
                    break
                }
                counter++
            }
        }
    }

    val doc = PDDocument()
    try {
        // High Quality 300 DPI Rendering Factor
        val exportPxPerMm = 11.81f
        // Exact physical match to ensure fonts scale correctly in off-screen scene
        val targetSceneDensity = exportPxPerMm / 3.78f

        reportItems.forEachIndexed { index, item ->
            val cfg = pageConfigs[item.id] ?: ReportConfig()
            val txts = pageTextData[item.id] ?: emptyList()
            val elems = pageElementData[item.id] ?: emptyList()
            val layout = if (cfg.showOuterBorder) PageLayoutType.ENGINEERING_STD else PageLayoutType.BLANK

            val hScale = if (cfg.legendType == "L-Section") lHScale else xHScale
            val vScale = if (cfg.legendType == "L-Section") lVScale else xVScale

            val pageNumStr = pageNumberOverrides[item.id] ?: "${index + 1}"
            val pageNumInt = pageNumStr.toIntOrNull() ?: (index + 1)

            val widthMm = if (isLandscape) paperSize.heightMm else paperSize.widthMm
            val heightMm = if (isLandscape) paperSize.widthMm else paperSize.heightMm

            val widthPx = (widthMm * exportPxPerMm).toInt()
            val heightPx = (heightMm * exportPxPerMm).toInt()

            // PDF Box requires Points (72 DPI)
            val pWidthPts = widthMm * (72.0f / 25.4f)
            val pHeightPts = heightMm * (72.0f / 25.4f)

            // 1. Launch a Headless Jetpack Compose Scene to Snap the Layout
            val scene = ImageComposeScene(
                width = widthPx,
                height = heightPx,
                density = Density(targetSceneDensity) // FIX: Compose will now natively scale all Fonts and DP values!
            )

            scene.setContent {
                PrintablePage(
                    item = item,
                    paperSize = paperSize,
                    isLandscape = isLandscape,
                    layoutType = layout,
                    config = cfg,
                    textAnnotations = txts,
                    elements = elems,
                    hScale = hScale, vScale = vScale,
                    showPre = showPre, showPost = showPost,
                    preColor = preColor, postColor = postColor,
                    preDotted = preDotted, postDotted = postDotted,
                    preWidth = preWidth, postWidth = postWidth,
                    preShowPoints = preShowPoints, postShowPoints = postShowPoints,
                    showGrid = showGrid,
                    pageNumber = pageNumInt, totalPageCount = reportItems.size,
                    annexureValue = pageAnnexureValues[item.id] ?: "",
                    b1Text = pageB1Values[item.id] ?: "",
                    pxPerMm = exportPxPerMm
                )
            }

            // 2. Capture Skia Bitmap and convert to standard AWT Image
            val skiaImage = scene.render()
            val composeBitmap = skiaImage.toComposeImageBitmap()
            val bufferedImage = composeBitmap.toAwtImage()

            scene.close()

            // 3. Inject HD Image into PDF Document
            val page = PDPage(PDRectangle(pWidthPts, pHeightPts))
            doc.addPage(page)

            val pdImage = LosslessFactory.createFromImage(doc, bufferedImage)
            PDPageContentStream(doc, page).use { contentStream ->
                contentStream.drawImage(pdImage, 0f, 0f, pWidthPts, pHeightPts)
            }
        }
        doc.save(targetFile) // Save to our lock-safe target file
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        doc.close()
    }

    try {
        if (targetFile.exists() && targetFile.length() > 0) {
            Desktop.getDesktop().open(targetFile)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}