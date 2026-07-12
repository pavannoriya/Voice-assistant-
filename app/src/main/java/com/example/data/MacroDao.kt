package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MacroDao {
    @Query("SELECT * FROM macros ORDER BY createdAt DESC")
    fun getAllMacros(): Flow<List<Macro>>

    @Query("SELECT * FROM macros WHERE LOWER(voicePhrase) = LOWER(:phrase) LIMIT 1")
    suspend fun getMacroByVoicePhrase(phrase: String): Macro?

    @Query("SELECT * FROM macro_actions WHERE macroId = :macroId ORDER BY orderIndex ASC")
    suspend fun getActionsForMacro(macroId: Int): List<MacroAction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMacro(macro: Macro): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActions(actions: List<MacroAction>)

    @Query("DELETE FROM macros WHERE id = :id")
    suspend fun deleteMacroById(id: Int)
}
