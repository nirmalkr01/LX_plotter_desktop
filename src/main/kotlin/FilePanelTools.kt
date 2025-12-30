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

@Composable
fun RibbonGroup(label: String, content: @Composable RowScope.() -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxHeight().padding(horizontal = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, content = content)
        Text(label, fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 2.dp))
    }
}

@Composable
fun RibbonLargeButton(icon: ImageVector, label: String, color: Color = Color(0xFF2B579A), onClick: () -> Unit) {
    Column(modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = color, modifier = Modifier.size(24.dp)); Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 11.sp, color = color)
    }
}

@Composable
fun RibboniconButton(icon: ImageVector, label: String, isActive: Boolean, onClick: () -> Unit) {
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
fun RibbonDropdown(label: String, icon: ImageVector, options: List<String>, onSelect: (Int) -> Unit) {
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
        if(label.isNotEmpty()) Text(label, fontSize = 11.sp, color = if(enabled) Color.DarkGray else Color.LightGray, modifier = Modifier.width(42.dp), maxLines = 1)
        BasicTextField(value = textValue, enabled = enabled, onValueChange = { str -> textValue = str; val num = str.toFloatOrNull(); if (num != null) onChange(num) }, textStyle = TextStyle(fontSize = 11.sp, textAlign = TextAlign.Center), modifier = Modifier.width(40.dp).background(if(enabled) Color.White else Color(0xFFEEEEEE), RoundedCornerShape(2.dp)).border(1.dp, Color.LightGray, RoundedCornerShape(2.dp)).padding(3.dp))
    }
}

@Composable
fun RibbonColorPicker(color: Color, allowTransparent: Boolean = false, onColorSelected: (Color) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(modifier = Modifier.size(16.dp).background(color, RoundedCornerShape(4.dp)).border(1.dp, Color.Black, RoundedCornerShape(4.dp)).clickable { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val colors = if(allowTransparent) listOf(Color.Transparent, Color.Black, Color.Red, Color.Blue, Color.Green, Color.Gray, Color.Yellow, Color.Cyan, Color.Magenta)
            else listOf(Color.Black, Color.Red, Color.Blue, Color.Green, Color.Gray, Color.Yellow, Color.Cyan, Color.Magenta)
            colors.forEach { c ->
                DropdownMenuItem(text = {
                    if(c == Color.Transparent) Text("None", fontSize=11.sp, color=Color.Gray)
                    else Box(Modifier.size(20.dp).background(c))
                }, onClick = { onColorSelected(c); expanded = false })
            }
        }
    }
}
