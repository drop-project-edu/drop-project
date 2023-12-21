/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2021 Pedro Alves
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
package org.dropProject.controllers

import com.fasterxml.jackson.annotation.JsonView
import io.swagger.annotations.*
import org.dropProject.dao.*
import org.dropProject.data.JSONViews
import org.dropProject.data.SubmissionInfo
import org.dropProject.extensions.realName
import org.dropProject.repository.*
import org.dropProject.services.*
import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.ui.ModelMap
import org.springframework.web.bind.annotation.*
import java.nio.file.Files
import java.security.Principal
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


@RestController
@RequestMapping("/api/teacher")
@Api(
    authorizations = [Authorization(value = "basicAuth")],
    value = "Teacher API",
    description = "All endpoints must be authenticated using a personal token associated with the TEACHER role, " +
            "sent with the standard \"basic auth\" header.<br/>" +
            "Using curl, it's as simple as including <pre>-u user:token</pre>"
)
class TeacherAPIController(
    val assignmentRepository: AssignmentRepository,
    val assigneeRepository: AssigneeRepository,
    val assignmentService: AssignmentService,
    val assignmentTeacherFiles: AssignmentTeacherFiles,
    val zipService: ZipService,
    val submissionRepository: SubmissionRepository,
    val submissionReportRepository: SubmissionReportRepository,
    val gitSubmissionRepository: GitSubmissionRepository,
    val buildReportBuilder: BuildReportBuilder,
    val reportService: ReportService
) {

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    @GetMapping(value = ["/assignments/current"], produces = [MediaType.APPLICATION_JSON_VALUE])
    @JsonView(JSONViews.TeacherAPI::class)  // to publish only certain fields of the Assignment
    @ApiOperation(
        value = "Get all the current assignments created or visible to the person identified by the \"basic auth\" header",
        response = Assignment::class, responseContainer = "List", ignoreJsonView = false
    )
    fun getCurrentAssignments(principal: Principal): ResponseEntity<List<Assignment>> {
        val assignments = assignmentService.getMyAssignments(principal, archived = false)
        return ResponseEntity.ok(assignments)
    }

    @Suppress("UNCHECKED_CAST")
    @GetMapping(value = ["/assignments/{assignmentId}/submissions"], produces = [MediaType.APPLICATION_JSON_VALUE])
    @JsonView(JSONViews.TeacherAPI::class)
    @ApiOperation(
        value = "Get all the submissions for the assignment identified by the assignmentID variable",
        response = SubmissionInfo::class, responseContainer = "List", ignoreJsonView = false
    )
    fun getAssignmentLatestSubmission(@PathVariable assignmentId: String, model: ModelMap,
                                      principal: Principal, request: HttpServletRequest): ResponseEntity<List<SubmissionInfo>> {
        assignmentService.getAllSubmissionsForAssignment(assignmentId, principal, model, request, mode = "summary")

        val result = (model["submissions"] as List<SubmissionInfo>).map{
            SubmissionInfo(it.projectGroup, it.lastSubmission, listOf())
        }

        return ResponseEntity.ok(result)
    }

    @GetMapping(value = ["/assignments/{assignmentId}/submissions/{groupId}"])
    @JsonView(JSONViews.TeacherAPI::class)
    @ApiOperation(
        value = "",
        response = Submission::class, responseContainer = "List", ignoreJsonView = false
    )
    fun getGroupAssignmentSubmissions(@PathVariable assignmentId: String, @PathVariable groupId: Long, model: ModelMap,
                                      principal: Principal, request: HttpServletRequest): ResponseEntity<List<Submission>> {
        val assignment = assignmentRepository.getById(assignmentId)

        val submissions = submissionRepository
            .findByGroupAndAssignmentIdOrderBySubmissionDateDescStatusDateDesc(ProjectGroup(groupId), assignmentId)
            .filter { it.getStatus() != SubmissionStatus.DELETED }

        for (submission in submissions) {
            val reportElements = submissionReportRepository.findBySubmissionId(submission.id)
            submission.reportElements = reportElements
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
        }

        return ResponseEntity.ok(submissions)
    }

    @GetMapping(value = ["/download/{submissionId}"], produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    @JsonView(JSONViews.TeacherAPI::class)
    @ApiOperation(value = "Download the mavenized zip file for this submission")
    fun downloadProject(@PathVariable submissionId: Long, principal: Principal,
                        request: HttpServletRequest, response: HttpServletResponse): FileSystemResource {

        val submission = submissionRepository.findById(submissionId).orElse(null)
        if (submission != null) {

            // check that principal belongs to the group that made this submission
            if (!request.isUserInRole("TEACHER")) {
                throw org.springframework.security.access.AccessDeniedException("${principal.realName()} is not allowed to view this report")
            }

            val projectFolder = assignmentTeacherFiles.getProjectFolderAsFile(submission,
                wasRebuilt = submission.getStatus() == SubmissionStatus.VALIDATED_REBUILT)

            if (!Files.exists(projectFolder.toPath())) {
                throw ResourceNotFoundException()
            }

            LOG.info("[${principal.realName()}] downloaded ${projectFolder.name}")

            val zipFilename = submission.group.authorsIdStr().replace(",", "_") + "_mavenized"
            val zipFile = zipService.createZipFromFolder(zipFilename, projectFolder)

            LOG.info("Created ${zipFile.file.absolutePath}")

            response.setHeader("Content-Disposition", "attachment; filename=${zipFilename}.zip")

            return FileSystemResource(zipFile.file)

        } else {
            throw ResourceNotFoundException()
        }
    }

    @RequestMapping(value = ["/submissions/{submissionId}"], method = [(RequestMethod.GET)], produces = [MediaType.APPLICATION_JSON_VALUE])
    @JsonView(JSONViews.TeacherAPI::class)
    @ApiOperation(value = "Get the build report associated with this submission")
    fun getBuildReport(@PathVariable submissionId: Long, principal: Principal,
                       request: HttpServletRequest): ResponseEntity<FullBuildReport> {

        val report = reportService.buildReport(submissionId, principal, request)

        return if (report.error != null) {
            ResponseEntity.internalServerError().body(report)
        } else {
            ResponseEntity.ok().body(report)
        }
    }
}
