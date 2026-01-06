import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ImagePanel(
    riverData: List<RiverPoint>,
    activeGraphId: Double,
    startCh: Double,
    endCh: Double,
    selectedGraphType: String,
    lHScale: Double, lVScale: Double,
    xHScale: Double, xVScale: Double,
    showPre: Boolean, showPost: Boolean,
    preColor: java.awt.Color, postColor: java.awt.Color,
    preDotted: Boolean, postDotted: Boolean,
    preWidth: Float, postWidth: Float,
    preShowPoints: Boolean, postShowPoints: Boolean,
    showGrid: Boolean,
    // NEW PARAMS
    selectedPartitionSlot: PartitionSlot? = null,
    onAddToReport: (ReportElement) -> Unit, // Changed to ReportElement
    onStatusChange: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val rawConfig = ReportConfig(
        marginTop = 2f, marginBottom = 2f, marginLeft = 2f, marginRight = 2f,
        showOuterBorder = false, showInnerBorder = false
    )

    Card(modifier = Modifier.fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Column {
            Row(Modifier.fillMaxWidth().background(Color(0xFFE3F2FD)).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("2. Image View (Raw)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                Button(onClick = {
                    if (activeGraphId != -100.0) {
                        pickFolder()?.let { folder ->
                            scope.launch {
                                onStatusChange("Saving Image...")
                                val data = if (activeGraphId == -1.0) getCurrentViewData(riverData, "L-Section", 0.0, startCh, endCh) else getCurrentViewData(riverData, "X-Section", activeGraphId, 0.0, 0.0)
                                val h = if(selectedGraphType=="L-Section") lHScale else xHScale
                                val v = if(selectedGraphType=="L-Section") lVScale else xVScale
                                withContext(Dispatchers.IO) {
                                    saveRawGraph(data, File(folder, "Graph_${if(activeGraphId==-1.0)"LSec" else "CH${activeGraphId.toInt()}"}.png"), selectedGraphType, h, v, showPre, showPost, preColor, postColor, preDotted, postDotted, preWidth, postWidth, preShowPoints, postShowPoints)
                                }
                                onStatusChange("Image Saved!")
                            }
                        }
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary), contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(28.dp)) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save .PNG", fontSize = 10.sp)
                }
            }

            Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.End) {
                Button(onClick = {
                    if (activeGraphId != -100.0) {
                        // Create a SNAPSHOT of current graph state
                        val data = if (activeGraphId == -1.0) getCurrentViewData(riverData, "L-Section", 0.0, startCh, endCh) else getCurrentViewData(riverData, "X-Section", activeGraphId, 0.0, 0.0)
                        val h = if(selectedGraphType=="L-Section") lHScale else xHScale
                        val v = if(selectedGraphType=="L-Section") lVScale else xVScale

                        // STRICT CHECK: Only allow if a Partition Slot is selected
                        if (selectedPartitionSlot != null) {
                            val xP = selectedPartitionSlot.xPercent
                            val yP = selectedPartitionSlot.yPercent
                            val wP = selectedPartitionSlot.wPercent
                            val hP = selectedPartitionSlot.hPercent

                            val newElement = ReportElement(
                                type = ElementType.GRAPH_IMAGE,
                                xPercent = xP,
                                yPercent = yP,
                                widthPercent = wP,
                                heightPercent = hP,
                                graphData = data,
                                graphType = selectedGraphType,
                                graphHScale = h,
                                graphVScale = v,
                                graphShowPre = showPre,
                                graphShowPost = showPost,
                                graphPreColor = Color(preColor.red, preColor.green, preColor.blue),
                                graphPostColor = Color(postColor.red, postColor.green, postColor.blue),
                                graphPreDotted = preDotted,
                                graphPostDotted = postDotted,
                                graphPreWidth = preWidth,
                                graphPostWidth = postWidth,
                                graphShowGrid = showGrid
                            )

                            onAddToReport(newElement)
                        } else {
                            onStatusChange("Enable Grid Mode & Select a Slot First!")
                        }
                    }
                },
                    // Disable button if no slot is selected
                    enabled = selectedPartitionSlot != null,
                    modifier = Modifier.height(30.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                    Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if(selectedPartitionSlot != null) "Add to Slot >>" else "Select Slot First", fontSize = 11.sp)
                }
            }
            Divider()

            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color.White).padding(16.dp)) {
                if (activeGraphId == -100.0) {
                    Text("No Data Selected", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
                } else {
                    val data = if (activeGraphId == -1.0) getCurrentViewData(riverData, "L-Section", 0.0, startCh, endCh) else getCurrentViewData(riverData, "X-Section", activeGraphId, 0.0, 0.0)
                    val hScale = if (selectedGraphType == "L-Section") lHScale else xHScale
                    val vScale = if (selectedGraphType == "L-Section") lVScale else xVScale

                    GraphPageCanvas(
                        modifier = Modifier.fillMaxSize(), data = data, type = selectedGraphType,
                        paperSize = PaperSize.A4, isLandscape = true, hScale = hScale, vScale = vScale, config = rawConfig,
                        showPre = showPre, showPost = showPost, preColor = preColor, postColor = postColor,
                        preDotted = preDotted, postDotted = postDotted, preWidth = preWidth, postWidth = postWidth,
                        preShowPoints = preShowPoints, postShowPoints = postShowPoints, showGrid = showGrid, isRawView = true
                    )
                }
            }
        }
    }
}