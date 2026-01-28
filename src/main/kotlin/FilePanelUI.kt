import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Helper for Tooltips (assuming Material3 Desktop)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RibbonIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tooltip: String,
    tint: Color = LocalContentColor.current,
    active: Boolean = false
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(tooltip)
            }
        },
        state = rememberTooltipState()
    ) {
        IconButton(
            onClick = onClick,
            colors = if(active) IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else IconButtonDefaults.iconButtonColors(),
            modifier = Modifier.size(32.dp) // Smaller header buttons
        ) {
            Icon(icon, contentDescription = tooltip, tint = if(active) MaterialTheme.colorScheme.onSecondaryContainer else tint, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun FilePanelRibbon(
    activeTab: String,
    onTabChange: (String) -> Unit,
    isRibbonExpanded: Boolean,
    onToggleRibbon: () -> Unit,

    // Header Actions
    isPartitionModeEnabled: Boolean,
    onPartitionModeToggle: (Boolean) -> Unit,
    onExportPdf: () -> Unit,

    // Navigation & Toggles
    onBack: () -> Unit,
    isMiddlePanelVisible: Boolean,
    onMiddlePanelToggle: () -> Unit,

    // Page Management Actions (Moved from Footer)
    onAddPage: () -> Unit,
    onDeletePage: () -> Unit,
    zoomPercent: Float,
    onZoomChange: (Float) -> Unit,

    // Original Params
    selectedPaperSize: PaperSize,
    onPaperSizeChange: (PaperSize) -> Unit,
    selectedLayoutType: PageLayoutType,
    onLayoutTypeChange: (PageLayoutType) -> Unit,
    activeConfig: ReportConfig,
    onConfigChange: (ReportConfig) -> Unit,
    activeItem: ReportPageItem?,
    pageAnnexureValues: MutableMap<String, String>,
    pageB1Values: MutableMap<String, String>,
    pageNumberOverrides: MutableMap<String, String>,
    activePageIndex: Int,
    applyAnnexureToAll: Boolean,
    onApplyAnnexureChange: (Boolean) -> Unit,
    applyB1ToAll: Boolean,
    onApplyB1Change: (Boolean) -> Unit,
    onApplyHeaderToAll: () -> Unit,
    renumberStartFrom: Float,
    onRenumberStartChange: (Float) -> Unit,
    onRenumberAll: () -> Unit,
    showPageNumber: Boolean,
    onShowPageNumberChange: (Boolean) -> Unit,
    applyMarginsToAll: Boolean,
    onApplyMarginsChange: (Boolean) -> Unit,
    applyBordersToAll: Boolean,
    onApplyBordersChange: (Boolean) -> Unit,
    onApplyStylesToAll: () -> Unit,
    isTextToolActive: Boolean,
    onTextToolToggle: () -> Unit,
    isSelectToolActive: Boolean,
    onSelectToolToggle: () -> Unit,
    hasGroupSelection: Boolean,
    onCopyGroup: () -> Unit,
    canPasteGroup: Boolean,
    onPasteGroup: () -> Unit,
    selectedElementId: String?,
    globalShapeStrokeColor: Color,
    onShapeStrokeColorChange: (Color) -> Unit,
    globalShapeFillColor: Color,
    onShapeFillColorChange: (Color) -> Unit,
    globalShapeStrokeWidth: Float,
    onShapeStrokeWidthChange: (Float) -> Unit,
    globalShapeRotation: Float,
    onShapeRotationChange: (Float) -> Unit,
    globalFontFamily: String,
    onFontFamilyChange: (String) -> Unit,
    globalTextSize: Float,
    onTextSizeChange: (Float) -> Unit,
    globalIsBold: Boolean,
    onBoldToggle: () -> Unit,
    globalIsItalic: Boolean,
    onItalicToggle: () -> Unit,
    globalIsUnderline: Boolean,
    onUnderlineToggle: () -> Unit,
    globalTextColor: Color,
    onTextColorChange: (Color) -> Unit,
    globalTextAlign: TextAlign,
    onTextAlignChange: (TextAlign) -> Unit,
    onAddElement: (ElementType) -> Unit
) {
    // State for B1 Expand Dialog
    var showB1ExpandDialog by remember { mutableStateOf(false) }

    if (showB1ExpandDialog && activeItem != null) {
        AlertDialog(
            onDismissRequest = { showB1ExpandDialog = false },
            title = { Text("Edit B1 Text") },
            text = {
                OutlinedTextField(
                    value = pageB1Values[activeItem.id] ?: "",
                    onValueChange = { pageB1Values[activeItem.id] = it },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    placeholder = { Text("Enter text for B1 box...") }
                )
            },
            confirmButton = {
                Button(onClick = { showB1ExpandDialog = false }) { Text("Done") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxWidth().background(Color(0xFFF3F2F1))) {
        // 1. TOP TOOLBAR
        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 4.dp), // Reduced Height
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Categories
            RibbonMenuButton("Home", activeTab == "Home") { onTabChange("Home"); if(!isRibbonExpanded) onToggleRibbon() }
            RibbonMenuButton("Layout", activeTab == "Layout") { onTabChange("Layout"); if(!isRibbonExpanded) onToggleRibbon() }
            RibbonMenuButton("Insert", activeTab == "Insert") { onTabChange("Insert"); if(!isRibbonExpanded) onToggleRibbon() }

            Spacer(Modifier.width(8.dp))

            // 2. Collapse Icon
            RibbonIconButton(
                onClick = onToggleRibbon,
                icon = if (isRibbonExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                tooltip = if (isRibbonExpanded) "Collapse Ribbon" else "Expand Ribbon"
            )

            Spacer(Modifier.weight(1f))

            // 3. Header Tools
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                RibbonIconButton(onClick = onBack, icon = Icons.AutoMirrored.Filled.ArrowBack, tooltip = "Back", tint = MaterialTheme.colorScheme.primary)
                VerticalDivider(modifier = Modifier.height(20.dp))
                RibbonIconButton(onClick = onAddPage, icon = Icons.Default.NoteAdd, tooltip = "Add Page", tint = Color(0xFF2E7D32))
                RibbonIconButton(onClick = onDeletePage, icon = Icons.Default.Delete, tooltip = "Del Page", tint = Color(0xFFC62828))
                VerticalDivider(modifier = Modifier.height(20.dp))
                // Compact Zoom
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RibbonIconButton(onClick = { if(zoomPercent>30) onZoomChange(zoomPercent-10) }, icon = Icons.Default.Remove, tooltip = "Out")
                    Text("${zoomPercent.toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
                    RibbonIconButton(onClick = { if(zoomPercent<200) onZoomChange(zoomPercent+10) }, icon = Icons.Default.Add, tooltip = "In")
                }
                VerticalDivider(modifier = Modifier.height(20.dp))
                RibbonIconButton(onClick = { onPartitionModeToggle(!isPartitionModeEnabled) }, icon = if(isPartitionModeEnabled) Icons.Default.Grid4x4 else Icons.Default.GridOff, tooltip = "Grid Mode", active = isPartitionModeEnabled, tint = if(isPartitionModeEnabled) MaterialTheme.colorScheme.primary else Color.Gray)
                RibbonIconButton(onClick = onExportPdf, icon = Icons.Default.Download, tooltip = "Export PDF", tint = Color(0xFFD32F2F))
            }
        }

        // 2. SUB-PANEL (Collapsible Content)
        AnimatedVisibility(
            visible = isRibbonExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(90.dp), // Static small height
                color = Color(0xFFF8F9FA),
                shadowElevation = 2.dp
            ) {
                Row(modifier = Modifier.fillMaxSize().padding(vertical = 4.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {

                    when (activeTab) {
                        // ================= HOME TAB =================
                        "Home" -> {
                            RibbonGroup("Tools") {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    RibbonLargeButton(icon = Icons.Default.AdsClick, label = "Select", color = if(isSelectToolActive) Color(0xFF2B579A) else Color.Gray, onClick = onSelectToolToggle)
                                    RibbonLargeButton(icon = Icons.Default.TextFields, label = "Text", color = if(isTextToolActive) Color(0xFF2B579A) else Color.Gray, onClick = onTextToolToggle)
                                }
                            }

                            VerticalDivider(Modifier.padding(vertical = 8.dp))

                            if (selectedElementId != null) {
                                RibbonGroup("Shape Style") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Row { Text("Stroke", fontSize=9.sp, modifier=Modifier.width(35.dp)); RibbonColorPicker(globalShapeStrokeColor) { onShapeStrokeColorChange(it) } }
                                            Row { Text("Fill", fontSize=9.sp, modifier=Modifier.width(35.dp)); RibbonColorPicker(globalShapeFillColor, true) { onShapeFillColorChange(it) } }
                                        }
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            RibbonNumberInput("Width", globalShapeStrokeWidth) { onShapeStrokeWidthChange(it) }
                                            RibbonNumberInput("Angle", globalShapeRotation) { onShapeRotationChange(it) }
                                        }
                                    }
                                }
                            } else {
                                RibbonGroup("Font") {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            val styleOptions = listOf("Arial", "Times New Roman", "Courier New", "Verdana", "Georgia", "Impact")
                                            RibbonDropdown(globalFontFamily, Icons.Default.FontDownload, styleOptions) { index -> onFontFamilyChange(styleOptions[index]) }
                                            Spacer(Modifier.width(4.dp))
                                            RibbonNumberInput("", globalTextSize) { onTextSizeChange(it) }
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            RibboniconButton(Icons.Default.FormatBold, "", globalIsBold) { onBoldToggle() }
                                            RibboniconButton(Icons.Default.FormatItalic, "", globalIsItalic) { onItalicToggle() }
                                            RibboniconButton(Icons.Default.FormatUnderlined, "", globalIsUnderline) { onUnderlineToggle() }
                                            Spacer(Modifier.width(8.dp))
                                            RibbonColorPicker(globalTextColor) { onTextColorChange(it) }
                                        }
                                    }
                                }
                                VerticalDivider(Modifier.padding(vertical = 8.dp))
                                RibbonGroup("Paragraph") {
                                    Row {
                                        RibboniconButton(Icons.AutoMirrored.Filled.FormatAlignLeft, "", globalTextAlign == TextAlign.Left) { onTextAlignChange(TextAlign.Left) }
                                        RibboniconButton(Icons.Default.FormatAlignCenter, "", globalTextAlign == TextAlign.Center) { onTextAlignChange(TextAlign.Center) }
                                        RibboniconButton(Icons.AutoMirrored.Filled.FormatAlignRight, "", globalTextAlign == TextAlign.Right) { onTextAlignChange(TextAlign.Right) }
                                    }
                                }
                            }

                            VerticalDivider(Modifier.padding(vertical = 8.dp))

                            // Compact Shape Grid
                            RibbonGroup("Shapes") {
                                val shapes = ElementType.entries
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    for (i in 0 until 2) { // 2 Rows
                                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            for (j in 0 until 6) { // 6 Cols
                                                val idx = i * 6 + j
                                                if (idx < shapes.size) {
                                                    val type = shapes[idx]
                                                    Box(
                                                        modifier = Modifier
                                                            .size(20.dp) // Fixed small size
                                                            .background(Color.White, RoundedCornerShape(2.dp))
                                                            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(2.dp))
                                                            .clickable { onAddElement(type) }
                                                            .padding(3.dp)
                                                    ) {
                                                        ElementRenderer(remember(type) { ReportElement(type = type, strokeWidth = 1f) })
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ================= LAYOUT TAB =================
                        "Layout" -> {
                            RibbonGroup("Setup") {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    RibbonDropdown("Sz: ${selectedPaperSize.name}", Icons.Default.Description, PaperSize.entries.map { it.name }) { onPaperSizeChange(PaperSize.entries[it]) }
                                    RibbonDropdown("Tpl: ${selectedLayoutType.displayName}", Icons.AutoMirrored.Filled.ViewQuilt, PageLayoutType.entries.map { it.displayName }) { onLayoutTypeChange(PageLayoutType.entries[it]) }
                                }
                            }

                            VerticalDivider(Modifier.padding(vertical = 8.dp))

                            if (selectedLayoutType == PageLayoutType.ENGINEERING_STD) {
                                RibbonGroup("Header") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            val options = listOf("X-Section", "L-Section")
                                            RibbonDropdown(activeConfig.legendType, Icons.Default.LegendToggle, options) { idx -> onConfigChange(activeConfig.copy(legendType = options[idx])) }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(checked = applyAnnexureToAll, onCheckedChange = onApplyAnnexureChange, modifier = Modifier.size(12.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("All", fontSize = 9.sp)
                                            }
                                        }
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            // Compact Inputs for Annex/B1
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Anx:", fontSize=9.sp, modifier = Modifier.width(25.dp))
                                                BasicTextField(
                                                    value = pageAnnexureValues[activeItem?.id] ?: "",
                                                    onValueChange = { if(activeItem!=null) pageAnnexureValues[activeItem.id] = it },
                                                    textStyle = TextStyle(fontSize = 10.sp),
                                                    modifier = Modifier.width(60.dp).background(Color.White).border(1.dp, Color.LightGray).padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("B1:", fontSize=9.sp, modifier = Modifier.width(25.dp))
                                                BasicTextField(
                                                    value = pageB1Values[activeItem?.id] ?: "",
                                                    onValueChange = { if(activeItem!=null) pageB1Values[activeItem.id] = it },
                                                    textStyle = TextStyle(fontSize = 10.sp),
                                                    modifier = Modifier.width(60.dp).background(Color.White).border(1.dp, Color.LightGray).padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                                Spacer(Modifier.width(2.dp))
                                                // EXPAND BUTTON FOR B1
                                                IconButton(onClick = { showB1ExpandDialog = true }, modifier = Modifier.size(16.dp)) {
                                                    Icon(Icons.Default.OpenInFull, null, tint = Color.Gray)
                                                }
                                            }
                                        }
                                        Button(onClick = onApplyHeaderToAll, modifier = Modifier.height(40.dp), contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(4.dp)) { Text("Apply", fontSize = 10.sp) }
                                    }
                                }
                                VerticalDivider(Modifier.padding(vertical = 8.dp))
                            }
                        }

                        // ================= INSERT TAB (MODIFIED) =================
                        "Insert" -> {
                            RibbonGroup("Panels") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // "Graphs" button REMOVED from here
                                    RibbonLargeButton(icon = Icons.Default.Image, label = "Editor", color = if (isMiddlePanelVisible) Color(0xFF2B579A) else Color.Gray, onClick = onMiddlePanelToggle)
                                }
                            }

                            VerticalDivider(Modifier.padding(vertical = 8.dp))

                            // 1. MARGINS GROUP (Expanded & Aligned)
                            RibbonGroup("Margins") {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        // Top
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Top:", fontSize = 9.sp, modifier = Modifier.width(22.dp))
                                            RibbonNumberInput("", activeConfig.marginTop) { onConfigChange(activeConfig.copy(marginTop = it)) }
                                        }
                                        // Bottom
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Bot:", fontSize = 9.sp, modifier = Modifier.width(22.dp))
                                            RibbonNumberInput("", activeConfig.marginBottom) { onConfigChange(activeConfig.copy(marginBottom = it)) }
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        // Left
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Lft:", fontSize = 9.sp, modifier = Modifier.width(22.dp))
                                            RibbonNumberInput("", activeConfig.marginLeft) { onConfigChange(activeConfig.copy(marginLeft = it)) }
                                        }
                                        // Right
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Rgt:", fontSize = 9.sp, modifier = Modifier.width(22.dp))
                                            RibbonNumberInput("", activeConfig.marginRight) { onConfigChange(activeConfig.copy(marginRight = it)) }
                                        }
                                    }
                                }
                            }

                            VerticalDivider(Modifier.padding(vertical = 8.dp))

                            // 2. BORDERS GROUP (Expanded - No Apply Button)
                            RibbonGroup("Borders") {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    // Row 1: Outer
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(activeConfig.showOuterBorder, { onConfigChange(activeConfig.copy(showOuterBorder = it)) }, Modifier.size(12.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Outer:", fontSize=9.sp, modifier = Modifier.width(30.dp))
                                        RibbonNumberInput("", activeConfig.outerThickness) { onConfigChange(activeConfig.copy(outerThickness = it)) }
                                    }
                                    // Row 2: Inner & Gap
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(activeConfig.showInnerBorder, { onConfigChange(activeConfig.copy(showInnerBorder = it)) }, Modifier.size(12.dp), enabled = activeConfig.showOuterBorder)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Inner:", fontSize=9.sp, modifier = Modifier.width(30.dp))
                                        RibbonNumberInput("", activeConfig.innerThickness, activeConfig.showOuterBorder) { onConfigChange(activeConfig.copy(innerThickness = it)) }
                                        Spacer(Modifier.width(4.dp))
                                        Text("Gap:", fontSize=9.sp)
                                        RibbonNumberInput("", activeConfig.borderGap, activeConfig.showOuterBorder) { onConfigChange(activeConfig.copy(borderGap = it)) }
                                    }
                                }
                            }

                            VerticalDivider(Modifier.padding(vertical = 8.dp))

                            // 3. APPLY SETTINGS GROUP (New Combined Logic)
                            RibbonGroup("Apply Settings") {
                                val options = listOf("Both", "Margins Only", "Borders Only")
                                var selectedIndex by remember { mutableStateOf(0) }

                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    RibbonDropdown(options[selectedIndex], Icons.Default.DoneAll, options) { selectedIndex = it }

                                    Button(
                                        onClick = {
                                            when(selectedIndex) {
                                                0 -> { onApplyMarginsChange(true); onApplyBordersChange(true) } // Both
                                                1 -> { onApplyMarginsChange(true); onApplyBordersChange(false) } // Margins Only
                                                2 -> { onApplyMarginsChange(false); onApplyBordersChange(true) } // Borders Only
                                            }
                                            onApplyStylesToAll()
                                        },
                                        modifier = Modifier.height(28.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        shape = RoundedCornerShape(4.dp)
                                    ) { Text("Apply All Pages", fontSize = 10.sp) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RibbonMenuButton(text: String, isActive: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (isActive) Color(0xFF2B579A) else Color.DarkGray,
            containerColor = if (isActive) Color.White else Color.Transparent
        ),
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        modifier = Modifier.height(32.dp) // Smaller tab height
    ) {
        Text(text, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal, fontSize = 11.sp)
    }
}