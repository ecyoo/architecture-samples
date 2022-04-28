/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.architecture.blueprints.todoapp.statistics

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.android.architecture.blueprints.todoapp.data.Task
import com.example.android.architecture.blueprints.todoapp.data.source.TasksRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the statistics screen.
 */
class StatisticsViewModel(
    private val tasksRepository: TasksRepository
) : ViewModel() {

    private val tasks: Flow<List<Task>> = tasksRepository.getTasks()
    private val _dataLoading = MutableLiveData<Boolean>(false)
    private val stats: MutableLiveData<StatsResult?> = MutableLiveData(null)

    var activeTasksPercent: MutableLiveData<Float> = MutableLiveData(0.0f)
    var completedTasksPercent: MutableLiveData<Float> = MutableLiveData(0.0f)
    val dataLoading: LiveData<Boolean> = _dataLoading
//    val error: LiveData<Boolean> = tasks.map { it is Error }
    var empty: MutableLiveData<Boolean> = MutableLiveData(true)

    init {
        viewModelScope.launch {
            tasks.collect { taskList ->
                stats.value = getActiveAndCompletedStats(taskList)
                empty.value = taskList.isEmpty()
                activeTasksPercent.value = stats.value?.activeTasksPercent ?: 0f
                completedTasksPercent.value = stats.value?.completedTasksPercent ?: 0f
            }

        }
    }

    fun refresh() {
        _dataLoading.value = true
        viewModelScope.launch {
            tasksRepository.refreshTasks()
            _dataLoading.value = false

        }
    }
}
