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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

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
    var selectedLayoutType by remember { mutableStateOf(PageLayoutType.BLANK) }
    val isLandscape = true
    var zoomPercent by remember { mutableStateOf(100f) }

    // Page Elements State
    var showPageNumber by remember { mutableStateOf(true) }

    // Store Annexure 'X' values per page ID
    val pageAnnexureValues = remember { mutableStateMapOf<String, String>() }
    // Store B1 values per page ID
    val pageB1Values = remember { mutableStateMapOf<String, String>() }

    val pageConfigs = remember { mutableStateMapOf<String, ReportConfig>() }
    val pageTextData = remember { mutableStateMapOf<String, MutableList<TextAnnotation>>() }
    // NEW: Store Shapes/Elements per page
    val pageElementData = remember { mutableStateMapOf<String, MutableList<ReportElement>>() }

    // --- GLOBAL DRAG STATE ---
    val pageBounds = remember { mutableStateMapOf<String, Rect>() }

    var parentOffset by remember { mutableStateOf(Offset.Zero) }

    // Dragging Text
    var draggingAnnotation by remember { mutableStateOf<TextAnnotation?>(null) }
    var draggingSourcePageId by remember { mutableStateOf<String?>(null) }
    var draggingCurrentOffset by remember { mutableStateOf(Offset.Zero) }

    var clipboardAnnotation by remember { mutableStateOf<TextAnnotation?>(null) }
    var activePageIndex by remember { mutableStateOf(0) }
    var activeTab by remember { mutableStateOf("Page Layout") }

    // Text Styling Globals
    var globalTextColor by remember { mutableStateOf(Color.Black) }
    var globalTextSize by remember { mutableStateOf(12f) }
    var globalIsBold by remember { mutableStateOf(false) }
    var globalIsItalic by remember { mutableStateOf(false) }
    var globalIsUnderline by remember { mutableStateOf(false) }
    var globalTextAlign by remember { mutableStateOf(TextAlign.Left) }
    var globalFontFamily by remember { mutableStateOf("Arial") }

    // Element Styling Globals
    var globalShapeStrokeColor by remember { mutableStateOf(Color.Black) }
    var globalShapeFillColor by remember { mutableStateOf(Color.Transparent) }
    var globalShapeStrokeWidth by remember { mutableStateOf(2f) }

    var isTextToolActive by remember { mutableStateOf(false) }
    var selectedAnnotationId by remember { mutableStateOf<String?>(null) }
    var selectedElementId by remember { mutableStateOf<String?>(null) }

    // --- SYNC RIBBON WITH SELECTION (TEXT) ---
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
                    globalFontFamily = txt.fontFamily
                    // Deselect element if text selected
                    selectedElementId = null
                }
            }
        }
    }

    // --- SYNC RIBBON WITH SELECTION (ELEMENT) ---
    LaunchedEffect(selectedElementId, activePageIndex) {
        if (selectedElementId != null) {
            val activeId = reportItems.getOrNull(activePageIndex)?.id
            if (activeId != null) {
                val el = pageElementData[activeId]?.find { it.id == selectedElementId }
                if (el != null) {
                    globalShapeStrokeColor = el.strokeColor
                    globalShapeFillColor = el.fillColor
                    globalShapeStrokeWidth = el.strokeWidth
                    // Deselect text if element selected
                    selectedAnnotationId = null
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
        align: TextAlign? = null,
        font: String? = null
    ) {
        if (color != null) globalTextColor = color
        if (size != null) globalTextSize = size
        if (bold != null) globalIsBold = bold
        if (italic != null) globalIsItalic = italic
        if (underline != null) globalIsUnderline = underline
        if (align != null) globalTextAlign = align
        if (font != null) globalFontFamily = font

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
                textAlign = align ?: old.textAlign,
                fontFamily = font ?: old.fontFamily
            )
        }
    }

    // --- HELPER: UPDATE SELECTED ELEMENT ---
    fun updateElementStyle(
        stroke: Color? = null,
        fill: Color? = null,
        width: Float? = null
    ) {
        if (stroke != null) globalShapeStrokeColor = stroke
        if (fill != null) globalShapeFillColor = fill
        if (width != null) globalShapeStrokeWidth = width

        val activeId = reportItems.getOrNull(activePageIndex)?.id ?: return
        val currentList = pageElementData[activeId] ?: return
        val selId = selectedElementId ?: return

        val index = currentList.indexOfFirst { it.id == selId }
        if (index != -1) {
            val old = currentList[index]
            currentList[index] = old.copy(
                strokeColor = stroke ?: old.strokeColor,
                fillColor = fill ?: old.fillColor,
                strokeWidth = width ?: old.strokeWidth
            )
        }
    }

    // --- HELPER: ADD NEW ELEMENT ---
    fun addElementToActivePage(type: ElementType) {
        val activeId = reportItems.getOrNull(activePageIndex)?.id ?: return
        val list = pageElementData[activeId] ?: return
        val newEl = ReportElement(
            type = type,
            strokeColor = globalShapeStrokeColor,
            fillColor = globalShapeFillColor,
            strokeWidth = globalShapeStrokeWidth
        )
        list.add(newEl)
        selectedElementId = newEl.id
        selectedAnnotationId = null
    }

    LaunchedEffect(reportItems.size) {
        if (reportItems.isEmpty()) {
            val newItem = ReportPageItem(graphId = -200.0, type = "Blank", data = emptyList())
            reportItems.add(newItem)
            pageConfigs[newItem.id] = ReportConfig()
            pageTextData[newItem.id] = mutableStateListOf()
            pageElementData[newItem.id] = mutableStateListOf()
            pageAnnexureValues[newItem.id] = "" // Default empty
            pageB1Values[newItem.id] = "" // Default B1 empty
            activePageIndex = 0
        } else {
            reportItems.forEach { item ->
                if (!pageConfigs.containsKey(item.id)) pageConfigs[item.id] = ReportConfig()
                if (!pageTextData.containsKey(item.id)) pageTextData[item.id] = mutableStateListOf()
                if (!pageElementData.containsKey(item.id)) pageElementData[item.id] = mutableStateListOf()
                if (!pageAnnexureValues.containsKey(item.id)) pageAnnexureValues[item.id] = ""
                if (!pageB1Values.containsKey(item.id)) pageB1Values[item.id] = ""
            }
        }
    }

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
                                        val hScale = if (cfg.legendType == "L-Section") lHScale else xHScale
                                        val vScale = if (cfg.legendType == "L-Section") lVScale else xVScale
                                        val annexVal = pageAnnexureValues[item.id] ?: ""

                                        saveSplitPage(
                                            item.data, File(folder, "Page_${index+1}.png"),
                                            item.xOffset.toInt(), item.yOffset.toInt(),
                                            item.type, selectedPaperSize, isLandscape, hScale, vScale, cfg,
                                            0.0, showPre, showPost, preColor, postColor, preDotted, postDotted, preWidth, postWidth, preShowPoints, postShowPoints, showGrid,
                                            textAnnotations = txts
                                            // Elements not exported yet as per Download.kt limitation
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
        HorizontalDivider(color = Color(0xFFE0E0E0))

        // ================= RIBBON TOOLS =================
        Surface(
            modifier = Modifier.fillMaxWidth().height(110.dp),
            color = Color(0xFFF8F9FA),
            shadowElevation = 2.dp
        ) {
            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {

                when (activeTab) {
                    "Page Layout" -> {
                        RibbonGroup("Paper") {
                            RibbonDropdown("Size: ${selectedPaperSize.name}", Icons.Default.Description, PaperSize.entries.map { it.name }) { selectedPaperSize = PaperSize.entries[it] }
                        }
                        VerticalDivider(Modifier.padding(vertical = 8.dp, horizontal = 8.dp))
                        RibbonGroup("Templates") {
                            RibbonDropdown("Layout: ${selectedLayoutType.displayName}", Icons.Default.ViewQuilt, PageLayoutType.entries.map { it.displayName }) { selectedLayoutType = PageLayoutType.entries[it] }
                        }
                        VerticalDivider(Modifier.padding(vertical = 8.dp, horizontal = 8.dp))
                        if (selectedLayoutType == PageLayoutType.ENGINEERING_STD) {
                            RibbonGroup("Header Info") {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Annexure (X)", fontSize = 11.sp, color = Color.DarkGray)
                                        Spacer(Modifier.height(4.dp))
                                        if (activeItem != null) {
                                            val currentVal = pageAnnexureValues[activeItem.id] ?: ""
                                            BasicTextField(
                                                value = currentVal,
                                                onValueChange = { pageAnnexureValues[activeItem.id] = it },
                                                textStyle = TextStyle(fontSize = 12.sp, textAlign = TextAlign.Center),
                                                modifier = Modifier.width(60.dp).height(24.dp).background(Color.White, RoundedCornerShape(4.dp)).border(1.dp, Color.Gray, RoundedCornerShape(4.dp)).wrapContentHeight(Alignment.CenterVertically)
                                            )
                                        } else Text("-", fontSize = 12.sp)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("B1 Text", fontSize = 11.sp, color = Color.DarkGray)
                                        Spacer(Modifier.height(4.dp))
                                        if (activeItem != null) {
                                            val currentB1 = pageB1Values[activeItem.id] ?: ""
                                            BasicTextField(
                                                value = currentB1,
                                                onValueChange = { pageB1Values[activeItem.id] = it },
                                                textStyle = TextStyle(fontSize = 12.sp, textAlign = TextAlign.Center),
                                                modifier = Modifier.width(80.dp).height(24.dp).background(Color.White, RoundedCornerShape(4.dp)).border(1.dp, Color.Gray, RoundedCornerShape(4.dp)).wrapContentHeight(Alignment.CenterVertically)
                                            )
                                        } else Text("-", fontSize = 12.sp)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Legend For:", fontSize = 11.sp, color = Color.DarkGray)
                                        Spacer(Modifier.height(4.dp))
                                        val options = listOf("X-Section", "L-Section")
                                        RibbonDropdown(activeConfig.legendType, Icons.Default.LegendToggle, options) { idx ->
                                            updateActiveConfig(activeConfig.copy(legendType = options[idx]))
                                        }
                                    }
                                }
                            }
                        }
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
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RibbonNumberInput("Thick", activeConfig.outerThickness) { updateActiveConfig(activeConfig.copy(outerThickness = it)) }
                                    Spacer(Modifier.width(8.dp))
                                    ColorPickerButton(activeConfig.outerColor) { updateActiveConfig(activeConfig.copy(outerColor = it)) }
                                }
                            }
                        }
                        VerticalDivider(Modifier.padding(vertical = 8.dp, horizontal = 8.dp))
                        val innerEnabled = activeConfig.showOuterBorder
                        RibbonGroup("Inner Border") {
                            Column(modifier = Modifier.alpha(if(innerEnabled) 1f else 0.4f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = activeConfig.showInnerBorder, onCheckedChange = if(innerEnabled) { { updateActiveConfig(activeConfig.copy(showInnerBorder = it)) } } else null, enabled = innerEnabled, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Show", fontSize=11.sp)
                                    Spacer(Modifier.width(8.dp))
                                    RibbonNumberInput("Gap", activeConfig.borderGap, innerEnabled) { updateActiveConfig(activeConfig.copy(borderGap = it)) }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RibbonNumberInput("Thick", activeConfig.innerThickness, innerEnabled) { updateActiveConfig(activeConfig.copy(innerThickness = it)) }
                                    Spacer(Modifier.width(8.dp))
                                    ColorPickerButton(activeConfig.innerColor) { if(innerEnabled) updateActiveConfig(activeConfig.copy(innerColor = it)) }
                                }
                            }
                        }
                    }

                    "Annotation" -> {
                        RibbonGroup("Insert") {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                // Text Box
                                Column(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(if(isTextToolActive) Color(0xFFE1EDFD) else Color.Transparent).clickable { isTextToolActive = !isTextToolActive; selectedElementId = null }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.TextFields, null, tint = Color(0xFF2B579A), modifier = Modifier.size(20.dp))
                                    Text("Text", fontSize = 10.sp, color = Color(0xFF2B579A))
                                }
                            }
                        }

                        VerticalDivider(Modifier.padding(vertical = 8.dp, horizontal = 8.dp))

                        // NEW: SHAPES GROUP
                        RibbonGroup("Shapes") {
                            Column(verticalArrangement = Arrangement.Center) {
                                val shapes = ElementType.entries
                                var expandedShape by remember { mutableStateOf(false) }
                                Box {
                                    Button(onClick = { expandedShape = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), modifier = Modifier.height(28.dp)) {
                                        Text("Shapes", fontSize = 11.sp)
                                        Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(14.dp))
                                    }
                                    DropdownMenu(expanded = expandedShape, onDismissRequest = { expandedShape = false }) {
                                        shapes.forEach { type ->
                                            DropdownMenuItem(
                                                text = { Text(type.name.replace("_", " "), fontSize = 12.sp) },
                                                onClick = {
                                                    addElementToActivePage(type)
                                                    expandedShape = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // NEW: SHAPE STYLING (Visible if element selected)
                        if(selectedElementId != null) {
                            VerticalDivider(Modifier.padding(vertical = 8.dp, horizontal = 8.dp))
                            RibbonGroup("Shape Style") {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Stroke:", fontSize=10.sp)
                                        Spacer(Modifier.width(4.dp))
                                        ColorPickerButton(globalShapeStrokeColor) { updateElementStyle(stroke = it) }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Fill:", fontSize=10.sp)
                                        Spacer(Modifier.width(4.dp))
                                        ColorPickerButton(globalShapeFillColor) { updateElementStyle(fill = it) }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Width:", fontSize=10.sp)
                                        Spacer(Modifier.width(4.dp))
                                        RibbonNumberInput("", globalShapeStrokeWidth) { updateElementStyle(width = it) }
                                    }
                                }
                            }
                        }

                        VerticalDivider(Modifier.padding(vertical = 8.dp, horizontal = 8.dp))

                        // Text Styling (Existing)
                        if(selectedElementId == null) {
                            RibbonGroup("Font") {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val styleOptions = listOf("Arial", "Times New Roman", "Courier New", "Verdana", "Georgia", "Comic Sans MS", "Impact")
                                        RibbonDropdown(globalFontFamily, Icons.Default.FontDownload, styleOptions) { index -> updateTextStyle(font = styleOptions[index]) }
                                        Spacer(Modifier.width(8.dp))
                                        RibbonNumberInput("Size", globalTextSize) { updateTextStyle(size = it) }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
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

                        VerticalDivider(Modifier.padding(vertical = 8.dp, horizontal = 8.dp))

                        RibbonGroup("Page Elements") {
                            val isBlank = selectedLayoutType == PageLayoutType.BLANK
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.alpha(if(isBlank) 1f else 0.4f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = showPageNumber, onCheckedChange = if(isBlank) { { showPageNumber = it } } else null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Page No.", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ================= WORKSPACE WITH GLOBAL DRAG OVERLAY =================
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF505050))
                .padding(vertical = 20.dp, horizontal = 20.dp)
                // --- CAPTURE PARENT POSITION ---
                .onGloballyPositioned { layoutCoordinates ->
                    parentOffset = layoutCoordinates.positionInWindow()
                }
        ) {
            // 1. The Scrollable List of Pages
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                itemsIndexed(reportItems) { idx, item ->
                    val isActive = idx == activePageIndex

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(bottom = 4.dp)
                            .onGloballyPositioned { layoutCoordinates ->
                                val pos = layoutCoordinates.positionInWindow()
                                val size = layoutCoordinates.size
                                pageBounds[item.id] = Rect(pos, Size(size.width.toFloat(), size.height.toFloat()))
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .shadow(8.dp, RoundedCornerShape(0.dp))
                                .border(
                                    if(isActive) 2.dp else 0.dp,
                                    if(isActive) Color(0xFF2B579A) else Color.Transparent
                                )
                        ) {
                            val currentAnnotations = pageTextData[item.id] ?: mutableStateListOf()
                            val currentElements = pageElementData[item.id] ?: mutableStateListOf()
                            val currentAnnexure = pageAnnexureValues[item.id] ?: ""
                            val currentB1 = pageB1Values[item.id] ?: ""

                            EditablePageContainer(
                                item = item,
                                pageNumber = idx + 1,
                                totalPageCount = reportItems.size,
                                annexureValue = currentAnnexure,
                                b1Text = currentB1,
                                paperSize = selectedPaperSize,
                                layoutType = selectedLayoutType,
                                isLandscape = isLandscape,
                                config = pageConfigs[item.id] ?: ReportConfig(),
                                zoomLevel = zoomPercent / 100f,
                                textAnnotations = currentAnnotations,
                                elements = currentElements, // NEW: Pass Elements
                                hScale = if (pageConfigs[item.id]?.legendType == "L-Section") lHScale else xHScale,
                                vScale = if (pageConfigs[item.id]?.legendType == "L-Section") lVScale else xVScale,
                                showPre = showPre, showPost = showPost, preColor = preColor, postColor = postColor,
                                preDotted = preDotted, postDotted = postDotted, preWidth = preWidth, postWidth = postWidth,
                                preShowPoints = preShowPoints, postShowPoints = postShowPoints, showGrid = showGrid,
                                showPageNumber = showPageNumber,
                                currentTextColor = globalTextColor,
                                currentTextSize = globalTextSize,
                                isBold = globalIsBold,
                                isItalic = globalIsItalic,
                                isUnderline = globalIsUnderline,
                                currentTextAlign = globalTextAlign,
                                currentFontFamily = globalFontFamily,
                                isTextToolActive = isTextToolActive && isActive,
                                selectedAnnotationId = if(isActive) selectedAnnotationId else null,
                                selectedElementId = if(isActive) selectedElementId else null, // NEW: Pass Selected Element
                                canPaste = clipboardAnnotation != null,
                                onAnnotationSelected = { id ->
                                    selectedAnnotationId = id
                                    selectedElementId = null // Exclusive Selection
                                    if(id != null) activePageIndex = idx
                                },
                                onElementSelected = { id ->
                                    selectedElementId = id
                                    selectedAnnotationId = null // Exclusive Selection
                                    if(id != null) activePageIndex = idx
                                },
                                onPageSelected = {
                                    activePageIndex = idx
                                    selectedAnnotationId = null
                                    selectedElementId = null
                                },
                                onToolUsed = { isTextToolActive = false },
                                onGraphPosChange = { x, y -> reportItems[idx] = item.copy(xOffset = x, yOffset = y) },
                                onDeleteAnnotation = { id -> pageTextData[item.id]?.removeAll { it.id == id }; selectedAnnotationId = null },
                                onDeleteElement = { id -> pageElementData[item.id]?.removeAll { it.id == id }; selectedElementId = null },
                                onCopyAnnotation = { txt -> clipboardAnnotation = txt },
                                onPasteAnnotation = {
                                    clipboardAnnotation?.let { copied ->
                                        val newTxt = copied.copy(id = java.util.UUID.randomUUID().toString(), xPercent = 0.4f, yPercent = 0.4f)
                                        pageTextData[item.id]?.add(newTxt)
                                        selectedAnnotationId = newTxt.id
                                    }
                                },
                                hiddenAnnotationId = if(draggingAnnotation != null && draggingSourcePageId == item.id) draggingAnnotation!!.id else null,
                                onGlobalDragStart = { annotation, absoluteStartPos ->
                                    draggingAnnotation = annotation
                                    draggingSourcePageId = item.id
                                    draggingCurrentOffset = absoluteStartPos
                                    selectedAnnotationId = annotation.id
                                },
                                onGlobalDrag = { dragDelta -> draggingCurrentOffset += dragDelta },
                                onGlobalDragEnd = {
                                    try {
                                        var droppedPageId: String? = null
                                        for ((pId, rect) in pageBounds) { if (rect.contains(draggingCurrentOffset)) { droppedPageId = pId; break } }
                                        if (droppedPageId != null && draggingAnnotation != null) {
                                            val targetRect = pageBounds[droppedPageId]!!
                                            val relX = (draggingCurrentOffset.x - targetRect.left) / targetRect.width
                                            val relY = (draggingCurrentOffset.y - targetRect.top) / targetRect.height
                                            val movedAnnotation = draggingAnnotation!!.copy(xPercent = relX.toFloat(), yPercent = relY.toFloat())
                                            pageTextData[draggingSourcePageId!!]?.removeAll { it.id == draggingAnnotation!!.id }
                                            pageTextData[droppedPageId]?.add(movedAnnotation)
                                            val newIdx = reportItems.indexOfFirst { it.id == droppedPageId }
                                            if(newIdx != -1) activePageIndex = newIdx
                                        }
                                    } finally { draggingAnnotation = null; draggingSourcePageId = null }
                                }
                            )
                        }
                    }
                }
            }

            // 2. The Ghost Annotation (Overlay)
            if (draggingAnnotation != null) {
                val ghost = draggingAnnotation!!
                Box(modifier = Modifier.offset { IntOffset((draggingCurrentOffset.x - parentOffset.x).roundToInt(), (draggingCurrentOffset.y - parentOffset.y).roundToInt()) }.background(Color.White.copy(alpha=0.8f)).border(1.dp, Color.Gray, RoundedCornerShape(4.dp)).padding(4.dp)) {
                    Text(text = ghost.text, color = ghost.color, fontSize = (ghost.fontSize * (zoomPercent/100f)).sp, fontWeight = if(ghost.isBold) FontWeight.Bold else FontWeight.Normal, fontStyle = if(ghost.isItalic) FontStyle.Italic else FontStyle.Normal, textDecoration = if(ghost.isUnderline) TextDecoration.Underline else TextDecoration.None, textAlign = ghost.textAlign, fontFamily = getFontFamily(ghost.fontFamily))
                }
            }
        }

        // ================= STATUS BAR =================
        Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF2B579A)).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Page ${activePageIndex + 1} of ${reportItems.size}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(16.dp))
            IconButton(onClick = {
                val newItem = ReportPageItem(graphId = -200.0, type = "Blank", data = emptyList())
                reportItems.add(newItem)
                pageConfigs[newItem.id] = ReportConfig()
                pageTextData[newItem.id] = mutableStateListOf()
                pageElementData[newItem.id] = mutableStateListOf()
                pageAnnexureValues[newItem.id] = ""
                pageB1Values[newItem.id] = ""
                scope.launch { listState.animateScrollToItem(reportItems.lastIndex); activePageIndex = reportItems.lastIndex }
            }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.NoteAdd, "New Page", tint = Color.White, modifier = Modifier.size(16.dp)) }
            IconButton(onClick = {
                if (reportItems.size > 0 && activePageIndex < reportItems.size) {
                    val id = reportItems[activePageIndex].id
                    reportItems.removeAt(activePageIndex)
                    pageConfigs.remove(id); pageTextData.remove(id); pageElementData.remove(id); pageAnnexureValues.remove(id); pageB1Values.remove(id)
                    if (reportItems.isEmpty()) {
                        val newItem = ReportPageItem(graphId = -200.0, type = "Blank", data = emptyList())
                        reportItems.add(newItem)
                        pageConfigs[newItem.id] = ReportConfig(); pageTextData[newItem.id] = mutableStateListOf(); pageElementData[newItem.id] = mutableStateListOf(); pageAnnexureValues[newItem.id] = ""; pageB1Values[newItem.id] = ""
                    }
                    activePageIndex = activePageIndex.coerceAtMost(reportItems.lastIndex)
                }
            }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Delete, "Delete Page", tint = Color.Red, modifier = Modifier.size(16.dp)) }
            Spacer(Modifier.weight(1f))
            Text("-", color = Color.White, modifier = Modifier.clickable { if(zoomPercent>30) zoomPercent-=5 }.padding(horizontal=4.dp))
            Slider(value = zoomPercent, onValueChange = { zoomPercent = it }, valueRange = 30f..200f, modifier = Modifier.width(100.dp), colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))
            Text("+", color = Color.White, modifier = Modifier.clickable { if(zoomPercent<200) zoomPercent+=5 }.padding(horizontal=4.dp))
            Text("${zoomPercent.toInt()}%", color = Color.White, fontSize = 11.sp, modifier = Modifier.width(30.dp))
        }
    }
}

// --- HELPER COMPONENTS ---

@Composable
fun RibbonTab(text: String, isActive: Boolean, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, fontSize = 13.sp, fontWeight = if(isActive) FontWeight.Bold else FontWeight.Normal, color = if(isActive) Color(0xFF2B579A) else Color.Black)
        if (isActive) Box(Modifier.height(3.dp).width(40.dp).background(Color(0xFF2B579A), RoundedCornerShape(topStart=2.dp, topEnd=2.dp)))
    }
}

@Composable
fun RibbonGroup(label: String, content: @Composable RowScope.() -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxHeight().padding(horizontal = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, content = content)
        Text(label, fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 2.dp))
    }
}

@Composable
fun RibbonLargeButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color = Color(0xFF2B579A), onClick: () -> Unit) {
    Column(modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = color, modifier = Modifier.size(24.dp)); Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 11.sp, color = color)
    }
}

@Composable
fun RibboniconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, isActive: Boolean, onClick: () -> Unit) {
    Row(modifier = Modifier.background(if(isActive) Color(0xFFE1EDFD) else Color.Transparent, RoundedCornerShape(4.dp)).border(1.dp, if(isActive) Color(0xFFA4C6F8) else Color.Transparent, RoundedCornerShape(4.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = if(isActive) Color(0xFF2B579A) else Color.Black)
        if(label.isNotEmpty()) { Spacer(Modifier.width(4.dp)); Text(label, fontSize = 11.sp, color = if(isActive) Color(0xFF2B579A) else Color.Black) }
    }
}

@Composable
fun RibbonTextButton(text: String, isActive: Boolean, onClick: () -> Unit) {
    Box(modifier = Modifier.background(if(isActive) Color(0xFFE1EDFD) else Color.Transparent, RoundedCornerShape(4.dp)).border(1.dp, if(isActive) Color(0xFFA4C6F8) else Color.Transparent, RoundedCornerShape(4.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if(isActive) Color(0xFF2B579A) else Color.Black)
    }
}

@Composable
fun RibbonDropdown(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, options: List<String>, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.border(1.dp, Color.LightGray, RoundedCornerShape(4.dp)).clip(RoundedCornerShape(4.dp)).clickable { expanded = true }.padding(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = Color(0xFF2B579A)); Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 11.sp); Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(14.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { idx, opt -> DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(idx); expanded = false }) }
        }
    }
}

@Composable
fun RibbonNumberInput(label: String, value: Float, enabled: Boolean = true, onChange: (Float) -> Unit) {
    var textValue by remember(value) { mutableStateOf(if(value == 0f) "" else value.toString()) }
    LaunchedEffect(value) { if(textValue.toFloatOrNull() != value) { textValue = if(value == 0f) "" else value.toString() } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if(label.isNotEmpty()) Text(label, fontSize = 11.sp, color = if(enabled) Color.DarkGray else Color.LightGray, modifier = Modifier.width(30.dp))
        BasicTextField(value = textValue, enabled = enabled, onValueChange = { str -> textValue = str; val num = str.toFloatOrNull(); if (num != null) onChange(num) }, textStyle = TextStyle(fontSize = 11.sp, textAlign = TextAlign.Center), modifier = Modifier.width(40.dp).background(if(enabled) Color.White else Color(0xFFEEEEEE), RoundedCornerShape(2.dp)).border(1.dp, Color.LightGray, RoundedCornerShape(2.dp)).padding(3.dp))
    }
}