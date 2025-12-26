package com.kcpd.myfolder.data.model

import java.util.Date
import java.util.UUID

data class UserFolder(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val color: FolderColor,
    val parentFolderId: String? = null,
    val categoryPath: String, // e.g., "photos", "notes" - which category this folder belongs to
    val createdAt: Date = Date()
)

enum class FolderColor(val colorHex: String, val displayName: String) {
    BLUE("#2196F3", "Blue"),
    RED("#F44336", "Red"),
    GREEN("#4CAF50", "Green"),
    YELLOW("#FFEB3B", "Yellow"),
    ORANGE("#FF9800", "Orange"),
    PURPLE("#9C27B0", "Purple"),
    PINK("#E91E63", "Pink"),
    TEAL("#009688", "Teal"),
    INDIGO("#3F51B5", "Indigo"),
    BROWN("#795548", "Brown"),
    GREY("#9E9E9E", "Grey"),
    CYAN("#00BCD4", "Cyan")
}
