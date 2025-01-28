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
import org.dropProject.dao.Assignment
import org.dropProject.dao.Indicator
import org.dropProject.dao.SubmissionMode
import org.dropProject.data.AssignmentInfoResponse
import org.dropProject.data.JSONViews
import org.dropProject.data.SubmissionResult
import org.dropProject.extensions.realName
import org.dropProject.forms.UploadForm
import org.dropProject.repository.AssigneeRepository
import org.dropProject.repository.AssignmentRepository
import org.dropProject.services.*
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.security.Principal
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.validation.Valid


@RestController
@RequestMapping("/api/student")
@Api(authorizations = [Authorization(value="basicAuth")],
    value="Student API",
    description = "All endpoints must be authenticated using a personal token, sent with the standard \"basic auth\" header.<br/>" +
            "Using curl, it's as simple as including <pre>-u user:token</pre>")
class StudentAPIController(
    val assignmentRepository: AssignmentRepository,
    val assigneeRepository: AssigneeRepository,
    val submissionService: SubmissionService,
    val assignmentService: AssignmentService,
    val reportService: ReportService,
    val assignmentTeacherFiles: AssignmentTeacherFiles
) {

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    @GetMapping(value = ["/assignments/current"], produces = [MediaType.APPLICATION_JSON_VALUE])
    @JsonView(JSONViews.StudentAPI::class)  // to publish only certain fields of the Assignment
    @ApiOperation(value = "Get all the current assignments assigned or associated with the person identified by the \"basic auth\" header",
        response = Assignment::class, responseContainer = "List", ignoreJsonView = false)
    fun getCurrentAssignments(principal: Principal, request: HttpServletRequest): ResponseEntity<List<Assignment>> {

        val assignments = assignmentService.getMyAssignments(principal, archived = false)
            .filter { if (request.isUserInRole("TEACHER")) true else it.active }
            .map {
                val instructions = assignmentTeacherFiles.getInstructions(it)
                if (instructions.format == AssignmentInstructionsFormat.MD) {
                    // when called through the API, always use HTML
                    // TODO change this when the plugin is capable of rendering markdown
                    instructions.format = AssignmentInstructionsFormat.HTML
                }
                it.copy(instructions = instructions)
            }

        return ResponseEntity.ok(assignments)
    }

    @RequestMapping(value = ["/submissions/new"], method = [(RequestMethod.POST)], produces = [MediaType.APPLICATION_JSON_VALUE])
    @ApiOperation(value = "Upload a submission (zip) file. This is an asynchronous operation: " +
            "you get a submissionId for a pending submission. You then have to check (polling) when the build results are available." +
            "Example using curl: curl -u student1:2RVawLImDTV0betF17P2 -F \"assignmentId=sampleJavaProject\" -F \"file=@/Users/pedroalves/Downloads/sampleJavaSubmission.zip\" http://localhost:8080/api/student/submissions/new")
    fun upload(@Valid @ModelAttribute("uploadForm") uploadForm: UploadForm,
               bindingResult: BindingResult,
               @RequestParam("file") file: MultipartFile,
               principal: Principal,
               request: HttpServletRequest
    ): ResponseEntity<SubmissionResult> {

        return submissionService.uploadSubmission(bindingResult, uploadForm, request, principal, file,
            assignmentRepository, assignmentService, submissionMode = SubmissionMode.API)
    }

    @RequestMapping(value = ["/submissions/{submissionId}"], method = [(RequestMethod.GET)], produces = [MediaType.APPLICATION_JSON_VALUE])
    @JsonView(JSONViews.StudentAPI::class)  // to publish only certain fields of the Assignment
    @ApiOperation(value = "Get the build report associated with this submission")
    fun getBuildReport(@PathVariable submissionId: Long, principal: Principal,
                       request: HttpServletRequest,
                       response: HttpServletResponse): ResponseEntity<FullBuildReport> {

        val report = reportService.buildReport(submissionId, principal, request)

        // remove results from hidden tests
        report.summary?.removeIf { it.reportKey == Indicator.HIDDEN_UNIT_TESTS.code}

        return when {
            report.autoRefresh == true -> {
                // force a response since this http code messes up spring security, redirecting to login page
                response.status = HttpServletResponse.SC_ACCEPTED
                response.flushBuffer()
                ResponseEntity.status(HttpStatus.ACCEPTED).build()  // this line is not doing anything...
            }
            report.error != null -> ResponseEntity.internalServerError().body(report)
            else -> ResponseEntity.ok().body(report)
        }

    }

    @RequestMapping(value = ["/assignments/{assignmentID}"], method = [(RequestMethod.GET)], produces = [MediaType.APPLICATION_JSON_VALUE])
    @JsonView(JSONViews.StudentAPI::class)  // to publish only certain fields of the Assignment
    @ApiOperation(value = "Get specific assignment information")
    fun getAssignmentInfo(principal: Principal, @PathVariable assignmentID: String) : ResponseEntity<AssignmentInfoResponse> {

        //control access on w-list
        assignmentService.checkAssignees(assignmentID, principal.realName())

        val assignmentInfoResponse = AssignmentInfoResponse()

        var assignment = assignmentRepository.findById(assignmentID).orElse(null)

        assignment?.instructions = assignmentTeacherFiles.getInstructions(assignment)

        if (assignment == null) {
            assignmentInfoResponse.errorCode = 404
        } else if (!assignment.active) {
            assignment=null
            assignmentInfoResponse.errorCode = 403
        }
        assignmentInfoResponse.assignment = assignment

        return ResponseEntity.ok().body(assignmentInfoResponse)

    }

}
