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
package org.dropProject.services

import org.dropProject.controllers.ResourceNotFoundException
import org.dropProject.dao.Assignment
import org.dropProject.dao.SubmissionStatus
import org.dropProject.data.StudentHistory
import org.dropProject.extensions.realName
import org.dropProject.repository.*
import org.springframework.stereotype.Service
import java.security.Principal
import java.util.HashMap

/**
 * Contains functionality related with students
 */
@Service
class StudentService(
    val authorRepository: AuthorRepository,
    val projectGroupRepository: ProjectGroupRepository,
    val submissionRepository: SubmissionRepository,
    val submissionReportRepository: SubmissionReportRepository,
    val assignmentRepository: AssignmentRepository,
    val assignmentTeacherFiles: AssignmentTeacherFiles,
    val buildReportBuilder: BuildReportBuilder,
    val assignmentACLRepository: AssignmentACLRepository
) {
    fun getStudentHistory(studentId: String, principal: Principal): StudentHistory? {
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
        val submissionIds = submissions.map { it.id }
        val submissionReports = submissionReportRepository.findBySubmissionIdIn(submissionIds)
        for (submission in submissions) {
            val assignmentAndGroup = Pair(submission.assignmentId, submission.group.id)
            val assignment = assignmentRepository.findById(submission.assignmentId).get()

            // fill indicators
            submission.reportElements = submissionReports.filter { it.submissionId == submission.id }
            submission.overdue = assignment.overdue(submission)
            submission.buildReport?.let {
                    buildReportDB ->
                val mavenizedProjectFolder = assignmentTeacherFiles.getProjectFolderAsFile(submission,
                    submission.getStatus() == SubmissionStatus.VALIDATED_REBUILT)
                val buildReport = buildReportBuilder.build(buildReportDB.buildReport.split("\n"),
                    mavenizedProjectFolder.absolutePath, assignment, submission)
                submission.ellapsed = buildReport.elapsedTimeJUnit()
                submission.teacherTests = buildReport.junitSummaryAsObject()
            }

            if (!assignmentsMap.containsKey(assignmentAndGroup)) {

                val acl = assignmentACLRepository.findByAssignmentId(submission.assignmentId)

                if (principal.realName() != assignment.ownerUserId && acl.find { it.userId == principal.realName() } == null) {
                    continue
                }

                assignmentsMap[assignmentAndGroup] = assignment
                studentHistory.addGroupAndAssignment(submission.group, assignment)
            }

            studentHistory.addSubmission(submission)
        }

        studentHistory.ensureSubmissionsAreSorted()

        return studentHistory
    }
}