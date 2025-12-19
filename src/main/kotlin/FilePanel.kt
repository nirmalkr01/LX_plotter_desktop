import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// --- CONSTANTS ---
const val BASE_SCALE_DP_PER_MM = 1.5f

@Composable
fun FilePanel(
    reportItems: MutableList<ReportPageItem>,
    lHScale: Double, lVScale: Double,
    xHScale: Double, xVScale: Double,
    showPre: Boolean, showPost: Boolean,
    preColor: java.awt.Color, postColor: java.awt.Color,
    preDotted: Boolean, postDotted: Boolean,
    preWidth: Float, postWidth: Float,
    preShowPoints: Boolean, postShowPoints: Boolean,
    showGrid: Boolean,
    onStatusChange: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // --- STATE MANAGEMENT ---
    var selectedPaperSize by remember { mutableStateOf(PaperSize.A4) }
    // 1) Always Landscape
    val isLandscape = true
    var zoomPercent by remember { mutableStateOf(100f) }

    val pageConfigs = remember { mutableStateMapOf<String, ReportConfig>() }
    val pageTextData = remember { mutableStateMapOf<String, MutableList<TextAnnotation>>() }

    // Clipboard for Copy/Paste
    var clipboardAnnotation by remember { mutableStateOf<TextAnnotation?>(null) }

    // Navigation State
    var activePageIndex by remember { mutableStateOf(0) }

    // --- TOOL STATE ---
    var activeTab by remember { mutableStateOf("Page Layout") }

    // Text Styling Globals (These sync with selected box)
    var globalTextColor by remember { mutableStateOf(Color.Black) }
    var globalTextSize by remember { mutableStateOf(12f) }
    var globalIsBold by remember { mutableStateOf(false) }
    var globalIsItalic by remember { mutableStateOf(false) }
    var globalIsUnderline by remember { mutableStateOf(false) }
    var globalTextAlign by remember { mutableStateOf(TextAlign.Left) }

    // Interaction Mode
    var isTextToolActive by remember { mutableStateOf(false) }
    var selectedAnnotationId by remember { mutableStateOf<String?>(null) }

    // --- SYNC RIBBON WITH SELECTION ---
    LaunchedEffect(selectedAnnotationId, activePageIndex) {
        if (selectedAnnotationId != null) {
            val activeId = reportItems.getOrNull(activePageIndex)?.id
            if (activeId != null) {
                val txt = pageTextData[activeId]?.find { it.id == selectedAnnotationId }
                if (txt != null) {
                    globalTextColor = txt.color
                    globalTextSize = txt.fontSize
                    globalIsBold = txt.isBold
                    globalIsItalic = txt.isItalic
                    globalIsUnderline = txt.isUnderline
                    globalTextAlign = txt.textAlign
                }
            }
        }
    }

    // --- HELPER: UPDATE SELECTED TEXT ---
    fun updateTextStyle(
        color: Color? = null,
        size: Float? = null,
        bold: Boolean? = null,
        italic: Boolean? = null,
        underline: Boolean? = null,
        align: TextAlign? = null
    ) {
        // Update Globals
        if (color != null) globalTextColor = color
        if (size != null) globalTextSize = size
        if (bold != null) globalIsBold = bold
        if (italic != null) globalIsItalic = italic
        if (underline != null) globalIsUnderline = underline
        if (align != null) globalTextAlign = align

        // Update Active Selection
        val activeId = reportItems.getOrNull(activePageIndex)?.id ?: return
        val currentList = pageTextData[activeId] ?: return
        val selId = selectedAnnotationId ?: return

        val index = currentList.indexOfFirst { it.id == selId }
        if (index != -1) {
            val old = currentList[index]
            currentList[index] = old.copy(
                color = color ?: old.color,
                fontSize = size ?: old.fontSize,
                isBold = bold ?: old.isBold,
                isItalic = italic ?: old.isItalic,
                isUnderline = underline ?: old.isUnderline,
                textAlign = align ?: old.textAlign
            )
        }
    }

    // --- INITIALIZATION ---
    LaunchedEffect(reportItems.size) {
        if (reportItems.isEmpty()) {
            val newItem = ReportPageItem(graphId = -200.0, type = "Blank", data = emptyList())
            reportItems.add(newItem)
            pageConfigs[newItem.id] = ReportConfig()
            pageTextData[newItem.id] = mutableStateListOf()
            activePageIndex = 0
        } else {
            reportItems.forEach { item ->
                if (!pageConfigs.containsKey(item.id)) pageConfigs[item.id] = ReportConfig()
                if (!pageTextData.containsKey(item.id)) pageTextData[item.id] = mutableStateListOf()
            }
        }
    }

    // Auto-detect active page from scroll (only if not manually interacting)
    LaunchedEffect(listState.firstVisibleItemIndex) {
        if (!listState.isScrollInProgress) {
            activePageIndex = listState.firstVisibleItemIndex.coerceIn(0, maxOf(0, reportItems.lastIndex))
        }
    }

    val activeItem = reportItems.getOrNull(activePageIndex)
    val activeConfig = if (activeItem != null) pageConfigs[activeItem.id] ?: ReportConfig() else ReportConfig()

    fun updateActiveConfig(newConfig: ReportConfig) {
        if (activeItem != null) pageConfigs[activeItem.id] = newConfig
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F2F1))
            .border(1.dp, Color(0xFFD1D1D1))
    ) {
        // ================= HEADER =================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2B579A))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EditNote, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Report Designer", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
            }

            Button(
                onClick = {
                    if(reportItems.isNotEmpty()) {
                        pickFolder()?.let { folder ->
                            scope.launch {
                                onStatusChange("Rendering...")
                                withContext(Dispatchers.IO) {
                                    reportItems.forEachIndexed { index, item ->
                                        val cfg = pageConfigs[item.id] ?: ReportConfig()
                                        val txts = pageTextData[item.id] ?: emptyList()
                                        val hScale = if (item.type == "L-Section") lHScale else xHScale
                                        val vScale = if (item.type == "L-Section") lVScale else xVScale

                                        saveSplitPage(
                                            item.data, File(folder, "Page_${index+1}.png"),
                                            item.xOffset.toInt(), item.yOffset.toInt(),
                                            item.type, selectedPaperSize, isLandscape, hScale, vScale, cfg,
                                            0.0, showPre, showPost, preColor, postColor, preDotted, postDotted, preWidth, postWidth, preShowPoints, postShowPoints, showGrid,
                                            textAnnotations = txts
                                        )
                                    }
                                }
                                onStatusChange("Export Complete!")
                            }
                        }
                    }
                },
                modifier = Modifier.height(28.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF2B579A))
            ) {
                Text("Export Images", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ================= RIBBON TABS =================
        Row(modifier = Modifier.fillMaxWidth().background(Color.White)) {
            RibbonTab("Page Layout", activeTab == "Page Layout") { activeTab = "Page Layout" }
            RibbonTab("Borders & Margins", activeTab == "Borders") { activeTab = "Borders" }
            RibbonTab("Insert & Text", activeTab == "Annotation") { activeTab = "Annotation" }
        }
        Divider(color = Color(0xFFE0E0E0))

        // ================= RIBBON TOOLS =================
        Surface(
            modifier = Modifier.fillMaxWidth().height(100.dp),
            color = Color(0xFFF8F9FA),
            shadowElevation = 2.dp
        ) {
            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {

                when (activeTab) {
                    "Page Layout" -> {
                        RibbonGroup("Paper") {
                            RibbonDropdown("Size: ${selectedPaperSize.name}", Icons.Default.Description, PaperSize.entries.map { it.name }) { selectedPaperSize = PaperSize.entries[it] }
                        }
                        // Pages group removed from here, moved to status bar area as requested
                    }

                    "Borders" -> {
                        RibbonGroup("Margins (mm)") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row {
                                    RibbonNumberInput("Top", activeConfig.marginTop) { updateActiveConfig(activeConfig.copy(marginTop = it)) }
                                    Spacer(Modifier.width(4.dp))
                                    RibbonNumberInput("Bot", activeConfig.marginBottom) { updateActiveConfig(activeConfig.copy(marginBottom = it)) }
                                }
                                Row {
                                    RibbonNumberInput("Left", activeConfig.marginLeft) { updateActiveConfig(activeConfig.copy(marginLeft = it)) }
                                    Spacer(Modifier.width(4.dp))
                                    RibbonNumberInput("Rgt", activeConfig.marginRight) { updateActiveConfig(activeConfig.copy(marginRight = it)) }
                                }
                            }
                        }
                        VerticalDivider(Modifier.padding(vertical = 8.dp, horizontal = 8.dp))

                        RibbonGroup("Outer Border") {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(activeConfig.showOuterBorder, { updateActiveConfig(activeConfig.copy(showOuterBorder = it)) }, Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Show", fontSize=11.sp)
                                }
                                Spacer(Modifier.height(4.dp))
                                RibbonNumberInput("Thick", activeConfig.outerThickness) { updateActiveConfig(activeConfig.copy(outerThickness = it)) }
                                Spacer(Modifier.height(4.dp))
                                ColorPickerButton(activeConfig.outerColor) { updateActiveConfig(activeConfig.copy(outerColor = it)) }
                            }
                        }

                        VerticalDivider(Modifier.padding(vertical = 8.dp, horizontal = 8.dp))

                        val innerEnabled = activeConfig.showOuterBorder
                        RibbonGroup("Inner Border") {
                            Column(modifier = Modifier.alpha(if(innerEnabled) 1f else 0.4f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = activeConfig.showInnerBorder,
                                        onCheckedChange = if(innerEnabled) { { updateActiveConfig(activeConfig.copy(showInnerBorder = it)) } } else null,
                                        enabled = innerEnabled,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Show", fontSize=11.sp)
                                }
                                Spacer(Modifier.height(4.dp))
                                Row {
                                    RibbonNumberInput("Gap", activeConfig.borderGap, innerEnabled) { updateActiveConfig(activeConfig.copy(borderGap = it)) }
                                    Spacer(Modifier.width(4.dp))
                                    RibbonNumberInput("Thick", activeConfig.innerThickness, innerEnabled) { updateActiveConfig(activeConfig.copy(innerThickness = it)) }
                                }
                                Spacer(Modifier.height(4.dp))
                                ColorPickerButton(activeConfig.innerColor) { if(innerEnabled) updateActiveConfig(activeConfig.copy(innerColor = it)) }
                            }
                        }
                    }

                    "Annotation" -> {
                        RibbonGroup("Insert") {
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if(isTextToolActive) Color(0xFFE1EDFD) else Color.Transparent)
                                    .border(1.dp, if(isTextToolActive) Color(0xFFA4C6F8) else Color.Transparent, RoundedCornerShape(4.dp))
                                    .clickable { isTextToolActive = !isTextToolActive }
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.TextFields, null, tint = Color(0xFF2B579A), modifier = Modifier.size(24.dp))
                                Spacer(Modifier.height(4.dp))
                                Text("Text Box", fontSize = 11.sp, color = Color(0xFF2B579A), fontWeight = if(isTextToolActive) FontWeight.Bold else FontWeight.Normal)
                            }
                        }

                        VerticalDivider(Modifier.padding(vertical = 8.dp, horizontal = 8.dp))

                        RibbonGroup("Font") {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val styleOptions = listOf("Arial", "Times New Roman", "Courier New", "Verdana", "Georgia", "Comic Sans MS", "Impact")
                                    RibbonDropdown("Arial", Icons.Default.FontDownload, styleOptions) { /* Placeholder for font family logic */ }
                                    Spacer(Modifier.width(8.dp))
                                    RibbonNumberInput("Size", globalTextSize) { updateTextStyle(size = it) }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // B, I, U Buttons
                                    RibbonTextButton("B", globalIsBold) { updateTextStyle(bold = !globalIsBold) }
                                    Spacer(Modifier.width(4.dp))
                                    RibbonTextButton("I", globalIsItalic) { updateTextStyle(italic = !globalIsItalic) }
                                    Spacer(Modifier.width(4.dp))
                                    RibbonTextButton("U", globalIsUnderline) { updateTextStyle(underline = !globalIsUnderline) }
                                    Spacer(Modifier.width(8.dp))
                                    ColorPickerButton(globalTextColor) { updateTextStyle(color = it) }
                                }
                            }
                        }

                        VerticalDivider(Modifier.padding(vertical = 8.dp, horizontal = 8.dp))

                        RibbonGroup("Paragraph") {
                            Row {
                                RibboniconButton(Icons.AutoMirrored.Filled.FormatAlignLeft, "", globalTextAlign == TextAlign.Left) { updateTextStyle(align = TextAlign.Left) }
                                RibboniconButton(Icons.Default.FormatAlignCenter, "", globalTextAlign == TextAlign.Center) { updateTextStyle(align = TextAlign.Center) }
                                RibboniconButton(Icons.AutoMirrored.Filled.FormatAlignRight, "", globalTextAlign == TextAlign.Right) { updateTextStyle(align = TextAlign.Right) }
                                RibboniconButton(Icons.Default.FormatAlignJustify, "", globalTextAlign == TextAlign.Justify) { updateTextStyle(align = TextAlign.Justify) }
                            }
                        }
                    }
                }
            }
        }

        // ================= WORKSPACE =================
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF505050))
                .padding(vertical = 20.dp, horizontal = 20.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                itemsIndexed(reportItems) { idx, item ->
                    val isActive = idx == activePageIndex

                    // 5) Remove mouse hover fade effects
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .shadow(8.dp, RoundedCornerShape(0.dp))
                                .border(
                                    if(isActive) 2.dp else 0.dp,
                                    if(isActive) Color(0xFF2B579A) else Color.Transparent
                                )
                        ) {
                            EditablePageContainer(
                                item = item,
                                pageNumber = idx + 1,
                                paperSize = selectedPaperSize,
                                isLandscape = isLandscape,
                                config = pageConfigs[item.id] ?: ReportConfig(),
                                zoomLevel = zoomPercent / 100f,
                                textAnnotations = pageTextData[item.id] ?: mutableStateListOf(),
                                // Graph Props
                                hScale = if (item.type == "L-Section") lHScale else xHScale,
                                vScale = if (item.type == "L-Section") lVScale else xVScale,
                                showPre = showPre, showPost = showPost, preColor = preColor, postColor = postColor,
                                preDotted = preDotted, postDotted = postDotted, preWidth = preWidth, postWidth = postWidth,
                                preShowPoints = preShowPoints, postShowPoints = postShowPoints, showGrid = showGrid,
                                // Tools State
                                currentTextColor = globalTextColor,
                                currentTextSize = globalTextSize,
                                isBold = globalIsBold,
                                isItalic = globalIsItalic,
                                isUnderline = globalIsUnderline,
                                currentTextAlign = globalTextAlign,
                                isTextToolActive = isTextToolActive && isActive,
                                selectedAnnotationId = if(isActive) selectedAnnotationId else null,
                                onAnnotationSelected = { id ->
                                    if (id == null) {
                                        selectedAnnotationId = null
                                    } else {
                                        selectedAnnotationId = id
                                    }
                                    activePageIndex = idx
                                },
                                onPageSelected = {
                                    activePageIndex = idx
                                    selectedAnnotationId = null
                                },
                                onToolUsed = { isTextToolActive = false },
                                onGraphPosChange = { x, y -> reportItems[idx] = item.copy(xOffset = x, yOffset = y) },
                                onDeleteAnnotation = { id ->
                                    pageTextData[item.id]?.removeAll { it.id == id }
                                    selectedAnnotationId = null
                                },
                                onCopyAnnotation = { txt ->
                                    clipboardAnnotation = txt
                                },
                                onPasteAnnotation = {
                                    clipboardAnnotation?.let { copied ->
                                        val newTxt = copied.copy(
                                            id = java.util.UUID.randomUUID().toString(),
                                            xPercent = 0.4f, // Center-ish paste
                                            yPercent = 0.4f
                                        )
                                        pageTextData[item.id]?.add(newTxt)
                                        selectedAnnotationId = newTxt.id
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // ================= STATUS BAR =================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2B579A))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Page ${activePageIndex + 1} of ${reportItems.size}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)

            Spacer(Modifier.width(16.dp))

            // New Page / Delete Page buttons moved here
            IconButton(onClick = {
                val newItem = ReportPageItem(graphId = -200.0, type = "Blank", data = emptyList())
                reportItems.add(newItem)
                pageConfigs[newItem.id] = ReportConfig()
                pageTextData[newItem.id] = mutableStateListOf()
                scope.launch {
                    listState.animateScrollToItem(reportItems.lastIndex)
                    activePageIndex = reportItems.lastIndex
                }
            }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.NoteAdd, "New Page", tint = Color.White, modifier = Modifier.size(16.dp))
            }

            IconButton(onClick = {
                if (reportItems.size > 0 && activePageIndex < reportItems.size) {
                    val id = reportItems[activePageIndex].id
                    reportItems.removeAt(activePageIndex)
                    pageConfigs.remove(id)
                    pageTextData.remove(id)
                    if (reportItems.isEmpty()) {
                        val newItem = ReportPageItem(graphId = -200.0, type = "Blank", data = emptyList())
                        reportItems.add(newItem)
                        pageConfigs[newItem.id] = ReportConfig()
                        pageTextData[newItem.id] = mutableStateListOf()
                    }
                    activePageIndex = activePageIndex.coerceAtMost(reportItems.lastIndex)
                }
            }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Delete, "Delete Page", tint = Color.Red, modifier = Modifier.size(16.dp))
            }

            Spacer(Modifier.weight(1f))
            Text("-", color = Color.White, modifier = Modifier.clickable { if(zoomPercent>30) zoomPercent-=5 }.padding(horizontal=4.dp))
            Slider(
                value = zoomPercent, onValueChange = { zoomPercent = it },
                valueRange = 30f..200f,
                modifier = Modifier.width(100.dp),
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
            )
            Text("+", color = Color.White, modifier = Modifier.clickable { if(zoomPercent<200) zoomPercent+=5 }.padding(horizontal=4.dp))
            Text("${zoomPercent.toInt()}%", color = Color.White, fontSize = 11.sp, modifier = Modifier.width(30.dp))
        }
    }
}