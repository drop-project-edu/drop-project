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

import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.dropProject.dao.Assignment
import org.dropProject.dao.GitSubmission
import org.dropProject.dao.Submission
import org.dropProject.dao.SubmissionStatus
import org.dropProject.data.SubmissionInfo
import org.dropProject.data.TestType
import org.dropProject.repository.*
import java.util.ArrayList

/**
 * Contains functionality related with Submissions (for example, get submissions from the database).
 */
@Service
class SubmissionService(
        val submissionRepository: SubmissionRepository,
        val submissionReportRepository: SubmissionReportRepository,
        val buildReportRepository: BuildReportRepository,
        val assignmentTeacherFiles: AssignmentTeacherFiles,
        val buildReportBuilder: BuildReportBuilder) {

    /**
     * Returns all the SubmissionInfo objects related with [assignment].
     * @param assignment is the target Assignment
     * @return an ArrayList with SubmissionInfo objects
     */
    fun getSubmissionsList(assignment: Assignment): ArrayList<SubmissionInfo> {
        val submissions = submissionRepository
                .findByAssignmentId(assignment.id)
                .filter { it.getStatus() != SubmissionStatus.DELETED }

        val submissionsByGroup = submissions.groupBy { it -> it.group }

        val submissionInfoList = ArrayList<SubmissionInfo>()
        for ((group, submissionList) in submissionsByGroup) {
            val sortedSubmissionList =
                    submissionList.sortedWith(compareByDescending<Submission> { it.submissionDate }
                            .thenByDescending { it.statusDate })

            // check if some submission has been marked as final. in that case, that goes into the start of the list
            var lastSubmission = sortedSubmissionList[0]
            for (submission in sortedSubmissionList) {
                if (submission.markedAsFinal) {
                    lastSubmission = submission
                    break
                }
            }

            lastSubmission.buildReportId?.let {
                buildReportId ->
                    val reportElements = submissionReportRepository.findBySubmissionId(lastSubmission.id)
                    lastSubmission.reportElements = reportElements

                    val mavenizedProjectFolder = assignmentTeacherFiles.getProjectFolderAsFile(lastSubmission,
                            lastSubmission.getStatus() == SubmissionStatus.VALIDATED_REBUILT)
                    val buildReportDB = buildReportRepository.getById(buildReportId)
                    val buildReport = buildReportBuilder.build(buildReportDB.buildReport.split("\n"),
                            mavenizedProjectFolder.absolutePath, assignment, lastSubmission)
                    lastSubmission.ellapsed = buildReport.elapsedTimeJUnit()
                    lastSubmission.teacherTests = buildReport.junitSummaryAsObject(TestType.TEACHER)
                    lastSubmission.hiddenTests = buildReport.junitSummaryAsObject(TestType.HIDDEN)
                    if (buildReport.jacocoResults.isNotEmpty()) {
                        lastSubmission.coverage = buildReport.jacocoResults[0].lineCoveragePercent
                    }

                    lastSubmission.testResults = buildReport.testResults()
            }

            submissionInfoList.add(SubmissionInfo(group, lastSubmission, sortedSubmissionList))
        }

        return submissionInfoList
    }

    /**
     * Marks a [Submission] as final, and all other submissions for the same group and assignment as not final
     * @param submission is the Submission to mark as final
     */
    fun markAsFinal(submission: Submission) {
        val otherSubmissions = submissionRepository.findByGroupAndAssignmentIdOrderBySubmissionDateDescStatusDateDesc(submission.group, submission.assignmentId)
        for (otherSubmission in otherSubmissions) {
            otherSubmission.markedAsFinal = false
            submissionRepository.save(submission)
        }

        submission.markedAsFinal = true
    }
}
