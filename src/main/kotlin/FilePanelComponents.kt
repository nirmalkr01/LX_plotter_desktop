import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
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
    pagePartitionsData: MutableMap<String, List<PartitionSlot>> = remember { mutableStateMapOf() }, // Added Partition State Access
    onActivePageChanged: (Int) -> Unit = {},
    activeGraphType: String = "X-Section",
    selectedPartitionSlot: PartitionSlot? = null,
    onPartitionSelected: (PartitionSlot?) -> Unit = {},
    isPartitionModeEnabled: Boolean,
    onPartitionModeToggle: (Boolean) -> Unit,
    onBack: () -> Unit,
    isLeftPanelVisible: Boolean,
    onLeftPanelToggle: () -> Unit,
    isMiddlePanelVisible: Boolean,
    onMiddlePanelToggle: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()
    val density = LocalDensity.current

    // --- STATE MANAGEMENT ---
    var selectedPaperSize by remember { mutableStateOf(PaperSize.A4) }
    var selectedLayoutType by remember { mutableStateOf(PageLayoutType.BLANK) }
    val isLandscape = true
    var zoomPercent by remember { mutableStateOf(100f) }

    // Page Elements State
    var showPageNumber by remember { mutableStateOf(true) }

    val pageAnnexureValues = remember { mutableStateMapOf<String, String>() }
    val pageB1Values = remember { mutableStateMapOf<String, String>() }
    val pageNumberOverrides = remember { mutableStateMapOf<String, String>() }

    val pageConfigs = remember { mutableStateMapOf<String, ReportConfig>() }
    val pageTextData = remember { mutableStateMapOf<String, MutableList<TextAnnotation>>() }

    val pageElementData = externalPageElementData

    // --- GLOBAL DRAG STATE ---
    val pageBounds = remember { mutableStateMapOf<String, Rect>() }
    var parentOffset by remember { mutableStateOf(Offset.Zero) }

    var draggingSourcePageId by remember { mutableStateOf<String?>(null) }
    var draggingCurrentOffset by remember { mutableStateOf(Offset.Zero) }
    var isDraggingGroup by remember { mutableStateOf(false) }

    var activeAlignmentLines by remember { mutableStateOf<List<AlignmentLine>>(emptyList()) }

    var groupDragStartBoxRelative by remember { mutableStateOf(Offset.Zero) }
    var groupGhostTexts by remember { mutableStateOf<List<TextAnnotation>>(emptyList()) }
    var groupGhostElements by remember { mutableStateOf<List<ReportElement>>(emptyList()) }
    var groupGhostBounds by remember { mutableStateOf(Rect.Zero) }

    var draggingAnnotation by remember { mutableStateOf<TextAnnotation?>(null) }
    var draggingElement by remember { mutableStateOf<ReportElement?>(null) }

    var clipboardTextList by remember { mutableStateOf<List<TextAnnotation>>(emptyList()) }
    var clipboardElementList by remember { mutableStateOf<List<ReportElement>>(emptyList()) }

    var activePageIndex by remember { mutableStateOf(0) }

    LaunchedEffect(activePageIndex) {
        onActivePageChanged(activePageIndex)
    }

    var activeTab by remember { mutableStateOf("Home") }
    var isRibbonExpanded by remember { mutableStateOf(true) }

    var globalTextColor by remember { mutableStateOf(Color.Black) }
    var globalTextSize by remember { mutableStateOf(12f) }
    var globalIsBold by remember { mutableStateOf(false) }
    var globalIsItalic by remember { mutableStateOf(false) }
    var globalIsUnderline by remember { mutableStateOf(false) }
    var globalTextAlign by remember { mutableStateOf(TextAlign.Left) }
    var globalFontFamily by remember { mutableStateOf("Arial") }

    var globalShapeStrokeColor by remember { mutableStateOf(Color.Black) }
    var globalShapeFillColor by remember { mutableStateOf(Color.Transparent) }
    var globalShapeStrokeWidth by remember { mutableStateOf(2f) }
    var globalShapeRotation by remember { mutableStateOf(0f) }

    var isTextToolActive by remember { mutableStateOf(false) }
    var isSelectToolActive by remember { mutableStateOf(false) }

    var selectedAnnotationId by remember { mutableStateOf<String?>(null) }
    var selectedElementId by remember { mutableStateOf<String?>(null) }

    val multiSelectedAnnotationIds = remember { mutableStateListOf<String>() }
    val multiSelectedElementIds = remember { mutableStateListOf<String>() }

    var applyAnnexureToAll by remember { mutableStateOf(true) }
    var applyB1ToAll by remember { mutableStateOf(true) }
    var applyMarginsToAll by remember { mutableStateOf(true) }
    var applyBordersToAll by remember { mutableStateOf(true) }
    var renumberStartFrom by remember { mutableStateOf(1f) }

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

    fun performPaste() {
        val activeId = reportItems.getOrNull(activePageIndex)?.id ?: return

        if (clipboardTextList.isNotEmpty() || clipboardElementList.isNotEmpty()) {
            clipboardTextList.forEach { item ->
                val newId = UUID.randomUUID().toString()
                pageTextData[activeId]?.add(item.copy(id = newId))
                if(isSelectToolActive) multiSelectedAnnotationIds.add(newId)
            }
            clipboardElementList.forEach { item ->
                val newId = UUID.randomUUID().toString()
                pageElementData[activeId]?.add(item.copy(id = newId))
                if(isSelectToolActive) multiSelectedElementIds.add(newId)
            }
            onStatusChange("Pasted items")
        }
    }

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

    fun performNudge(dxMm: Float, dyMm: Float) {
        val activeId = reportItems.getOrNull(activePageIndex)?.id ?: return
        val currentTexts = pageTextData[activeId] ?: return
        val currentElems = pageElementData[activeId] ?: return

        fun nudgeText(id: String) {
            val idx = currentTexts.indexOfFirst { it.id == id }
            if (idx != -1) {
                val old = currentTexts[idx]
                currentTexts[idx] = old.copy(xMm = old.xMm + dxMm, yMm = old.yMm + dyMm)
            }
        }
        fun nudgeElem(id: String) {
            val idx = currentElems.indexOfFirst { it.id == id }
            if (idx != -1) {
                val old = currentElems[idx]
                currentElems[idx] = old.copy(xMm = old.xMm + dxMm, yMm = old.yMm + dyMm)
            }
        }

        if (isSelectToolActive) {
            multiSelectedAnnotationIds.forEach { nudgeText(it) }
            multiSelectedElementIds.forEach { nudgeElem(it) }
        } else {
            selectedAnnotationId?.let { nudgeText(it) }
            selectedElementId?.let { nudgeElem(it) }
        }
    }

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

    fun addElementToActivePage(type: ElementType) {
        val activeId = reportItems.getOrNull(activePageIndex)?.id ?: return
        val list = pageElementData[activeId] ?: return
        val newEl = ReportElement(
            type = type,
            // Default 50x50 mm at 10,10 mm
            xMm = 10f, yMm = 10f, widthMm = 50f, heightMm = 50f,
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
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    if (event.isCtrlPressed) {
                        when (event.key) {
                            Key.C -> { performCopy(); true }
                            Key.V -> { performPaste(); true }
                            else -> false
                        }
                    } else if (event.key == Key.Delete) {
                        performDelete(); true
                    } else if (!isPartitionModeEnabled) {
                        // NUDGE LOGIC IN MM
                        val step = if (event.isShiftPressed) 10f else 1f // 10mm or 1mm
                        when (event.key) {
                            Key.DirectionLeft -> { performNudge(-step, 0f); true }
                            Key.DirectionRight -> { performNudge(step, 0f); true }
                            Key.DirectionUp -> { performNudge(0f, -step); true }
                            Key.DirectionDown -> { performNudge(0f, step); true }
                            else -> false
                        }
                    } else false
                } else false
            }
    ) {
        FilePanelRibbon(
            activeTab = activeTab,
            onTabChange = { activeTab = it },
            isRibbonExpanded = isRibbonExpanded,
            onToggleRibbon = { isRibbonExpanded = !isRibbonExpanded },
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
                                    preColor = Color(preColor.red, preColor.green, preColor.blue, preColor.alpha),
                                    postColor = Color(postColor.red, postColor.green, postColor.blue, postColor.alpha),
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
            onBack = onBack,
            isMiddlePanelVisible = isMiddlePanelVisible,
            onMiddlePanelToggle = { onMiddlePanelToggle() },
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
                            updated = updated.copy(marginTop = srcCfg.marginTop, marginBottom = srcCfg.marginBottom, marginLeft = srcCfg.marginLeft, marginRight = srcCfg.marginRight)
                        }
                        if (applyBordersToAll) {
                            updated = updated.copy(showOuterBorder = srcCfg.showOuterBorder, outerThickness = srcCfg.outerThickness, outerColor = srcCfg.outerColor, showInnerBorder = srcCfg.showInnerBorder, innerThickness = srcCfg.innerThickness, innerColor = srcCfg.innerColor, borderGap = srcCfg.borderGap)
                        }
                        pageConfigs[item.id] = updated
                    }
                    onStatusChange("Applied Styles to ${reportItems.size} pages")
                }
            },
            isTextToolActive = isTextToolActive,
            onTextToolToggle = { isTextToolActive = !isTextToolActive; isSelectToolActive = false; selectedElementId = null },
            isSelectToolActive = isSelectToolActive,
            onSelectToolToggle = { isSelectToolActive = !isSelectToolActive; isTextToolActive = false; if(!isSelectToolActive) { multiSelectedAnnotationIds.clear(); multiSelectedElementIds.clear(); selectedAnnotationId = null; selectedElementId = null } },
            hasGroupSelection = false, onCopyGroup = { }, canPasteGroup = false, onPasteGroup = { },
            selectedElementId = selectedElementId,
            globalShapeStrokeColor = globalShapeStrokeColor, onShapeStrokeColorChange = { updateElementStyle(stroke = it) },
            globalShapeFillColor = globalShapeFillColor, onShapeFillColorChange = { updateElementStyle(fill = it) },
            globalShapeStrokeWidth = globalShapeStrokeWidth, onShapeStrokeWidthChange = { updateElementStyle(width = it) },
            globalShapeRotation = globalShapeRotation, onShapeRotationChange = { updateElementStyle(rotation = it) },
            globalFontFamily = globalFontFamily, onFontFamilyChange = { updateTextStyle(font = it) },
            globalTextSize = globalTextSize, onTextSizeChange = { updateTextStyle(size = it) },
            globalIsBold = globalIsBold, onBoldToggle = { updateTextStyle(bold = !globalIsBold) },
            globalIsItalic = globalIsItalic, onItalicToggle = { updateTextStyle(italic = !globalIsItalic) },
            globalIsUnderline = globalIsUnderline, onUnderlineToggle = { updateTextStyle(underline = !globalIsUnderline) },
            globalTextColor = globalTextColor, onTextColorChange = { updateTextStyle(color = it) },
            globalTextAlign = globalTextAlign, onTextAlignChange = { updateTextStyle(align = it) },
            onAddElement = { addElementToActivePage(it) }
        )

        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFF505050)).padding(vertical = 20.dp, horizontal = 20.dp)
                .onGloballyPositioned { parentOffset = it.positionInWindow() }
        ) {
            val constraints = this.constraints
            val minContainerWidthDp = with(density) { constraints.maxWidth.toDp() }

            Box(modifier = Modifier.fillMaxSize().horizontalScroll(horizontalScrollState)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.widthIn(min = minContainerWidthDp).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    itemsIndexed(reportItems) { idx, item ->
                        val isActive = idx == activePageIndex

                        // Calculate Pixel Scale for this Preview
                        val widthMm = if (isLandscape) selectedPaperSize.heightMm else selectedPaperSize.widthMm
                        val pxPerMm = 3.78f * density.density * (zoomPercent / 100f) // Base 3.78 ~ 96 DPI

                        val paperW_px = widthMm * pxPerMm
                        val paperW_dp_final = with(density) { paperW_px.toDp() }

                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(paperW_dp_final).padding(bottom = 4.dp).onGloballyPositioned { pageBounds[item.id] = Rect(it.positionInWindow(), Size(it.size.width.toFloat(), it.size.height.toFloat())) }) {
                            Box(modifier = Modifier.shadow(8.dp).border(if(isActive) 2.dp else 0.dp, if(isActive) Color(0xFF2B579A) else Color.Transparent)) {
                                EditablePageContainer(
                                    item = item, pageNumber = (pageNumberOverrides[item.id] ?: "${idx + 1}").toIntOrNull() ?: (idx + 1), totalPageCount = reportItems.size,
                                    annexureValue = pageAnnexureValues[item.id] ?: "", b1Text = pageB1Values[item.id] ?: "",
                                    paperSize = selectedPaperSize, layoutType = selectedLayoutType, isLandscape = isLandscape, config = pageConfigs[item.id] ?: ReportConfig(), zoomLevel = zoomPercent,
                                    textAnnotations = pageTextData[item.id] ?: mutableStateListOf(), elements = pageElementData[item.id] ?: mutableStateListOf(),
                                    hScale = if (pageConfigs[item.id]?.legendType == "L-Section") lHScale else xHScale, vScale = if (pageConfigs[item.id]?.legendType == "L-Section") lVScale else xVScale,
                                    showPre = showPre, showPost = showPost, preColor = preColor, postColor = postColor, preDotted = preDotted, postDotted = postDotted, preWidth = preWidth, postWidth = postWidth, preShowPoints = preShowPoints, postShowPoints = postShowPoints, showGrid = showGrid,
                                    showPageNumber = showPageNumber, currentTextColor = globalTextColor, currentTextSize = globalTextSize, isBold = globalIsBold, isItalic = globalIsItalic, isUnderline = globalIsUnderline, currentTextAlign = globalTextAlign, currentFontFamily = globalFontFamily,
                                    isTextToolActive = isTextToolActive && isActive && !isPartitionModeEnabled,
                                    selectedAnnotationId = if(isActive && !isPartitionModeEnabled) selectedAnnotationId else null,
                                    selectedElementId = if(isActive && !isPartitionModeEnabled) selectedElementId else null,
                                    isSelectToolActive = isSelectToolActive && isActive && !isPartitionModeEnabled,
                                    multiSelectedAnnotationIds = if(isActive && !isPartitionModeEnabled) multiSelectedAnnotationIds else mutableListOf(),
                                    multiSelectedElementIds = if(isActive && !isPartitionModeEnabled) multiSelectedElementIds else mutableListOf(),
                                    canPaste = (clipboardTextList.isNotEmpty() || clipboardElementList.isNotEmpty()) && !isPartitionModeEnabled,
                                    onAnnotationSelected = { id -> if(!isPartitionModeEnabled) { selectedAnnotationId = id; selectedElementId = null; if(id != null) activePageIndex = idx; if(!isSelectToolActive) { multiSelectedAnnotationIds.clear(); multiSelectedElementIds.clear() } } },
                                    onElementSelected = { id -> if(!isPartitionModeEnabled) { selectedElementId = id; selectedAnnotationId = null; if(id != null) activePageIndex = idx; if(!isSelectToolActive) { multiSelectedAnnotationIds.clear(); multiSelectedElementIds.clear() } } },
                                    onPageSelected = { activePageIndex = idx; if(!isSelectToolActive && !isPartitionModeEnabled) { selectedAnnotationId = null; selectedElementId = null } },
                                    onToolUsed = { isTextToolActive = false }, onGraphPosChange = { _, _ ->  },
                                    onDeleteAnnotation = { id -> if(!isPartitionModeEnabled) { pageTextData[item.id]?.removeAll { it.id == id }; selectedAnnotationId = null } },
                                    onDeleteElement = { id -> if(!isPartitionModeEnabled) { pageElementData[item.id]?.removeAll { it.id == id }; selectedElementId = null } },
                                    onCopyAnnotation = { if(!isPartitionModeEnabled) { clipboardTextList = listOf(it); clipboardElementList = emptyList() } },
                                    onPasteAnnotation = { if(!isPartitionModeEnabled) performPaste() },
                                    onContextMenuCopy = { if(!isPartitionModeEnabled) performCopy() }, onContextMenuPaste = { if(!isPartitionModeEnabled) performPaste() }, onContextMenuDelete = { if(!isPartitionModeEnabled) performDelete() },
                                    hasClipboardContent = clipboardTextList.isNotEmpty() || clipboardElementList.isNotEmpty(),
                                    hiddenAnnotationId = if(draggingAnnotation != null && draggingSourcePageId == item.id) draggingAnnotation!!.id else null,
                                    hiddenElementId = if(draggingElement != null && draggingSourcePageId == item.id) draggingElement!!.id else null,
                                    onGlobalDragStart = { obj, pos -> if(!isPartitionModeEnabled) {
                                        if (obj is TextAnnotation) { draggingAnnotation = obj; draggingSourcePageId = item.id; draggingCurrentOffset = pos }
                                        if (obj is ReportElement) { draggingElement = obj; draggingSourcePageId = item.id; draggingCurrentOffset = pos }
                                        isDraggingGroup = false
                                    }},
                                    onGlobalElementDragStart = { el, pos -> if(!isPartitionModeEnabled) { draggingElement = el; draggingSourcePageId = item.id; draggingCurrentOffset = pos; isDraggingGroup = false } },
                                    onGroupDragStart = { pos, rect -> if(!isPartitionModeEnabled) { isDraggingGroup = true; draggingSourcePageId = item.id; draggingCurrentOffset = pos; groupDragStartBoxRelative = pos - rect.topLeft; groupGhostBounds = rect; groupGhostTexts = pageTextData[item.id]?.filter { multiSelectedAnnotationIds.contains(it.id) }?.map { it.copy() } ?: emptyList(); groupGhostElements = pageElementData[item.id]?.filter { multiSelectedElementIds.contains(it.id) }?.map { it.copy() } ?: emptyList() } },
                                    onGlobalDrag = { dragDelta -> if(!isPartitionModeEnabled) { draggingCurrentOffset += dragDelta } },
                                    onGlobalDragEnd = {
                                        activeAlignmentLines = emptyList()
                                        if(!isPartitionModeEnabled && draggingSourcePageId != null) {
                                            // DROP LOGIC
                                            var droppedPageId: String? = null
                                            var targetRect: Rect? = null
                                            for ((pId, rect) in pageBounds) { if (rect.contains(draggingCurrentOffset)) { droppedPageId = pId; targetRect = rect; break } }

                                            if (droppedPageId != null && targetRect != null) {
                                                // Calculate Scale of Source and Target
                                                val targetPxPerMm = 3.78f * density.density * (zoomPercent / 100f) // Must match preview logic

                                                if (isDraggingGroup) {
                                                    // Group drop logic
                                                    if(droppedPageId != draggingSourcePageId) {
                                                        // Transfer logic
                                                        val offsetPx = draggingCurrentOffset - groupDragStartBoxRelative - targetRect.topLeft
                                                        val dxMm = offsetPx.x / targetPxPerMm
                                                        val dyMm = offsetPx.y / targetPxPerMm
                                                    }
                                                } else {
                                                    // Single Item Drop Cross Page
                                                    if (droppedPageId != draggingSourcePageId) {
                                                        val relPx = draggingCurrentOffset - targetRect.topLeft
                                                        val newXMm = relPx.x / targetPxPerMm
                                                        val newYMm = relPx.y / targetPxPerMm

                                                        if (draggingAnnotation != null) {
                                                            val obj = pageTextData[draggingSourcePageId!!]?.find{it.id == draggingAnnotation!!.id}?.copy(xMm = newXMm, yMm = newYMm)
                                                            if (obj != null) {
                                                                pageTextData[draggingSourcePageId!!]?.removeIf{it.id == draggingAnnotation!!.id}
                                                                pageTextData[droppedPageId]?.add(obj)
                                                            }
                                                        }
                                                        if (draggingElement != null) {
                                                            val obj = pageElementData[draggingSourcePageId!!]?.find{it.id == draggingElement!!.id}?.copy(xMm = newXMm, yMm = newYMm)
                                                            if (obj != null) {
                                                                pageElementData[draggingSourcePageId!!]?.removeIf{it.id == draggingElement!!.id}
                                                                pageElementData[droppedPageId]?.add(obj)
                                                            }
                                                        }
                                                        activePageIndex = reportItems.indexOfFirst { it.id == droppedPageId }
                                                    }
                                                }
                                            }
                                        }
                                        draggingAnnotation = null; draggingElement = null; draggingSourcePageId = null; isDraggingGroup = false; groupGhostTexts = emptyList(); groupGhostElements = emptyList()
                                    }
                                )

                                if (isActive && isPartitionModeEnabled) {
                                    val config = pageConfigs[item.id] ?: ReportConfig()
                                    val heightMm = if (isLandscape) selectedPaperSize.widthMm else selectedPaperSize.heightMm

                                    // Ensure the baseline default partition schema exists locally
                                    LaunchedEffect(item.id, widthMm, heightMm, config, selectedLayoutType, activeGraphType) {
                                        if (pagePartitionsData[item.id] == null) {
                                            pagePartitionsData[item.id] = calculatePartitions(
                                                widthMm.toFloat(), heightMm.toFloat(),
                                                config.marginTop, config.marginBottom,
                                                config.marginLeft, config.marginRight,
                                                selectedLayoutType, activeGraphType, config
                                            )
                                        }
                                    }

                                    // EXTRACT THE CURRENT GRID LAYER FOR THE PAGE
                                    val partitions = pagePartitionsData[item.id] ?: emptyList()

                                    Canvas(modifier = Modifier.matchParentSize()) {
                                        partitions.forEach { slot ->
                                            val isSelected = selectedPartitionSlot?.id == slot.id
                                            val rectPx = slot.getScreenRect(pxPerMm) // Call helper bound conversion
                                            if (isSelected) {
                                                drawRect(Color(0xFF2196F3).copy(alpha = 0.2f), topLeft = rectPx.topLeft, size = rectPx.size)
                                                drawRect(Color(0xFF2196F3), topLeft = rectPx.topLeft, size = rectPx.size, style = Stroke(width = 2f))
                                            } else {
                                                drawRect(Color.Gray.copy(alpha = 0.3f), topLeft = rectPx.topLeft, size = rectPx.size, style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
                                            }
                                        }
                                    }
                                    Box(modifier = Modifier.matchParentSize().pointerInput(partitions) {
                                        detectTapGestures { offset ->
                                            val clicked = partitions.find {
                                                val r = it.getScreenRect(pxPerMm)
                                                r.contains(offset)
                                            }
                                            if (clicked != null) onPartitionSelected(clicked)
                                        }
                                    })
                                }
                            }
                        }
                    }
                }
            }

            // GHOST RENDER (Scaled)
            if (isDraggingGroup && (groupGhostTexts.isNotEmpty() || groupGhostElements.isNotEmpty())) {
                val currentBoxTopLeft = draggingCurrentOffset - groupDragStartBoxRelative
                val pageW = pageBounds[draggingSourcePageId]?.width ?: 1f
                val pxPerMm = 3.78f * density.density * (zoomPercent / 100f) // Recalculate or pass down

                Box(modifier = Modifier.offset { IntOffset((currentBoxTopLeft.x - parentOffset.x).roundToInt(), (currentBoxTopLeft.y - parentOffset.y).roundToInt()) }.zIndex(100f)) {
                    // Logic to render ghost items relative to box top-left... simplified for now as exact mm relative offset is complex without full logic
                    // Render simple placeholder box
                    Box(Modifier.size(100.dp, 50.dp).background(Color.Blue.copy(0.3f)))
                }
            }

            if (!isDraggingGroup) {
                // Single Item Ghost
                val pxPerMm = 3.78f * density.density * (zoomPercent / 100f)
                if (draggingAnnotation != null) {
                    val txt = pageTextData[draggingSourcePageId]?.find { it.id == draggingAnnotation!!.id } ?: draggingAnnotation!!
                    Box(modifier = Modifier.offset { IntOffset((draggingCurrentOffset.x - parentOffset.x).roundToInt(), (draggingCurrentOffset.y - parentOffset.y).roundToInt()) }
                        .zIndex(100f).background(Color.White.copy(0.8f)).border(1.dp, Color.Gray).padding(4.dp)) {
                        Text(txt.text, color = txt.color, fontSize = (txt.fontSize * (pxPerMm/3.78f)).sp)
                    }
                }
                if (draggingElement != null) {
                    val el = pageElementData[draggingSourcePageId]?.find { it.id == draggingElement!!.id } ?: draggingElement!!
                    val wPx = el.widthMm * pxPerMm
                    val hPx = el.heightMm * pxPerMm
                    Box(modifier = Modifier.offset { IntOffset((draggingCurrentOffset.x - parentOffset.x).roundToInt(), (draggingCurrentOffset.y - parentOffset.y).roundToInt()) }
                        .zIndex(100f).size(with(density){wPx.toDp()}, with(density){hPx.toDp()})) {

                        // FIX: Explicitly Render GraphPageCanvas when the ElementType is GRAPH_IMAGE
                        if (el.type == ElementType.GRAPH_IMAGE) {
                            val awtPreColor = java.awt.Color(
                                (el.graphPreColor.red * 255).toInt(),
                                (el.graphPreColor.green * 255).toInt(),
                                (el.graphPreColor.blue * 255).toInt(),
                                (el.graphPreColor.alpha * 255).toInt()
                            )
                            val awtPostColor = java.awt.Color(
                                (el.graphPostColor.red * 255).toInt(),
                                (el.graphPostColor.green * 255).toInt(),
                                (el.graphPostColor.blue * 255).toInt(),
                                (el.graphPostColor.alpha * 255).toInt()
                            )

                            GraphPageCanvas(
                                modifier = Modifier.fillMaxSize(),
                                data = el.graphData,
                                type = el.graphType,
                                paperSize = selectedPaperSize,
                                isLandscape = isLandscape,
                                hScale = el.graphHScale,
                                vScale = el.graphVScale,
                                config = ReportConfig(),
                                showPre = el.graphShowPre,
                                showPost = el.graphShowPost,
                                preColor = awtPreColor,
                                postColor = awtPostColor,
                                preWidth = el.graphPreWidth,
                                postWidth = el.graphPostWidth,
                                preDotted = el.graphPreDotted,
                                postDotted = el.graphPostDotted,
                                preShowPoints = true,
                                postShowPoints = true,
                                showGrid = el.graphShowGrid,
                                isTransparentOverlay = true,
                                pxPerMm = pxPerMm,
                                riverOffsets = el.riverOffsets,
                                blueLineOffsets = el.blueLineOffsets,
                                chLabelOffset = el.chLabelOffset,
                                deletedRiverIndices = el.deletedRiverIndices,
                                deletedBlueLineIndices = el.deletedBlueLineIndices,
                                isChLabelDeleted = el.isChLabelDeleted,
                                datumSize = el.datumSize,
                                axisLabelSize = el.axisLabelSize,
                                tableTextSize = el.tableTextSize,
                                tableGap = el.tableGap,
                                riverTextSize = el.riverTextSize,
                                chainageTextSize = el.chainageTextSize
                            )
                        } else {
                            ElementRenderer(el)
                        }
                    }
                }
            }

            HorizontalScrollbar(adapter = rememberScrollbarAdapter(horizontalScrollState), modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth())
        }
    }
}