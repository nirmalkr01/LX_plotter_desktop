import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RibbonTab(text: String, isActive: Boolean, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, fontSize = 13.sp, fontWeight = if(isActive) FontWeight.Bold else FontWeight.Normal, color = if(isActive) Color(0xFF2B579A) else Color.Black)
        if (isActive) Box(Modifier.height(3.dp).width(40.dp).background(Color(0xFF2B579A), RoundedCornerShape(topStart=2.dp, topEnd=2.dp)))
    }
}

// Fixed Ribbon Group with Label at bottom
@Composable
fun RibbonGroup(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxHeight().padding(horizontal = 4.dp)
    ) {
        Box(
            modifier = Modifier.weight(1f).wrapContentWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                content()
            }
        }
        Text(
            text = label,
            fontSize = 9.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 2.dp)
        )
    }
}

// Large Button (Icon Top, Text Bottom)
@Composable
fun RibbonLargeButton(icon: ImageVector, label: String, color: Color = Color(0xFF2B579A), onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(50.dp) // Static width
            .fillMaxHeight(0.9f)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 10.sp, textAlign = TextAlign.Center, lineHeight = 10.sp, color = color, maxLines = 1)
    }
}

// Small Row Button (Icon Left, Text Right optional)
@Composable
fun RibboniconButton(icon: ImageVector, label: String, isActive: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(24.dp)
            .background(if(isActive) Color(0xFFE1EDFD) else Color.Transparent, RoundedCornerShape(2.dp))
            .border(1.dp, if(isActive) Color(0xFFA4C6F8) else Color.Transparent, RoundedCornerShape(2.dp))
            .clip(RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = if(isActive) Color(0xFF2B579A) else Color.Black)
        if(label.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 10.sp, color = if(isActive) Color(0xFF2B579A) else Color.Black)
        }
    }
}

@Composable
fun RibbonTextButton(text: String, isActive: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(24.dp)
            .background(if(isActive) Color(0xFFE1EDFD) else Color.Transparent, RoundedCornerShape(2.dp))
            .border(1.dp, if(isActive) Color(0xFFA4C6F8) else Color.Transparent, RoundedCornerShape(2.dp))
            .clip(RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if(isActive) Color(0xFF2B579A) else Color.Black)
    }
}

@Composable
fun RibbonDropdown(label: String, icon: ImageVector, options: List<String>, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .height(24.dp)
            .border(1.dp, Color.LightGray, RoundedCornerShape(2.dp))
            .clip(RoundedCornerShape(2.dp))
            .clickable { expanded = true }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(14.dp), tint = Color(0xFF2B579A))
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 10.sp)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(12.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { idx, opt ->
                DropdownMenuItem(
                    text = { Text(opt, fontSize = 12.sp) },
                    onClick = { onSelect(idx); expanded = false },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp)
                )
            }
        }
    }
}

@Composable
fun RibbonNumberInput(label: String, value: Float, enabled: Boolean = true, onChange: (Float) -> Unit) {
    var textValue by remember(value) { mutableStateOf(if(value == 0f) "" else value.toString().removeSuffix(".0")) }
    LaunchedEffect(value) {
        val strVal = value.toString().removeSuffix(".0")
        if(textValue != strVal && textValue.toFloatOrNull() != value) {
            textValue = if(value == 0f) "" else strVal
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(24.dp)) {
        if(label.isNotEmpty()) Text(label, fontSize = 10.sp, color = if(enabled) Color.DarkGray else Color.LightGray, modifier = Modifier.padding(end = 4.dp))
        BasicTextField(
            value = textValue,
            enabled = enabled,
            onValueChange = { str ->
                textValue = str
                val num = str.toFloatOrNull()
                if (num != null) onChange(num)
            },
            textStyle = TextStyle(fontSize = 10.sp, textAlign = TextAlign.Center),
            singleLine = true,
            modifier = Modifier
                .width(40.dp)
                .fillMaxHeight()
                .background(if(enabled) Color.White else Color(0xFFEEEEEE), RoundedCornerShape(2.dp))
                .border(1.dp, Color.LightGray, RoundedCornerShape(2.dp))
                .padding(top = 5.dp) // Visual centering adjustment
        )
    }
}

@Composable
fun RibbonColorPicker(color: Color, allowTransparent: Boolean = false, onColorSelected: (Color) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(color, RoundedCornerShape(2.dp))
                .border(1.dp, Color.LightGray, RoundedCornerShape(2.dp))
                .clickable { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val colors = if(allowTransparent) listOf(Color.Transparent, Color.Black, Color.Red, Color.Blue, Color.Green, Color.Gray, Color.Yellow, Color.Cyan, Color.Magenta)
            else listOf(Color.Black, Color.Red, Color.Blue, Color.Green, Color.Gray, Color.Yellow, Color.Cyan, Color.Magenta)

            // Simple grid layout for colors in dropdown
            val rows = colors.chunked(3)
            rows.forEach { rowColors ->
                Row {
                    rowColors.forEach { c ->
                        DropdownMenuItem(
                            text = {
                                if(c == Color.Transparent) Text("None", fontSize=10.sp, color=Color.Gray)
                                else Box(Modifier.size(20.dp).background(c).border(1.dp, Color.Gray))
                            },
                            onClick = { onColorSelected(c); expanded = false },
                            contentPadding = PaddingValues(4.dp),
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
        }
    }
}