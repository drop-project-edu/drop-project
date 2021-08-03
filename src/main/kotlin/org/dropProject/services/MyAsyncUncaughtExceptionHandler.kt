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

import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler
import org.springframework.stereotype.Service
import org.dropProject.dao.Submission
import org.dropProject.repository.SubmissionRepository
import java.lang.reflect.Method
import java.util.*



@Service
class MyAsyncUncaughtExceptionHandler(val submissionRepository: SubmissionRepository): SimpleAsyncUncaughtExceptionHandler() {

    override fun handleUncaughtException(ex: Throwable, method: Method, vararg params: Any) {
        super.handleUncaughtException(ex, method, *params)
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
