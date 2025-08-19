/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2024 Pedro Alves
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

import com.fasterxml.jackson.annotation.JsonView
import org.dropproject.dao.Assignment
import org.dropproject.data.JSONViews
import org.dropproject.data.StudentHistory
import org.dropproject.extensions.realName
import org.dropproject.repository.*
import org.springframework.stereotype.Service
import java.security.Principal
import java.util.HashMap

/**
 * Contains functionality related with students
 */

data class StudentListResponse(
    @JsonView(JSONViews.TeacherAPI::class)
    val value: String,
    @JsonView(JSONViews.TeacherAPI::class)
    val text: String
)

@Service
class StudentService(
    val authorRepository: AuthorRepository,
    val projectGroupRepository: ProjectGroupRepository,
    val submissionRepository: SubmissionRepository,
    val submissionReportRepository: SubmissionReportRepository,
    val assignmentRepository: AssignmentRepository,
    val assignmentTeacherFiles: AssignmentTeacherFiles,
    val assignmentACLRepository: AssignmentACLRepository,
    val submissionService: SubmissionService
) {
    /**
     * @param teacherPrincipal if the request is made by a student, this parameter is null. otherwise, it
     * is the principal associated with the session. This is to prevent teachers from seeing submissions
     * to assignments that they don't have access
     */
    fun getStudentHistory(studentId: String, teacherPrincipal: Principal? = null): StudentHistory? {
        val authorGroups = authorRepository.findByUserId(studentId)

        if (authorGroups.isNullOrEmpty()) {
            return null
        }

        val projectGroups = projectGroupRepository.getGroupsForAuthor(studentId)

        // since there may be several authors (same student in different groups), we'll just choose the
        // first one, since the goals is to just get his name
        val studentHistory = StudentHistory(authorGroups[0])

        // store assignments on hashmap for performance reasons
        // the key is a pair (assignmentId, groupId), since a student can participate
        // in the same assignment with different groups
        val assignmentsMap = HashMap<Pair<String,Long>, Assignment>()

        val submissions = submissionRepository.findByGroupIn(projectGroups)

        submissionService.fillIndicatorsFor(submissions)

        for (submission in submissions) {
            submission.submitterName = authorRepository.findByUserId(submission.submitterUserId)?.last()?.name
            val assignmentAndGroup = Pair(submission.assignmentId, submission.group.id)
            val assignment = assignmentRepository.findById(submission.assignmentId).get()

            if (!assignmentsMap.containsKey(assignmentAndGroup)) {

                if (teacherPrincipal != null) {
                    val acl = assignmentACLRepository.findByAssignmentId(submission.assignmentId)
                    if (teacherPrincipal.realName() != assignment.ownerUserId && acl.find { it.userId == teacherPrincipal.realName() } == null) {
                        continue
                    }
                }

                assignmentsMap[assignmentAndGroup] = assignment
                studentHistory.addGroupAndAssignment(submission.group, assignment)
            }

            studentHistory.addSubmission(submission)
        }

        studentHistory.ensureSubmissionsAreSorted()

        return studentHistory
    }

    fun getStudentList(query: String): List<StudentListResponse> =
        authorRepository.findAll()
            .filter { it.name.lowercase().contains(query.lowercase()) || it.userId.lowercase().contains(query.lowercase())}
            .distinctBy { it.userId }
            .map { StudentListResponse(it.userId, it.name) }
}