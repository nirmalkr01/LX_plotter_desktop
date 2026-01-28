import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlin.math.max
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
    onStatusChange: (String) -> Unit,
    externalPageElementData: MutableMap<String, MutableList<ReportElement>> = remember { mutableStateMapOf() },
    onActivePageChanged: (Int) -> Unit = {},
    // --- NEW PARAMS FOR PARTITIONING ---
    activeGraphType: String = "X-Section",
    selectedPartitionSlot: PartitionSlot? = null,
    onPartitionSelected: (PartitionSlot?) -> Unit = {},
    // NEW: Partition Mode State
    isPartitionModeEnabled: Boolean,
    onPartitionModeToggle: (Boolean) -> Unit,

    // --- NEW PARAMS FOR NAV & TOGGLES ---
    onBack: () -> Unit,
    isLeftPanelVisible: Boolean,
    onLeftPanelToggle: () -> Unit,
    isMiddlePanelVisible: Boolean,
    onMiddlePanelToggle: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState() // Added for horizontal scrolling
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

    // USE EXTERNAL MAP FOR ELEMENTS (To allow floating graphs from other screens)
    val pageElementData = externalPageElementData

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

    // Notify parent about active page change
    LaunchedEffect(activePageIndex) {
        onActivePageChanged(activePageIndex)
    }

    // CHANGED: Default tab is now "Home"
    var activeTab by remember { mutableStateOf("Home") }
    var isRibbonExpanded by remember { mutableStateOf(true) } // Default Open

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
            // USE EXTERNAL MAP, DON'T RE-INIT
            if(!pageElementData.containsKey(newItem.id)) pageElementData[newItem.id] = mutableStateListOf()

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
        // ================= UNIFIED TOOLBAR (REPLACED HEADER & TABS) =================
        FilePanelRibbon(
            activeTab = activeTab,
            onTabChange = { activeTab = it },
            isRibbonExpanded = isRibbonExpanded,
            onToggleRibbon = { isRibbonExpanded = !isRibbonExpanded },

            // HEADER ACTIONS MERGED HERE
            isPartitionModeEnabled = isPartitionModeEnabled,
            onPartitionModeToggle = onPartitionModeToggle,
            onExportPdf = {
                if(reportItems.isNotEmpty()) {
                    pickSaveFile("Report.pdf")?.let { file ->
                        scope.launch {
                            onStatusChange("Generating PDF...")
                            withContext(Dispatchers.IO) {
                                saveReportToPdf(
                                    reportItems = reportItems,
                                    file = file,
                                    paperSize = selectedPaperSize,
                                    isLandscape = isLandscape,
                                    lHScale = lHScale, lVScale = lVScale,
                                    xHScale = xHScale, xVScale = xVScale,
                                    pageConfigs = pageConfigs,
                                    pageTextData = pageTextData,
                                    pageElementData = pageElementData,
                                    pageAnnexureValues = pageAnnexureValues,
                                    pageB1Values = pageB1Values,
                                    pageNumberOverrides = pageNumberOverrides,
                                    showPre = showPre, showPost = showPost,
                                    preColor = preColor, postColor = postColor,
                                    preDotted = preDotted, postDotted = postDotted,
                                    preWidth = preWidth, postWidth = postWidth,
                                    preShowPoints = preShowPoints, postShowPoints = postShowPoints,
                                    showGrid = showGrid
                                )
                            }
                            onStatusChange("PDF Saved Successfully!")
                        }
                    }
                }
            },

            // --- PASS NEW BUTTON PARAMS ---
            onBack = onBack,
            isMiddlePanelVisible = isMiddlePanelVisible,
            onMiddlePanelToggle = { onMiddlePanelToggle() },

            // PAGE MANAGEMENT (Moved from Footer)
            onAddPage = {
                val newItem = ReportPageItem(graphId = -200.0, type = "Blank", data = emptyList())
                reportItems.add(newItem)
                pageConfigs[newItem.id] = ReportConfig()
                pageTextData[newItem.id] = mutableStateListOf()
                if(!pageElementData.containsKey(newItem.id)) pageElementData[newItem.id] = mutableStateListOf()
                pageAnnexureValues[newItem.id] = ""; pageB1Values[newItem.id] = ""; pageNumberOverrides[newItem.id] = "${reportItems.size}"
                scope.launch { listState.animateScrollToItem(reportItems.lastIndex); activePageIndex = reportItems.lastIndex }
            },
            onDeletePage = {
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
            },
            zoomPercent = zoomPercent,
            onZoomChange = { zoomPercent = it },

            // EXISTING PARAMS
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
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF505050))
                .padding(vertical = 20.dp, horizontal = 20.dp)
                .onGloballyPositioned { layoutCoordinates ->
                    parentOffset = layoutCoordinates.positionInWindow()
                }
        ) {
            val constraints = this.constraints
            // Convert constraint width to Dp for widthIn
            val minContainerWidthDp = with(density) { constraints.maxWidth.toDp() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(horizontalScrollState)
            ) {
                // MODIFIED: LazyColumn now has a minimum width equal to the container width.
                // This ensures that if the pages are narrower than the screen, the LazyColumn
                // fills the screen and centers the content via horizontalAlignment.
                // If the pages are wider, the LazyColumn expands naturally and scrolling works.
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .widthIn(min = minContainerWidthDp)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    itemsIndexed(reportItems) { idx, item ->
                        val isActive = idx == activePageIndex
                        // Ensure each item requests enough width based on paper size/zoom, preventing shrink
                        val widthMm = if (isLandscape) selectedPaperSize.heightMm else selectedPaperSize.widthMm
                        val pxPerMm = 1.5f * (zoomPercent / 100f)
                        val paperW_dp = (widthMm * pxPerMm).dp

                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(paperW_dp).padding(bottom = 4.dp).onGloballyPositioned { pageBounds[item.id] = Rect(it.positionInWindow(), Size(it.size.width.toFloat(), it.size.height.toFloat())) }) {
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

                                    // Disable standard editing interaction in Partition Mode
                                    isTextToolActive = isTextToolActive && isActive && !isPartitionModeEnabled,
                                    selectedAnnotationId = if(isActive && !isPartitionModeEnabled) selectedAnnotationId else null,
                                    selectedElementId = if(isActive && !isPartitionModeEnabled) selectedElementId else null,
                                    isSelectToolActive = isSelectToolActive && isActive && !isPartitionModeEnabled,
                                    multiSelectedAnnotationIds = if(isActive && !isPartitionModeEnabled) multiSelectedAnnotationIds else mutableListOf(),
                                    multiSelectedElementIds = if(isActive && !isPartitionModeEnabled) multiSelectedElementIds else mutableListOf(),

                                    canPaste = (clipboardTextList.isNotEmpty() || clipboardElementList.isNotEmpty()) && !isPartitionModeEnabled,
                                    onAnnotationSelected = { id ->
                                        if(!isPartitionModeEnabled) {
                                            selectedAnnotationId = id
                                            selectedElementId = null
                                            if(id != null) activePageIndex = idx
                                            if(!isSelectToolActive) {
                                                multiSelectedAnnotationIds.clear()
                                                multiSelectedElementIds.clear()
                                            }
                                        }
                                    },
                                    onElementSelected = { id ->
                                        if(!isPartitionModeEnabled) {
                                            selectedElementId = id
                                            selectedAnnotationId = null
                                            if(id != null) activePageIndex = idx
                                            if(!isSelectToolActive) {
                                                multiSelectedAnnotationIds.clear()
                                                multiSelectedElementIds.clear()
                                            }
                                        }
                                    },
                                    onPageSelected = { activePageIndex = idx; if(!isSelectToolActive && !isPartitionModeEnabled) { selectedAnnotationId = null; selectedElementId = null } },
                                    onToolUsed = { isTextToolActive = false }, onGraphPosChange = { x, y -> if(!isPartitionModeEnabled) reportItems[idx] = item.copy(xOffset = x, yOffset = y) },
                                    onDeleteAnnotation = { id -> if(!isPartitionModeEnabled) { pageTextData[item.id]?.removeAll { it.id == id }; selectedAnnotationId = null } },
                                    onDeleteElement = { id -> if(!isPartitionModeEnabled) { pageElementData[item.id]?.removeAll { it.id == id }; selectedElementId = null } },
                                    onCopyAnnotation = { if(!isPartitionModeEnabled) { clipboardTextList = listOf(it); clipboardElementList = emptyList() } },
                                    onPasteAnnotation = { if(!isPartitionModeEnabled) performPaste() },
                                    onContextMenuCopy = { if(!isPartitionModeEnabled) performCopy() },
                                    onContextMenuPaste = { if(!isPartitionModeEnabled) performPaste() },
                                    onContextMenuDelete = { if(!isPartitionModeEnabled) performDelete() },
                                    hasClipboardContent = clipboardTextList.isNotEmpty() || clipboardElementList.isNotEmpty(),

                                    hiddenAnnotationId = if(draggingAnnotation != null && draggingSourcePageId == item.id) draggingAnnotation!!.id else null,
                                    hiddenElementId = if(draggingElement != null && draggingSourcePageId == item.id) draggingElement!!.id else null,

                                    // Disable Dragging in Partition Mode
                                    onGlobalDragStart = { annotation, pos ->
                                        if(!isPartitionModeEnabled) { draggingAnnotation = annotation; draggingSourcePageId = item.id; draggingCurrentOffset = pos; isDraggingGroup = false }
                                    },
                                    onGlobalElementDragStart = { element, pos ->
                                        if(!isPartitionModeEnabled) { draggingElement = element; draggingSourcePageId = item.id; draggingCurrentOffset = pos; isDraggingGroup = false }
                                    },
                                    onGroupDragStart = { pos, rect ->
                                        if(!isPartitionModeEnabled) { isDraggingGroup = true; draggingSourcePageId = item.id; draggingCurrentOffset = pos; groupDragStartBoxRelative = pos - rect.topLeft; groupGhostBounds = rect; groupGhostTexts = pageTextData[item.id]?.filter { multiSelectedAnnotationIds.contains(it.id) }?.map { it.copy() } ?: emptyList(); groupGhostElements = pageElementData[item.id]?.filter { multiSelectedElementIds.contains(it.id) }?.map { it.copy() } ?: emptyList() }
                                    },
                                    onGlobalDrag = { dragDelta ->
                                        if(!isPartitionModeEnabled) draggingCurrentOffset += dragDelta
                                    },
                                    onGlobalDragEnd = {
                                        // CROSS-PAGE DROP LOGIC FOR GROUPS AND SINGLES
                                        if(!isPartitionModeEnabled) {
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

                                                    val pageW = targetRect.width
                                                    val pageH = targetRect.height
                                                    val sourceRect = pageBounds[draggingSourcePageId!!]!!

                                                    val originalBoxLeftPx = groupGhostBounds.left - sourceRect.left
                                                    val originalBoxTopPx = groupGhostBounds.top - sourceRect.top

                                                    val deltaX = relativeX - originalBoxLeftPx
                                                    val deltaY = relativeY - originalBoxTopPx

                                                    val deltaXPct = deltaX / pageW
                                                    val deltaYPct = deltaY / pageH

                                                    val sourceTexts = pageTextData[draggingSourcePageId!!]!!
                                                    val movingTextsIDs = multiSelectedAnnotationIds.toList()

                                                    if (droppedPageId != draggingSourcePageId) {
                                                        val movingItems = sourceTexts.filter { movingTextsIDs.contains(it.id) }.map {
                                                            it.copy(xPercent = it.xPercent + deltaXPct, yPercent = it.yPercent + deltaYPct)
                                                        }
                                                        sourceTexts.removeAll { movingTextsIDs.contains(it.id) }
                                                        pageTextData[droppedPageId]?.addAll(movingItems)
                                                    } else {
                                                        movingTextsIDs.forEach { id ->
                                                            val idx = sourceTexts.indexOfFirst { it.id == id }
                                                            if (idx != -1) {
                                                                val old = sourceTexts[idx]
                                                                sourceTexts[idx] = old.copy(xPercent = old.xPercent + deltaXPct, yPercent = old.yPercent + deltaYPct)
                                                            }
                                                        }
                                                    }

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
                                        }
                                        // Reset State
                                        draggingAnnotation = null; draggingElement = null; draggingSourcePageId = null; isDraggingGroup = false
                                        groupGhostTexts = emptyList(); groupGhostElements = emptyList()
                                    }
                                )

                                // --- DRAW PARTITION OVERLAY ---
                                if (isActive && isPartitionModeEnabled) {
                                    val config = pageConfigs[item.id] ?: ReportConfig()
                                    // Fetch Paper Dimensions based on user selection
                                    val widthMm = if (isLandscape) selectedPaperSize.heightMm else selectedPaperSize.widthMm
                                    val heightMm = if (isLandscape) selectedPaperSize.widthMm else selectedPaperSize.heightMm

                                    // Calculate pixels per mm based on current zoom
                                    val pxPerMm = 1.5f * (zoomPercent / 100f)

                                    val paperW = widthMm * pxPerMm
                                    val paperH = heightMm * pxPerMm

                                    // CALCULATE SLOTS
                                    val partitions = remember(paperW, paperH, config, activeGraphType, selectedLayoutType) {
                                        calculatePartitions(
                                            paperWidthPx = paperW,
                                            paperHeightPx = paperH,
                                            marginTopPx = config.marginTop * pxPerMm,
                                            marginBottomPx = config.marginBottom * pxPerMm,
                                            marginLeftPx = config.marginLeft * pxPerMm,
                                            marginRightPx = config.marginRight * pxPerMm,
                                            layoutType = selectedLayoutType,
                                            graphType = activeGraphType,
                                            config = config,
                                            pxPerMm = pxPerMm
                                        )
                                    }

                                    // DRAW SLOTS
                                    Canvas(modifier = Modifier.matchParentSize()) {
                                        partitions.forEach { slot ->
                                            val isSelected = selectedPartitionSlot?.id == slot.id

                                            if (isSelected) {
                                                // Blue Highlight for selection
                                                drawRect(Color(0xFF2196F3).copy(alpha = 0.2f), topLeft = Offset(slot.rect.left, slot.rect.top), size = Size(slot.rect.width, slot.rect.height))
                                                drawRect(Color(0xFF2196F3), topLeft = Offset(slot.rect.left, slot.rect.top), size = Size(slot.rect.width, slot.rect.height), style = Stroke(width = 2f))
                                            } else {
                                                // Gray Dashed for available slots
                                                drawRect(Color.Gray.copy(alpha = 0.3f), topLeft = Offset(slot.rect.left, slot.rect.top), size = Size(slot.rect.width, slot.rect.height), style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
                                            }
                                        }
                                    }

                                    // HANDLE CLICKS
                                    Box(modifier = Modifier.matchParentSize().pointerInput(partitions) {
                                        detectTapGestures { offset ->
                                            val clicked = partitions.find { it.rect.contains(offset) }
                                            if (clicked != null) {
                                                onPartitionSelected(clicked)
                                            }
                                        }
                                    })
                                }
                            }
                        }
                    }
                }
            }

            // GHOST LAYERS (RENDERED LAST TO BE ON TOP)
            if (isDraggingGroup && (groupGhostTexts.isNotEmpty() || groupGhostElements.isNotEmpty())) {
                val currentBoxTopLeft = draggingCurrentOffset - groupDragStartBoxRelative
                val originalPageRect = pageBounds[draggingSourcePageId]
                if (originalPageRect != null) {
                    val pageW = originalPageRect.width
                    val pageH = originalPageRect.height

                    Box(modifier = Modifier.offset { IntOffset((currentBoxTopLeft.x - parentOffset.x).roundToInt(), (currentBoxTopLeft.y - parentOffset.y).roundToInt()) }) {
                        groupGhostTexts.forEach { txt ->
                            val itemPxX = txt.xPercent * pageW
                            val itemPxY = txt.yPercent * pageH
                            val relX = itemPxX - (groupGhostBounds.left - originalPageRect.left)
                            val relY = itemPxY - (groupGhostBounds.top - originalPageRect.top)

                            Box(modifier = Modifier.offset { IntOffset(relX.roundToInt(), relY.roundToInt()) }
                                .size(with(density){ (txt.widthPercent * pageW).toDp() }, with(density){ (txt.heightPercent * pageH).toDp() })
                                .background(Color.White.copy(alpha=0.5f)).border(1.dp, Color.Gray)) {
                                Text(text = txt.text, color = txt.color, fontSize = 10.sp)
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
                                if (el.type == ElementType.GRAPH_IMAGE) {
                                    val awtPreColor = java.awt.Color(el.graphPreColor.red, el.graphPreColor.green, el.graphPreColor.blue)
                                    val awtPostColor = java.awt.Color(el.graphPostColor.red, el.graphPostColor.green, el.graphPostColor.blue)
                                    GraphPageCanvas(
                                        modifier = Modifier.fillMaxSize(), data = el.graphData, type = el.graphType, paperSize = PaperSize.A4, isLandscape = isLandscape, hScale = el.graphHScale, vScale = el.graphVScale, config = ReportConfig(), showPre = el.graphShowPre, showPost = el.graphShowPost, preColor = awtPreColor, postColor = awtPostColor, preWidth = el.graphPreWidth, postWidth = el.graphPostWidth, preDotted = el.graphPreDotted, postDotted = el.graphPostDotted, preShowPoints = true, postShowPoints = true, showGrid = el.graphShowGrid, isTransparentOverlay = true
                                    )
                                } else {
                                    ElementRenderer(el)
                                }
                            }
                        }
                    }
                }
            }

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
                    // Fix ghost width logic to prevent disappearing
                    val ghostWidth = if (sourceRect != null) sourceRect.width * currentElState.widthPercent else 50f
                    val ghostHeight = if (sourceRect != null) sourceRect.height * currentElState.heightPercent else 50f

                    Box(modifier = Modifier
                        .offset { IntOffset((draggingCurrentOffset.x - parentOffset.x).roundToInt(), (draggingCurrentOffset.y - parentOffset.y).roundToInt()) }
                        .size(with(density) { ghostWidth.toDp() }, with(density) { ghostHeight.toDp() })
                        .background(Color.Transparent)
                    ) {
                        if (currentElState.type == ElementType.GRAPH_IMAGE) {
                            val el = currentElState
                            val awtPreColor = java.awt.Color(el.graphPreColor.red, el.graphPreColor.green, el.graphPreColor.blue)
                            val awtPostColor = java.awt.Color(el.graphPostColor.red, el.graphPostColor.green, el.graphPostColor.blue)
                            GraphPageCanvas(
                                modifier = Modifier.fillMaxSize(), data = el.graphData, type = el.graphType, paperSize = PaperSize.A4, isLandscape = isLandscape, hScale = el.graphHScale, vScale = el.graphVScale, config = ReportConfig(), showPre = el.graphShowPre, showPost = el.graphShowPost, preColor = awtPreColor, postColor = awtPostColor, preWidth = el.graphPreWidth, postWidth = el.graphPostWidth, preDotted = el.graphPreDotted, postDotted = el.graphPostDotted, preShowPoints = true, postShowPoints = true, showGrid = showGrid, isTransparentOverlay = true
                            )
                        } else {
                            ElementRenderer(currentElState)
                        }
                    }
                }
            }
            // Add Horizontal Scrollbar
            HorizontalScrollbar(
                adapter = rememberScrollbarAdapter(horizontalScrollState),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 0.dp) // Adjust padding if needed to not overlap status bar
            )
        }
    }
}