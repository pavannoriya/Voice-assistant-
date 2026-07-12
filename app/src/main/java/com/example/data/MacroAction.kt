package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(
    tableName = "macro_actions",
    foreignKeys = [
        ForeignKey(
            entity = Macro::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("macroId"),
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MacroAction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val macroId: Int,
    val orderIndex: Int,
    val actionType: String, // e.g., "TAP"
    val xCoordinate: Float,
    val yCoordinate: Float,
    val viewId: String? = null,
    val clickedText: String? = null,
    val textToType: String? = null,
    val delayMs: Long = 1000L
)
