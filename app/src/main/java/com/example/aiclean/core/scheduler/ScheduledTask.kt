package com.example.aiclean.core.scheduler

import java.util.UUID

data class ScheduledTask(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val hour: Int,
    val minute: Int,
    val repeatDaily: Boolean = true,
    val isEnabled: Boolean = true,
    val lastRun: Long? = null,
    val taskType: TaskType = TaskType.CLEAN_CACHE
)

enum class TaskType {
    CLEAN_CACHE,
    CLEAN_JUNK,
    CLEAN_DUPLICATES,
    FULL_CLEAN
}
