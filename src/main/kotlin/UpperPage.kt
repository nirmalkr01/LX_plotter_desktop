import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun WordStyleHomePage(
    recentFiles: List<String>,
    onOpenFile: (File) -> Unit,
    onNewProject: () -> Unit,
    onDeleteFile: (String) -> Unit,
    onShowHelp: () -> Unit
) {
    // Track file pending deletion
    var fileToDelete by remember { mutableStateOf<String?>(null) }

    // Dynamic Greeting Logic
    val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (currentHour) {
        in 0..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    // Separate Recent (Today) and Older files
    // We use a derived state so this recalculates if recentFiles changes
    val (recentList, olderList) = remember(recentFiles) {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val files = recentFiles.map { File(it) }.filter { it.exists() }

        val recent = mutableListOf<File>()
        val older = mutableListOf<File>()

        files.forEach { file ->
            if (file.lastModified() >= todayStart) {
                recent.add(file)
            } else {
                older.add(file)
            }
        }
        // Sort by last modified descending
        Pair(
            recent.sortedByDescending { it.lastModified() },
            older.sortedByDescending { it.lastModified() }
        )
    }

    // Fixed narrow width for "Rail" style sidebar
    val sidebarWidth = 72.dp

    // Delete Confirmation Dialog
    if (fileToDelete != null) {
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Confirm Deletion", fontWeight = FontWeight.Bold) },
            text = { Text("Do you want to delete this item from history? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteFile(fileToDelete!!)
                        fileToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Row(modifier = Modifier.fillMaxSize().background(Color(0xFFF3F2F1))) {
        // --- LEFT SIDEBAR (Navigation Rail Style) ---
        Column(
            modifier = Modifier
                .width(sidebarWidth)
                .fillMaxHeight()
                .background(Color(0xFFF3F2F1))
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Navigation Items (Home is selected)
            SidebarItem("Home", Icons.Default.Home, true, onClick = {})

            // Help Button just below Home
            SidebarItem("Help", Icons.Default.HelpOutline, false, onClick = onShowHelp)

            Spacer(Modifier.weight(1f))

            // Bottom Items (Disabled)
            SidebarItem("Account", Icons.Default.Person, false, enabled = false, onClick = {})
            SidebarItem("Options", Icons.Default.Settings, false, enabled = false, onClick = {})
        }

        // --- MAIN CONTENT AREA ---
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color.White)
                .padding(horizontal = 32.dp, vertical = 24.dp)
        ) {
            // Dynamic Greeting
            Text(greeting, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
            Spacer(Modifier.height(24.dp))

            // --- TOP SECTION: NEW ---
            Text("New", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.Black)
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                // Blank Document Card (Triggers Upload)
                NewProjectCard(
                    title = "Blank Project",
                    subtitle = "Import CSV data",
                    icon = Icons.Default.PostAdd,
                    enabled = true,
                    onClick = onNewProject
                )

                // Placeholder Template (Disabled)
                NewProjectCard(
                    title = "Demo Template",
                    subtitle = "Example L-Section",
                    icon = Icons.Default.ViewQuilt,
                    enabled = false,
                    onClick = {}
                )
            }

            Spacer(Modifier.height(40.dp))

            // --- BOTTOM SECTION: FILE LIST ---

            // List Header
            Row(Modifier.padding(vertical = 8.dp)) {
                Text("Name", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.weight(1f))
                Text("Last modified", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.width(120.dp))
                Spacer(Modifier.width(40.dp)) // Space for delete icon
            }
            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val listState = rememberLazyListState()

                if (recentList.isEmpty() && olderList.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No recent files", color = Color.Gray)
                    }
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(end = 12.dp)) {

                        // Recent Section (Today)
                        if (recentList.isNotEmpty()) {
                            item {
                                Text(
                                    "Recent",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                )
                            }
                            items(recentList) { file ->
                                RecentFileRow(
                                    file = file,
                                    onOpenFile = {
                                        // Update timestamp to now (pushed to top of recent by Main.kt logic)
                                        if(file.exists()) {
                                            file.setLastModified(System.currentTimeMillis())
                                            onOpenFile(file)
                                        }
                                    },
                                    onDelete = { fileToDelete = file.absolutePath }
                                )
                            }
                        }

                        // Older Section
                        if (olderList.isNotEmpty()) {
                            item {
                                Text(
                                    "Older",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                )
                            }
                            items(olderList) { file ->
                                RecentFileRow(
                                    file = file,
                                    onOpenFile = {
                                        // Update timestamp to now so it moves to "Recent" next time
                                        if(file.exists()) {
                                            file.setLastModified(System.currentTimeMillis())
                                            onOpenFile(file)
                                        }
                                    },
                                    onDelete = { fileToDelete = file.absolutePath }
                                )
                            }
                        }
                    }
                }

                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState = listState)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SidebarItem(
    text: String,
    icon: ImageVector,
    isSelected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val content = @Composable {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                null,
                modifier = Modifier.size(24.dp),
                tint = if (!enabled) Color.LightGray else if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text,
                fontSize = 10.sp,
                color = if (!enabled) Color.LightGray else if (isSelected) MaterialTheme.colorScheme.primary else Color.Black,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }

    if (!enabled) {
        TooltipArea(
            tooltip = {
                Surface(
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "Under Development",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        ) {
            content()
        }
    } else {
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NewProjectCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val content = @Composable {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(140.dp)
                .clickable(enabled = enabled, onClick = onClick)
        ) {
            // Paper-like Card
            Surface(
                modifier = Modifier.size(120.dp, 160.dp),
                color = if(enabled) Color.White else Color(0xFFF5F5F5),
                border = BorderStroke(1.dp, if(enabled) Color(0xFFE0E0E0) else Color(0xFFEEEEEE)),
                shadowElevation = if(enabled) 2.dp else 0.dp,
                shape = RoundedCornerShape(2.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        null,
                        modifier = Modifier.size(48.dp),
                        tint = if(enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else Color.LightGray
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if(enabled) Color.Black else Color.Gray)
            Text(subtitle, fontSize = 11.sp, color = Color.Gray)
        }
    }

    if (!enabled) {
        TooltipArea(
            tooltip = {
                Surface(
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "Under Development",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        ) {
            content()
        }
    } else {
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentFileRow(file: File, onOpenFile: () -> Unit, onDelete: () -> Unit) {
    var isHovered by remember { mutableStateOf(false) }
    val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
    val dateStr = dateFormat.format(Date(if(file.exists()) file.lastModified() else System.currentTimeMillis()))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenFile)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Description,
            null,
            tint = if(file.exists()) MaterialTheme.colorScheme.primary else Color.Gray,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(file.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if(file.exists()) Color.Black else Color.Gray)
            Text(file.parent ?: "", fontSize = 11.sp, color = Color.Gray, maxLines = 1)
        }
        Text(dateStr, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.width(120.dp))

        // Delete Icon with Tooltip
        TooltipArea(
            tooltip = {
                Surface(
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "Delete",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        ) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Remove from history", tint = Color.Gray, modifier = Modifier.size(20.dp))
            }
        }
    }
    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
}