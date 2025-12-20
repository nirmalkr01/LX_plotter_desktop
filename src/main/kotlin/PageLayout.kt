import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

enum class PageLayoutType(val displayName: String) {
    BLANK("Blank"),
    ENGINEERING_STD("Engineering (Std)")
}

// Helper to draw the specific layout
fun DrawScope.drawPageLayout(
    type: PageLayoutType,
    paperWidthPx: Float,
    paperHeightPx: Float,
    marginLeftPx: Float,
    marginTopPx: Float,
    marginRightPx: Float,
    marginBottomPx: Float,
    borderColor: Color,
    borderThickness: Float, // This is user setting (ignored for internal layout lines now)
    textMeasurer: TextMeasurer
) {
    if (type == PageLayoutType.BLANK) return

    val left = marginLeftPx
    val top = marginTopPx
    val right = paperWidthPx - marginRightPx
    val bottom = paperHeightPx - marginBottomPx

    // FIXED THICKNESS for the Engineering Layout Lines
    val layoutStrokeWidth = 1.0f

    // 1. Calculate Scale Factor based on A3 Landscape Width (42.0 cm)
    val scale = paperWidthPx / 42.0f

    // 2. Define Dimensions (in A3 units/cm)
    val rowH = 1.0f * scale
    val logoW = 3.1f * scale
    val logoH = 1.0f * scale

    // Column Widths
    val colA_W = (5.4f + 4.1f) * scale // 9.5
    val colB_W = 9.2f * scale
    val colC_W = 8.2f * scale
    val colD_W = (3.7f + 2.5f) * scale // 6.2

    // 3. Calculate X Positions (Anchored to RIGHT Margin)
    val x_D_Start = right - colD_W
    val x_C_Start = x_D_Start - colC_W
    val x_B_Start = x_C_Start - colB_W
    val x_A_Start = x_B_Start - colA_W

    // Internal Column Splits
    val x_A_Split = x_A_Start + (5.4f * scale)
    val x_D_Split = x_D_Start + (3.7f * scale)

    // 4. Calculate Y Positions (Anchored to BOTTOM Margin)
    val y_Row1 = bottom - rowH       // Top of Row 1 (Bottom of D1/A1)
    val y_Footer_Top = bottom - (3 * rowH)
    val y_Row_Mid1 = bottom - (2 * rowH)
    val y_Row_Mid2 = bottom - rowH

    // D5 sits above the footer
    val y_D5_Top = y_Footer_Top - rowH

    // --- HELPER: Draw Text Centered in Box ---
    fun drawBoxText(
        text: String,
        x1: Float, y1: Float, x2: Float, y2: Float,
        isBold: Boolean = false,
        color: Color = Color.Black,
        scaleFactor: Float = 0.40f // Increased Default Size
    ) {
        val centerX = x1 + (x2 - x1) / 2
        val centerY = y1 + (y2 - y1) / 2

        // Convert scale unit to SP using DrawScope's density context
        // This ensures crisp rendering on high-DPI screens
        val fontSizeSp = with(this) { (scale * scaleFactor).toSp() }

        val style = TextStyle(
            color = color,
            fontSize = fontSizeSp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Serif, // Times New Roman equivalent
            textAlign = TextAlign.Center
        )

        val layoutResult = textMeasurer.measure(
            text = text,
            style = style
        )

        drawText(
            textLayoutResult = layoutResult,
            topLeft = Offset(centerX - layoutResult.size.width / 2, centerY - layoutResult.size.height / 2)
        )
    }

    // --- HELPER: Draw ID (For remaining dynamic/debug boxes) ---
    fun drawId(id: String, x1: Float, y1: Float, x2: Float, y2: Float) {
        drawBoxText(id, x1, y1, x2, y2, isBold = false, color = Color.Red.copy(alpha = 0.3f), scaleFactor = 0.25f)
    }

    if (type == PageLayoutType.ENGINEERING_STD) {
        // --- DRAW MAIN BORDERS ---

        // 1. Logo (Top Right)
        drawLine(borderColor, Offset(right - logoW, top), Offset(right - logoW, top + logoH), layoutStrokeWidth) // Left vertical
        drawLine(borderColor, Offset(right - logoW, top + logoH), Offset(right, top + logoH), layoutStrokeWidth) // Bottom horizontal
        drawId("LOGO", right - logoW, top, right, top + logoH)

        // 2. Footer Outline
        // Main Left Boundary (A-Start)
        drawLine(borderColor, Offset(x_A_Start, y_Footer_Top), Offset(x_A_Start, bottom), layoutStrokeWidth)

        // Main Top Boundary (A to C)
        drawLine(borderColor, Offset(x_A_Start, y_Footer_Top), Offset(x_D_Start, y_Footer_Top), layoutStrokeWidth)

        // D5 Outline (The "Chimney")
        // Left of D5 (Extension of C|D line)
        drawLine(borderColor, Offset(x_D_Start, y_D5_Top), Offset(x_D_Start, bottom), layoutStrokeWidth)
        // Top of D5
        drawLine(borderColor, Offset(x_D_Start, y_D5_Top), Offset(right, y_D5_Top), layoutStrokeWidth)

        // Line between D5 and D1
        drawLine(borderColor, Offset(x_D_Start, y_Footer_Top), Offset(right, y_Footer_Top), layoutStrokeWidth)

        // --- VERTICAL DIVIDERS ---
        drawLine(borderColor, Offset(x_B_Start, y_Footer_Top), Offset(x_B_Start, bottom), layoutStrokeWidth) // A|B
        drawLine(borderColor, Offset(x_C_Start, y_Footer_Top), Offset(x_C_Start, bottom), layoutStrokeWidth) // B|C

        // Internal Vertical Splits
        drawLine(borderColor, Offset(x_A_Split, y_Footer_Top), Offset(x_A_Split, bottom), layoutStrokeWidth) // A split
        drawLine(borderColor, Offset(x_D_Split, y_Footer_Top), Offset(x_D_Split, bottom), layoutStrokeWidth) // D split (D1, D2/3)

        // --- HORIZONTAL ROW LINES ---

        // Row 1 Line (Top of A3/A4/C1-mid/D2)
        drawLine(borderColor, Offset(x_A_Start, y_Row_Mid1), Offset(x_B_Start, y_Row_Mid1), layoutStrokeWidth)
        drawLine(borderColor, Offset(x_D_Start, y_Row_Mid1), Offset(right, y_Row_Mid1), layoutStrokeWidth)

        // Row 2 Line (Top of A5/A6)
        drawLine(borderColor, Offset(x_A_Start, y_Row_Mid2), Offset(x_B_Start, y_Row_Mid2), layoutStrokeWidth)
        drawLine(borderColor, Offset(x_D_Split, y_Row_Mid2), Offset(right, y_Row_Mid2), layoutStrokeWidth)


        // --- STATIC TEXT & IDS ---

        // NEW: Legend Text above A1
        val legendX = x_A_Start + (0.3f * scale)
        val legendY = y_Footer_Top - (0.4f * scale) // Slightly above the line
        val legendStyle = TextStyle(
            color = Color.Black,
            fontSize = with(this) { (scale * 0.40f).toSp() }, // Match standard size
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal // No Bold
        )
        drawText(textMeasurer, "LEGEND:-", Offset(legendX, legendY), legendStyle)

        // D5: All dimensions in meters(m)
        drawBoxText("All dimensions in meters(m)", x_D_Start, y_D5_Top, right, y_Footer_Top, isBold = false, scaleFactor = 0.35f)

        // D1 (Split L/R)
        // D1-Left: SCALE
        drawBoxText("SCALE", x_D_Start, y_Footer_Top, x_D_Split, y_Row_Mid1, isBold = false, scaleFactor = 0.40f)
        // D1-Right: Dynamic (Keep ID or Blank for now)
        drawId("d1-r", x_D_Split, y_Footer_Top, right, y_Row_Mid1)

        // D2
        drawId("d2", x_D_Start, y_Row_Mid1, x_D_Split, bottom)

        // D3: SHEET NO.
        drawBoxText("SHEET NO.", x_D_Split, y_Row_Mid1, right, y_Row_Mid2, isBold = false, scaleFactor = 0.40f)

        // D4
        drawId("d4", x_D_Split, y_Row_Mid2, right, bottom)

        // C1: Civil Engineering Dept... (Needs smaller font to fit multiple lines)
        val c1Text = "Civil Engineering Department\nIndian Institute of Technology Roorkee\nRoorkee -247667"
        drawBoxText(c1Text, x_C_Start, y_Footer_Top, x_D_Start, bottom, isBold = false, scaleFactor = 0.32f)

        // B1
        drawId("b1", x_B_Start, y_Footer_Top, x_C_Start, bottom)

        // A Cols - Top Row
        // A1: DESCRIPTION
        drawBoxText("DESCRIPTION", x_A_Start, y_Footer_Top, x_A_Split, y_Row_Mid1, isBold = false, scaleFactor = 0.40f)
        // A2: SYMBOL
        drawBoxText("SYMBOL", x_A_Split, y_Footer_Top, x_B_Start, y_Row_Mid1, isBold = false, scaleFactor = 0.40f)

        // A Cols - Mid Row
        drawId("a3", x_A_Start, y_Row_Mid1, x_A_Split, y_Row_Mid2)
        drawId("a4", x_A_Split, y_Row_Mid1, x_B_Start, y_Row_Mid2)

        // A Cols - Bot Row
        drawId("a5", x_A_Start, y_Row_Mid2, x_A_Split, bottom)
        drawId("a6", x_A_Split, y_Row_Mid2, x_B_Start, bottom)
    }
}