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
package com.example.android.architecture.blueprints.todoapp.data.source.remote

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.map
import com.example.android.architecture.blueprints.todoapp.data.Task
import com.example.android.architecture.blueprints.todoapp.data.source.TasksDataSource
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.squareup.okhttp.Dispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import timber.log.Timber
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Implementation of the data source that adds a latency simulating network.
 */
object TasksRemoteDataSource : TasksDataSource {

    // Initialize Cloud Firestore
    private val db: FirebaseFirestore
        get() {
            return Firebase.firestore
        }

    private val collectRef: CollectionReference
        get() {
            return db.collection("Task")
        }

    private val observableTasks = MutableLiveData<List<Task>>(listOf())

    override suspend fun refreshTasks() {
        getTasks().collect { taskList ->
            observableTasks.value = taskList
        }
    }

    override suspend fun refreshTask(taskId: String) {
        refreshTasks()
    }

    override fun getTasks(): Flow<List<Task>> {
        return callbackFlow<List<Task>> {
            collectRef.get()
                .addOnSuccessListener { docs ->
                    val list = docs.map {
                        Task(it.data)
                    }
                    trySend(list).isSuccess
                    close()
                }
                .addOnFailureListener { e ->
                    close(e)
                }
            awaitClose { Timber.d("get tasks finish. ") }
        }
    }

    override fun getTask(taskId: String): Flow<Task> {
        return callbackFlow {
            collectRef.document(taskId).get()
                .addOnSuccessListener { document ->
                    if (document.data != null) {
                        trySend(Task(document.data!!))
                        close()
                    } else {
                        close(Exception("Task Not Found."))
                    }
                }.addOnFailureListener { e ->
                    close(e)
                }
            awaitClose()
        }
    }

    private fun addTask(title: String, description: String) {
        val newTask = Task(title, description)
        collectRef.document(newTask.id)
            .set(newTask, SetOptions.merge())
            .addOnFailureListener { e -> throw e }
    }

    override suspend fun saveTask(task: Task) {
        suspendCoroutine<Unit> { continuation ->
            collectRef.document(task.id)
                .set(task, SetOptions.merge())
                .addOnSuccessListener { continuation.resumeWith(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resumeWithException(e) }
        }
    }

    override suspend fun completeTask(task: Task) {
        completeTask(task.id)
    }

    override suspend fun completeTask(taskId: String) {
        suspendCoroutine<Unit> { continuation ->
            collectRef.document(taskId)
                .update("isCompleted", true)
                .addOnSuccessListener { continuation.resumeWith(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resumeWithException(e) }
        }
    }

    override suspend fun activateTask(task: Task) {
        activateTask(task.id)
    }

    override suspend fun activateTask(taskId: String) {
        suspendCoroutine<Unit> { continuation ->
            collectRef.document(taskId)
                .update("isCompleted", false)
                .addOnSuccessListener { continuation.resumeWith(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resumeWithException(e) }
        }
    }

    override suspend fun clearCompletedTasks() {
        getTasksSingleFlow()
            .filter { it.isCompleted }
            .onEach {
                if (it.id.isNotEmpty()) {
                    suspendCoroutine<Unit> { continuation ->
                        collectRef.document(it.id)
                            .delete()
                            .addOnSuccessListener { continuation.resumeWith(Result.success(Unit)) }
                            .addOnFailureListener { e -> continuation.resumeWithException(e) }
                    }

                }
        }.collect()
    }

    override suspend fun deleteAllTasks() {
        getTasksSingleFlow().onEach { task ->
            suspendCoroutine<Unit> { continuation ->
                collectRef.document(task.id)
                    .delete()
                    .addOnSuccessListener { continuation.resumeWith(Result.success(Unit)) }
                    .addOnFailureListener { e -> continuation.resumeWithException(e) }
            }
        }.collect()
    }

    override suspend fun deleteTask(taskId: String) {
        suspendCoroutine<Unit> { continuation ->
            collectRef.document(taskId)
                .delete()
                .addOnSuccessListener { continuation.resumeWith(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resumeWithException(e) }
        }
    }

    private fun getTasksSingleFlow(): Flow<Task> {
        return callbackFlow<Task> {
            collectRef.get()
                .addOnSuccessListener { docs ->
                    val list = docs.map {
                        Task(it.data)
                    }
                    for (task in list) {
                        trySend(task).isSuccess
                    }
                    close()
                }
                .addOnFailureListener { e ->
                    close(e)
                }
            awaitClose { Timber.d("get tasks finish. ") }
        }
    }
}
