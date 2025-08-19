/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 Pedro Alves
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
package org.dropproject.services

import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Future
import kotlin.concurrent.schedule

/**
 * Tasks created through this scheduler are cancelled after a certain time
 */
class CancellableTaskScheduler(var timeout: Long) : ThreadPoolTaskScheduler() {

    override fun submit(task: Runnable): Future<*> {
        val future = super.submit(task)

        Timer("Timeout", false).schedule(timeout) { future.cancel(true) }

        return future
    }

    // on the latest spring version, this seems to be the method that is called
    override fun <T : Any?> submit(task: Callable<T>): Future<T> {
        val future = super.submit(task)

        Timer("Timeout", false).schedule(timeout) {
            future.cancel(true)
        }

        return future
    }
}
