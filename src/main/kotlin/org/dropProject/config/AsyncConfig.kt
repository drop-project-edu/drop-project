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
package org.dropProject.config

import org.dropProject.services.CancellableTaskScheduler
import org.dropProject.services.MyAsyncUncaughtExceptionHandler
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.dropProject.config.DropProjectProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import java.util.concurrent.Executor

@Configuration
@EnableAsync
@EnableScheduling
@Profile("!test")
class AsyncConfig(
    val dropProjectProperties: DropProjectProperties
) : AsyncConfigurer, org.dropProject.config.AsyncConfigurer {

    var asyncTimeout: Int = dropProjectProperties.async.timeout
        set(value) {
            field = value
            scheduler.timeout = value * 1000L
            LOG.info("Changed async execution timeout to $value seconds")
        }

    var asyncThreadPoolSize: Int = 1
        set(value) {
            field = value
            scheduler.poolSize = value
            LOG.info("Changed thread pool size to $value threads")
        }

    val LOG = LoggerFactory.getLogger("AsyncConfig")

    lateinit var scheduler: CancellableTaskScheduler

    @Autowired
    private val asyncUncaughtExceptionHandler : MyAsyncUncaughtExceptionHandler? = null

    override fun getAsyncUncaughtExceptionHandler(): MyAsyncUncaughtExceptionHandler {
        return asyncUncaughtExceptionHandler!!
    }

    @Bean(name=["asyncExecutor"])
    override fun getAsyncExecutor(): Executor? {
        LOG.info("Initializing task scheduler")

        scheduler = CancellableTaskScheduler(dropProjectProperties.async.timeout * 1000L)
        scheduler.poolSize = 1
        scheduler.initialize()
        return scheduler
    }

    override fun getTimeout(): Int {
        return asyncTimeout
    }

    override fun setTimeout(timeout: Int) {
        asyncTimeout = timeout
    }

    override fun getThreadPoolSize(): Int {
        return asyncThreadPoolSize
    }

    override fun setThreadPoolSize(numThreads: Int) {
        asyncThreadPoolSize = numThreads
    }
}
