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

import org.dropProject.services.GitClient
import java.text.SimpleDateFormat
import java.util.*
import jakarta.persistence.*

/**
 * Represents a [GitSubmission] which is a [Submission] performed by connecting to a GitHub repository and pulling
 * it's code.
 *
 * @property id is a Long with a primary-key like generated value
 * @property assignmentId is a String identifying the relevant Assignment
 * @property submitterUserId is a String identifying the user that performed the submission
 * @property createDate is a Date
 * @property connected is a Boolean
 * @property lastCommitDate is a Date with the date of the last commit performed in the GitHub repository
 * @property lastSubmissionId is a Long
 * @property gitRepositoryUrl is a String with the URL of the GitHub repository
 * @property gitRepositoryPubKey is a String
 * @property gitRepositoryPrivKey is a String
 * @property group is a [ProjectGroup]
 */
@Entity
@Table(uniqueConstraints=[UniqueConstraint(columnNames = ["submitterUserId", "assignmentId"])])
data class GitSubmission(
        @Id @GeneratedValue
        val id: Long = 0,

        @Column(length = 50)
        val assignmentId: String,
        var submitterUserId: String,

        val createDate: Date = Date(),

        var connected: Boolean = false,
        var lastCommitDate: Date? = null,
        var lastSubmissionId: Long? = null,  // FK submission.id

        val gitRepositoryUrl: String,
        @Column(columnDefinition = "TEXT")
        var gitRepositoryPubKey: String? = null,
        @Column(columnDefinition = "TEXT")
        var gitRepositoryPrivKey: String? = null

) {
    @ManyToOne
    lateinit var group: ProjectGroup

    constructor(assignmentId: String, submitterUserId: String, gitRepositoryUrl: String, group: ProjectGroup) :
            this(assignmentId = assignmentId, submitterUserId = submitterUserId, gitRepositoryUrl = gitRepositoryUrl) {
        this.group = group
    }

    fun getFolderRelativeToStorageRoot() : String {
        val repoName = GitClient().getGitRepoInfo(gitRepositoryUrl).second
        return "${getParentFolderRelativeToStorageRoot()}/${createDate.time}-${repoName}"
    }

    // returns the parent folder of the repo (needed for the export)
    fun getParentFolderRelativeToStorageRoot() : String {
        return "${assignmentId}/${SimpleDateFormat("w-yy").format(createDate)}"
    }

}
