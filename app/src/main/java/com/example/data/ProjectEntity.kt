package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val timestamp: Long = System.currentTimeMillis(),
    val imagePaths: String, // Comma-separated paths of original files (copied internally)
    val editedImagePaths: String = "", // Comma-separated paths of edited/cropped files
    val layoutType: Int = 1, // Current layout choice (1-7)
    val customCols: Int = 2, // For custom layout builder
    val customRows: Int = 3, // For custom layout builder
    val passportCopies: Int = 12 // For passport layout
)
