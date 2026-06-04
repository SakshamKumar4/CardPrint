package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ProjectEntity
import com.example.ui.MainViewModel
import com.example.ui.Screen
import com.example.ui.components.CustomCropView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: MainViewModel,
    project: ProjectEntity,
    imageIndex: Int
) {
    val originalBmp by viewModel.originalEditingBitmap.collectAsState()
    val previewBmp by viewModel.processedPreviewBitmap.collectAsState()

    val corners = viewModel.activeCorners
    val brightness by viewModel.brightness.collectAsState()
    val contrast by viewModel.contrast.collectAsState()
    val saturation by viewModel.saturation.collectAsState()
    val sharpness by viewModel.sharpness.collectAsState()
    val activeFilter by viewModel.activeFilter.collectAsState()

    val originalCount = project.imagePaths.split(",").filter { it.isNotBlank() }.size

    var activeControlTab by remember { mutableStateOf(0) } // 0 = Crop & Rotate, 1 = Filters, 2 = Adjustments

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Edit Card ${imageIndex + 1} of $originalCount",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(Screen.Home) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back home")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.resetEditingControls() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.navigationBarsPadding()) {
                    // Navigation Tabs
                    TabRow(selectedTabIndex = activeControlTab) {
                        Tab(
                            selected = activeControlTab == 0,
                            onClick = { activeControlTab = 0 },
                            icon = { Icon(Icons.Default.Crop, contentDescription = "Crop") },
                            text = { Text("Crop") }
                        )
                        Tab(
                            selected = activeControlTab == 1,
                            onClick = { activeControlTab = 1 },
                            icon = { Icon(Icons.Default.Filter, contentDescription = "Filters") },
                            text = { Text("Filters") }
                        )
                        Tab(
                            selected = activeControlTab == 2,
                            onClick = { activeControlTab = 2 },
                            icon = { Icon(Icons.Default.Tune, contentDescription = "Adjust") },
                            text = { Text("Adjust") }
                        )
                    }

                    // Content based on selected Tab
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        when (activeControlTab) {
                            0 -> CropRotatePanel(
                                viewModel = viewModel,
                                onAutoCrop = { viewModel.triggerAutoCrop() }
                            )
                            1 -> FilterSelectionPanel(
                                selectedFilter = activeFilter,
                                onFilterSelected = { filter ->
                                    viewModel.activeFilter.value = filter
                                    viewModel.applyFilterPreview()
                                }
                            )
                            2 -> AdjustmentsPanel(
                                brightness = brightness,
                                contrast = contrast,
                                saturation = saturation,
                                sharpness = sharpness,
                                onBrightnessChanged = { value ->
                                    viewModel.brightness.value = value
                                    viewModel.applyFilterPreview()
                                },
                                onContrastChanged = { value ->
                                    viewModel.contrast.value = value
                                    viewModel.applyFilterPreview()
                                },
                                onSaturationChanged = { value ->
                                    viewModel.saturation.value = value
                                    viewModel.applyFilterPreview()
                                },
                                onSharpnessChanged = { value ->
                                    viewModel.sharpness.value = value
                                    viewModel.applyFilterPreview()
                                }
                            )
                        }
                    }

                    // Bottom CTA Action
                    Button(
                        onClick = { viewModel.applyCropAndSave(imageIndex) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(54.dp),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (imageIndex + 1 < originalCount) "Next Card" else "Warp & Generate Layout",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.Done, contentDescription = "Done")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            // Render interactive custom cropper when original image is loaded
            originalBmp?.let { bmp ->
                CustomCropView(
                    bitmap = bmp,
                    corners = corners,
                    onCornersUpdated = { updated ->
                        viewModel.activeCorners.clear()
                        viewModel.activeCorners.addAll(updated)
                    }
                )
            } ?: Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun CropRotatePanel(
    viewModel: MainViewModel,
    onAutoCrop: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rotate Left Button
        OutlinedButton(
            onClick = { viewModel.rotateActiveImage(-90f) },
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(Icons.Default.RotateLeft, contentDescription = "Rotate Left")
            Spacer(modifier = Modifier.width(6.dp))
            Text("Rotate L", fontWeight = FontWeight.SemiBold)
        }

        // Auto Crop Action
        Button(
            onClick = onAutoCrop,
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(Icons.Default.AutoFixHigh, contentDescription = "Auto Crop")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Auto Crop", fontWeight = FontWeight.Bold)
        }

        // Rotate Right Button
        OutlinedButton(
            onClick = { viewModel.rotateActiveImage(90f) },
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(Icons.Default.RotateRight, contentDescription = "Rotate Right")
            Spacer(modifier = Modifier.width(6.dp))
            Text("Rotate R", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun FilterSelectionPanel(
    selectedFilter: String,
    onFilterSelected: (String) -> Unit
) {
    val filters = listOf(
        "ORIGINAL" to "Original",
        "SCANNER" to "Scanner Mode",
        "BLACK_AND_WHITE" to "B&W",
        "GRAYSCALE" to "Grayscale",
        "HIGH_CONTRAST" to "Contrast Pro",
        "BRIGHTNESS_ENHANCE" to "Brighten",
        "VINTAGE" to "Vintage"
    )

    Row(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for ((filterKey, filterName) in filters) {
            val isSelected = filterKey == selectedFilter
            FilterChip(
                selected = isSelected,
                onClick = { onFilterSelected(filterKey) },
                label = { Text(filterName, fontWeight = FontWeight.SemiBold) },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Default.Done, contentDescription = "Selected Filter", modifier = Modifier.size(16.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

@Composable
fun AdjustmentsPanel(
    brightness: Float,
    contrast: Float,
    saturation: Float,
    sharpness: Float,
    onBrightnessChanged: (Float) -> Unit,
    onContrastChanged: (Float) -> Unit,
    onSaturationChanged: (Float) -> Unit,
    onSharpnessChanged: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Brightness Slider
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Brightness5, contentDescription = "Brightness", modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text("Bright", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(45.dp))
            Slider(
                value = brightness,
                onValueChange = onBrightnessChanged,
                valueRange = -80f..80f,
                modifier = Modifier.weight(1f)
            )
            Text("${brightness.toInt()}", fontSize = 12.sp, modifier = Modifier.width(30.dp))
        }

        // Contrast Slider
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Contrast, contentDescription = "Contrast", modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text("Contr", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(45.dp))
            Slider(
                value = contrast,
                onValueChange = onContrastChanged,
                valueRange = 0.5f..2.5f,
                modifier = Modifier.weight(1f)
            )
            Text(String.format("%.1fx", contrast), fontSize = 12.sp, modifier = Modifier.width(30.dp))
        }

        // Saturation Slider
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Palette, contentDescription = "Saturation", modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text("Satur", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(45.dp))
            Slider(
                value = saturation,
                onValueChange = onSaturationChanged,
                valueRange = 0.0f..2.0f,
                modifier = Modifier.weight(1f)
            )
            Text(String.format("%.1fx", saturation), fontSize = 12.sp, modifier = Modifier.width(30.dp))
        }

        // Sharpness Slider
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Details, contentDescription = "Sharpness", modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text("Sharp", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(45.dp))
            Slider(
                value = sharpness,
                onValueChange = onSharpnessChanged,
                valueRange = 0f..100f,
                modifier = Modifier.weight(1f)
            )
            Text("${sharpness.toInt()}%", fontSize = 12.sp, modifier = Modifier.width(30.dp))
        }
    }
}
