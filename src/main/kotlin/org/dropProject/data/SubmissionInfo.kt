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
package org.dropproject.data

import com.fasterxml.jackson.annotation.JsonView
import org.dropproject.dao.ProjectGroup
import org.dropproject.dao.Submission

/**
 * Represents the [Submission]s that a [ProjectGroup] did for a certain [Assignment].
 *
 * @property projectGroup is a ProjectGroup
 * @property lastSubmission is the last (most recent) [Submission] performed by the ProjectGroup
 * @property allSubmissions is a List of [Submission]s
 */
data class SubmissionInfo(
    @JsonView(JSONViews.TeacherAPI::class)
    val projectGroup: ProjectGroup,
    @JsonView(JSONViews.TeacherAPI::class)
    val lastSubmission: Submission,
    @JsonView(JSONViews.TeacherAPI::class)
    val allSubmissions: List<Submission>) {
}
