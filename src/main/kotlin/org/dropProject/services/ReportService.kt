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

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonView
import jakarta.persistence.EntityNotFoundException
import org.dropProject.AsyncConfigurer
import org.dropProject.dao.*
import org.dropProject.data.AuthorDetails
import org.dropProject.data.JSONViews
import org.dropProject.extensions.realName
import org.dropProject.repository.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.MessageSource
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import java.io.File
import java.security.Principal
import java.util.*
import jakarta.servlet.http.HttpServletRequest

@JsonInclude(JsonInclude.Include.NON_EMPTY)  // exclude nulls fields and empty lists from serialization
class FullBuildReport(
    @JsonView(JSONViews.StudentAPI::class)
    var numSubmissions: Long? = null,
    @JsonView(JSONViews.StudentAPI::class)
    var assignment: Assignment? = null,
    @JsonView(JSONViews.StudentAPI::class)
    var submission: Submission? = null,
    var gitSubmission: GitSubmission? = null,
    var gitRepository: String? = null,
    var gitRepositoryWithHash: String? = null,
    var readmeHtml: String? = null,
    @JsonView(JSONViews.StudentAPI::class)
    var error: String? = null,
    var isValidating: Boolean? = null,
    @JsonView(JSONViews.StudentAPI::class)
    var summary: MutableList<SubmissionReport>? = null,
    @JsonView(JSONViews.StudentAPI::class)
    var structureErrors: List<String>? = null,
    var authors: ArrayList<AuthorDetails>? = null,
    @JsonView(JSONViews.StudentAPI::class)
    var buildReport: org.dropProject.data.BuildReport? = null
)

/**
 * Contains functionality related with reports
 */
@Service
class ReportService(
    val assignmentRepository: AssignmentRepository,
    val submissionRepository: SubmissionRepository,
    val gitSubmissionRepository: GitSubmissionRepository,
    val submissionGitInfoRepository: SubmissionGitInfoRepository,
    val submissionReportRepository: SubmissionReportRepository,
    val buildReportRepository: BuildReportRepository,
    val assignmentTeacherFiles: AssignmentTeacherFiles,
    val buildReportBuilder: BuildReportBuilder,
    val i18n: MessageSource,
    val gitClient: GitClient,
    val asyncConfigurer: AsyncConfigurer,
    val markdownRenderer: MarkdownRenderer,
) {

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    @Value("\${spring.web.locale}")
    val currentLocale : Locale = Locale.getDefault()

    fun buildReport(submissionId: Long, principal: Principal,
                    request: HttpServletRequest): FullBuildReport {

        val fullBuildReport = FullBuildReport()

        val submission = submissionRepository.findById(submissionId).orElse(null)

        if (submission != null) {

            // check that principal belongs to the group that made this submission
            if (!request.isUserInRole("TEACHER")) {
                val groupElements = submission.group.authors
                if (groupElements.filter { it -> it.userId == principal.realName() }.isEmpty()) {
                    throw org.springframework.security.access.AccessDeniedException("${principal.realName()} is not allowed to view this report")
                }
            }

            if (submission.getStatus() == SubmissionStatus.DELETED) {
                throw AccessDeniedException("This submission was deleted")
            }

            fullBuildReport.numSubmissions = submissionRepository.countBySubmitterUserIdAndAssignmentId(submission.submitterUserId, submission.assignmentId)

            val assignment = assignmentRepository.findById(submission.assignmentId).orElse(null)

            fullBuildReport.assignment = assignment
            fullBuildReport.submission = submission
            submission.gitSubmissionId?.let {
                    gitSubmissionId ->
                val gitSubmission = gitSubmissionRepository.findById(gitSubmissionId)
                    .orElseThrow { EntityNotFoundException("GitSubmission ${gitSubmissionId} not found") }
                val submissionGitInfo = submissionGitInfoRepository.getBySubmissionId(submissionId)
                fullBuildReport.gitSubmission = gitSubmission
                fullBuildReport.gitRepository = gitClient.convertSSHGithubURLtoHttpURL(gitSubmission.gitRepositoryUrl)
                if (submissionGitInfo != null) {
                    fullBuildReport.gitRepositoryWithHash = "${fullBuildReport.gitRepository}/tree/${submissionGitInfo.gitCommitHash}"
                }
            }

            // check README
            val mavenizedProjectFolder = assignmentTeacherFiles.getProjectFolderAsFile(submission,
                submission.getStatus() == SubmissionStatus.VALIDATED_REBUILT)
            if (File(mavenizedProjectFolder, "README.txt").exists()) {
                val readmeContent = File(mavenizedProjectFolder, "README.txt").readText()
                fullBuildReport.readmeHtml = "<pre>$readmeContent</pre>"
            } else if (File(mavenizedProjectFolder, "README.md").exists()) {

                val readmeContent = File(mavenizedProjectFolder, "README.md").readText()
                val htmlContent = markdownRenderer.render(readmeContent,
                    "${submissionId}/",
                    "${submissionId}/")
                fullBuildReport.readmeHtml = "<hr/>\n$htmlContent<hr/>\n"
            }

            // check the submission status
            when (submission.getStatus()) {
                SubmissionStatus.ILLEGAL_ACCESS -> fullBuildReport.error = i18n.getMessage("student.build-report.illegalAccess", null, currentLocale)
                SubmissionStatus.FAILED -> {
                    fullBuildReport.error = i18n.getMessage("student.build-report.failed", null, currentLocale)

                    // in this case, it may be useful to show the maven output
                    submission.buildReport?.let {
                            buildReportDB ->
                        fullBuildReport.buildReport = buildReportBuilder.build(buildReportDB.buildReport.split("\n"),
                            mavenizedProjectFolder.absolutePath, assignment, submission)
                    }
                }
                SubmissionStatus.ABORTED_BY_TIMEOUT -> fullBuildReport.error = i18n.getMessage("student.build-report.abortedByTimeout", arrayOf(
                    asyncConfigurer.getTimeout()
                ), currentLocale)
                SubmissionStatus.TOO_MUCH_OUTPUT -> fullBuildReport.error = i18n.getMessage("student.build-report.tooMuchOutput", null, currentLocale)
                SubmissionStatus.DELETED -> fullBuildReport.error = i18n.getMessage("student.build-report.deleted", null, currentLocale)
                SubmissionStatus.SUBMITTED, SubmissionStatus.SUBMITTED_FOR_REBUILD, SubmissionStatus.REBUILDING -> {
                    fullBuildReport.error = i18n.getMessage("student.build-report.submitted", null, currentLocale)
                    fullBuildReport.isValidating = true
                }
                SubmissionStatus.VALIDATED, SubmissionStatus.VALIDATED_REBUILT -> {
                    val submissionReport = submissionReportRepository.findBySubmissionId(submission.id)

                    // fill the assignment in the reports
                    submissionReport.forEach { it.assignment = assignment }

                    fullBuildReport.summary = submissionReport.toMutableList()
                    fullBuildReport.structureErrors = submission.structureErrors?.split(";") ?: emptyList<String>()

                    val authors = ArrayList<AuthorDetails>()
                    for (authorDB in submission.group.authors) {
                        authors.add(AuthorDetails(name = authorDB.name, number = authorDB.userId,
                            submitter = submission.submitterUserId == authorDB.userId))
                    }
                    fullBuildReport.authors = authors

                    submission.buildReport?.let {
                            buildReportDB ->
                        fullBuildReport.buildReport = buildReportBuilder.build(buildReportDB.buildReport.split("\n"),
                            mavenizedProjectFolder.absolutePath, assignment, submission)
                    }
                }
            }
        }

        return fullBuildReport

    }
}
