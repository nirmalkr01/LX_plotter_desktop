import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
import java.util.UUID

enum class ElementType {
    SQUARE, CIRCLE, TRIANGLE,
    LINE, CURVE,
    ARROW_RIGHT, ARROW_LEFT, ARROW_UP, ARROW_DOWN,
    ELBOW_ARROW_RIGHT_DOWN, ELBOW_ARROW_DOWN_RIGHT, ELBOW_ARROW_LEFT_DOWN
}

data class ReportElement(
    val id: String = UUID.randomUUID().toString(),
    val type: ElementType,
    var xPercent: Float = 0.1f,
    var yPercent: Float = 0.1f,
    var widthPercent: Float = 0.1f,
    var heightPercent: Float = 0.1f,
    var strokeColor: Color = Color.Black,
    var fillColor: Color = Color.Transparent,
    var strokeWidth: Float = 2f,
    var rotation: Float = 0f
)

@Composable
fun ElementRenderer(
    element: ReportElement,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cx = w / 2
        val cy = h / 2

        rotate(element.rotation, pivot = Offset(cx, cy)) {
            val stroke = Stroke(width = element.strokeWidth)
            val path = Path()

            when (element.type) {
                ElementType.SQUARE -> {
                    if (element.fillColor != Color.Transparent) {
                        drawRect(color = element.fillColor, size = size)
                    }
                    drawRect(color = element.strokeColor, size = size, style = stroke)
                }
                ElementType.CIRCLE -> {
                    if (element.fillColor != Color.Transparent) {
                        drawOval(color = element.fillColor, size = size)
                    }
                    drawOval(color = element.strokeColor, size = size, style = stroke)
                }
                ElementType.TRIANGLE -> {
                    path.moveTo(w / 2, 0f)
                    path.lineTo(w, h)
                    path.lineTo(0f, h)
                    path.close()
                    if (element.fillColor != Color.Transparent) {
                        drawPath(path, element.fillColor, style = Fill)
                    }
                    drawPath(path, element.strokeColor, style = stroke)
                }
                ElementType.LINE -> {
                    // Horizontal line centered vertically
                    drawLine(
                        color = element.strokeColor,
                        start = Offset(0f, h / 2),
                        end = Offset(w, h / 2),
                        strokeWidth = element.strokeWidth
                    )
                }
                ElementType.CURVE -> {
                    path.moveTo(0f, h)
                    path.quadraticBezierTo(w / 4, 0f, w, 0f)
                    drawPath(path, element.strokeColor, style = stroke)
                }
                ElementType.ARROW_RIGHT -> {
                    drawLine(element.strokeColor, Offset(0f, h / 2), Offset(w, h / 2), element.strokeWidth)
                    path.moveTo(w, h / 2)
                    path.lineTo(w * 0.7f, h / 2 - h * 0.3f)
                    path.lineTo(w * 0.7f, h / 2 + h * 0.3f)
                    path.close()
                    if (element.fillColor != Color.Transparent) {
                        drawPath(path, element.fillColor, style = Fill)
                    }
                    drawPath(path, element.strokeColor, style = Fill)
                }
                ElementType.ARROW_LEFT -> {
                    drawLine(element.strokeColor, Offset(0f, h / 2), Offset(w, h / 2), element.strokeWidth)
                    path.moveTo(0f, h / 2)
                    path.lineTo(w * 0.3f, h / 2 - h * 0.3f)
                    path.lineTo(w * 0.3f, h / 2 + h * 0.3f)
                    path.close()
                    if (element.fillColor != Color.Transparent) {
                        drawPath(path, element.fillColor, style = Fill)
                    }
                    drawPath(path, element.strokeColor, style = Fill)
                }
                ElementType.ARROW_UP -> {
                    drawLine(element.strokeColor, Offset(w / 2, h), Offset(w / 2, 0f), element.strokeWidth)
                    path.moveTo(w / 2, 0f)
                    path.lineTo(w / 2 - w * 0.3f, h * 0.3f)
                    path.lineTo(w / 2 + w * 0.3f, h * 0.3f)
                    path.close()
                    if (element.fillColor != Color.Transparent) {
                        drawPath(path, element.fillColor, style = Fill)
                    }
                    drawPath(path, element.strokeColor, style = Fill)
                }
                ElementType.ARROW_DOWN -> {
                    drawLine(element.strokeColor, Offset(w / 2, 0f), Offset(w / 2, h), element.strokeWidth)
                    path.moveTo(w / 2, h)
                    path.lineTo(w / 2 - w * 0.3f, h * 0.7f)
                    path.lineTo(w / 2 + w * 0.3f, h * 0.7f)
                    path.close()
                    if (element.fillColor != Color.Transparent) {
                        drawPath(path, element.fillColor, style = Fill)
                    }
                    drawPath(path, element.strokeColor, style = Fill)
                }
                ElementType.ELBOW_ARROW_RIGHT_DOWN -> {
                    // Horizontal then Vertical
                    path.moveTo(0f, 0f)
                    path.lineTo(w, 0f)
                    path.lineTo(w, h)
                    drawPath(path, element.strokeColor, style = stroke)
                    // Head at bottom
                    val headPath = Path()
                    headPath.moveTo(w, h)
                    headPath.lineTo(w - w*0.2f, h - h*0.2f)
                    headPath.lineTo(w + w*0.2f, h - h*0.2f)
                    headPath.close()
                    // Fill Color Logic
                    if (element.fillColor != Color.Transparent) {
                        drawPath(headPath, element.fillColor, style = Fill)
                    }
                    drawPath(headPath, element.strokeColor, style = Fill)
                }
                ElementType.ELBOW_ARROW_DOWN_RIGHT -> {
                    // Vertical then Horizontal
                    path.moveTo(0f, 0f)
                    path.lineTo(0f, h)
                    path.lineTo(w, h)
                    drawPath(path, element.strokeColor, style = stroke)
                    // Head at right
                    val headPath = Path()
                    headPath.moveTo(w, h)
                    headPath.lineTo(w - w*0.2f, h - h*0.2f)
                    headPath.lineTo(w - w*0.2f, h + h*0.2f)
                    headPath.close()
                    // Fill Color Logic
                    if (element.fillColor != Color.Transparent) {
                        drawPath(headPath, element.fillColor, style = Fill)
                    }
                    drawPath(headPath, element.strokeColor, style = Fill)
                }
                ElementType.ELBOW_ARROW_LEFT_DOWN -> {
                    // Horizontal (Right to Left) then Vertical
                    path.moveTo(w, 0f)
                    path.lineTo(0f, 0f)
                    path.lineTo(0f, h)
                    drawPath(path, element.strokeColor, style = stroke)
                    // Head at bottom-left
                    val headPath = Path()
                    headPath.moveTo(0f, h)
                    headPath.lineTo(0f - w*0.2f, h - h*0.2f)
                    headPath.lineTo(0f + w*0.2f, h - h*0.2f)
                    headPath.close()
                    if (element.fillColor != Color.Transparent) {
                        drawPath(headPath, element.fillColor, style = Fill)
                    }
                    drawPath(headPath, element.strokeColor, style = Fill)
                }
            }
        }
    }
}