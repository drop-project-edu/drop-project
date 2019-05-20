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
package org.dropProject.forms

import org.dropProject.Constants
import org.hibernate.validator.constraints.NotEmpty
import org.springframework.format.annotation.DateTimeFormat
import org.dropProject.dao.Language
import org.dropProject.dao.TestVisibility
import java.time.LocalDateTime
import javax.validation.constraints.Min
import javax.validation.constraints.NotNull

enum class SubmissionMethod {
    UPLOAD, GIT
}

data class AssignmentForm(
        @field:NotEmpty(message = "Error: Assignment Id must not be empty")
        var assignmentId: String? = null,

        @field:NotEmpty(message = "Error: Assignment Name must not be empty")
        var assignmentName: String? = null,

        var assignmentPackage: String? = null,

        @field:NotNull(message = "Error: Language must not be empty")
        var language: Language? = null,

        @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        var dueDate: LocalDateTime? = null,

        var acceptsStudentTests: Boolean = false,
        var minStudentTests: Int? = null,
        var calculateStudentTestsCoverage: Boolean = false,
        var hiddenTestsVisibility: TestVisibility? = null,
        var cooloffPeriod: Int? = null,

        @field:Min(value=32, message="Error: Max memory must be >= 32")
        var maxMemoryMb: Int? = null,

        var showLeaderBoard: Boolean = false,

        var assignees: String? = null,

        var editMode: Boolean = false,

        @field:NotNull(message = "Error: Submission Method must not be empty")
        var submissionMethod: SubmissionMethod? = null,

        @field:NotEmpty(message = "Error: Git repository must not be empty")
        var gitRepositoryUrl: String? = null,

        var acl: String? = null
)
    
