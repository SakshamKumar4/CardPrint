package com.example.ui.screens

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ProjectEntity
import com.example.ui.MainViewModel
import com.example.ui.Screen
import com.example.utils.Exporter
import com.example.utils.LayoutEngine
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayoutScreen(
    viewModel: MainViewModel,
    project: ProjectEntity
) {
    val context = LocalContext.current
    val editedImagePaths = remember(project.editedImagePaths) {
        project.editedImagePaths.split(",").filter { it.isNotBlank() }
    }

    // Load active edited bitmaps into memory for virtual A4 preview
    val loadedBitmaps = remember(editedImagePaths) {
        editedImagePaths.mapNotNull { path ->
            try {
                BitmapFactory.decodeFile(path)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    val selectedLayout by viewModel.layoutType.collectAsState()
    val cols by viewModel.customCols.collectAsState()
    val rows by viewModel.customRows.collectAsState()
    val passportCount by viewModel.passportCopies.collectAsState()

    var showExportSuccessDialog by remember { mutableStateOf<String?>(null) }
    var isExporting by remember { mutableStateOf(false) }

    // Re-calculate layout coordinates in point space
    val layoutItems = remember(selectedLayout, editedImagePaths.size, cols, rows, passportCount) {
        LayoutEngine.getLayoutItems(
            layoutType = selectedLayout,
            imageCount = editedImagePaths.size,
            customCols = cols,
            customRows = rows,
            passportCopies = passportCount
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Print Layout Studio", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(Screen.Home) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Home")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            // Navigate index 0 for complete redo editing of first card
                            viewModel.navigateTo(Screen.Editor(project, 0))
                        }
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Cards")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Re-Edit", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Scrollable Settings Pane top, fixed preview in center
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Layout Selector segment
                Text(
                    text = "Select A4 Print Template",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val templates = listOf(
                        1 to "Single Centered",
                        2 to "Two Side-by-Side",
                        3 to "Two Top-Bottom",
                        4 to "Four Card Page",
                        5 to "Six Card Page",
                        6 to "Passport Photos",
                        7 to "Custom Builder"
                    )

                    for ((type, title) in templates) {
                        val isSelected = selectedLayout == type
                        ElevatedFilterChip(
                            selected = isSelected,
                            onClick = {
                                viewModel.updateLayoutConfigurationInDb(type, cols, rows, passportCount)
                            },
                            label = { Text(title, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.elevatedFilterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Custom settings segment in case of layout triggers
                AnimatedVisibility(visible = selectedLayout == 7) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Custom Grid Matrix Builder",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Columns: $cols",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Slider(
                                    value = cols.toFloat(),
                                    onValueChange = {
                                        viewModel.updateLayoutConfigurationInDb(
                                            selectedLayout,
                                            it.toInt(),
                                            rows,
                                            passportCount
                                        )
                                    },
                                    valueRange = 1f..5f,
                                    steps = 3
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Rows: $rows",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Slider(
                                    value = rows.toFloat(),
                                    onValueChange = {
                                        viewModel.updateLayoutConfigurationInDb(
                                            selectedLayout,
                                            cols,
                                            it.toInt(),
                                            passportCount
                                        )
                                    },
                                    valueRange = 1f..8f,
                                    steps = 6
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = selectedLayout == 6) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Passport Multi-Copies",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Total Copies: $passportCount",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Slider(
                                value = passportCount.toFloat(),
                                onValueChange = {
                                    viewModel.updateLayoutConfigurationInDb(
                                        selectedLayout,
                                        cols,
                                        rows,
                                        it.toInt()
                                    )
                                },
                                valueRange = 4f..20f,
                                steps = 15,
                                modifier = Modifier.weight(2f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Virtual A4 Sheet Container
                Text(
                    text = "A4 Page Layout Mockup",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 30.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Lock A4 standard aspect bounding ratio (210:297)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.82f)
                            .aspectRatio(210f / 297f)
                            .shadow(12.dp, RoundedCornerShape(4.dp))
                            .background(Color.White)
                            .border(1.dp, Color.LightGray)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val canvasW = size.width
                            val canvasH = size.height

                            val scaleX = canvasW / 595f
                            val scaleY = canvasH / 842f

                            for (item in layoutItems) {
                                val bmp = loadedBitmaps.getOrNull(item.imageIndex % loadedBitmaps.size)
                                if (bmp != null) {
                                    val left = item.rect.left * scaleX
                                    val top = item.rect.top * scaleY
                                    val right = item.rect.right * scaleX
                                    val bottom = item.rect.bottom * scaleY
                                    val width = right - left
                                    val height = bottom - top

                                    drawImage(
                                        image = bmp.asImageBitmap(),
                                        dstOffset = IntOffset(left.toInt(), top.toInt()),
                                        dstSize = IntSize(width.toInt(), height.toInt())
                                    )

                                    // Print cut lines border outline
                                    drawRect(
                                        color = Color(android.graphics.Color.parseColor("#CCCCCC")),
                                        topLeft = Offset(left, top),
                                        size = Size(width, height),
                                        style = Stroke(width = 0.5.dp.toPx())
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Fixed Print & Export action deck
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isExporting) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Processing high quality print documents...",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        // Hardware Printing Row
                        Button(
                            onClick = {
                                isExporting = true
                                viewModel.exportResultFile("PDF") { file ->
                                    isExporting = false
                                    if (file != null) {
                                        Exporter.printFile(context, file)
                                    } else {
                                        Toast.makeText(context, "Print generation failed!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Print, contentDescription = "Print PDF")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Direct Print Document", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // PDF Button
                            ElevatedButton(
                                onClick = {
                                    isExporting = true
                                    viewModel.exportResultFile("PDF") { file ->
                                        isExporting = false
                                        if (file != null) {
                                            showExportSuccessDialog = "PDF saved as: ${file.name}\n\nPath: ${file.absolutePath}"
                                        } else {
                                            Toast.makeText(context, "Export failed!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.elevatedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF Icon")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Export PDF", fontWeight = FontWeight.Bold)
                                }
                            }

                            // Share Deck
                            ElevatedButton(
                                onClick = {
                                    isExporting = true
                                    viewModel.exportResultFile("PDF") { file ->
                                        isExporting = false
                                        if (file != null) {
                                            Exporter.shareFile(context, file, "application/pdf")
                                        } else {
                                            Toast.makeText(context, "Document compile error!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.elevatedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Share, contentDescription = "Share Document")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Share", fontWeight = FontWeight.Bold)
                                }
                            }

                            // JPG Button
                            ElevatedButton(
                                onClick = {
                                    isExporting = true
                                    viewModel.exportResultFile("JPG") { file ->
                                        isExporting = false
                                        if (file != null) {
                                            showExportSuccessDialog = "p300 DPI high resolution printing graphic generated:\n\n${file.name}"
                                        } else {
                                            Toast.makeText(context, "Rasterizing failed!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Image, contentDescription = "JPG Icon")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Export JPG", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Success notification dialog
    showExportSuccessDialog?.let { details ->
        AlertDialog(
            onDismissRequest = { showExportSuccessDialog = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Succeeded!", fontWeight = FontWeight.Bold)
                }
            },
            text = { Text(details) },
            confirmButton = {
                Button(onClick = { showExportSuccessDialog = null }) {
                    Text("Alright", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
