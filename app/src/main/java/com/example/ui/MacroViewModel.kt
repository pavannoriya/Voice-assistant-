package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Macro
import com.example.data.MacroAction
import com.example.data.MacroRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MacroViewModel(private val repository: MacroRepository) : androidx.lifecycle.ViewModel() {
    
    val allMacros: StateFlow<List<Macro>> = repository.allMacros
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun saveMacro(name: String, voicePhrase: String, actions: List<MacroAction>) {
        viewModelScope.launch {
            repository.saveMacroWithActions(name, voicePhrase, actions)
        }
    }

    fun deleteMacro(macro: Macro) {
        viewModelScope.launch {
            repository.deleteMacro(macro.id)
        }
    }

    suspend fun getActionsForMacro(macroId: Int): List<MacroAction> {
        return repository.getActionsForMacro(macroId)
    }
}
