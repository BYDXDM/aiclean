package com.example.aiclean.ui.screens.scheduler

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiclean.core.scheduler.ScheduledTask
import com.example.aiclean.core.scheduler.TaskType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SchedulerUiState(
    val tasks: List<ScheduledTask> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SchedulerViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(SchedulerUiState())
    val uiState: StateFlow<SchedulerUiState> = _uiState.asStateFlow()

    private val tasks = mutableListOf<ScheduledTask>()

    init {
        // Load saved tasks (mock data for now)
        tasks.addAll(listOf(
            ScheduledTask(
                name = "Daily Cache Clean",
                hour = 3,
                minute = 0,
                repeatDaily = true,
                isEnabled = true,
                lastRun = System.currentTimeMillis() - 86400000
            ),
            ScheduledTask(
                name = "Weekly Full Clean",
                hour = 2,
                minute = 0,
                repeatDaily = false,
                isEnabled = false
            )
        ))
        _uiState.value = SchedulerUiState(tasks = tasks.toList())
    }

    fun addTask(name: String, hour: Int, minute: Int, repeatDaily: Boolean) {
        viewModelScope.launch {
            val newTask = ScheduledTask(
                name = name,
                hour = hour,
                minute = minute,
                repeatDaily = repeatDaily,
                isEnabled = true
            )
            tasks.add(newTask)
            _uiState.value = SchedulerUiState(tasks = tasks.toList())
        }
    }

    fun toggleTask(taskId: String) {
        viewModelScope.launch {
            val index = tasks.indexOfFirst { it.id == taskId }
            if (index != -1) {
                tasks[index] = tasks[index].copy(isEnabled = !tasks[index].isEnabled)
                _uiState.value = SchedulerUiState(tasks = tasks.toList())
            }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            tasks.removeAll { it.id == taskId }
            _uiState.value = SchedulerUiState(tasks = tasks.toList())
        }
    }
}
