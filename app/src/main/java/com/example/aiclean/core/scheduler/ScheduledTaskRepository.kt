package com.example.aiclean.core.scheduler

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.example.aiclean.core.settings.dataStore
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import androidx.datastore.preferences.core.stringPreferencesKey

@Singleton
class ScheduledTaskRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    private val tasksKey = stringPreferencesKey("scheduled_cleaning_tasks")

    val tasks: Flow<List<ScheduledTask>> = context.dataStore.data.map { prefs ->
        val raw = prefs[tasksKey] ?: return@map emptyList()
        runCatching {
            gson.fromJson(raw, Array<ScheduledTask>::class.java)?.toList().orEmpty()
        }.getOrDefault(emptyList())
    }

    suspend fun save(tasks: List<ScheduledTask>) {
        context.dataStore.edit { it[tasksKey] = gson.toJson(tasks) }
    }
}
