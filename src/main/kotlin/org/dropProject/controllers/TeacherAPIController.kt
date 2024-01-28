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
import org.dropProject.data.StudentHistory
import org.dropProject.data.SubmissionInfo
import org.dropProject.extensions.realName
import org.dropProject.repository.*
import org.dropProject.services.*
import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.ui.ModelMap
import org.springframework.web.bind.annotation.*
import java.nio.file.Files
import java.security.Principal
import java.util.HashMap
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
    val submissionService: SubmissionService,
    val gitSubmissionRepository: GitSubmissionRepository,
    val buildReportBuilder: BuildReportBuilder,
    val reportService: ReportService,
    val authorRepository: AuthorRepository,
    val projectGroupRepository: ProjectGroupRepository,
    val assignmentACLRepository: AssignmentACLRepository
) {

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    @GetMapping(value = ["/assignments/current"], produces = [MediaType.APPLICATION_JSON_VALUE])
    @JsonView(JSONViews.TeacherAPI::class)
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
        value = "Get the latest submissions by each group for the assignment identified by the assignmentID variable",
        response = SubmissionInfo::class, responseContainer = "List", ignoreJsonView = false
    )
    fun getAssignmentLatestSubmissions(@PathVariable assignmentId: String, model: ModelMap,
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
                throw AccessDeniedException("${principal.realName()} is not allowed to view this report")
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

    @GetMapping(value = ["/submissions/{submissionId}"], produces = [MediaType.APPLICATION_JSON_VALUE])
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

    @GetMapping(value = ["/studentHistory/{studentId}"], produces = [MediaType.APPLICATION_JSON_VALUE])
    @JsonView(JSONViews.TeacherAPI::class)
    @ApiOperation(value = "Get the student's student history")
    fun getStudentHistory(@PathVariable studentId: String,
                          principal: Principal, request: HttpServletRequest): ResponseEntity<StudentHistory> {
        val authorGroups = authorRepository.findByUserId(studentId)

        if (authorGroups.isNullOrEmpty()) {
            throw ResourceNotFoundException()
        }

        val projectGroups = projectGroupRepository.getGroupsForAuthor(studentId)

        // since there may be several authors (same student in different groups), we'll just choose the
        // first one, since the goals is to just get his name
        val studentHistory = StudentHistory(authorGroups[0])

        // store assignments on hashmap for performance reasons
        // the key is a pair (assignmentId, groupId), since a student can participate
        // in the same assignment with different groups
        val assignmentsMap = HashMap<Pair<String,Long>,Assignment>()

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

        return ResponseEntity.ok().body(studentHistory)
    }

    @GetMapping(value = ["/studentSearch/{query}"], produces = [MediaType.APPLICATION_JSON_VALUE])
    @JsonView(JSONViews.TeacherAPI::class)
    @ApiOperation(value = "Get all students that match the query value")
    fun searchStudents(@PathVariable("query") query: String): ResponseEntity<List<ReportController.StudentListResponse>> {
        val result = authorRepository.findAll()
            .filter { it.name.lowercase().contains(query.lowercase()) || it.userId.lowercase().contains(query.lowercase())}
            .distinctBy { it.userId }
            .map { ReportController.StudentListResponse(it.userId, it.name) }

        return ResponseEntity(result, HttpStatus.OK)
    }

    @GetMapping(value = ["/submissions/{submissionId}/markAsFinal"])
    @ApiOperation(value = "Mark the submission as final")
    fun markAsFinal(@PathVariable submissionId: Long, principal: Principal): Boolean {
        val submission = submissionRepository.findById(submissionId).orElse(null)
        val assignment = assignmentRepository.findById(submission.assignmentId).orElse(null)

        val acl = assignmentACLRepository.findByAssignmentId(assignment.id)
        if ((principal.realName() != assignment.ownerUserId && acl.find { it.userId == principal.realName() } == null)
            || submission.markedAsFinal) {
            return false
        }

        submissionService.markAsFinal(submission)
        submissionRepository.save(submission)

        return true
    }

    @GetMapping(value = ["/assignmentSearch/{query}"], produces = [MediaType.APPLICATION_JSON_VALUE])
    @JsonView(JSONViews.TeacherAPI::class)
    @ApiOperation(value = "Get all assignments that match the query value")
    fun searchAssignments(@PathVariable("query") query: String, principal: Principal): ResponseEntity<List<ReportController.StudentListResponse>> {
        val result = assignmentRepository.findAll()
            .filter {
                val acl = assignmentACLRepository.findByAssignmentId(it.id)
                !(principal.realName() != it.ownerUserId && acl.find { a -> a.userId == principal.realName() } == null)
            }
            .filter {
                it.tagsStr = assignmentService.getTagsStr(it)
                it.name.lowercase().contains(query.lowercase())
                        || it.id.lowercase().contains(query.lowercase())
                        || it.tagsStr?.map { t -> t.lowercase() }?.contains(query.lowercase()) == true
            }
            .distinctBy { it.id }
            .map { ReportController.StudentListResponse(it.id, it.name) }

        return ResponseEntity(result, HttpStatus.OK)
    }
}
