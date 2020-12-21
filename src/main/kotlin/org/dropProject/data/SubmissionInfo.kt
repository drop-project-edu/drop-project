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
package org.dropProject.data

import org.dropProject.dao.ProjectGroup
import org.dropProject.dao.Submission


// Created by palves

/**
 * Represents the Submissions that a [ProjectGroup] does in a certain [Assignment].
 * @property projectGroup is a ProjectGroup
 * @property lastSubmission is the last (most recent) [Submission] performed by the ProjectGroup
 * @property allSubmissions is a List of [Submission]s
 */
data class SubmissionInfo(val projectGroup: ProjectGroup,
                     val lastSubmission: Submission,
                     val allSubmissions: List<Submission>) {
}
