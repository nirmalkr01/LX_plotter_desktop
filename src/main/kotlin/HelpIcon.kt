import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- NEW HELP WORKFLOW UI ---

@Composable
fun HelpActionIcon(onClick: () -> Unit) {
    // Reuses the standardized HeaderIconButton from Components.kt
    HeaderIconButton(
        onClick = onClick,
        icon = Icons.Default.HelpOutline,
        tooltip = "Help & Documentation",
        tint = Color(0xFF0078D4) // Professional Word Blue
    )
}

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("LX Plotter User Guide", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .width(650.dp)
                    .height(500.dp)
                    .background(Color(0xFFFAFAFA))
                    .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                HelpSection(
                    title = "1. Getting Started",
                    icon = Icons.Default.UploadFile,
                    steps = listOf(
                        "Click the 'Load' (+) button in the top header or use 'Blank Project' on the Home screen.",
                        "Select your CSV file containing survey data.",
                        "Map your CSV columns (Chainage, Offset, Pre/Post levels) in the popup window.",
                        "Verify the data preview and confirm to load."
                    )
                )

                HelpSection(
                    title = "2. Analysis & Editing",
                    icon = Icons.Default.Analytics,
                    steps = listOf(
                        "Switch between 'X-Section' and 'L-Section' using the tabs in the Ribbon.",
                        "Use 'Manual Mode' toggle in the table footer to edit specific RL values or set Zero points.",
                        "Check the 'Notifications' bell icon for data anomalies (e.g., Pre level > Post level)."
                    )
                )

                HelpSection(
                    title = "3. Visual Styling",
                    icon = Icons.Default.Palette,
                    steps = listOf(
                        "Use the 'View' tab in the ribbon to toggle Grid, Ruler, or data series visibility.",
                        "Customize line colors, thickness, and style (Dotted/Solid) using the dropdowns.",
                        "Zoom in/out using the controls in the header or the ribbon."
                    )
                )

                HelpSection(
                    title = "4. Report Generation",
                    icon = Icons.Default.PictureAsPdf,
                    steps = listOf(
                        "Click the 'Report' button in the header (under Save icon) to enter Report Mode.",
                        "Add pages using the footer controls.",
                        "Enable 'Grid Mode' (blue button) to define layout slots on the paper.",
                        "Select a slot, then click the 'Arrow' button in the center panel to inject the current graph.",
                        "For L-Sections, use the 'Auto Split' tool to automatically segment long profiles."
                    )
                )

                HelpSection(
                    title = "5. Final Export",
                    icon = Icons.Default.Save,
                    steps = listOf(
                        "Review all pages in the right-hand panel.",
                        "Apply global settings (Borders, Margins, Headers) via the 'Layout' tab.",
                        "Click the red 'Export PDF' button in the header to save your document."
                    )
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Close Guide") }
        }
    )
}

@Composable
private fun HelpSection(title: String, icon: ImageVector, steps: List<String>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            Icon(icon, null, tint = Color(0xFF2B579A), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B579A))
        }
        steps.forEach { step ->
            Row(modifier = Modifier.padding(bottom = 4.dp, start = 8.dp)) {
                Text("•", color = Color.Gray, modifier = Modifier.padding(top = 1.dp))
                Spacer(Modifier.width(8.dp))
                Text(step, fontSize = 13.sp, color = Color.DarkGray, lineHeight = 18.sp)
            }
        }
        Divider(color = Color.LightGray.copy(alpha = 0.5f), modifier = Modifier.padding(top = 12.dp))
    }
}