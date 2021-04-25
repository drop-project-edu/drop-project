package org.dropProject

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope

/**
 * Manages tasks that are executed asynchronously such as assignments export
 */
class PendingTasks {

    // key is the id of the task
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