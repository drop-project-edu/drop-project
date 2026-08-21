/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2021 Pedro Alves
 * %%
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
 * =========================LICENSE_END==================================
 */
package org.dropproject.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope
import java.io.File
import java.lang.Exception
import java.util.concurrent.ConcurrentHashMap

/**
 * Used to signal errors on pending tasks
 */
data class PendingTaskError(val exception: Throwable)

/**
 * The result of an assignment export: the name to give to the downloaded file and the zip file itself
 */
data class PendingExport(val filename: String, val zipFile: File)

/**
 * Manages tasks that are executed asynchronously such as assignments export. Tasks are put here by the
 * asynchronous threads and read by the request threads that are polling for their result.
 */
class PendingTasks {

    private data class PendingTask(val data: Any, val createdAt: Long)

    // key is the id of the task, value can be anything but if it is an error, will be PendingTaskError
    private val pendingTasks = ConcurrentHashMap<String,PendingTask>()

    fun get(taskId: String) : Any? {
        return pendingTasks[taskId]?.data
    }

    fun put(taskId: String, data: Any) {
        pendingTasks[taskId] = PendingTask(data, System.currentTimeMillis())
    }

    /**
     * Removes all the tasks that were created before [timestamp], returning their results, so that the caller
     * can dispose of whatever resources they are holding.
     */
    fun removeCreatedBefore(timestamp: Long) : List<Any> {

        val removed = mutableListOf<Any>()
        val iterator = pendingTasks.entries.iterator()
        while (iterator.hasNext()) {
            val pendingTask = iterator.next().value
            if (pendingTask.createdAt < timestamp) {
                removed.add(pendingTask.data)
                iterator.remove()
            }
        }

        return removed
    }
}

@Configuration
class PendingTasksConfig {

    @Bean
    @Scope("singleton")
    fun pendingTasks(): PendingTasks {
        return PendingTasks()
    }
}
