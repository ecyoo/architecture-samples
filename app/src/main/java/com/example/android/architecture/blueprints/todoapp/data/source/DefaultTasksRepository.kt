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
package com.example.android.architecture.blueprints.todoapp.data.source

import com.example.android.architecture.blueprints.todoapp.data.Task
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber

/**
 * Default implementation of [TasksRepository]. Single entry point for managing tasks' data.
 */
class DefaultTasksRepository(
    private val tasksRemoteDataSource: TasksDataSource,
    private val tasksLocalDataSource: TasksDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : TasksRepository {

    override fun getTasks(forceUpdate: Boolean): Flow<List<Task>> {
        return flow<List<Task>> {
            if (forceUpdate) {
                try {
                    updateTasksFromRemoteDataSource()
                } catch (ex: Exception) {
                    emit(listOf())
                }
            }
            tasksLocalDataSource.getTasks().collect{ emit(it) }
        }
    }

    override suspend fun refreshTasks() {
        updateTasksFromRemoteDataSource()
    }

    override suspend fun refreshTask(taskId: String) {
        updateTaskFromRemoteDataSource(taskId)
    }

    private suspend fun updateTasksFromRemoteDataSource() {
        tasksRemoteDataSource.getTasks().map { taskList ->
            tasksLocalDataSource.deleteAllTasks()
            taskList.forEach { task ->
                tasksLocalDataSource.saveTask(task)
            }
        }.catch { exp ->
            Timber.e(exp)
        }.flowOn(ioDispatcher).collect()
    }

    private suspend fun updateTaskFromRemoteDataSource(taskId: String) {
        tasksRemoteDataSource.getTask(taskId).map { task ->
            tasksLocalDataSource.saveTask(task)
        }.catch { exp ->
            Timber.e(exp)
        }.flowOn(ioDispatcher)
            .collect()
    }

    /**
     * Relies on [getTasks] to fetch data and picks the task with the same ID.
     */
    override fun getTask(taskId: String, forceUpdate: Boolean): Flow<Task> {
        return flow {
            if (forceUpdate) {
                updateTaskFromRemoteDataSource(taskId)
            }
            tasksLocalDataSource.getTask(taskId).collect {
                emit(it)
            }
        }
    }

    override suspend fun saveTask(task: Task) {
        coroutineScope {
            launch { tasksRemoteDataSource.saveTask(task) }
            launch { tasksLocalDataSource.saveTask(task) }
        }
    }

    override suspend fun completeTask(task: Task) {
        coroutineScope {
            launch { tasksRemoteDataSource.completeTask(task) }
            launch { tasksLocalDataSource.completeTask(task) }
        }
    }

    override suspend fun completeTask(taskId: String) {
        getTaskWithId(taskId)
            .flowOn(ioDispatcher)
            .collect { task ->
                completeTask(task)
            }
    }

    override suspend fun activateTask(task: Task) = withContext<Unit>(ioDispatcher) {
        coroutineScope {
            launch { tasksRemoteDataSource.activateTask(task) }
            launch { tasksLocalDataSource.activateTask(task) }
        }
    }

    override suspend fun activateTask(taskId: String) {
        getTaskWithId(taskId)
            .flowOn(ioDispatcher)
            .collect { task ->
                activateTask(task)
            }
    }

    override suspend fun clearCompletedTasks() {
        coroutineScope {
            launch {
                tasksRemoteDataSource.clearCompletedTasks()
                tasksLocalDataSource.clearCompletedTasks()
            }
        }
    }

    override suspend fun deleteAllTasks() {
        withContext(ioDispatcher) {
            coroutineScope {
                launch { tasksRemoteDataSource.deleteAllTasks() }
                launch { tasksLocalDataSource.deleteAllTasks() }
            }
        }
    }

    override suspend fun deleteTask(taskId: String) {
        coroutineScope {
            launch { tasksRemoteDataSource.deleteTask(taskId) }
            launch { tasksLocalDataSource.deleteTask(taskId) }
        }
    }

    private suspend fun getTaskWithId(id: String): Flow<Task> {
        return tasksLocalDataSource.getTask(id)
    }
}
