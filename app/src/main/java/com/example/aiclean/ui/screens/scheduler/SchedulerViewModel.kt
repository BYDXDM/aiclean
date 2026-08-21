package com.example.aiclean.ui.screens.scheduler

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiclean.core.scheduler.ScheduledTask
import com.example.aiclean.core.scheduler.ScheduledTaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SchedulerUiState(
    val tasks: List<ScheduledTask> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

/** 任务现已持久化。实际后台清理由系统限制需要用户打开应用确认，避免后台静默误删。 */
@HiltViewModel
class SchedulerViewModel @Inject constructor(
    private val repository: ScheduledTaskRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SchedulerUiState())
    val uiState: StateFlow<SchedulerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.tasks.collect { tasks ->
                _uiState.value = SchedulerUiState(tasks = tasks.sortedWith(compareBy({ it.hour }, { it.minute })))
            }
        }
    }

    fun addTask(name: String, hour: Int, minute: Int, repeatDaily: Boolean) = viewModelScope.launch {
        val cleanName = name.trim().take(40)
        val current = _uiState.value.tasks
        repository.save(current + ScheduledTask(name = cleanName, hour = hour, minute = minute, repeatDaily = repeatDaily))
    }

    fun toggleTask(taskId: String) = update(taskId) { it.copy(isEnabled = !it.isEnabled) }

    fun deleteTask(taskId: String) = viewModelScope.launch {
        repository.save(_uiState.value.tasks.filterNot { it.id == taskId })
    }

    private fun update(id: String, transform: (ScheduledTask) -> ScheduledTask) = viewModelScope.launch {
        repository.save(_uiState.value.tasks.map { if (it.id == id) transform(it) else it })
    }
}
