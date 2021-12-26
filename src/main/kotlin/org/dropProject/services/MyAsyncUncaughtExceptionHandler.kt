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
package org.dropProject.services

import org.dropProject.PendingTaskError
import org.dropProject.PendingTasks
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler
import org.springframework.stereotype.Service
import org.dropProject.dao.Submission
import org.dropProject.dao.SubmissionStatus
import org.dropProject.repository.SubmissionRepository
import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.util.*
import kotlin.reflect.full.memberFunctions


@Service
class MyAsyncUncaughtExceptionHandler(val submissionRepository: SubmissionRepository,
                                      val pendingTasks: PendingTasks): SimpleAsyncUncaughtExceptionHandler() {

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    override fun handleUncaughtException(ex: Throwable, method: Method, vararg params: Any) {

        val methodThatThrewTheException = method.name

        LOG.error("Uncaught exception from an async method", ex)

        // since the method comparison is not typesafe, I minimize the chance of error with this
        require(AssignmentService::class.memberFunctions.any { it.name == "exportAssignment" })
        require(BuildWorker::class.memberFunctions.any { it.name == "checkProject" })

        when (methodThatThrewTheException) {
            "exportAssignment" -> {
                val assignmentId = params[0] as String
                val taskId = params[2] as String
                pendingTasks.put(taskId, PendingTaskError(ex))
            }

            "checkProject" -> {
                val submission = params[2] as Submission
                submission.setStatus(SubmissionStatus.FAILED)
                submissionRepository.save(submission)
            }

            else -> super.handleUncaughtException(ex, method, *params)
        }

        // TODO: test this for build_report table
//        if (params.size >= 3) {
//            val submission = params[2] as Submission
//            submission.buildReport = null  // clear buildReport to prevent max_packet_size errors on mySQL
//            submission.status = "Failed"
//            submission.statusDate = Date()
//            submissionRepository.save(submission)
//        }
    }
}
