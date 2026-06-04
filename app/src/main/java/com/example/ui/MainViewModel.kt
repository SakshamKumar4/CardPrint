package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ProjectEntity
import com.example.data.ProjectRepository
import com.example.processing.ImageProcessor
import com.example.utils.Exporter
import com.example.utils.FileStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class Screen {
    object Home : Screen()
    data class Editor(val project: ProjectEntity, val imageIndex: Int) : Screen()
    data class LayoutPreview(val project: ProjectEntity) : Screen()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ProjectRepository
    val allProjects: StateFlow<List<ProjectEntity>>

    val currentScreen = MutableStateFlow<Screen>(Screen.Home)

    // Current editing project states
    val activeProject = MutableStateFlow<ProjectEntity?>(null)
    
    // Original bitmap loaded for current image index
    val originalEditingBitmap = MutableStateFlow<Bitmap?>(null)
    
    // Dynamic crop corners in the image coordinate space
    val activeCorners = mutableStateListOf<PointF>()

    // Enhancement Slider Values
    val brightness = MutableStateFlow(0f)      // -100 to 100
    val contrast = MutableStateFlow(1f)        // 0.5 to 2.5
    val saturation = MutableStateFlow(1f)      // 0.0 to 2.0
    val sharpness = MutableStateFlow(0f)       // 0 to 100
    val activeFilter = MutableStateFlow("ORIGINAL")

    // Result after cropping and filters applied
    val processedPreviewBitmap = MutableStateFlow<Bitmap?>(null)

    // Layout configuration values
    val layoutType = MutableStateFlow(1)
    val customCols = MutableStateFlow(2)
    val customRows = MutableStateFlow(3)
    val passportCopies = MutableStateFlow(12)

    init {
        val projectDao = AppDatabase.getDatabase(application).projectDao()
        repository = ProjectRepository(projectDao)
        allProjects = repository.allProjects.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun navigateTo(screen: Screen) {
        viewModelScope.launch {
            currentScreen.emit(screen)
            if (screen is Screen.Editor) {
                loadProjectImageForEditing(screen.project, screen.imageIndex)
            } else if (screen is Screen.LayoutPreview) {
                // Initialize layout values from saved project
                activeProject.emit(screen.project)
                layoutType.emit(screen.project.layoutType)
                customCols.emit(screen.project.customCols)
                customRows.emit(screen.project.customRows)
                passportCopies.emit(screen.project.passportCopies)
            }
        }
    }

    // Import from Gallery Uris
    fun importImagesFromUris(uris: List<Uri>, projectName: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            val copiedPaths = mutableListOf<String>()
            val context = getApplication<Application>().applicationContext
            
            for (uri in uris) {
                val path = FileStorage.copyImageToInternal(context, uri)
                if (path != null) {
                    copiedPaths.add(path)
                }
            }

            if (copiedPaths.isNotEmpty()) {
                val pName = if (projectName.isNotBlank()) projectName else "Project ${System.currentTimeMillis() / 1000}"
                val imagePathsString = copiedPaths.joinToString(",")
                // For initial state, edited image paths are the same as original
                val newProject = ProjectEntity(
                    name = pName,
                    imagePaths = imagePathsString,
                    editedImagePaths = imagePathsString
                )
                val idLong = repository.insert(newProject)
                val savedProject = newProject.copy(id = idLong.toInt())
                
                withContext(Dispatchers.Main) {
                    navigateTo(Screen.Editor(savedProject, 0))
                }
            }
        }
    }

    // Save temporary captured picture directly
    fun addCameraCaptureToProject(tempFile: File, projectName: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            val uri = Uri.fromFile(tempFile)
            val path = FileStorage.copyImageToInternal(context, uri)
            
            if (path != null) {
                val pName = if (projectName.isNotBlank()) projectName else "Photo ${System.currentTimeMillis() / 1000}"
                val newProject = ProjectEntity(
                    name = pName,
                    imagePaths = path,
                    editedImagePaths = path
                )
                val idLong = repository.insert(newProject)
                val savedProject = newProject.copy(id = idLong.toInt())
                
                withContext(Dispatchers.Main) {
                    navigateTo(Screen.Editor(savedProject, 0))
                }
            }
            // Cleanup temp file safely
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    // Delete a project
    fun deleteProject(project: ProjectEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            // Delete actual image files to save storage in low-end devices
            val orig = project.imagePaths.split(",")
            val edited = project.editedImagePaths.split(",")
            val allFiles = (orig + edited).filter { it.isNotBlank() }.distinct()
            for (filePath in allFiles) {
                try {
                    val file = File(filePath)
                    if (file.exists() && file.parentFile?.name == "card_print_images") {
                        file.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            repository.deleteById(project.id)
        }
    }

    // Load active image index
    private suspend fun loadProjectImageForEditing(project: ProjectEntity, index: Int) {
        withContext(Dispatchers.IO) {
            activeProject.emit(project)
            val origPaths = project.imagePaths.split(",").filter { it.isNotBlank() }
            val imagePath = origPaths.getOrNull(index)

            if (imagePath != null) {
                val bitmap = BitmapFactory.decodeFile(imagePath)
                if (bitmap != null) {
                    originalEditingBitmap.emit(bitmap)
                    resetEditingControls()
                    
                    // Pre-calculate optimal crop corners using Auto Crop Heuristics
                    val detected = ImageProcessor.detectCardCorners(bitmap)
                    withContext(Dispatchers.Main) {
                        activeCorners.clear()
                        activeCorners.addAll(detected)
                    }
                    applyFilterPreview()
                }
            }
        }
    }

    fun triggerAutoCrop() {
        viewModelScope.launch(Dispatchers.Default) {
            val bmp = originalEditingBitmap.value ?: return@launch
            val detected = ImageProcessor.detectCardCorners(bmp)
            withContext(Dispatchers.Main) {
                activeCorners.clear()
                activeCorners.addAll(detected)
            }
        }
    }

    fun resetEditingControls() {
        brightness.value = 0f
        contrast.value = 1f
        saturation.value = 1f
        sharpness.value = 0f
        activeFilter.value = "ORIGINAL"
    }

    fun rotateActiveImage(degrees: Float) {
        viewModelScope.launch(Dispatchers.Default) {
            val currentBmp = originalEditingBitmap.value ?: return@launch
            val rotated = ImageProcessor.rotateBitmap(currentBmp, degrees)
            originalEditingBitmap.value = rotated
            
            // Recalculate center crop markers
            val detected = ImageProcessor.detectCardCorners(rotated)
            withContext(Dispatchers.Main) {
                activeCorners.clear()
                activeCorners.addAll(detected)
            }
            applyFilterPreview()
        }
    }

    fun applyFilterPreview() {
        viewModelScope.launch(Dispatchers.Default) {
            val bmp = originalEditingBitmap.value ?: return@launch
            val filtered = ImageProcessor.applyFilterAndAdjustments(
                src = bmp,
                filterType = activeFilter.value,
                brightness = brightness.value,
                contrast = contrast.value,
                saturation = saturation.value,
                sharpness = sharpness.value
            )
            processedPreviewBitmap.value = filtered
        }
    }

    // Save Crops + Adjustments to Project record and storage
    fun applyCropAndSave(imageIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val proj = activeProject.value ?: return@launch
            val sourceBmp = originalEditingBitmap.value ?: return@launch

            if (activeCorners.size != 4) return@launch

            // 1. Perspective Warp the selected 4 corners
            val warped = ImageProcessor.perspectiveWarp(
                bitmap = sourceBmp,
                tl = activeCorners[0],
                tr = activeCorners[1],
                br = activeCorners[2],
                bl = activeCorners[3]
            )

            // 2. Apply chosen filters and slider values on the high-res warped crop
            val finalizedBmp = ImageProcessor.applyFilterAndAdjustments(
                src = warped,
                filterType = activeFilter.value,
                brightness = brightness.value,
                contrast = contrast.value,
                saturation = saturation.value,
                sharpness = sharpness.value
            )

            // 3. Save to internal application storage
            val context = getApplication<Application>().applicationContext
            val savedPath = FileStorage.saveBitmapToInternal(context, finalizedBmp)

            warped.recycle()
            finalizedBmp.recycle()

            if (savedPath != null) {
                val editedPaths = proj.editedImagePaths.split(",").toMutableList()
                if (imageIndex < editedPaths.size) {
                    editedPaths[imageIndex] = savedPath
                } else {
                    editedPaths.add(savedPath)
                }

                // Update Project Entity
                val updatedProject = proj.copy(
                    editedImagePaths = editedPaths.joinToString(","),
                    layoutType = layoutType.value,
                    customCols = customCols.value,
                    customRows = customRows.value,
                    passportCopies = passportCopies.value
                )
                repository.update(updatedProject)
                activeProject.value = updatedProject

                // Check if there's any remaining card image the user brought in
                val totalOriginalImages = proj.imagePaths.split(",").size
                withContext(Dispatchers.Main) {
                    if (imageIndex + 1 < totalOriginalImages) {
                        navigateTo(Screen.Editor(updatedProject, imageIndex + 1))
                    } else {
                        navigateTo(Screen.LayoutPreview(updatedProject))
                    }
                }
            }
        }
    }

    // Update Project layout values inside Room DB
    fun updateLayoutConfigurationInDb(
        lType: Int,
        cols: Int,
        rows: Int,
        passCopies: Int
    ) {
        layoutType.value = lType
        customCols.value = cols
        customRows.value = rows
        passportCopies.value = passCopies

        viewModelScope.launch(Dispatchers.IO) {
            val proj = activeProject.value ?: return@launch
            val updated = proj.copy(
                layoutType = lType,
                customCols = cols,
                customRows = rows,
                passportCopies = passCopies
            )
            repository.update(updated)
            activeProject.value = updated
        }
    }

    // Generate output file for Save/Share
    fun exportResultFile(type: String, onFinished: (File?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val proj = activeProject.value
            if (proj == null) {
                onFinished(null)
                return@launch
            }

            val context = getApplication<Application>().applicationContext
            val editedPaths = proj.editedImagePaths.split(",").filter { it.isNotBlank() }

            val exportDir = File(context.cacheDir, "exports")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            if (type.uppercase() == "PDF") {
                val file = File(exportDir, "CardPrint_${proj.name.lowercase().replace(" ", "_")}.pdf")
                val success = Exporter.generatePdf(
                    context = context,
                    editedImagePaths = editedPaths,
                    layoutType = layoutType.value,
                    customCols = customCols.value,
                    customRows = customRows.value,
                    passportCopies = passportCopies.value,
                    outputFile = file
                )
                onFinished(if (success) file else null)
            } else {
                val file = File(exportDir, "CardPrint_${proj.name.lowercase().replace(" ", "_")}.jpg")
                val success = Exporter.generateJpg(
                    context = context,
                    editedImagePaths = editedPaths,
                    layoutType = layoutType.value,
                    customCols = customCols.value,
                    customRows = customRows.value,
                    passportCopies = passportCopies.value,
                    outputFile = file
                )
                onFinished(if (success) file else null)
            }
        }
    }
}
