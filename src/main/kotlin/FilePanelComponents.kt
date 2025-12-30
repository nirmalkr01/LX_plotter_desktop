import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

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
    val density = LocalDensity.current

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
    // Store Custom Page Number Overrides
    val pageNumberOverrides = remember { mutableStateMapOf<String, String>() }

    val pageConfigs = remember { mutableStateMapOf<String, ReportConfig>() }
    val pageTextData = remember { mutableStateMapOf<String, MutableList<TextAnnotation>>() }
    // Store Shapes/Elements per page
    val pageElementData = remember { mutableStateMapOf<String, MutableList<ReportElement>>() }

    // --- GLOBAL DRAG STATE ---
    // Stores the Absolute Screen Bounds of each page to detect drops
    val pageBounds = remember { mutableStateMapOf<String, Rect>() }

    var parentOffset by remember { mutableStateOf(Offset.Zero) }

    // Dragging Logic
    var draggingSourcePageId by remember { mutableStateOf<String?>(null) }
    var draggingCurrentOffset by remember { mutableStateOf(Offset.Zero) }
    var isDraggingGroup by remember { mutableStateOf(false) }

    // Group Drag Data Snapshots (For Ghost Rendering)
    var groupDragStartBoxRelative by remember { mutableStateOf(Offset.Zero) } // Offset of mouse relative to box top-left
    var groupGhostTexts by remember { mutableStateOf<List<TextAnnotation>>(emptyList()) }
    var groupGhostElements by remember { mutableStateOf<List<ReportElement>>(emptyList()) }
    var groupGhostBounds by remember { mutableStateOf(Rect.Zero) }

    // Single item placeholders for ghost rendering (Legacy single drag)
    var draggingAnnotation by remember { mutableStateOf<TextAnnotation?>(null) }
    var draggingElement by remember { mutableStateOf<ReportElement?>(null) }

    // Clipboard Lists
    var clipboardTextList by remember { mutableStateOf<List<TextAnnotation>>(emptyList()) }
    var clipboardElementList by remember { mutableStateOf<List<ReportElement>>(emptyList()) }

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
    var globalShapeRotation by remember { mutableStateOf(0f) }

    // TOOLS
    var isTextToolActive by remember { mutableStateOf(false) }
    var isSelectToolActive by remember { mutableStateOf(false) }

    // SELECTION
    var selectedAnnotationId by remember { mutableStateOf<String?>(null) }
    var selectedElementId by remember { mutableStateOf<String?>(null) }

    // Multi-Selection Sets
    val multiSelectedAnnotationIds = remember { mutableStateListOf<String>() }
    val multiSelectedElementIds = remember { mutableStateListOf<String>() }

    // --- APPLY ALL STATES ---
    var applyAnnexureToAll by remember { mutableStateOf(true) }
    var applyB1ToAll by remember { mutableStateOf(true) }
    var applyMarginsToAll by remember { mutableStateOf(true) }
    var applyBordersToAll by remember { mutableStateOf(true) }
    var renumberStartFrom by remember { mutableStateOf(1f) }

    // COPY FUNCTION
    fun performCopy() {
        val activeId = reportItems.getOrNull(activePageIndex)?.id ?: return
        val texts = pageTextData[activeId] ?: emptyList()
        val elems = pageElementData[activeId] ?: emptyList()

        if (isSelectToolActive) {
            clipboardTextList = texts.filter { multiSelectedAnnotationIds.contains(it.id) }
            clipboardElementList = elems.filter { multiSelectedElementIds.contains(it.id) }
        } else {
            if (selectedAnnotationId != null) clipboardTextList = texts.filter { it.id == selectedAnnotationId }
            else clipboardTextList = emptyList()

            if (selectedElementId != null) clipboardElementList = elems.filter { it.id == selectedElementId }
            else clipboardElementList = emptyList()
        }

        if (clipboardTextList.isNotEmpty() || clipboardElementList.isNotEmpty()) {
            onStatusChange("Copied to clipboard")
        }
    }

    // PASTE FUNCTION
    fun performPaste() {
        val activeId = reportItems.getOrNull(activePageIndex)?.id ?: return

        if (clipboardTextList.isNotEmpty() || clipboardElementList.isNotEmpty()) {
            clipboardTextList.forEach { item ->
                val newId = UUID.randomUUID().toString()
                pageTextData[activeId]?.add(item.copy(id = newId)) // Paste at same pos
                if(isSelectToolActive) multiSelectedAnnotationIds.add(newId)
            }
            clipboardElementList.forEach { item ->
                val newId = UUID.randomUUID().toString()
                pageElementData[activeId]?.add(item.copy(id = newId)) // Paste at same pos
                if(isSelectToolActive) multiSelectedElementIds.add(newId)
            }
            onStatusChange("Pasted items")
        }
    }

    // DELETE FUNCTION
    fun performDelete() {
        val activeId = reportItems.getOrNull(activePageIndex)?.id ?: return

        if (isSelectToolActive) {
            if (multiSelectedAnnotationIds.isNotEmpty()) {
                pageTextData[activeId]?.removeAll { multiSelectedAnnotationIds.contains(it.id) }
                multiSelectedAnnotationIds.clear()
            }
            if (multiSelectedElementIds.isNotEmpty()) {
                pageElementData[activeId]?.removeAll { multiSelectedElementIds.contains(it.id) }
                multiSelectedElementIds.clear()
            }
        } else {
            selectedAnnotationId?.let { id -> pageTextData[activeId]?.removeAll { it.id == id }; selectedAnnotationId = null }
            selectedElementId?.let { id -> pageElementData[activeId]?.removeAll { it.id == id }; selectedElementId = null }
        }
    }

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
                    globalShapeRotation = el.rotation
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

        val targets = if(multiSelectedAnnotationIds.isNotEmpty()) multiSelectedAnnotationIds else listOfNotNull(selectedAnnotationId)

        targets.forEach { targetId ->
            val index = currentList.indexOfFirst { it.id == targetId }
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
    }

    // --- HELPER: UPDATE SELECTED ELEMENT ---
    fun updateElementStyle(
        stroke: Color? = null,
        fill: Color? = null,
        width: Float? = null,
        rotation: Float? = null
    ) {
        if (stroke != null) globalShapeStrokeColor = stroke
        if (fill != null) globalShapeFillColor = fill
        if (width != null) globalShapeStrokeWidth = width
        if (rotation != null) globalShapeRotation = rotation

        val activeId = reportItems.getOrNull(activePageIndex)?.id ?: return
        val currentList = pageElementData[activeId] ?: return

        val targets = if(multiSelectedElementIds.isNotEmpty()) multiSelectedElementIds else listOfNotNull(selectedElementId)

        targets.forEach { targetId ->
            val index = currentList.indexOfFirst { it.id == targetId }
            if (index != -1) {
                val old = currentList[index]
                currentList[index] = old.copy(
                    strokeColor = stroke ?: old.strokeColor,
                    fillColor = fill ?: old.fillColor,
                    strokeWidth = width ?: old.strokeWidth,
                    rotation = rotation ?: old.rotation
                )
            }
        }
    }

    // --- HELPER: ADD NEW ELEMENT ---
    fun addElementToActivePage(type: ElementType) {
        val activeId = reportItems.getOrNull(activePageIndex)?.id ?: return
        val list = pageElementData[activeId] ?: return
        val newEl = ReportElement(
            type = type,
            strokeColor = Color.Black,
            fillColor = Color.Transparent,
            strokeWidth = 2f,
            rotation = 0f
        )
        list.add(newEl)

        selectedElementId = newEl.id
        selectedAnnotationId = null
        if(isSelectToolActive) {
            multiSelectedAnnotationIds.clear()
            multiSelectedElementIds.clear()
            multiSelectedElementIds.add(newEl.id)
        }
    }

    LaunchedEffect(reportItems.size) {
        if (reportItems.isEmpty()) {
            val newItem = ReportPageItem(graphId = -200.0, type = "Blank", data = emptyList())
            reportItems.add(newItem)
            pageConfigs[newItem.id] = ReportConfig()
            pageTextData[newItem.id] = mutableStateListOf()
            pageElementData[newItem.id] = mutableStateListOf()
            pageAnnexureValues[newItem.id] = ""
            pageB1Values[newItem.id] = ""
            pageNumberOverrides[newItem.id] = "1"
            activePageIndex = 0
        } else {
            reportItems.forEachIndexed { idx, item ->
                if (!pageConfigs.containsKey(item.id)) pageConfigs[item.id] = ReportConfig()
                if (!pageTextData.containsKey(item.id)) pageTextData[item.id] = mutableStateListOf()
                if (!pageElementData.containsKey(item.id)) pageElementData[item.id] = mutableStateListOf()
                if (!pageAnnexureValues.containsKey(item.id)) pageAnnexureValues[item.id] = ""
                if (!pageB1Values.containsKey(item.id)) pageB1Values[item.id] = ""
                if (!pageNumberOverrides.containsKey(item.id)) pageNumberOverrides[item.id] = "${idx + 1}"
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
            // Keyboard Shortcuts Handler
            .onKeyEvent { event ->
                if (event.isCtrlPressed && event.key == Key.C && event.type == KeyEventType.KeyUp) {
                    performCopy()
                    true
                } else if (event.isCtrlPressed && event.key == Key.V && event.type == KeyEventType.KeyUp) {
                    performPaste()
                    true
                } else if (event.key == Key.Delete && event.type == KeyEventType.KeyUp) {
                    performDelete()
                    true
                } else {
                    false
                }
            }
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
                                        val pageNumStr = pageNumberOverrides[item.id] ?: "${index + 1}"

                                        saveSplitPage(
                                            item.data, File(folder, "Page_${pageNumStr}.png"),
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
        HorizontalDivider(color = Color(0xFFE0E0E0))

        // ================= RIBBON UI =================
        FilePanelRibbon(
            activeTab = activeTab,
            selectedPaperSize = selectedPaperSize,
            onPaperSizeChange = { selectedPaperSize = it },
            selectedLayoutType = selectedLayoutType,
            onLayoutTypeChange = { selectedLayoutType = it },
            activeConfig = activeConfig,
            onConfigChange = { updateActiveConfig(it) },
            activeItem = activeItem,
            pageAnnexureValues = pageAnnexureValues,
            pageB1Values = pageB1Values,
            pageNumberOverrides = pageNumberOverrides,
            activePageIndex = activePageIndex,
            applyAnnexureToAll = applyAnnexureToAll,
            onApplyAnnexureChange = { applyAnnexureToAll = it },
            applyB1ToAll = applyB1ToAll,
            onApplyB1Change = { applyB1ToAll = it },
            onApplyHeaderToAll = {
                if (activeItem != null) {
                    val srcAnnex = pageAnnexureValues[activeItem.id] ?: ""
                    val srcB1 = pageB1Values[activeItem.id] ?: ""
                    val srcLegend = pageConfigs[activeItem.id]?.legendType ?: "X-Section"

                    reportItems.forEach { item ->
                        if (applyAnnexureToAll) pageAnnexureValues[item.id] = srcAnnex
                        if (applyB1ToAll) pageB1Values[item.id] = srcB1
                        if (applyAnnexureToAll) {
                            val oldCfg = pageConfigs[item.id] ?: ReportConfig()
                            pageConfigs[item.id] = oldCfg.copy(legendType = srcLegend)
                        }
                    }
                    onStatusChange("Applied Header settings to ${reportItems.size} pages")
                }
            },
            renumberStartFrom = renumberStartFrom,
            onRenumberStartChange = { renumberStartFrom = it },
            onRenumberAll = {
                var counter = renumberStartFrom.toInt()
                reportItems.forEach { item ->
                    pageNumberOverrides[item.id] = counter.toString()
                    counter++
                }
                onStatusChange("Renumbered pages starting from ${renumberStartFrom.toInt()}")
            },
            showPageNumber = showPageNumber,
            onShowPageNumberChange = { showPageNumber = it },
            applyMarginsToAll = applyMarginsToAll,
            onApplyMarginsChange = { applyMarginsToAll = it },
            applyBordersToAll = applyBordersToAll,
            onApplyBordersChange = { applyBordersToAll = it },
            onApplyStylesToAll = {
                if (activeItem != null) {
                    val srcCfg = pageConfigs[activeItem.id] ?: ReportConfig()

                    reportItems.forEach { item ->
                        val current = pageConfigs[item.id] ?: ReportConfig()
                        var updated = current

                        if (applyMarginsToAll) {
                            updated = updated.copy(
                                marginTop = srcCfg.marginTop,
                                marginBottom = srcCfg.marginBottom,
                                marginLeft = srcCfg.marginLeft,
                                marginRight = srcCfg.marginRight
                            )
                        }
                        if (applyBordersToAll) {
                            updated = updated.copy(
                                showOuterBorder = srcCfg.showOuterBorder,
                                outerThickness = srcCfg.outerThickness,
                                outerColor = srcCfg.outerColor,
                                showInnerBorder = srcCfg.showInnerBorder,
                                innerThickness = srcCfg.innerThickness,
                                innerColor = srcCfg.innerColor,
                                borderGap = srcCfg.borderGap
                            )
                        }
                        pageConfigs[item.id] = updated
                    }
                    onStatusChange("Applied Styles to ${reportItems.size} pages")
                }
            },
            isTextToolActive = isTextToolActive,
            onTextToolToggle = {
                isTextToolActive = !isTextToolActive
                isSelectToolActive = false
                selectedElementId = null
            },
            isSelectToolActive = isSelectToolActive,
            onSelectToolToggle = {
                isSelectToolActive = !isSelectToolActive
                isTextToolActive = false
                if(!isSelectToolActive) {
                    multiSelectedAnnotationIds.clear()
                    multiSelectedElementIds.clear()
                    selectedAnnotationId = null
                    selectedElementId = null
                }
            },
            hasGroupSelection = false,
            onCopyGroup = { },
            canPasteGroup = false,
            onPasteGroup = { },
            selectedElementId = selectedElementId,
            globalShapeStrokeColor = globalShapeStrokeColor,
            onShapeStrokeColorChange = { updateElementStyle(stroke = it) },
            globalShapeFillColor = globalShapeFillColor,
            onShapeFillColorChange = { updateElementStyle(fill = it) },
            globalShapeStrokeWidth = globalShapeStrokeWidth,
            onShapeStrokeWidthChange = { updateElementStyle(width = it) },
            globalShapeRotation = globalShapeRotation,
            onShapeRotationChange = { updateElementStyle(rotation = it) },
            globalFontFamily = globalFontFamily,
            onFontFamilyChange = { updateTextStyle(font = it) },
            globalTextSize = globalTextSize,
            onTextSizeChange = { updateTextStyle(size = it) },
            globalIsBold = globalIsBold,
            onBoldToggle = { updateTextStyle(bold = !globalIsBold) },
            globalIsItalic = globalIsItalic,
            onItalicToggle = { updateTextStyle(italic = !globalIsItalic) },
            globalIsUnderline = globalIsUnderline,
            onUnderlineToggle = { updateTextStyle(underline = !globalIsUnderline) },
            globalTextColor = globalTextColor,
            onTextColorChange = { updateTextStyle(color = it) },
            globalTextAlign = globalTextAlign,
            onTextAlignChange = { updateTextStyle(align = it) },
            onAddElement = { addElementToActivePage(it) }
        )

        // ================= WORKSPACE =================
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF505050))
                .padding(vertical = 20.dp, horizontal = 20.dp)
                .onGloballyPositioned { layoutCoordinates ->
                    parentOffset = layoutCoordinates.positionInWindow()
                }
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 4.dp).onGloballyPositioned { pageBounds[item.id] = Rect(it.positionInWindow(), Size(it.size.width.toFloat(), it.size.height.toFloat())) }) {
                        Box(modifier = Modifier.shadow(8.dp).border(if(isActive) 2.dp else 0.dp, if(isActive) Color(0xFF2B579A) else Color.Transparent)) {
                            // DETERMINE PAGE NUMBER TO DISPLAY
                            val displayPageNumStr = pageNumberOverrides[item.id] ?: "${idx + 1}"
                            val displayPageNumInt = displayPageNumStr.toIntOrNull() ?: (idx + 1)

                            EditablePageContainer(
                                item = item, pageNumber = displayPageNumInt, totalPageCount = reportItems.size, annexureValue = pageAnnexureValues[item.id] ?: "", b1Text = pageB1Values[item.id] ?: "",
                                paperSize = selectedPaperSize, layoutType = selectedLayoutType, isLandscape = isLandscape, config = pageConfigs[item.id] ?: ReportConfig(), zoomLevel = zoomPercent / 100f,
                                textAnnotations = pageTextData[item.id] ?: mutableStateListOf(), elements = pageElementData[item.id] ?: mutableStateListOf(),
                                hScale = if (pageConfigs[item.id]?.legendType == "L-Section") lHScale else xHScale, vScale = if (pageConfigs[item.id]?.legendType == "L-Section") lVScale else xVScale,
                                showPre = showPre, showPost = showPost, preColor = preColor, postColor = postColor, preDotted = preDotted, postDotted = postDotted, preWidth = preWidth, postWidth = postWidth, preShowPoints = preShowPoints, postShowPoints = postShowPoints, showGrid = showGrid,
                                showPageNumber = showPageNumber, currentTextColor = globalTextColor, currentTextSize = globalTextSize, isBold = globalIsBold, isItalic = globalIsItalic, isUnderline = globalIsUnderline, currentTextAlign = globalTextAlign, currentFontFamily = globalFontFamily,
                                isTextToolActive = isTextToolActive && isActive, selectedAnnotationId = if(isActive) selectedAnnotationId else null, selectedElementId = if(isActive) selectedElementId else null,

                                // PASS MULTI-SELECT STATE TO CHILD
                                isSelectToolActive = isSelectToolActive && isActive,
                                multiSelectedAnnotationIds = if(isActive) multiSelectedAnnotationIds else mutableListOf(),
                                multiSelectedElementIds = if(isActive) multiSelectedElementIds else mutableListOf(),

                                canPaste = clipboardTextList.isNotEmpty() || clipboardElementList.isNotEmpty(),
                                onAnnotationSelected = { id ->
                                    selectedAnnotationId = id
                                    selectedElementId = null
                                    if(id != null) activePageIndex = idx
                                    if(!isSelectToolActive) {
                                        multiSelectedAnnotationIds.clear()
                                        multiSelectedElementIds.clear()
                                    }
                                },
                                onElementSelected = { id ->
                                    selectedElementId = id
                                    selectedAnnotationId = null
                                    if(id != null) activePageIndex = idx
                                    if(!isSelectToolActive) {
                                        multiSelectedAnnotationIds.clear()
                                        multiSelectedElementIds.clear()
                                    }
                                },
                                onPageSelected = { activePageIndex = idx; if(!isSelectToolActive) { selectedAnnotationId = null; selectedElementId = null } },
                                onToolUsed = { isTextToolActive = false }, onGraphPosChange = { x, y -> reportItems[idx] = item.copy(xOffset = x, yOffset = y) },
                                onDeleteAnnotation = { id -> pageTextData[item.id]?.removeAll { it.id == id }; selectedAnnotationId = null },
                                onDeleteElement = { id -> pageElementData[item.id]?.removeAll { it.id == id }; selectedElementId = null },
                                onCopyAnnotation = { clipboardTextList = listOf(it); clipboardElementList = emptyList() }, // Fallback for right-click copy
                                onPasteAnnotation = {
                                    performPaste()
                                },
                                // NEW: Context Menu Callbacks
                                onContextMenuCopy = { performCopy() },
                                onContextMenuPaste = { performPaste() },
                                onContextMenuDelete = { performDelete() },
                                hasClipboardContent = clipboardTextList.isNotEmpty() || clipboardElementList.isNotEmpty(),

                                hiddenAnnotationId = if(draggingAnnotation != null && draggingSourcePageId == item.id) draggingAnnotation!!.id else null,
                                hiddenElementId = if(draggingElement != null && draggingSourcePageId == item.id) draggingElement!!.id else null,

                                // GLOBAL DRAG HANDLERS
                                onGlobalDragStart = { annotation, pos ->
                                    draggingAnnotation = annotation
                                    draggingSourcePageId = item.id
                                    draggingCurrentOffset = pos
                                    isDraggingGroup = false
                                },
                                onGlobalElementDragStart = { element, pos ->
                                    draggingElement = element
                                    draggingSourcePageId = item.id
                                    draggingCurrentOffset = pos
                                    isDraggingGroup = false
                                },
                                onGroupDragStart = { pos, rect ->
                                    isDraggingGroup = true
                                    draggingSourcePageId = item.id
                                    draggingCurrentOffset = pos
                                    groupDragStartBoxRelative = pos - rect.topLeft
                                    groupGhostBounds = rect

                                    // Snapshot for ghost
                                    groupGhostTexts = pageTextData[item.id]?.filter { multiSelectedAnnotationIds.contains(it.id) }?.map { it.copy() } ?: emptyList()
                                    groupGhostElements = pageElementData[item.id]?.filter { multiSelectedElementIds.contains(it.id) }?.map { it.copy() } ?: emptyList()
                                },
                                onGlobalDrag = { dragDelta ->
                                    draggingCurrentOffset += dragDelta
                                },
                                onGlobalDragEnd = {
                                    // CROSS-PAGE DROP LOGIC FOR GROUPS AND SINGLES
                                    var droppedPageId: String? = null
                                    var targetRect: Rect? = null

                                    // Find which page we dropped on
                                    for ((pId, rect) in pageBounds) {
                                        if (rect.contains(draggingCurrentOffset)) {
                                            droppedPageId = pId
                                            targetRect = rect
                                            break
                                        }
                                    }

                                    if (droppedPageId != null && targetRect != null && draggingSourcePageId != null) {

                                        if (isDraggingGroup) {
                                            // Determine group new top-left relative to target page
                                            val newBoxTopLeftScreen = draggingCurrentOffset - groupDragStartBoxRelative
                                            val relativeX = newBoxTopLeftScreen.x - targetRect.left
                                            val relativeY = newBoxTopLeftScreen.y - targetRect.top

                                            // Calculate delta relative to original percentages
                                            // We need to shift every item by the same delta relative to the page
                                            val pageW = targetRect.width
                                            val pageH = targetRect.height

                                            // Current Ghost TopLeft vs Original Box TopLeft (in percentage relative to original page size)
                                            // NOTE: Assuming pages are same size for simple percentage transfer logic
                                            // Calculate shift delta in pixels from original position on source page
                                            // Source Rect
                                            val sourceRect = pageBounds[draggingSourcePageId!!]!!

                                            val originalBoxLeftPx = groupGhostBounds.left - sourceRect.left
                                            val originalBoxTopPx = groupGhostBounds.top - sourceRect.top

                                            val deltaX = relativeX - originalBoxLeftPx
                                            val deltaY = relativeY - originalBoxTopPx

                                            val deltaXPct = deltaX / pageW
                                            val deltaYPct = deltaY / pageH

                                            // Process Texts
                                            val sourceTexts = pageTextData[draggingSourcePageId!!]!!
                                            val movingTextsIDs = multiSelectedAnnotationIds.toList()

                                            // Update or Move
                                            if (droppedPageId != draggingSourcePageId) {
                                                val movingItems = sourceTexts.filter { movingTextsIDs.contains(it.id) }.map {
                                                    it.copy(xPercent = it.xPercent + deltaXPct, yPercent = it.yPercent + deltaYPct)
                                                }
                                                sourceTexts.removeAll { movingTextsIDs.contains(it.id) }
                                                pageTextData[droppedPageId]?.addAll(movingItems)
                                            } else {
                                                // Same page update
                                                movingTextsIDs.forEach { id ->
                                                    val idx = sourceTexts.indexOfFirst { it.id == id }
                                                    if (idx != -1) {
                                                        val old = sourceTexts[idx]
                                                        sourceTexts[idx] = old.copy(xPercent = old.xPercent + deltaXPct, yPercent = old.yPercent + deltaYPct)
                                                    }
                                                }
                                            }

                                            // Process Elements
                                            val sourceElems = pageElementData[draggingSourcePageId!!]!!
                                            val movingElemsIDs = multiSelectedElementIds.toList()

                                            if (droppedPageId != draggingSourcePageId) {
                                                val movingItems = sourceElems.filter { movingElemsIDs.contains(it.id) }.map {
                                                    it.copy(xPercent = it.xPercent + deltaXPct, yPercent = it.yPercent + deltaYPct)
                                                }
                                                sourceElems.removeAll { movingElemsIDs.contains(it.id) }
                                                pageElementData[droppedPageId]?.addAll(movingItems)
                                            } else {
                                                movingElemsIDs.forEach { id ->
                                                    val idx = sourceElems.indexOfFirst { it.id == id }
                                                    if (idx != -1) {
                                                        val old = sourceElems[idx]
                                                        sourceElems[idx] = old.copy(xPercent = old.xPercent + deltaXPct, yPercent = old.yPercent + deltaYPct)
                                                    }
                                                }
                                            }
                                            activePageIndex = reportItems.indexOfFirst { it.id == droppedPageId }

                                        } else {
                                            // SINGLE ITEM LOGIC
                                            val relX = draggingCurrentOffset.x - targetRect.left
                                            val relY = draggingCurrentOffset.y - targetRect.top
                                            val newXPercent = relX / targetRect.width
                                            val newYPercent = relY / targetRect.height

                                            if (draggingAnnotation != null) {
                                                val sourceList = pageTextData[draggingSourcePageId!!]
                                                val obj = sourceList?.find { it.id == draggingAnnotation!!.id } ?: draggingAnnotation!!
                                                val movedObj = obj.copy(xPercent = newXPercent, yPercent = newYPercent)

                                                if (droppedPageId != draggingSourcePageId) {
                                                    pageTextData[draggingSourcePageId!!]?.removeAll { it.id == draggingAnnotation!!.id }
                                                    pageTextData[droppedPageId]?.add(movedObj)
                                                    activePageIndex = reportItems.indexOfFirst { it.id == droppedPageId }
                                                } else {
                                                    val idx = sourceList?.indexOfFirst { it.id == draggingAnnotation!!.id }
                                                    if (idx != null && idx != -1) sourceList[idx] = movedObj
                                                }
                                            }

                                            if (draggingElement != null) {
                                                val sourceList = pageElementData[draggingSourcePageId!!]
                                                val obj = sourceList?.find { it.id == draggingElement!!.id } ?: draggingElement!!
                                                val movedObj = obj.copy(xPercent = newXPercent, yPercent = newYPercent)

                                                if (droppedPageId != draggingSourcePageId) {
                                                    pageElementData[draggingSourcePageId!!]?.removeAll { it.id == draggingElement!!.id }
                                                    pageElementData[droppedPageId]?.add(movedObj)
                                                    activePageIndex = reportItems.indexOfFirst { it.id == droppedPageId }
                                                } else {
                                                    val idx = sourceList?.indexOfFirst { it.id == draggingElement!!.id }
                                                    if (idx != null && idx != -1) sourceList[idx] = movedObj
                                                }
                                            }
                                        }
                                    }
                                    // Reset State
                                    draggingAnnotation = null; draggingElement = null; draggingSourcePageId = null; isDraggingGroup = false
                                    groupGhostTexts = emptyList(); groupGhostElements = emptyList()
                                }
                            )
                        }
                    }
                }
            }

            // GHOST LAYERS
            // 1. Group Ghost
            if (isDraggingGroup && (groupGhostTexts.isNotEmpty() || groupGhostElements.isNotEmpty())) {
                // Calculate current screen position of the ghost box top-left
                val currentBoxTopLeft = draggingCurrentOffset - groupDragStartBoxRelative

                // We need to render the ghost items relative to this top-left
                // BUT, the items inside 'groupGhostTexts' store percentage data relative to their ORIGINAL page.
                // We need to map their position relative to the Group Box TopLeft.

                val originalPageRect = pageBounds[draggingSourcePageId]
                if (originalPageRect != null) {
                    val pageW = originalPageRect.width
                    val pageH = originalPageRect.height

                    // Render Group Container at current position
                    Box(modifier = Modifier.offset { IntOffset((currentBoxTopLeft.x - parentOffset.x).roundToInt(), (currentBoxTopLeft.y - parentOffset.y).roundToInt()) }) {
                        // Iterate ghost items
                        groupGhostTexts.forEach { txt ->
                            // Calculate pixel offset relative to the GROUP BOX
                            val itemPxX = txt.xPercent * pageW
                            val itemPxY = txt.yPercent * pageH

                            // Relative to the Ghost Box TopLeft
                            val relX = itemPxX - (groupGhostBounds.left - originalPageRect.left)
                            val relY = itemPxY - (groupGhostBounds.top - originalPageRect.top)

                            Box(modifier = Modifier.offset { IntOffset(relX.roundToInt(), relY.roundToInt()) }
                                .size(with(density){ (txt.widthPercent * pageW).toDp() }, with(density){ (txt.heightPercent * pageH).toDp() })
                                .background(Color.White.copy(alpha=0.5f)).border(1.dp, Color.Gray)) {
                                Text(text = txt.text, color = txt.color, fontSize = 10.sp) // Simplified ghost text
                            }
                        }
                        groupGhostElements.forEach { el ->
                            val itemPxX = el.xPercent * pageW
                            val itemPxY = el.yPercent * pageH

                            val relX = itemPxX - (groupGhostBounds.left - originalPageRect.left)
                            val relY = itemPxY - (groupGhostBounds.top - originalPageRect.top)

                            Box(modifier = Modifier.offset { IntOffset(relX.roundToInt(), relY.roundToInt()) }
                                .size(with(density){ (el.widthPercent * pageW).toDp() }, with(density){ (el.heightPercent * pageH).toDp() })
                                .alpha(0.5f)) {
                                ElementRenderer(el)
                            }
                        }
                    }
                }
            }

            // 2. Single Item Ghost
            if (!isDraggingGroup) {
                if (draggingAnnotation != null) {
                    val currentTextState = pageTextData[draggingSourcePageId]?.find { it.id == draggingAnnotation!!.id } ?: draggingAnnotation!!
                    Box(modifier = Modifier.offset { IntOffset((draggingCurrentOffset.x - parentOffset.x).roundToInt(), (draggingCurrentOffset.y - parentOffset.y).roundToInt()) }.background(Color.White.copy(alpha=0.8f)).border(1.dp, Color.Gray, RoundedCornerShape(4.dp)).padding(4.dp)) {
                        Text(text = currentTextState.text, color = currentTextState.color, fontSize = (currentTextState.fontSize * (zoomPercent/100f)).sp, fontWeight = if (currentTextState.isBold) FontWeight.Bold else FontWeight.Normal, fontStyle = if (currentTextState.isItalic) FontStyle.Italic else FontStyle.Normal, textDecoration = if (currentTextState.isUnderline) TextDecoration.Underline else TextDecoration.None, fontFamily = getFontFamily(currentTextState.fontFamily))
                    }
                }
                if (draggingElement != null && draggingSourcePageId != null) {
                    val currentElState = pageElementData[draggingSourcePageId]?.find { it.id == draggingElement!!.id } ?: draggingElement!!
                    val sourceRect = pageBounds[draggingSourcePageId]
                    val ghostWidth = if (sourceRect != null) sourceRect.width * currentElState.widthPercent else 50f
                    val ghostHeight = if (sourceRect != null) sourceRect.height * currentElState.heightPercent else 50f

                    Box(modifier = Modifier
                        .offset { IntOffset((draggingCurrentOffset.x - parentOffset.x).roundToInt(), (draggingCurrentOffset.y - parentOffset.y).roundToInt()) }
                        .size(with(density) { ghostWidth.toDp() }, with(density) { ghostHeight.toDp() })
                        .background(Color.Transparent)
                    ) {
                        ElementRenderer(currentElState)
                    }
                }
            }
        }

        // Status Bar
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
                pageNumberOverrides[newItem.id] = "${reportItems.size}"
                scope.launch { listState.animateScrollToItem(reportItems.lastIndex); activePageIndex = reportItems.lastIndex }
            }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.NoteAdd, "New Page", tint = Color.White, modifier = Modifier.size(16.dp)) }
            IconButton(onClick = {
                if (reportItems.size > 0 && activePageIndex < reportItems.size) {
                    val id = reportItems[activePageIndex].id
                    reportItems.removeAt(activePageIndex)
                    pageConfigs.remove(id); pageTextData.remove(id); pageElementData.remove(id); pageAnnexureValues.remove(id); pageB1Values.remove(id); pageNumberOverrides.remove(id)
                    if (reportItems.isEmpty()) {
                        val newItem = ReportPageItem(graphId = -200.0, type = "Blank", data = emptyList())
                        reportItems.add(newItem)
                        pageConfigs[newItem.id] = ReportConfig(); pageTextData[newItem.id] = mutableStateListOf(); pageElementData[newItem.id] = mutableStateListOf(); pageAnnexureValues[newItem.id] = ""; pageB1Values[newItem.id] = ""; pageNumberOverrides[newItem.id] = "1"
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