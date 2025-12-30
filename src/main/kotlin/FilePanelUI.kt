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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FilePanelRibbon(
    activeTab: String,
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
    Surface(
        modifier = Modifier.fillMaxWidth().height(110.dp),
        color = Color(0xFFF8F9FA),
        shadowElevation = 2.dp
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {

            when (activeTab) {
                "Page Layout" -> {
                    RibbonGroup("Paper") {
                        RibbonDropdown("Size: ${selectedPaperSize.name}", Icons.Default.Description, PaperSize.entries.map { it.name }) { onPaperSizeChange(PaperSize.entries[it]) }
                    }
                    VerticalDivider(Modifier.padding(vertical = 8.dp, horizontal = 8.dp))
                    RibbonGroup("Templates") {
                        RibbonDropdown("Layout: ${selectedLayoutType.displayName}", Icons.AutoMirrored.Filled.ViewQuilt, PageLayoutType.entries.map { it.displayName }) { onLayoutTypeChange(PageLayoutType.entries[it]) }
                    }
                    VerticalDivider(Modifier.padding(vertical = 8.dp, horizontal = 8.dp))

                    if (selectedLayoutType == PageLayoutType.ENGINEERING_STD) {
                        RibbonGroup("Header Info (Current)") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Legend:", fontSize = 10.sp, color = Color.Gray)
                                    val options = listOf("X-Section", "L-Section")
                                    RibbonDropdown(activeConfig.legendType, Icons.Default.LegendToggle, options) { idx ->
                                        onConfigChange(activeConfig.copy(legendType = options[idx]))
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Annex (X)", fontSize = 10.sp, color = Color.Gray)
                                    if (activeItem != null) {
                                        val currentVal = pageAnnexureValues[activeItem.id] ?: ""
                                        BasicTextField(
                                            value = currentVal,
                                            onValueChange = { pageAnnexureValues[activeItem.id] = it },
                                            textStyle = TextStyle(fontSize = 12.sp, textAlign = TextAlign.Center),
                                            modifier = Modifier.width(50.dp).height(24.dp).background(Color.White, RoundedCornerShape(4.dp)).border(1.dp, Color.Gray, RoundedCornerShape(4.dp)).wrapContentHeight(Alignment.CenterVertically)
                                        )
                                    } else Text("-", fontSize = 12.sp)
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("B1 Text", fontSize = 10.sp, color = Color.Gray)
                                    if (activeItem != null) {
                                        val currentB1 = pageB1Values[activeItem.id] ?: ""
                                        BasicTextField(
                                            value = currentB1,
                                            onValueChange = { pageB1Values[activeItem.id] = it },
                                            textStyle = TextStyle(fontSize = 12.sp, textAlign = TextAlign.Center),
                                            modifier = Modifier.width(100.dp).height(24.dp).background(Color.White, RoundedCornerShape(4.dp)).border(1.dp, Color.Gray, RoundedCornerShape(4.dp)).wrapContentHeight(Alignment.CenterVertically)
                                        )
                                    } else Text("-", fontSize = 12.sp)
                                }
                            }
                        }
                        VerticalDivider(Modifier.padding(vertical = 8.dp, horizontal = 8.dp))

                        RibbonGroup("Apply to All Pages") {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = applyAnnexureToAll, onCheckedChange = onApplyAnnexureChange, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp)); Text("Annexure", fontSize = 10.sp)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = applyB1ToAll, onCheckedChange = onApplyB1Change, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp)); Text("B1 Text", fontSize = 10.sp)
                                    }
                                }
                                Button(
                                    onClick = onApplyHeaderToAll,
                                    modifier = Modifier.height(24.dp).fillMaxWidth(),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("Apply Now", fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    VerticalDivider(Modifier.padding(vertical = 8.dp, horizontal = 8.dp))

                    RibbonGroup("Page Numbering") {
                        val isBlank = selectedLayoutType == PageLayoutType.BLANK
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Current:", fontSize = 10.sp, color = Color.Gray)
                                Spacer(Modifier.width(4.dp))
                                if(activeItem != null) {
                                    val curr = pageNumberOverrides[activeItem.id] ?: "${activePageIndex + 1}"
                                    BasicTextField(
                                        value = curr,
                                        onValueChange = { pageNumberOverrides[activeItem.id] = it },
                                        textStyle = TextStyle(fontSize = 11.sp, textAlign = TextAlign.Center),
                                        modifier = Modifier.width(30.dp).background(Color.White).border(1.dp, Color.Gray).padding(1.dp)
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Start All:", fontSize = 10.sp, color = Color.Gray)
                                Spacer(Modifier.width(4.dp))
                                RibbonNumberInput("", renumberStartFrom) { onRenumberStartChange(it) }
                                Spacer(Modifier.width(4.dp))
                                Button(
                                    onClick = onRenumberAll,
                                    modifier = Modifier.height(20.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Text("Go", fontSize = 9.sp)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(if(isBlank) 1f else 0.4f)) {
                                Checkbox(checked = showPageNumber, onCheckedChange = if(isBlank) { { onShowPageNumberChange(it) } } else null, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Visible (Blank)", fontSize = 9.sp)
                            }
                        }
                    }
                }

                "Borders" -> {
                    RibbonGroup("Margins (mm)") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row {
                                RibbonNumberInput("Top", activeConfig.marginTop) { onConfigChange(activeConfig.copy(marginTop = it)) }
                                Spacer(Modifier.width(4.dp))
                                RibbonNumberInput("Bot", activeConfig.marginBottom) { onConfigChange(activeConfig.copy(marginBottom = it)) }
                            }
                            Row {
                                RibbonNumberInput("Left", activeConfig.marginLeft) { onConfigChange(activeConfig.copy(marginLeft = it)) }
                                Spacer(Modifier.width(4.dp))
                                RibbonNumberInput("Rgt", activeConfig.marginRight) { onConfigChange(activeConfig.copy(marginRight = it)) }
                            }
                        }
                    }
                    VerticalDivider(Modifier.padding(vertical = 8.dp, horizontal = 8.dp))
                    RibbonGroup("Outer Border") {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(activeConfig.showOuterBorder, { onConfigChange(activeConfig.copy(showOuterBorder = it)) }, Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Show", fontSize=11.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RibbonNumberInput("Thick", activeConfig.outerThickness) { onConfigChange(activeConfig.copy(outerThickness = it)) }
                                Spacer(Modifier.width(8.dp))
                                RibbonColorPicker(activeConfig.outerColor) { onConfigChange(activeConfig.copy(outerColor = it)) }
                            }
                        }
                    }
                    VerticalDivider(Modifier.padding(vertical = 8.dp, horizontal = 8.dp))
                    val innerEnabled = activeConfig.showOuterBorder
                    RibbonGroup("Inner Border") {
                        Column(modifier = Modifier.alpha(if(innerEnabled) 1f else 0.4f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = activeConfig.showInnerBorder, onCheckedChange = if(innerEnabled) { { onConfigChange(activeConfig.copy(showInnerBorder = it)) } } else null, enabled = innerEnabled, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Show", fontSize=11.sp)
                                Spacer(Modifier.width(8.dp))
                                RibbonNumberInput("Gap", activeConfig.borderGap, innerEnabled) { onConfigChange(activeConfig.copy(borderGap = it)) }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RibbonNumberInput("Thick", activeConfig.innerThickness, innerEnabled) { onConfigChange(activeConfig.copy(innerThickness = it)) }
                                Spacer(Modifier.width(8.dp))
                                RibbonColorPicker(activeConfig.innerColor) { if(innerEnabled) onConfigChange(activeConfig.copy(innerColor = it)) }
                            }
                        }
                    }
                    VerticalDivider(Modifier.padding(vertical = 8.dp, horizontal = 8.dp))

                    RibbonGroup("Apply to All Pages") {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = applyMarginsToAll, onCheckedChange = onApplyMarginsChange, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp)); Text("Margins", fontSize = 10.sp)
                                }
                                Spacer(Modifier.width(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = applyBordersToAll, onCheckedChange = onApplyBordersChange, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp)); Text("Borders", fontSize = 10.sp)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = onApplyStylesToAll,
                                modifier = Modifier.height(24.dp).fillMaxWidth(),
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text("Apply Styles Now", fontSize = 10.sp)
                            }
                        }
                    }
                }

                "Annotation" -> {
                    RibbonGroup("Tools") {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // SELECT TOOL
                            Column(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(if(isSelectToolActive) Color(0xFFE1EDFD) else Color.Transparent).clickable {
                                onSelectToolToggle()
                            }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.AdsClick, null, tint = Color(0xFF2B579A), modifier = Modifier.size(20.dp))
                                Text("Select", fontSize = 10.sp, color = Color(0xFF2B579A))
                            }

                            // TEXT TOOL
                            Column(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(if(isTextToolActive) Color(0xFFE1EDFD) else Color.Transparent).clickable {
                                onTextToolToggle()
                            }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.TextFields, null, tint = Color(0xFF2B579A), modifier = Modifier.size(20.dp))
                                Text("Text", fontSize = 10.sp, color = Color(0xFF2B579A))
                            }
                        }
                    }

                    // GROUP ACTIONS
                    VerticalDivider(Modifier.padding(vertical = 8.dp, horizontal = 8.dp))
                    if(hasGroupSelection) {
                        RibbonGroup("Group Actions") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(onClick = onCopyGroup, modifier = Modifier.height(24.dp), contentPadding = PaddingValues(horizontal=8.dp)) { Text("Copy Group", fontSize=9.sp) }
                            }
                        }
                    }

                    if(canPasteGroup) {
                        VerticalDivider(Modifier.padding(vertical = 8.dp, horizontal = 8.dp))
                        RibbonGroup("Clipboard") {
                            Button(onClick = onPasteGroup, modifier = Modifier.height(24.dp), contentPadding = PaddingValues(horizontal=8.dp)) { Text("Paste Group", fontSize=9.sp) }
                        }
                    }

                    VerticalDivider(Modifier.padding(vertical = 8.dp, horizontal = 8.dp))

                    if(selectedElementId != null) {
                        RibbonGroup("Shape Style") {
                            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Stroke:", fontSize=9.sp); Spacer(Modifier.width(4.dp))
                                    RibbonColorPicker(globalShapeStrokeColor) { onShapeStrokeColorChange(it) }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Fill:", fontSize=9.sp); Spacer(Modifier.width(4.dp))
                                    RibbonColorPicker(globalShapeFillColor, allowTransparent = true) { onShapeFillColorChange(it) }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Width:", fontSize=9.sp); Spacer(Modifier.width(4.dp))
                                    RibbonNumberInput("", globalShapeStrokeWidth) { onShapeStrokeWidthChange(it) }
                                }
                            }
                        }
                        VerticalDivider(Modifier.padding(vertical = 8.dp, horizontal = 8.dp))
                        RibbonGroup("Orientation") {
                            Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                Row {
                                    IconButton(onClick = { onShapeRotationChange(globalShapeRotation - 90f) }, modifier = Modifier.size(24.dp)) { Icon(Icons.AutoMirrored.Filled.RotateLeft, "Rotate Left", modifier=Modifier.size(16.dp)) }
                                    Spacer(Modifier.width(4.dp))
                                    IconButton(onClick = { onShapeRotationChange(globalShapeRotation + 90f) }, modifier = Modifier.size(24.dp)) { Icon(Icons.AutoMirrored.Filled.RotateRight, "Rotate Right", modifier=Modifier.size(16.dp)) }
                                }
                                Spacer(Modifier.height(4.dp))
                                RibbonNumberInput("Angle", globalShapeRotation) { onShapeRotationChange(it) }
                            }
                        }
                        VerticalDivider(Modifier.padding(vertical = 8.dp, horizontal = 8.dp))
                    }

                    if(selectedElementId == null) {
                        RibbonGroup("Font") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val styleOptions = listOf("Arial", "Times New Roman", "Courier New", "Verdana", "Georgia", "Comic Sans MS", "Impact")
                                    RibbonDropdown(globalFontFamily, Icons.Default.FontDownload, styleOptions) { index -> onFontFamilyChange(styleOptions[index]) }
                                    Spacer(Modifier.width(8.dp))
                                    RibbonNumberInput("Size", globalTextSize) { onTextSizeChange(it) }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RibbonTextButton("B", globalIsBold) { onBoldToggle() }
                                    Spacer(Modifier.width(4.dp))
                                    RibbonTextButton("I", globalIsItalic) { onItalicToggle() }
                                    Spacer(Modifier.width(4.dp))
                                    RibbonTextButton("U", globalIsUnderline) { onUnderlineToggle() }
                                    Spacer(Modifier.width(8.dp))
                                    RibbonColorPicker(globalTextColor) { onTextColorChange(it) }
                                }
                            }
                        }

                        VerticalDivider(Modifier.padding(vertical = 8.dp, horizontal = 8.dp))

                        RibbonGroup("Paragraph") {
                            Row {
                                RibboniconButton(Icons.AutoMirrored.Filled.FormatAlignLeft, "", globalTextAlign == TextAlign.Left) { onTextAlignChange(TextAlign.Left) }
                                RibboniconButton(Icons.Default.FormatAlignCenter, "", globalTextAlign == TextAlign.Center) { onTextAlignChange(TextAlign.Center) }
                                RibboniconButton(Icons.AutoMirrored.Filled.FormatAlignRight, "", globalTextAlign == TextAlign.Right) { onTextAlignChange(TextAlign.Right) }
                                RibboniconButton(Icons.Default.FormatAlignJustify, "", globalTextAlign == TextAlign.Justify) { onTextAlignChange(TextAlign.Justify) }
                            }
                        }
                        VerticalDivider(Modifier.padding(vertical = 8.dp, horizontal = 8.dp))
                    }

                    // Page Elements
                    RibbonGroup("Page Elements") {
                        val isBlank = selectedLayoutType == PageLayoutType.BLANK
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.alpha(if(isBlank) 1f else 0.4f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = showPageNumber, onCheckedChange = if(isBlank) { { onShowPageNumberChange(it) } } else null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Page No.", fontSize = 11.sp)
                            }
                        }
                    }

                    VerticalDivider(Modifier.padding(vertical = 8.dp, horizontal = 8.dp))

                    // Shapes as Paint-like grid
                    RibbonGroup("Shapes") {
                        val shapes = ElementType.entries
                        Column {
                            for (i in 0 until 3) {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    for (j in 0 until 4) {
                                        val idx = i * 4 + j
                                        if (idx < shapes.size) {
                                            val type = shapes[idx]
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .background(Color.White, RoundedCornerShape(2.dp))
                                                    .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(2.dp))
                                                    .clickable { onAddElement(type) }
                                                    .padding(4.dp)
                                            ) {
                                                val previewEl = remember(type) { ReportElement(type = type, strokeWidth = 1.5f) }
                                                ElementRenderer(previewEl)
                                            }
                                        }
                                    }
                                }
                                if (i < 2) Spacer(Modifier.height(2.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
