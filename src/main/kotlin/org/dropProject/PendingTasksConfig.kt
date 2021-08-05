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
package org.dropProject

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope
import java.lang.Exception

/**
 * Used to signal errors on pending tasks
 */
data class PendingTaskError(val exception: Throwable)

/**
 * Manages tasks that are executed asynchronously such as assignments export
 */
class PendingTasks {

    // key is the id of the task, value can be anything but if it is an error, will be PendingTaskError
    val pendingTasks = HashMap<String,Any>()

    fun get(taskId: String) : Any? {
        return pendingTasks[taskId]
    }

    fun put(taskId: String, data: Any) {
        pendingTasks[taskId] = data
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
