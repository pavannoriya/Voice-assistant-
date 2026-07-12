package com.example.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.Flow

class MacroRepository(private val macroDao: MacroDao) {
    val allMacros: Flow<List<Macro>> = macroDao.getAllMacros()

    suspend fun saveMacroWithActions(name: String, voicePhrase: String, actions: List<MacroAction>) {
        val macro = Macro(name = name, voicePhrase = voicePhrase)
        val macroId = macroDao.insertMacro(macro).toInt()
        val actionsWithId = actions.map { it.copy(macroId = macroId) }
        macroDao.insertActions(actionsWithId)
    }

    suspend fun getMacroByVoicePhrase(phrase: String): Macro? {
        return macroDao.getMacroByVoicePhrase(phrase)
    }

    suspend fun getActionsForMacro(macroId: Int): List<MacroAction> {
        return macroDao.getActionsForMacro(macroId)
    }
    
    suspend fun deleteMacro(id: Int) {
        macroDao.deleteMacroById(id)
    }

    companion object {
        @Volatile
        private var INSTANCE: MacroRepository? = null

        fun getInstance(context: Context): MacroRepository {
            return INSTANCE ?: synchronized(this) {
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "macro_database"
                ).fallbackToDestructiveMigration().build()
                MacroRepository(db.macroDao()).also { INSTANCE = it }
            }
        }
    }
}
