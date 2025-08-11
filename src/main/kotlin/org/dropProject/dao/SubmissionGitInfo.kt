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
package org.dropProject.dao

import jakarta.persistence.*

/**
 * Represents specific git information for submissions. This is different from the class [GitSubmission]
 * because [GitSubmission] represents a single connection to a git repository that used by several submissions.
 *
 * This class represents specific information for each submission.
 *
 * @property id is a primary-key like generated value
 * @property submissionId is an id of a [Submission] (FK)
 * @property gitCommitHash is the git hash associated with the commit that corresponds to this submission
 */
@Entity
class SubmissionGitInfo(
        @Id @GeneratedValue val id: Long = 0,
        @Column(unique = true) val submissionId: Long, // FK for Submission
        val gitCommitHash: String)
